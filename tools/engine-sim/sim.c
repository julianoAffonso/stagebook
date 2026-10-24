// Simulador do motor (engine.c) no Mac, sem o tablet e sem a mesa.
//
// Uma USB interface falsa responde aos ioctl do usbfs no ritmo real (cada URB = 8 pacotes de 125 µs)
// e devolve a entrada no relógio da mesa. O mesmo programa compila com o engine.c validado (tag
// audio-validado) e com o atual, para comparar os dois. Ver run.sh.
#include <errno.h>
#include <math.h>
#include <pthread.h>
#include <stdarg.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/ioctl.h>
#include <sys/resource.h>
#include <time.h>
#include <unistd.h>

int sim_verbose = 0;
#define gettid() 0

void *sim_calloc(size_t n, size_t s);
void sim_free(void *p);
int sim_usleep(useconds_t us);
int fake_ioctl(int fd, unsigned long req, ...);

#define calloc sim_calloc
#define free sim_free
#define usleep sim_usleep
#define ioctl fake_ioctl
#define stb_vorbis_seek_frame sim_seek_frame
#define stb_vorbis_get_samples_float_interleaved sim_get_samples
#include ENGINE_C
#undef calloc
#undef free
#undef usleep
#undef ioctl
#undef stb_vorbis_seek_frame
#undef stb_vorbis_get_samples_float_interleaved

int stb_vorbis_seek_frame(stb_vorbis *f, unsigned int sample_number);
int stb_vorbis_get_samples_float_interleaved(stb_vorbis *f, int channels, float *buffer, int num_floats);

static double now(void) {
    struct timespec ts;
    clock_gettime(CLOCK_MONOTONIC, &ts);
    return ts.tv_sec + ts.tv_nsec / 1e9;
}

// ---- ganchos: disco lento, CPU quente, memória ----
static volatile int slow_seek_ms = 0, slow_decode_ms = 0, slow_decode_every = 0;
static volatile long decode_calls = 0, usleep_calls = 0;

int sim_seek_frame(stb_vorbis *f, unsigned int s) {
    if (slow_seek_ms) { struct timespec t = {0, slow_seek_ms * 1000000L}; nanosleep(&t, NULL); }
    return stb_vorbis_seek_frame(f, s);
}
int sim_get_samples(stb_vorbis *f, int ch, float *b, int n) {
    long k = __sync_add_and_fetch(&decode_calls, 1);
    if (slow_decode_ms && slow_decode_every && k % slow_decode_every == 0) {
        struct timespec t = {0, slow_decode_ms * 1000000L}; nanosleep(&t, NULL);
    }
    return stb_vorbis_get_samples_float_interleaved(f, ch, b, n);
}
int sim_usleep(useconds_t us) { __sync_add_and_fetch(&usleep_calls, 1); return usleep(us); }

#define MAXP 4096
static pthread_mutex_t mem_mx = PTHREAD_MUTEX_INITIALIZER;
static void *live[MAXP];
static int nlive = 0;
static volatile int freed_while_queued = 0;
static int queued(void *p);

void *sim_calloc(size_t n, size_t s) {
    void *p = calloc(n, s);
    pthread_mutex_lock(&mem_mx);
    if (nlive < MAXP) live[nlive++] = p;
    pthread_mutex_unlock(&mem_mx);
    return p;
}
void sim_free(void *p) {
    if (queued(p)) freed_while_queued++;   // memória que o "kernel" ainda usa: crash no aparelho
    pthread_mutex_lock(&mem_mx);
    for (int i = 0; i < nlive; i++) if (live[i] == p) { live[i] = live[--nlive]; break; }
    pthread_mutex_unlock(&mem_mx);
    free(p);
}

// ---- USB interface falsa ----
#define QMAX 64
typedef struct { struct usbdevfs_urb *u; double due; int st; } qent;
static pthread_mutex_t dev_mx = PTHREAD_MUTEX_INITIALIZER;
static pthread_cond_t dev_cv = PTHREAD_COND_INITIALIZER;
static qent q[QMAX];
static int nq = 0;
static double out_due = 0, in_due = 0, in_acc = 0;
static int disconnected = 0, fail_next_reap = 0;
static double clock_ratio = 1.0002;    // relógio da mesa um pouco adiantado (o feedback corrige)
static long iso_gaps = 0;              // a mesa ficou sem pacote: no aparelho vira estalo/repetição
static double worst_starve = 0;
static long out_urbs = 0;

#define IN_FB 16                       // bytes por quadro da entrada simulada (4 ch x 32 bits)
#define OUT_FB 16
static float *cap = NULL;              // saída capturada (4 canais)
static long cap_n = 0, cap_max = 0;
static volatile int capturing = 0;

static int queued(void *p) {
    pthread_mutex_lock(&dev_mx);
    int r = 0;
    for (int i = 0; i < nq; i++) if (q[i].u == p || q[i].u->buffer == p) r = 1;
    pthread_mutex_unlock(&dev_mx);
    return r;
}

static void capture_out(struct usbdevfs_urb *u) {
    if (!capturing) return;
    uint8_t *b = u->buffer;
    int off = 0;
    for (int k = 0; k < u->number_of_packets; k++) {
        int nf = u->iso_frame_desc[k].length / OUT_FB;
        for (int f = 0; f < nf && cap_n < cap_max; f++, cap_n++) {
            for (int c = 0; c < 4; c++) {
                int32_t v; memcpy(&v, b + off + f * OUT_FB + c * 4, 4);
                cap[cap_n * 4 + c] = (float)(v >> 8) / 8388607.0f;
            }
        }
        off += u->iso_frame_desc[k].length;
    }
}

static void complete(struct usbdevfs_urb *u, int st) {
    u->status = st;
    u->actual_length = 0;
    for (int k = 0; k < u->number_of_packets; k++) {
        if (u->endpoint & 0x80) {
            in_acc += 48000.0 * clock_ratio / 8000;
            int nf = (int)in_acc; in_acc -= nf;
            u->iso_frame_desc[k].actual_length = st ? 0 : nf * IN_FB;
        } else {
            u->iso_frame_desc[k].actual_length = u->iso_frame_desc[k].length;
        }
        u->iso_frame_desc[k].status = st ? 1 : 0;
        u->actual_length += u->iso_frame_desc[k].actual_length;
    }
    if (!(u->endpoint & 0x80) && !st) capture_out(u);
}

static int pop_ready(struct usbdevfs_urb **out, int block) {
    for (;;) {
        int best = -1;
        for (int i = 0; i < nq; i++) if (best < 0 || q[i].due < q[best].due) best = i;
        if (best < 0) {
            if (disconnected || !block) { errno = disconnected ? ENODEV : EAGAIN; return -1; }
            pthread_cond_wait(&dev_cv, &dev_mx);
            continue;
        }
        double t = now();
        if (q[best].due > t) {
            if (!block) { errno = EAGAIN; return -1; }
            double w = q[best].due;
            struct timespec ts; clock_gettime(CLOCK_REALTIME, &ts);
            double d = w - t;
            ts.tv_sec += (time_t)d; ts.tv_nsec += (long)((d - (time_t)d) * 1e9);
            if (ts.tv_nsec >= 1000000000L) { ts.tv_sec++; ts.tv_nsec -= 1000000000L; }
            pthread_cond_timedwait(&dev_cv, &dev_mx, &ts);
            continue;
        }
        qent e = q[best]; q[best] = q[--nq];
        complete(e.u, e.st);
        *out = e.u;
        return 0;
    }
}

int fake_ioctl(int fd, unsigned long req, ...) {
    va_list a; va_start(a, req); void *arg = va_arg(a, void *); va_end(a);
    if (req == USBDEVFS_GET_SPEED) return USB_SPEED_HIGH;
    if (req == USBDEVFS_SETINTERFACE) return 0;
    pthread_mutex_lock(&dev_mx);
    int r = 0;
    if (req == USBDEVFS_SUBMITURB) {
        struct usbdevfs_urb *u = arg;
        if (disconnected) { errno = ENODEV; r = -1; }
        else if (nq == QMAX) { errno = ENOMEM; r = -1; }
        else {
            double t = now(), *line = (u->endpoint & 0x80) ? &in_due : &out_due;
            if (!(u->endpoint & 0x80)) {
                out_urbs++;
                // a mesa toca cada URB na hora marcada, recolhido ou não: se o último já passou,
                // ela ficou sem pacote
                if (out_urbs > NURBS && t > *line + 0.0002) {
                    iso_gaps++;
                    if (t - *line > worst_starve) worst_starve = t - *line;
                }
            }
            if (*line < t) *line = t;
            *line += PKTS_PER_URB / 8000.0;
            q[nq++] = (qent){u, *line, 0};
            pthread_cond_broadcast(&dev_cv);
        }
    } else if (req == USBDEVFS_REAPURB || req == USBDEVFS_REAPURBNDELAY) {
        if (fail_next_reap && req == USBDEVFS_REAPURB) { fail_next_reap = 0; errno = EPROTO; r = -1; }
        else r = pop_ready((struct usbdevfs_urb **)arg, req == USBDEVFS_REAPURB);
    } else if (req == USBDEVFS_DISCARDURB) {
        r = -1; errno = EINVAL;
        for (int i = 0; i < nq; i++) if (q[i].u == arg) { q[i].due = now(); q[i].st = -ENOENT; r = 0; }
        pthread_cond_broadcast(&dev_cv);
    }
    pthread_mutex_unlock(&dev_mx);
    return r;
}

static void sim_unplug(void) {   // cabo solto: o kernel devolve tudo com erro
    pthread_mutex_lock(&dev_mx);
    disconnected = 1;
    for (int i = 0; i < nq; i++) { q[i].due = now(); q[i].st = -ENODEV; }
    pthread_cond_broadcast(&dev_cv);
    pthread_mutex_unlock(&dev_mx);
}
static int sim_kernel_urbs(void) { pthread_mutex_lock(&dev_mx); int n = nq; pthread_mutex_unlock(&dev_mx); return n; }
static void sim_close_conn(void) {   // UsbLink.close(): fechar o fd devolve o que sobrou
    pthread_mutex_lock(&dev_mx);
    nq = 0; disconnected = 0; out_due = in_due = 0; out_urbs = 0;
    pthread_mutex_unlock(&dev_mx);
}

// ---- JNI falso ----
static const char *j_get(JNIEnv *e, jstring s, jboolean *c) { return (const char *)s; }
static void j_rel(JNIEnv *e, jstring s, const char *c) {}
static float last_arr[64];
static jfloatArray j_newf(JNIEnv *e, jsize n) { return (jfloatArray)last_arr; }
static void j_setf(JNIEnv *e, jfloatArray a, jsize st, jsize n, const jfloat *b) { memcpy((float *)a + st, b, n * sizeof(float)); }
static void j_getf(JNIEnv *e, jfloatArray a, jsize st, jsize n, jfloat *b) { memcpy(b, (float *)a + st, n * sizeof(float)); }
static jstring j_newstr(JNIEnv *e, const char *s) { return (jstring)s; }
static struct JNINativeInterface_ fns;
static const struct JNINativeInterface_ *envp = &fns;
static JNIEnv *env = (JNIEnv *)&envp;

// SIM_METER (binário "medidor"): o motor atual com os medidores ao vivo ligados o tempo todo; o som
// tem que sair idêntico ao validado do mesmo jeito
#ifdef SIM_METER
#define E_START() (FN(start)(env, NULL, 3, 0x01, 7 * OUT_FB, 4, 4, 1, 1, 0x81, 7 * IN_FB, IN_FB), FN(setMeter)(env, NULL, 1))
#else
#define E_START() FN(start)(env, NULL, 3, 0x01, 7 * OUT_FB, 4, 4, 1, 1, 0x81, 7 * IN_FB, IN_FB)
#endif
static jlong e_load(const char *p) { return FN(load)(env, NULL, (jstring)p); }
static void sleep_s(double s) { struct timespec t = {(time_t)s, (long)((s - (time_t)s) * 1e9)}; nanosleep(&t, NULL); }
static void reset_metrics(void) { pthread_mutex_lock(&dev_mx); iso_gaps = 0; worst_starve = 0; pthread_mutex_unlock(&dev_mx); }

// O Mac não dá prioridade de tempo real ao simulador: faltas curtas e raras (até ~15 ms com o Mac
// ocupado, nas duas versões: SIM_STEADY=20 mede) são o agendador dele. Reprova só falta longa,
// como a da busca com o lock preso (~60 ms).
#define MAX_STARVE 0.025


// ---- mesa emulada: o que chega em cada canal da USB interface ----
// canais 1/2 da faixa (backing) entram no BT/USB; canais 3/4 (metrônomo) no 7/8
static const char *CH_NAME[4] = {"BT/USB L", "BT/USB R", "7/8 L", "7/8 R"};
typedef struct { double rms[4], peak[4]; long clip[4]; } meters;

static double db(double x) { return x > 1e-12 ? 20 * log10(x) : -999; }

static meters meter(const float *a, long n) {
    meters m = {{0}};
    for (long i = 0; i < n; i++)
        for (int c = 0; c < 4; c++) {
            double v = a[i * 4 + c];
            m.rms[c] += v * v;
            if (fabs(v) > m.peak[c]) m.peak[c] = fabs(v);
            if (fabs(v) >= 0.99999) m.clip[c]++;
        }
    for (int c = 0; c < 4; c++) m.rms[c] = sqrt(m.rms[c] / (n ? n : 1));
    return m;
}

static void show(const char *label, meters m) {
    printf("  %-34s", label);
    for (int c = 0; c < 4; c++) {
        if (m.peak[c] == 0) printf(" | %-8s   mudo           ", CH_NAME[c]);
        else printf(" | %-8s %6.1f dB pico %5.1f%s", CH_NAME[c], db(m.rms[c]), db(m.peak[c]), m.clip[c] ? " CLIP" : "     ");
    }
    printf("\n");
}

// o que está gravado na faixa nesse trecho (sem passar pelo motor)
static meters source_meters(const char *path, long at_ms, double secs) {
    int err;
    stb_vorbis *v = stb_vorbis_open_filename(path, &err, NULL);
    stb_vorbis_info inf = stb_vorbis_get_info(v);
    stb_vorbis_seek_frame(v, (unsigned)(at_ms * inf.sample_rate / 1000));
    long n = (long)(secs * inf.sample_rate);
    float *b = malloc(sizeof(float) * inf.channels * n), *q4 = calloc(n * 4, sizeof(float));
    n = stb_vorbis_get_samples_float_interleaved(v, inf.channels, b, inf.channels * n);
    for (long i = 0; i < n; i++) for (int c = 0; c < 4 && c < inf.channels; c++) q4[i * 4 + c] = b[i * inf.channels + c];
    meters m = meter(q4, n);
    free(b); free(q4); stb_vorbis_close(v);
    return m;
}

static float EQ_FLAT[5] = {0, 0, 0, 0, 0};
// "não mudou": o metrônomo são cliques e a janela de 1,5 s pega um pouco mais ou menos deles
// conforme o instante da medição (±0,3 dB); vazamento de verdade aparece nos testes de "mudo"
#define SAME_DB 0.5

static void check(int ok, const char *what, const char *detail);

// toca 1,5 s a partir de 1:00 com os ajustes dados e mede a saída
static meters scene(float gb, float gc, float nb, float nc, int eq_on, float *eq) {
    FN(setGains)(env, NULL, gb, gc);
    FN(setNorm)(env, NULL, nb, nc);
    FN(setEq)(env, NULL, eq_on, (jfloatArray)eq);
    FN(seek)(env, NULL, 60000);
    FN(play)(env, NULL);
    sleep_s(0.3);
#ifdef SIM_METER
    FN(peaks)(env, NULL);   // zera: a leitura seguinte cobre só a gravação
#endif
    cap_n = 0; capturing = 1;
    sleep_s(1.5);
    capturing = 0;
#ifdef SIM_METER
    float pk[4 + SP_BINS]; memcpy(pk, FN(peaks)(env, NULL), sizeof pk);
#endif
    FN(pause)(env, NULL);
    sleep_s(0.1);
    meters m = meter(cap, cap_n);
#ifdef SIM_METER
    // o motor mede o que manda (até 32 ms antes de a mesa receber): picos iguais com folga de 1 dB;
    // acima de 0 dBFS a mesa recebe cortado em 1 e o medidor mostra o pico de verdade
    int ok = 1; char d[160] = "", *p = d;
    for (int c = 0; c < 4; c++) {
        double want = m.peak[c], got = pk[c] > 1 ? 1 : pk[c];
        if (want == 0 ? got != 0 : fabs(db(got) - db(want)) > 1.0) ok = 0;
        p += snprintf(p, d + sizeof d - p, "%s%.1f/%.1f", c ? " " : "", db(got), db(want));
    }
    double bands = 0; for (int b = 4; b < 4 + SP_BINS; b++) bands += pk[b];
    if (m.peak[0] > 0 && bands == 0) ok = 0;
    check(ok, "medidor = pico que chegou na mesa (motor/mesa, dB) e espectro com sinal", d);
#endif
    return m;
}

static void write_wav(const char *path, int c0) {   // 2 canais, 16 bits, 48 kHz
    FILE *f = fopen(path, "wb");
    uint32_t data = cap_n * 4, riff = 36 + data, sr = 48000, br = sr * 4, fmt = 16;
    uint16_t pcm = 1, ch = 2, ba = 4, bits = 16;
    fwrite("RIFF", 1, 4, f); fwrite(&riff, 4, 1, f); fwrite("WAVEfmt ", 1, 8, f); fwrite(&fmt, 4, 1, f);
    fwrite(&pcm, 2, 1, f); fwrite(&ch, 2, 1, f); fwrite(&sr, 4, 1, f); fwrite(&br, 4, 1, f);
    fwrite(&ba, 2, 1, f); fwrite(&bits, 2, 1, f); fwrite("data", 1, 4, f); fwrite(&data, 4, 1, f);
    for (long i = 0; i < cap_n; i++)
        for (int c = c0; c < c0 + 2; c++) { int16_t v = (int16_t)lrintf(cap[i * 4 + c] * 32767); fwrite(&v, 2, 1, f); }
    fclose(f);
}

static int fails = 0;
static void check(int ok, const char *what, const char *detail) {
    printf("  %s %s%s%s\n", ok ? "OK  " : "FALHA", what, detail[0] ? ": " : "", detail);
    if (!ok) fails++;
}

static void dump(const char *path) {
    FILE *f = fopen(path, "wb");
    fwrite(cap, sizeof(float) * 4, cap_n, f);
    fclose(f);
}

int main(int argc, char **argv) {
    const char *ogg = argv[1], *ogg48 = argv[2], *outdir = argv[3], *wavdir = argc > 4 ? argv[4] : NULL;
    sim_verbose = getenv("SIM_VERBOSE") != NULL;
    fns.GetStringUTFChars = j_get; fns.ReleaseStringUTFChars = j_rel; fns.NewFloatArray = j_newf;
    fns.SetFloatArrayRegion = j_setf; fns.GetFloatArrayRegion = j_getf; fns.NewStringUTF = j_newstr;
    cap_max = 48000L * 40; cap = malloc(sizeof(float) * 4 * cap_max);
    char buf[256], path[512];
    printf("motor: %s\n", ENGINE_NAME);

    if (getenv("SIM_STEADY")) {   // só reprodução contínua, para medir o agendamento da thread USB
        double secs = atof(getenv("SIM_STEADY"));
        E_START();
        e_load(ogg);
        FN(seek)(env, NULL, 30000);
        FN(play)(env, NULL);
        sleep_s(0.5);
        reset_metrics();
        sleep_s(secs);
        printf("  %.0f s tocando: %ld faltas, pior %.1f ms\n", secs, iso_gaps, worst_starve * 1000);
        FN(stop)(env, NULL);
        return 0;
    }

    // 1) tocar, buscar e mudar de música: a saída é gravada para comparar as duas versões
    printf("[1] tocar faixa 44,1 kHz (reamostrada), buscar para 1:00 e trocar para a faixa 48 kHz\n");
    E_START();
    jlong dur = e_load(ogg);
    FN(setGains)(env, NULL, 1.0f, 1.0f);
    capturing = 1;
    FN(play)(env, NULL);
    sleep_s(3.0);
    long p1 = FN(position)(env, NULL);
    FN(seek)(env, NULL, 60000);
    sleep_s(2.0);
    long p2 = FN(position)(env, NULL);
    FN(pause)(env, NULL);
    sleep_s(0.2);
    capturing = 0;
    snprintf(path, sizeof path, "%s/saida-%s.f32", outdir, ENGINE_NAME); dump(path);
    snprintf(buf, sizeof buf, "duração %lld ms, posição %ld ms após 3 s, %ld ms após buscar 1:00 e tocar 2 s", (long long)dur, p1, p2);
    check(dur > 200000 && labs(p1 - 3000) < 150 && labs(p2 - 62000) < 150, "posição acompanha o tempo real", buf);
    snprintf(buf, sizeof buf, "%ld pacotes sem dados, pior falta %.1f ms", iso_gaps, worst_starve * 1000);
    check(worst_starve < MAX_STARVE, "fluxo USB contínuo", buf);
    long under = st_underrun;
    snprintf(buf, sizeof buf, "%ld quadros", under);
    printf("  info: ring vazio na partida/busca: %s\n", buf);

    e_load(ogg48);
    capturing = 1; cap_n = 0;
    FN(play)(env, NULL);
    sleep_s(1.5);
    FN(pause)(env, NULL);
    sleep_s(0.1);
    capturing = 0;
    snprintf(path, sizeof path, "%s/saida48-%s.f32", outdir, ENGINE_NAME); dump(path);
    float pk = 0; for (long i = 0; i < cap_n * 4; i++) if (fabsf(cap[i]) > pk) pk = fabsf(cap[i]);
    snprintf(buf, sizeof buf, "pico %.2f", pk);
    check(pk > 0.05f && pk <= 1.0f, "faixa 48 kHz toca (sem reamostrar)", buf);

    // 1b) tocando sem mexer em nada
    printf("[1b] tocar 8 s sem mexer\n");
    e_load(ogg);
    FN(seek)(env, NULL, 30000);
    FN(play)(env, NULL);
    sleep_s(0.5);
    reset_metrics();
    sleep_s(8.0);
    snprintf(buf, sizeof buf, "%ld vezes a mesa ficou sem pacote, pior falta %.1f ms", iso_gaps, worst_starve * 1000);
    check(worst_starve < MAX_STARVE, "8 s sem falta longa de pacote", buf);
    FN(pause)(env, NULL);

    // 2) disco lento + CPU quente: busca de 40 ms com a música tocando
    printf("[2] busca lenta (40 ms de disco) e decodificação travando 15 ms a cada 20 blocos\n");
    e_load(ogg);
    FN(play)(env, NULL);
    sleep_s(0.5);
    reset_metrics();
    slow_seek_ms = 40; slow_decode_ms = 15; slow_decode_every = 20;
    for (int i = 0; i < 15; i++) { FN(seek)(env, NULL, 20000 + i * 5000); sleep_s(0.3); }
    slow_seek_ms = 0; slow_decode_ms = 0;
    snprintf(buf, sizeof buf, "%ld vezes a mesa ficou sem pacote, pior falta %.1f ms", iso_gaps, worst_starve * 1000);
    check(worst_starve < MAX_STARVE, "busca não trava a USB", buf);
    FN(pause)(env, NULL);

    // 3) parado: quantas vezes o decoder acorda
    printf("[3] player parado por 2 s\n");
    sleep_s(0.1);
    long u0 = usleep_calls;
    sleep_s(2.0);
    double wps = (usleep_calls - u0) / 2.0;
    snprintf(buf, sizeof buf, "%.0f despertares/s", wps);
    check(wps <= 60, "decoder quase dormindo parado", buf);

    // 4) desligar
    printf("[4] desligar\n");
    double t0 = now();
    FN(stop)(env, NULL);
    double ts = now() - t0;
    snprintf(buf, sizeof buf, "%.0f ms (silêncio de fim para a mesa não apitar)", ts * 1000);
    check(ts > 0.4 && ts < 0.8, "desliga com o silêncio de ~0,5 s", buf);
    snprintf(buf, sizeof buf, "%d URBs ainda com o kernel, %d blocos de memória do motor soltos", sim_kernel_urbs(), nlive);
    check(sim_kernel_urbs() == 0 && nlive == 0, "nada sobra ao desligar", buf);
    sim_close_conn();

    // 5) cabo solto no meio da música, depois religado (o que o serviço faz: stop, fecha, start)
    printf("[5] cabo da mesa solto tocando e religado\n");
    E_START();
    FN(play)(env, NULL);
    sleep_s(0.8);
    sim_unplug();
    sleep_s(0.3);
    snprintf(buf, sizeof buf, "isRunning=%d, status \"%s\"", FN(isRunning)(env, NULL), g_status);
    check(!FN(isRunning)(env, NULL), "motor percebe a queda (vigia do serviço vê)", buf);
    snprintf(buf, sizeof buf, "%d blocos soltos, %d liberados com o kernel ainda usando", nlive, freed_while_queued);
    check(nlive == 0 && freed_while_queued == 0, "memória devolvida sem risco", buf);
    FN(stop)(env, NULL);
    sim_close_conn();
    E_START();
    FN(seek)(env, NULL, 90000);
    FN(play)(env, NULL);
    sleep_s(1.0);
    long p5 = FN(position)(env, NULL);
    snprintf(buf, sizeof buf, "posição %ld ms (esperado ~91000)", p5);
    check(FN(isRunning)(env, NULL) && labs(p5 - 91000) < 150, "volta a tocar após religar", buf);

    // 6) fluxo morre sem aviso de desconexão (a mesa reinicia), o kernel ainda segura URBs
    printf("[6] fluxo USB morre sem aviso (erro de protocolo)\n");
    fail_next_reap = 1;
    sleep_s(0.3);
    snprintf(buf, sizeof buf, "isRunning=%d, %d blocos soltos, %d liberados com o kernel usando", FN(isRunning)(env, NULL), nlive, freed_while_queued);
    check(!FN(isRunning)(env, NULL) && nlive == 0 && freed_while_queued == 0, "queda percebida e memória devolvida", buf);
    FN(stop)(env, NULL);
    sim_close_conn();

    // 7) 10 ciclos de ligar/desligar
    printf("[7] 10 ciclos de ligar/desligar\n");
    for (int i = 0; i < 10; i++) { E_START(); FN(play)(env, NULL); sleep_s(0.1); FN(stop)(env, NULL); sim_close_conn(); }
    snprintf(buf, sizeof buf, "%d blocos de memória soltos", nlive);
    check(nlive == 0, "sem vazamento", buf);

    // 8) normalização
    printf("[8] análise de loudness\n");
    jfloatArray m = FN(analyze)(env, NULL, (jstring)ogg);
    float *mv = (float *)m;
    snprintf(buf, sizeof buf, "backing %.2f LUFS, pico %.3f, metrônomo pico %.3f", mv[0], mv[1], mv[2]);
    check(m && mv[0] < -5 && mv[0] > -40, "mede o backing", buf);
    snprintf(path, sizeof path, "%s/lufs-%s.txt", outdir, ENGINE_NAME);
    FILE *f = fopen(path, "w"); fprintf(f, "%.6f %.6f %.6f\n", mv[0], mv[1], mv[2]); fclose(f);

    // 9) mesa emulada: o que cada canal da USB interface recebe
    printf("[9] mesa emulada: medidores por canal (trecho de 1:00 da faixa 44,1 kHz)\n");
    meters src = source_meters(ogg, 60000, 1.5);
    E_START();
    e_load(ogg);
    show("na faixa (canais 1/2 e 3/4)", src);
    meters a0 = scene(1, 1, 1, 1, 0, EQ_FLAT);
    show("faders 100%, sem normalizar", a0);
    // faixas com pico acima de 0 dB clipam com 100% sem normalizar: o nível é conferido a 50%
    meters h0 = scene(0.5f, 0.5f, 1, 1, 0, EQ_FLAT);
    show("faders 50%, sem normalizar", h0);
    int same = 1;
    for (int c = 0; c < 4; c++) same &= fabs(db(h0.rms[c]) + 6.02 - db(src.rms[c])) < 0.3;
    check(same, "os 4 canais chegam: 1/2 da faixa no BT/USB e 3/4 no 7/8, no mesmo nível", "");

    meters b0 = scene(1, 0, 1, 1, 0, EQ_FLAT);
    show("metrônomo 0%", b0);
    check(b0.peak[2] == 0 && b0.peak[3] == 0 && fabs(db(b0.rms[0]) - db(a0.rms[0])) < SAME_DB,
          "fader do metrônomo só mexe no 7/8 (zera de verdade)", "");
    meters c0 = scene(0, 1, 1, 1, 0, EQ_FLAT);
    show("backing 0%", c0);
    check(c0.peak[0] == 0 && c0.peak[1] == 0 && fabs(db(c0.rms[2]) - db(a0.rms[2])) < SAME_DB,
          "fader do backing só mexe no BT/USB (nada vaza do metrônomo)", "");
    meters d0 = scene(0.5f, 1, 1, 1, 0, EQ_FLAT);
    show("backing 50%", d0);
    snprintf(buf, sizeof buf, "%.2f dB", db(d0.rms[0]) - db(a0.rms[0]));
    check(fabs(db(d0.rms[0]) - db(a0.rms[0]) + 6.02) < 0.3 && fabs(db(d0.rms[2]) - db(a0.rms[2])) < SAME_DB,
          "backing 50% = -6 dB no BT/USB, 7/8 igual", buf);
    float bass[5] = {6, 0, 0, 0, 0};
    meters e0 = scene(1, 1, 1, 1, 1, bass);
    show("EQ: graves +6 dB", e0);
    check(fabs(db(e0.rms[2]) - db(a0.rms[2])) < SAME_DB && fabs(db(e0.rms[0]) - db(a0.rms[0])) > 0.1
          && e0.clip[0] <= a0.clip[0], "EQ só no BT/USB, e o reforço não aumenta o clip", "");

    // normalização com os ganhos que o Normalizer.java calcularia para esta faixa
    float *an = (float *)FN(analyze)(env, NULL, (jstring)ogg);
    float lufs = an[0], pkb = an[1], pkc = an[2];
    double gbn = fmin(-16 - lufs, -1 - db(pkb)); gbn = fmax(-24, fmin(15, gbn));
    double gcn = pkc > 1e-6 ? fmax(-24, fmin(15, -6 - db(pkc))) : 0;
    meters f0 = scene(1, 1, powf(10, gbn / 20), powf(10, gcn / 20), 0, EQ_FLAT);
    snprintf(buf, sizeof buf, "normalizado (%+.1f / %+.1f dB)", gbn, gcn);
    show(buf, f0);
    snprintf(buf, sizeof buf, "backing %+.1f dB, metrônomo %+.1f dB; clip sem normalizar: BT/USB %ld/%ld, 7/8 %ld/%ld amostras",
             db(f0.rms[0]) - db(src.rms[0]), db(f0.rms[2]) - db(src.rms[2]), a0.clip[0], a0.clip[1], a0.clip[2], a0.clip[3]);
    check(f0.clip[0] + f0.clip[1] + f0.clip[2] + f0.clip[3] == 0 && fabs(db(f0.rms[0]) - db(src.rms[0]) - gbn) < 0.3,
          "normalização: sem clip, ganho certo em cada par", buf);
#ifdef LIM_CEIL
    // EQ com limitador (19/10/2026): o reforço não abaixa mais o backing todo; só os picos são segurados
    double ceil_db = db(LIM_CEIL) + 0.01;
    snprintf(buf, sizeof buf, "backing %+.1f dB contra sem EQ (o validado ficava ~-5 dB); pico %.1f dB",
             db(e0.rms[0]) - db(a0.rms[0]), db(fmax(e0.peak[0], e0.peak[1])));
    check(e0.clip[0] + e0.clip[1] == 0 && db(e0.rms[0]) > db(a0.rms[0]) - 1.0 && db(fmax(e0.peak[0], e0.peak[1])) <= ceil_db,
          "EQ graves +6, faders 100%: sem clip, volume mantido, pico até -1 dBFS", buf);
    meters n1 = scene(1, 1, powf(10, gbn / 20), powf(10, gcn / 20), 1, bass);
    show("normalizado + graves +6", n1);
    snprintf(buf, sizeof buf, "backing %+.1f dB contra normalizado sem EQ", db(n1.rms[0]) - db(f0.rms[0]));
    check(n1.clip[0] + n1.clip[1] == 0 && db(n1.rms[0]) > db(f0.rms[0]) - 0.5 && fabs(db(n1.rms[2]) - db(f0.rms[2])) < SAME_DB,
          "normalizado + EQ: sem clip, backing não abaixa, metrônomo igual", buf);
    meters z1 = scene(1, 1, powf(10, gbn / 20), powf(10, gcn / 20), 1, EQ_FLAT);
    snprintf(buf, sizeof buf, "diferença %.3f dB", db(z1.rms[0]) - db(f0.rms[0]));
    check(fabs(db(z1.rms[0]) - db(f0.rms[0])) < 0.05, "EQ ligado e plano, sem pico acima de -1 dBFS: limitador não mexe", buf);
    float hard[5] = {12, 12, 0, 0, 0};
    meters k1 = scene(1, 1, 1, 1, 1, hard);
    show("EQ: graves +12 e corpo +12", k1);
    snprintf(buf, sizeof buf, "pico %.1f dB", db(fmax(k1.peak[0], k1.peak[1])));
    check(k1.clip[0] + k1.clip[1] == 0 && db(fmax(k1.peak[0], k1.peak[1])) <= ceil_db,
          "reforço exagerado (+12 e +12): limitador segura, sem clip", buf);
#endif
    if (wavdir) {
        snprintf(path, sizeof path, "%s/mesa-BT-USB-%s.wav", wavdir, ENGINE_NAME); write_wav(path, 0);
        snprintf(path, sizeof path, "%s/mesa-7-8-%s.wav", wavdir, ENGINE_NAME); write_wav(path, 2);
    }

    FN(seek)(env, NULL, 60000);
    FN(play)(env, NULL);
    sleep_s(0.5);
    FN(pause)(env, NULL);
    sleep_s(0.05);
    cap_n = 0; capturing = 1;
    sleep_s(0.5);
    capturing = 0;
    meters g0 = meter(cap, cap_n);
    show("pausado", g0);
    check(g0.peak[0] + g0.peak[1] + g0.peak[2] + g0.peak[3] == 0, "pausa = silêncio nos 4 canais (fluxo segue ativo)", "");
    FN(stop)(env, NULL);
    sim_close_conn();

#ifdef SIM_METER
    // FFT do espectro com sinal conhecido: seno de 1 kHz em -6 dBFS (amplitude 0,5)
    printf("[10] espectro: seno de 1 kHz, -6 dBFS\n");
    sp_init();
    for (int i = 0; i < SP_N; i++) g_sp_in[i] = 0.5f * (float)sin(2 * M_PI * 1000.0 * i / OUT_RATE);
    g_sp_pos = 0;
    float sp[SP_BINS]; sp_run(sp);
    int hit = -1; double far = -999;
    for (int b = 0; b < SP_BINS; b++) {
        double lo = SP_LO * pow(SP_HI / SP_LO, (double)b / SP_BINS), hi = SP_LO * pow(SP_HI / SP_LO, (double)(b + 1) / SP_BINS);
        if (lo <= 1000 && 1000 < hi) hit = b;
        else if (hi < 500 || lo > 2000) far = fmax(far, db(sp[b]));
    }
    snprintf(buf, sizeof buf, "faixa do 1 kHz %.2f dB, longe dele no máximo %.1f dB", db(sp[hit]), far);
    check(hit >= 0 && fabs(db(sp[hit]) + 6.02) < 1.5 && far < -50, "espectro: nível certo no 1 kHz e nada longe dele", buf);
#endif

    printf("%s: %d falha(s)\n", ENGINE_NAME, fails);
    return fails ? 1 : 0;
}
