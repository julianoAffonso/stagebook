// Engine (engine.c) simulator on the Mac, without the tablet or the desk.
//
// A fake USB interface (8 outputs) answers usbfs ioctls at real pace (each URB = 8 packets of 125 µs) and returns input
// on the desk's clock. The same program compiles with the validated engine.c (tag audio-validado) and with the current
// one, to compare both. See run.sh.
#include <errno.h>
#include <math.h>
#include <poll.h>
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
static int fake_poll(struct pollfd *p, nfds_t n, int ms) { struct timespec t = {0, 1000000}; nanosleep(&t, NULL); return 1; }

#define calloc sim_calloc
#define free sim_free
#define usleep sim_usleep
#define ioctl fake_ioctl
#define poll fake_poll
#define stb_vorbis_seek_frame sim_seek_frame
#define stb_vorbis_get_samples_float_interleaved sim_get_samples
#include ENGINE_C
#undef calloc
#undef free
#undef usleep
#undef ioctl
#undef poll
#undef stb_vorbis_seek_frame
#undef stb_vorbis_get_samples_float_interleaved

int stb_vorbis_seek_frame(stb_vorbis *f, unsigned int sample_number);
int stb_vorbis_get_samples_float_interleaved(stb_vorbis *f, int channels, float *buffer, int num_floats);

static double now(void) {
    struct timespec ts;
    clock_gettime(CLOCK_MONOTONIC, &ts);
    return ts.tv_sec + ts.tv_nsec / 1e9;
}

// ---- hooks: slow disk, hot CPU, memory ----
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
    if (queued(p)) freed_while_queued++;   // memory the "kernel" still uses: a crash on the device
    pthread_mutex_lock(&mem_mx);
    for (int i = 0; i < nlive; i++) if (live[i] == p) { live[i] = live[--nlive]; break; }
    pthread_mutex_unlock(&mem_mx);
    free(p);
}

// ---- interface USB falsa ----
#define QMAX 64
typedef struct { struct usbdevfs_urb *u; double due; int st; } qent;
static pthread_mutex_t dev_mx = PTHREAD_MUTEX_INITIALIZER;
static pthread_cond_t dev_cv = PTHREAD_COND_INITIALIZER;
static qent q[QMAX];
static int nq = 0;
static double out_due = 0, in_due = 0, in_acc = 0;
static int disconnected = 0, fail_next_reap = 0;
static double clock_ratio = 1.0002;    // desk clock slightly fast (the feedback corrects it)
static long iso_gaps = 0;              // the desk ran out of packets: on the device it becomes a click/repeat
static double worst_starve = 0;
static long out_urbs = 0;

#define IN_FB 16                       // bytes per frame of the simulated input (4 ch x 32 bits)
#define OUT_FB 16
static float *cap = NULL;              // captured output (4 channels)
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
                // the desk plays each URB at its scheduled time, reaped or not: if the last one already passed, it ran
                // out of packets
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
        if (fail_next_reap) { fail_next_reap = 0; errno = EPROTO; r = -1; }
        else r = pop_ready((struct usbdevfs_urb **)arg, req == USBDEVFS_REAPURB);
    } else if (req == USBDEVFS_DISCARDURB) {
        r = -1; errno = EINVAL;
        for (int i = 0; i < nq; i++) if (q[i].u == arg) { q[i].due = now(); q[i].st = -ENOENT; r = 0; }
        pthread_cond_broadcast(&dev_cv);
    }
    pthread_mutex_unlock(&dev_mx);
    return r;
}

static void sim_unplug(void) {   // cable unplugged: the kernel returns everything with an error
    pthread_mutex_lock(&dev_mx);
    disconnected = 1;
    for (int i = 0; i < nq; i++) { q[i].due = now(); q[i].st = -ENODEV; }
    pthread_cond_broadcast(&dev_cv);
    pthread_mutex_unlock(&dev_mx);
}
static int sim_kernel_urbs(void) { pthread_mutex_lock(&dev_mx); int n = nq; pthread_mutex_unlock(&dev_mx); return n; }
static void sim_close_conn(void) {   // UsbLink.close(): closing the fd returns what's left
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

// SIM_METER ("medidor" binary): the current engine with live meters on all the time; the sound has to come out
// identical to the validated one all the same
#ifdef SIM_METER
#define E_START() (FN(start)(env, NULL, 3, 0x01, 7 * OUT_FB, 4, 4, 1, 1, 0x81, 7 * IN_FB, IN_FB), FN(setMeter)(env, NULL, 1))
#else
#define E_START() FN(start)(env, NULL, 3, 0x01, 7 * OUT_FB, 4, 4, 1, 1, 0x81, 7 * IN_FB, IN_FB)
#endif

// Fader and normalization for the backing (1/2) and the metronome (3/4). On the validated engine, through the two old
// faders; on Stagebook's (per-channel mixing only), through the equivalent setup: 1-2 backing with EQ, 3-4 metronome,
// direct routing. The sound has to come out identical.
static float s_gb = 1, s_gc = 1, s_nb = 1, s_nc = 1;
static void sim_mix(void) {
#ifdef ENGINE_HAS_MIX
    float g[8] = {s_gb, s_gb, s_gc, s_gc}, n[8] = {s_nb, s_nb, s_nc, s_nc}, r[64] = {0};
    for (int o = 0; o < 4; o++) r[o * 8 + o] = 1.0f;
    FN(setMix)(env, NULL, (jfloatArray)g, (jfloatArray)n, 0x3, (jfloatArray)r);
#endif
}
static void set_gains(float gb, float gc) {
    s_gb = gb; s_gc = gc;
#ifndef ENGINE_HAS_MIX
    FN(setGains)(env, NULL, gb, gc);
#endif
    sim_mix();
}
static void set_norm(float nb, float nc) {
    s_nb = nb; s_nc = nc;
#ifndef ENGINE_HAS_MIX
    FN(setNorm)(env, NULL, nb, nc);
#endif
    sim_mix();
}
static jlong e_load(const char *p) { jlong d = FN(load)(env, NULL, (jstring)p); sim_mix(); return d; }
static void sleep_s(double s) { struct timespec t = {(time_t)s, (long)((s - (time_t)s) * 1e9)}; nanosleep(&t, NULL); }
static void reset_metrics(void) { pthread_mutex_lock(&dev_mx); iso_gaps = 0; worst_starve = 0; pthread_mutex_unlock(&dev_mx); }

// The Mac doesn't give the simulator real-time priority: short, rare gaps (up to ~15 ms with the Mac busy, in both
// versions: SIM_STEADY=20 measures) come from its scheduler. Only a long gap fails, like the one from seeking with the
// lock held (~60 ms).
#define MAX_STARVE 0.025


// ---- emulated desk: what reaches each interface output ----
// track channels 1/2 (backing) go into BT/USB; channels 3/4 (metronome) into 7/8
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
        if (m.peak[c] == 0) printf(" | %-8s   muted          ", CH_NAME[c]);
        else printf(" | %-8s %6.1f dB peak %5.1f%s", CH_NAME[c], db(m.rms[c]), db(m.peak[c]), m.clip[c] ? " CLIP" : "     ");
    }
    printf("\n");
}

// what's recorded on the track in this stretch (without going through the engine)
static meters source_meters(const char *path, long at_ms, double secs) {
#ifdef ENGINE_HAS_SRC
    // Stagebook's engine: reads through the engine's same source layer (OGG, MP3 or WAV)
    int err = 0, rate = 0, ch = 0;
    long len = 0;
    src_t s = src_open(path, &err, &rate, &ch, &len);
    src_seek(&s, (unsigned)(at_ms * rate / 1000));
    long n = (long)(secs * rate);
    float *b = malloc(sizeof(float) * ch * n), *q4 = calloc(n * 4, sizeof(float));
    long got = 0;
    while (got < n) { int k = src_read(&s, ch, b + got * ch, (int)((n - got) > 4096 ? 4096 : (n - got))); if (k <= 0) break; got += k; }
    n = got;
    for (long i = 0; i < n; i++) for (int c = 0; c < 4 && c < ch; c++) q4[i * 4 + c] = b[i * ch + c];
    meters m = meter(q4, n);
    free(b); free(q4); src_close(&s);
    return m;
#else
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
#endif
}

static float EQ_FLAT[5] = {0, 0, 0, 0, 0};
// "didn't change": the metronome is clicks and the 1.5 s window catches a few more or fewer of them depending on when
// it measures (±0.3 dB); real leakage shows up in the "mute" tests
#define SAME_DB 0.5

static void check(int ok, const char *what, const char *detail);

// plays 1.5 s from 1:00 with the given settings and measures the output
static meters scene(float gb, float gc, float nb, float nc, int eq_on, float *eq) {
    set_gains(gb, gc);
    set_norm(nb, nc);
    FN(setEq)(env, NULL, eq_on, (jfloatArray)eq);
    FN(seek)(env, NULL, 60000);
    FN(play)(env, NULL);
    sleep_s(0.3);
#ifdef SIM_METER
    FN(peaks)(env, NULL);   // reset: the next read covers only the recording
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
    // the engine measures what it sends (up to 32 ms before the desk gets it): equal peaks within 1 dB; above 0 dBFS
    // the desk gets it clipped at 1 and the meter shows the real peak
    int ok = 1; char d[160] = "", *p = d;
    for (int c = 0; c < 4; c++) {
        double want = m.peak[c], got = pk[c] > 1 ? 1 : pk[c];
        if (want == 0 ? got != 0 : fabs(db(got) - db(want)) > 1.0) ok = 0;
        p += snprintf(p, d + sizeof d - p, "%s%.1f/%.1f", c ? " " : "", db(got), db(want));
    }
    double bands = 0; for (int b = 4; b < 4 + SP_BINS; b++) bands += pk[b];
    if (m.peak[0] > 0 && bands == 0) ok = 0;
    check(ok, "meter = peak that reached the desk (engine/desk, dB) and spectrum with signal", d);
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
    printf("  %s %s%s%s\n", ok ? "OK  " : "FAIL ", what, detail[0] ? ": " : "", detail);
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
    printf("engine: %s\n", ENGINE_NAME);

    if (getenv("SIM_STEADY")) {   // continuous playback only, to measure the USB thread's scheduling
        double secs = atof(getenv("SIM_STEADY"));
        E_START();
        e_load(ogg);
        FN(seek)(env, NULL, 30000);
        FN(play)(env, NULL);
        sleep_s(0.5);
        reset_metrics();
        sleep_s(secs);
        printf("  %.0f s playing: %ld gaps, worst %.1f ms\n", secs, iso_gaps, worst_starve * 1000);
        FN(stop)(env, NULL);
        return 0;
    }

    // 1) play, seek and change songs: the output is recorded to compare both versions
    printf("[1] play the 44.1 kHz track (resampled), seek to 1:00 and switch to the 48 kHz track\n");
    E_START();
    jlong dur = e_load(ogg);
    set_gains(1.0f, 1.0f);
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
    snprintf(path, sizeof path, "%s/output-%s.f32", outdir, ENGINE_NAME); dump(path);
    snprintf(buf, sizeof buf, "duration %lld ms, position %ld ms after 3 s, %ld ms after seeking to 1:00 and playing 2 s", (long long)dur, p1, p2);
    check(dur > 200000 && labs(p1 - 3000) < 150 && labs(p2 - 62000) < 150, "position follows real time", buf);
    snprintf(buf, sizeof buf, "%ld packets without data, worst gap %.1f ms", iso_gaps, worst_starve * 1000);
    check(worst_starve < MAX_STARVE, "continuous USB stream", buf);
    long under = st_underrun;
    snprintf(buf, sizeof buf, "%ld quadros", under);
    printf("  info: ring empty on start/seek: %s\n", buf);

    e_load(ogg48);
    capturing = 1; cap_n = 0;
    FN(play)(env, NULL);
    sleep_s(1.5);
    FN(pause)(env, NULL);
    sleep_s(0.1);
    capturing = 0;
    snprintf(path, sizeof path, "%s/output48-%s.f32", outdir, ENGINE_NAME); dump(path);
    float pk = 0; for (long i = 0; i < cap_n * 4; i++) if (fabsf(cap[i]) > pk) pk = fabsf(cap[i]);
    snprintf(buf, sizeof buf, "peak %.2f", pk);
    check(pk > 0.05f && pk <= 1.0f, "48 kHz track plays (no resampling)", buf);

    // 1b) playing without touching anything
    printf("[1b] play 8 s without touching\n");
    e_load(ogg);
    FN(seek)(env, NULL, 30000);
    FN(play)(env, NULL);
    sleep_s(0.5);
    reset_metrics();
    sleep_s(8.0);
    snprintf(buf, sizeof buf, "%ld times the desk ran out of packets, worst gap %.1f ms", iso_gaps, worst_starve * 1000);
    check(worst_starve < MAX_STARVE, "8 s without a long packet gap", buf);
    FN(pause)(env, NULL);

    // 2) slow disk + hot CPU: a 40 ms seek with the song playing
    printf("[2] slow seek (40 ms of disk) and decoding stalling 15 ms every 20 blocks\n");
    e_load(ogg);
    FN(play)(env, NULL);
    sleep_s(0.5);
    reset_metrics();
    slow_seek_ms = 40; slow_decode_ms = 15; slow_decode_every = 20;
    for (int i = 0; i < 15; i++) { FN(seek)(env, NULL, 20000 + i * 5000); sleep_s(0.3); }
    slow_seek_ms = 0; slow_decode_ms = 0;
    snprintf(buf, sizeof buf, "%ld times the desk ran out of packets, worst gap %.1f ms", iso_gaps, worst_starve * 1000);
    check(worst_starve < MAX_STARVE, "seeking doesn't block USB", buf);
    FN(pause)(env, NULL);

    // 3) stopped: how often the decoder wakes up
    printf("[3] player stopped for 2 s\n");
    sleep_s(0.1);
    long u0 = usleep_calls;
    sleep_s(2.0);
    double wps = (usleep_calls - u0) / 2.0;
    snprintf(buf, sizeof buf, "%.0f despertares/s", wps);
    check(wps <= 60, "decoder nearly asleep when stopped", buf);

    // 4) desligar
    printf("[4] desligar\n");
    double t0 = now();
    FN(stop)(env, NULL);
    double ts = now() - t0;
    snprintf(buf, sizeof buf, "%.0f ms (closing silence so the desk doesn't whine)", ts * 1000);
    check(ts > 0.4 && ts < 0.8, "turns off with ~0.5 s of silence", buf);
    snprintf(buf, sizeof buf, "%d URBs still with the kernel, %d engine memory blocks leaked", sim_kernel_urbs(), nlive);
    check(sim_kernel_urbs() == 0 && nlive == 0, "nada sobra ao desligar", buf);
    sim_close_conn();

    // 5) cable unplugged mid-song, then plugged back in (what the service does: stop, close, start)
    printf("[5] desk cable unplugged while playing and plugged back\n");
    E_START();
    FN(play)(env, NULL);
    sleep_s(0.8);
    sim_unplug();
    sleep_s(0.3);
    snprintf(buf, sizeof buf, "isRunning=%d, status \"%s\"", FN(isRunning)(env, NULL), g_status);
    check(!FN(isRunning)(env, NULL), "engine notices the drop (the service watchdog sees it)", buf);
    snprintf(buf, sizeof buf, "%d blocks leaked, %d freed while the kernel was still using them", nlive, freed_while_queued);
    check(nlive == 0 && freed_while_queued == 0, "memory returned safely", buf);
    FN(stop)(env, NULL);
    sim_close_conn();
    E_START();
    FN(seek)(env, NULL, 90000);
    FN(play)(env, NULL);
    sleep_s(1.0);
    long p5 = FN(position)(env, NULL);
    snprintf(buf, sizeof buf, "position %ld ms (expected ~91000)", p5);
    check(FN(isRunning)(env, NULL) && labs(p5 - 91000) < 150, "plays again after reconnecting", buf);

    // 6) stream dies with no disconnect notice (the desk restarts), the kernel still holds URBs
    printf("[6] USB stream dies without notice (protocol error)\n");
    fail_next_reap = 1;
    sleep_s(0.3);
    snprintf(buf, sizeof buf, "isRunning=%d, %d blocks leaked, %d freed while the kernel was using them", FN(isRunning)(env, NULL), nlive, freed_while_queued);
    check(!FN(isRunning)(env, NULL) && nlive == 0 && freed_while_queued == 0, "drop noticed and memory returned", buf);
    FN(stop)(env, NULL);
    sim_close_conn();

    // 7) 10 on/off cycles
    printf("[7] 10 on/off cycles\n");
    for (int i = 0; i < 10; i++) { E_START(); FN(play)(env, NULL); sleep_s(0.1); FN(stop)(env, NULL); sim_close_conn(); }
    snprintf(buf, sizeof buf, "%d memory blocks leaked", nlive);
    check(nlive == 0, "no leak", buf);

    // 8) normalization
    printf("[8] loudness analysis\n");
    jfloatArray m = FN(analyze)(env, NULL, (jstring)ogg);
    float *mv = (float *)m;
    snprintf(buf, sizeof buf, "backing %.2f LUFS, peak %.3f, metronome peak %.3f", mv[0], mv[1], mv[2]);
    check(m && mv[0] < -5 && mv[0] > -40, "measures the backing", buf);
    snprintf(path, sizeof path, "%s/lufs-%s.txt", outdir, ENGINE_NAME);
    FILE *f = fopen(path, "w"); fprintf(f, "%.6f %.6f %.6f\n", mv[0], mv[1], mv[2]); fclose(f);

    // 9) emulated desk: what each interface output gets
    printf("[9] emulated desk: per-channel meters (stretch at 1:00 of the 44.1 kHz track)\n");
    meters src = source_meters(ogg, 60000, 1.5);
    E_START();
    e_load(ogg);
    show("on the track (channels 1/2 and 3/4)", src);
    meters a0 = scene(1, 1, 1, 1, 0, EQ_FLAT);
    show("faders 100%, not normalized", a0);
    // tracks peaking above 0 dB clip at 100% without normalizing: the level is checked at 50%
    meters h0 = scene(0.5f, 0.5f, 1, 1, 0, EQ_FLAT);
    show("faders 50%, not normalized", h0);
    int same = 1;
    for (int c = 0; c < 4; c++) same &= fabs(db(h0.rms[c]) + 6.02 - db(src.rms[c])) < 0.3;
    check(same, "all 4 channels arrive: track 1/2 on BT/USB and 3/4 on 7/8, at the same level", "");

    meters b0 = scene(1, 0, 1, 1, 0, EQ_FLAT);
    show("metronome 0%", b0);
    check(b0.peak[2] == 0 && b0.peak[3] == 0 && fabs(db(b0.rms[0]) - db(a0.rms[0])) < SAME_DB,
          "metronome fader only affects 7/8 (truly zeroes it)", "");
    meters c0 = scene(0, 1, 1, 1, 0, EQ_FLAT);
    show("backing 0%", c0);
    check(c0.peak[0] == 0 && c0.peak[1] == 0 && fabs(db(c0.rms[2]) - db(a0.rms[2])) < SAME_DB,
          "backing fader only affects BT/USB (nothing leaks from the metronome)", "");
    meters d0 = scene(0.5f, 1, 1, 1, 0, EQ_FLAT);
    show("backing 50%", d0);
    snprintf(buf, sizeof buf, "%.2f dB", db(d0.rms[0]) - db(a0.rms[0]));
    check(fabs(db(d0.rms[0]) - db(a0.rms[0]) + 6.02) < 0.3 && fabs(db(d0.rms[2]) - db(a0.rms[2])) < SAME_DB,
          "backing 50% = -6 dB on BT/USB, 7/8 unchanged", buf);
    float bass[5] = {6, 0, 0, 0, 0};
    meters e0 = scene(1, 1, 1, 1, 1, bass);
    show("EQ: lows +6 dB", e0);
    check(fabs(db(e0.rms[2]) - db(a0.rms[2])) < SAME_DB && fabs(db(e0.rms[0]) - db(a0.rms[0])) > 0.1
          && e0.clip[0] <= a0.clip[0], "EQ only on BT/USB, and the boost doesn't increase clipping", "");

    // normalization with the gains the old normalizer computed for this track
    float *an = (float *)FN(analyze)(env, NULL, (jstring)ogg);
    float lufs = an[0], pkb = an[1], pkc = an[2];
    double gbn = fmin(-16 - lufs, -1 - db(pkb)); gbn = fmax(-24, fmin(15, gbn));
    double gcn = pkc > 1e-6 ? fmax(-24, fmin(15, -6 - db(pkc))) : 0;
    meters f0 = scene(1, 1, powf(10, gbn / 20), powf(10, gcn / 20), 0, EQ_FLAT);
    snprintf(buf, sizeof buf, "normalized (%+.1f / %+.1f dB)", gbn, gcn);
    show(buf, f0);
    snprintf(buf, sizeof buf, "backing %+.1f dB, metronome %+.1f dB; clipping without normalizing: BT/USB %ld/%ld, 7/8 %ld/%ld samples",
             db(f0.rms[0]) - db(src.rms[0]), db(f0.rms[2]) - db(src.rms[2]), a0.clip[0], a0.clip[1], a0.clip[2], a0.clip[3]);
    check(f0.clip[0] + f0.clip[1] + f0.clip[2] + f0.clip[3] == 0 && fabs(db(f0.rms[0]) - db(src.rms[0]) - gbn) < 0.3,
          "normalization: no clipping, right gain on each pair", buf);
#ifdef LIM_CEIL
    // EQ with limiter (19/10/2026): the boost no longer lowers the whole backing; only the peaks are held
    double ceil_db = db(LIM_CEIL) + 0.01;
    snprintf(buf, sizeof buf, "backing %+.1f dB vs. no EQ (the validated one was ~-5 dB); peak %.1f dB",
             db(e0.rms[0]) - db(a0.rms[0]), db(fmax(e0.peak[0], e0.peak[1])));
    check(e0.clip[0] + e0.clip[1] == 0 && db(e0.rms[0]) > db(a0.rms[0]) - 1.0 && db(fmax(e0.peak[0], e0.peak[1])) <= ceil_db,
          "EQ lows +6, faders 100%: no clipping, volume kept, peak up to -1 dBFS", buf);
    meters n1 = scene(1, 1, powf(10, gbn / 20), powf(10, gcn / 20), 1, bass);
    show("normalized + lows +6", n1);
    snprintf(buf, sizeof buf, "backing %+.1f dB vs. normalized without EQ", db(n1.rms[0]) - db(f0.rms[0]));
    check(n1.clip[0] + n1.clip[1] == 0 && db(n1.rms[0]) > db(f0.rms[0]) - 0.5 && fabs(db(n1.rms[2]) - db(f0.rms[2])) < SAME_DB,
          "normalized + EQ: no clipping, backing doesn't drop, metronome unchanged", buf);
    meters z1 = scene(1, 1, powf(10, gbn / 20), powf(10, gcn / 20), 1, EQ_FLAT);
    snprintf(buf, sizeof buf, "difference %.3f dB", db(z1.rms[0]) - db(f0.rms[0]));
    check(fabs(db(z1.rms[0]) - db(f0.rms[0])) < 0.05, "EQ on and flat, no peak above -1 dBFS: limiter stays out", buf);
    float hard[5] = {12, 12, 0, 0, 0};
    meters k1 = scene(1, 1, 1, 1, 1, hard);
    show("EQ: lows +12 and body +12", k1);
    snprintf(buf, sizeof buf, "pico %.1f dB", db(fmax(k1.peak[0], k1.peak[1])));
    check(k1.clip[0] + k1.clip[1] == 0 && db(fmax(k1.peak[0], k1.peak[1])) <= ceil_db,
          "extreme boost (+12 and +12): limiter holds, no clipping", buf);
#endif
    if (wavdir) {
        snprintf(path, sizeof path, "%s/desk-BT-USB-%s.wav", wavdir, ENGINE_NAME); write_wav(path, 0);
        snprintf(path, sizeof path, "%s/desk-7-8-%s.wav", wavdir, ENGINE_NAME); write_wav(path, 2);
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
    show("paused", g0);
    check(g0.peak[0] + g0.peak[1] + g0.peak[2] + g0.peak[3] == 0, "pause = silence on all 4 channels (stream stays active)", "");
    FN(stop)(env, NULL);
    sim_close_conn();

#ifdef SIM_METER
    // spectrum FFT with a known signal: a 1 kHz sine at -6 dBFS (amplitude 0.5)
    printf("[10] spectrum: 1 kHz sine, -6 dBFS\n");
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
    snprintf(buf, sizeof buf, "1 kHz band %.2f dB, far from it at most %.1f dB", db(sp[hit]), far);
    check(hit >= 0 && fabs(db(sp[hit]) + 6.02) < 1.5 && far < -50, "spectrum: right level at 1 kHz and nothing far from it", buf);
#endif

    printf("%s: %d failure(s)\n", ENGINE_NAME, fails);
    return fails ? 1 : 0;
}
