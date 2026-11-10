// Engine (engine.c) simulator on the Mac, without the tablet or the desk.
//
// A fake USB interface (8 outputs) answers usbfs ioctls at real pace (each URB = 8 packets of 125 µs) and returns input
// on the desk's clock. The same program compiles with the validated engine.c (tag audio-validado) and with the current
// one, to compare both. See run.sh.
#ifndef __APPLE__
#define _GNU_SOURCE   // Linux (CI): gettid and friends, as on Android
#endif
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
#ifdef __APPLE__
#define gettid() 0   // macOS has no gettid; on Linux the real one (glibc, _GNU_SOURCE above)
#endif

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
// the engine's stall watch wakes the USB thread with a signal, and on the device REAPURB returns EINTR: the fake reap
// waits on a condition that ignores signals, so the wake-up goes through here (before, no stall path ever ran here)
static int sim_pthread_kill(pthread_t thr, int sig);
#define pthread_kill sim_pthread_kill
#define stb_vorbis_seek_frame sim_seek_frame
#define stb_vorbis_get_samples_float_interleaved sim_get_samples
#include ENGINE_C
#undef pthread_kill
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
static int sim_intr = 0;   // a signal for the USB thread: the next blocking reap returns EINTR
// usb_hold: the USB thread loses the CPU inside its next output submit, until the stall watch has signalled it. On the
// device a signal that finds the thread outside the reap only runs the handler, no REAPURB returns EINTR for it: so it is
// here while usb_held, from the hold to the thread's next reap
static int usb_hold = 0, usb_held = 0, held_signals = 0;
static int sim_pthread_kill(pthread_t thr, int sig) {
    pthread_mutex_lock(&dev_mx);
    if (usb_held) held_signals++;
    else { sim_intr = 1; pthread_cond_broadcast(&dev_cv); }
    pthread_mutex_unlock(&dev_mx);
    return 0;
}
static double in_dead_from = 0;   // from this time on, input URBs are accepted and never come back (an input that went quiet)
static double clock_ratio = 1.0002;    // desk clock slightly fast (the feedback corrects it)
static double in_jitter = 0;               // each input URB comes back up to this many seconds early or late: a host whose timing is noisy
static double in_spike = 0;                // and every in_spike_n-th input URB comes this late (a busy moment of the host)
static long in_spike_n = 0, in_urb_count = 0;
static double runner_jitter = 0;           // SIM_RUNNER_JITTER=<seconds>: a shared CI runner's timing noise, on top of everything
static int in_loss_boundary = 0;           // packets lost at the start of every input URB (a host controller that loses a poll or two per boundary)
static double out_dies_with_in_s = 0;      // output URBs stop coming back this long after the first input URB, the input keeps going (the S26 with a high-speed interface)
// From all_dead_from on no URB comes back, and no error says so (a queue that stalled for good); the input's keep coming
// for in_lingers_s more, so the last URB back is an input one, well after the output's last. A discarded URB still returns
static double all_dead_from = 0, in_lingers_s = 0;
static int stalled_for_good(const struct usbdevfs_urb *u, int st, double t) {
    return all_dead_from > 0 && !st && t > all_dead_from + ((u->endpoint & 0x80) ? in_lingers_s : 0);
}
static double in_long_dies_s = 0, in_t0 = 0;   // input URBs longer than 16 packets stop coming back this long after the first input URB (the S26)
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
            u->iso_frame_desc[k].actual_length = st || k < in_loss_boundary ? 0 : nf * IN_FB;
        } else {
            u->iso_frame_desc[k].actual_length = u->iso_frame_desc[k].length;
        }
        u->iso_frame_desc[k].status = st || ((u->endpoint & 0x80) && k < in_loss_boundary) ? 1 : 0;
        u->actual_length += u->iso_frame_desc[k].actual_length;
    }
    if (!(u->endpoint & 0x80) && !st) capture_out(u);
}

static int pop_ready(struct usbdevfs_urb **out, int block) {
    for (;;) {
        if (block && sim_intr) { sim_intr = 0; errno = EINTR; return -1; }
        int best = -1;
        double at = now();
        for (int i = 0; i < nq; i++) if (!stalled_for_good(q[i].u, q[i].st, at) && (best < 0 || q[i].due < q[best].due)) best = i;
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
        usb_held = 0;
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
        if (usb_hold && !(u->endpoint & 0x80)) {
            usb_hold = 0; usb_held = 1;
            for (int ms = 0; !held_signals && ms < 2000; ms++) {
                pthread_mutex_unlock(&dev_mx);
                struct timespec w = {0, 1000000}; nanosleep(&w, NULL);
                pthread_mutex_lock(&dev_mx);
            }
        }
        if (disconnected) { errno = ENODEV; r = -1; }
        else if (nq == QMAX) { errno = ENOMEM; r = -1; }
        else {
            double t = now(), *line = (u->endpoint & 0x80) ? &in_due : &out_due;
            if ((u->endpoint & 0x80) && in_t0 == 0) in_t0 = t;
            if ((u->endpoint & 0x80) && in_long_dies_s > 0 && u->number_of_packets > 16 && t > in_t0 + in_long_dies_s) in_dead_from = 1e-9;
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
            *line += u->number_of_packets / 8000.0;   // a URB takes its own packets' worth of microframes (the input's are longer)
            int out_dead = !(u->endpoint & 0x80) && out_dies_with_in_s > 0 && in_t0 > 0 && t > in_t0 + out_dies_with_in_s;
            q[nq++] = (qent){u, out_dead || ((u->endpoint & 0x80) && in_dead_from > 0 && t > in_dead_from) ? 1e18 : *line + ((u->endpoint & 0x80) && in_jitter + runner_jitter > 0 ? (drand48() * 2 - 1) * (in_jitter + runner_jitter) : 0) + ((u->endpoint & 0x80) && in_spike_n > 0 && ++in_urb_count % in_spike_n == 0 ? in_spike : 0), 0};
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
// Holds the USB thread (see usb_hold) and waits for it to go on: 1 when the stall watch signalled it meanwhile
static int sim_hold_usb(void) {
    pthread_mutex_lock(&dev_mx); usb_hold = 1; held_signals = 0; pthread_mutex_unlock(&dev_mx);
    for (int i = 0; i < 300; i++) {
        struct timespec w = {0, 10000000}; nanosleep(&w, NULL);
        pthread_mutex_lock(&dev_mx); int went_on = held_signals > 0 && !usb_held; pthread_mutex_unlock(&dev_mx);
        if (went_on) return 1;
    }
    return 0;
}
static int sim_kernel_urbs(void) { pthread_mutex_lock(&dev_mx); int n = nq; pthread_mutex_unlock(&dev_mx); return n; }
static void sim_close_conn(void) {   // UsbLink.close(): closing the fd returns what's left
    pthread_mutex_lock(&dev_mx);
    nq = 0; disconnected = 0; out_due = in_due = 0; out_urbs = 0; in_dead_from = 0; in_t0 = 0; all_dead_from = 0; in_lingers_s = 0;
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
#define E_START_FAST() (FN(start)(env, NULL, 3, 0x01, 7 * OUT_FB, 4, 4, 1, 1, 0x81, 7 * IN_FB, IN_FB), FN(setMeter)(env, NULL, 1))
#else
#define E_START_FAST() FN(start)(env, NULL, 3, 0x01, 7 * OUT_FB, 4, 4, 1, 1, 0x81, 7 * IN_FB, IN_FB)
#endif
#ifdef ENGINE_HAS_PROBE
// the engine holds its output until it has judged the input clock (about 2 s): the tests that need sound wait for it
static void wait_probe(void) { double t0 = now(); while (FN(probing)(env, NULL) && now() - t0 < 5.0) usleep(10000); }
#define E_START() (E_START_FAST(), wait_probe())
// A shared CI runner can be noisy enough for the probe to call the host noisy (plan B at open), which is right, and not what
// the tests of plan A are about: they open again, up to 3 times, until the host is judged well, and when even the fourth
// open is judged noisy the runner is too noisy for those tests, which are then reported as skipped (the same checks run clean
// on a quiet machine, and the tests of plan B at open run everywhere)
static int runner_noisy = 0;
static void open_judged_a(long seek_ms) {
    for (int i = 0; i < 3; i++) {
        E_START_FAST();
        FN(seek)(env, NULL, seek_ms);
        FN(play)(env, NULL);
        wait_probe();
        if (!g_fb_bad) return;
        FN(stop)(env, NULL);
        sim_close_conn();
    }
    E_START_FAST();
    FN(seek)(env, NULL, seek_ms);
    FN(play)(env, NULL);
    wait_probe();
    if (g_fb_bad) runner_noisy = 1;
}
#else
#define E_START() E_START_FAST()
#endif

// Fader and normalization for the backing (1/2) and the metronome (3/4). On the validated engine, through the two old
// faders; on Stagebook's (per-channel mixing only), through the equivalent setup: 1-2 backing with EQ, 3-4 metronome,
// direct routing. The sound has to come out identical.
static float s_gb = 1, s_gc = 1, s_nb = 1, s_nc = 1;
static void sim_mix(void) {
#ifdef ENGINE_HAS_MIX
    float g[8] = {s_gb, s_gb, s_gc, s_gc}, n[8] = {s_nb, s_nb, s_nc, s_nc}, r[32 * 8] = {0};
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
    setvbuf(stdout, NULL, _IOLBF, 0);   // line by line: a run cut short still shows how far it got
    sim_verbose = getenv("SIM_VERBOSE") != NULL;
    if (getenv("SIM_RUNNER_JITTER")) runner_jitter = atof(getenv("SIM_RUNNER_JITTER"));
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

    // SIM_ONLY=starve: only the stall watch's scenarios, in seconds, while working on it (the full run is for commits and CI)
    const char *only = getenv("SIM_ONLY");
#ifdef ENGINE_HAS_PROBE
    if (only && !strcmp(only, "starve")) { e_load(ogg); goto only_starve; }
#endif
    if (only) { printf("SIM_ONLY=%s: no such scenario here (starve needs an engine with the clock probe)\n", only); return 2; }

#ifdef OUTS_MAX
    // 0) an 18-channel desk: a channel routed above output 8 reaches it (the matrix used to stop at 8 outputs)
    printf("[0] a channel routed to output 13 of 18 reaches it\n");
    {
        float g[8] = {1, 1}, nm[8] = {1, 1}, r[OUTS_MAX * 8] = {0}, lane[8] = {0.5f, 0.25f}, out[OUTS_MAX];
        r[12 * 8 + 0] = 1; r[17 * 8 + 1] = 1;
        FN(setMix)(env, NULL, (jfloatArray)g, (jfloatArray)nm, 0, (jfloatArray)r);
        int lanes_was = g_lanes; float fade_was = g_fade;
        g_lanes = 2; g_fade = 1;
        mix_custom(lane, out, 18);
        g_lanes = lanes_was; g_fade = fade_was;
        char d[96]; snprintf(d, sizeof d, "out13=%.2f out18=%.2f out8=%.2f", out[12], out[17], out[7]);
        check(out[12] == 0.5f && out[17] == 0.25f && out[7] == 0, "a channel routed above output 8 reaches it", d);
        sim_mix();
    }
#endif
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
    check(FN(isRunning)(env, NULL) && labs(p5 - 91000) < 400, "plays again after reconnecting", buf);

    // 6) stream dies with no disconnect notice (the desk restarts), the kernel still holds URBs
    printf("[6] USB stream dies without notice (protocol error)\n");
    fail_next_reap = 1;
    sleep_s(0.3);
    snprintf(buf, sizeof buf, "isRunning=%d, %d blocks leaked, %d freed while the kernel was using them", FN(isRunning)(env, NULL), nlive, freed_while_queued);
    check(!FN(isRunning)(env, NULL) && nlive == 0 && freed_while_queued == 0, "drop noticed and memory returned", buf);
    FN(stop)(env, NULL);
    sim_close_conn();

    // 6b) the input answers for a second and then goes quiet for good (seen on a phone: the engine followed its frozen
    // count and crawled at -17%); the output must keep the real-time pace on its own
    // the validated reference (23/10) never had this defense: its result is printed, not counted
    printf("[6b] input answers for 1 s and then never again\n");
    int fails_before_6b = fails;
    pthread_mutex_lock(&dev_mx); in_dead_from = now() + 1.0; pthread_mutex_unlock(&dev_mx);
    E_START();
    FN(seek)(env, NULL, 30000);
    FN(play)(env, NULL);
    sleep_s(5.0);
    long p6 = FN(position)(env, NULL);
    snprintf(buf, sizeof buf, "position %ld ms after 5 s of play (expected ~35000)", p6);
    check(FN(isRunning)(env, NULL) && p6 > 34000 && p6 < 35300, "keeps real-time pace without the input", buf);
    if (!strcmp(ENGINE_NAME, "validated")) { fails = fails_before_6b; printf("  (reference engine: not counted)\n"); }
    FN(stop)(env, NULL);
    sim_close_conn();

    // 7) 10 on/off cycles
    printf("[7] 10 on/off cycles\n");
    for (int i = 0; i < 10; i++) { E_START_FAST(); FN(play)(env, NULL); sleep_s(0.1); FN(stop)(env, NULL); sim_close_conn(); }
    snprintf(buf, sizeof buf, "%d memory blocks leaked", nlive);
    check(nlive == 0, "no leak", buf);

    // 7b) stopping in order to reconnect (the interface's first connection came silent): no half second of silent tail
#ifdef ENGINE_HAS_FASTSTOP
    printf("[7b] stopping to reconnect doesn't wait for the silent tail\n");
    E_START_FAST(); FN(play)(env, NULL); sleep_s(0.4);
    double t7 = now(); FN(stopFast)(env, NULL); double fast7 = (now() - t7) * 1000;
    sim_close_conn();
    E_START_FAST(); FN(play)(env, NULL); sleep_s(0.4);
    t7 = now(); FN(stop)(env, NULL); double slow7 = (now() - t7) * 1000;
    sim_close_conn();
    snprintf(buf, sizeof buf, "fast stop %.0f ms, normal stop %.0f ms", fast7, slow7);
    // relative, not absolute: on a shared CI runner both stops pay the same scheduling delay (fast 190-325 ms, normal
    // 630-780 ms seen on GitHub), but only the normal one waits out the ~500 ms silent tail
    check(slow7 - fast7 > 300, "a fast stop skips the silent tail, the normal one keeps it", buf);

    // 7c) the player closes the stream after 10 s of nothing sounding and opens it again on the next play: an open idle
    // stream makes about a thousand submits a second, a closed one none, and playing from a closed stream starts clean
    printf("[7c] closing the stream when idle, and playing from a closed one\n");
    E_START(); sleep_s(0.3);   // open, nothing playing
    pthread_mutex_lock(&dev_mx); long idle0 = out_urbs; pthread_mutex_unlock(&dev_mx);
    sleep_s(1.0);
    pthread_mutex_lock(&dev_mx); long idle_rate = out_urbs - idle0; pthread_mutex_unlock(&dev_mx);
    FN(stopFast)(env, NULL);
    sim_close_conn();
    pthread_mutex_lock(&dev_mx); long closed0 = out_urbs; pthread_mutex_unlock(&dev_mx);
    sleep_s(1.0);
    pthread_mutex_lock(&dev_mx); long closed_rate = out_urbs - closed0; pthread_mutex_unlock(&dev_mx);
    snprintf(buf, sizeof buf, "open and idle: %ld submits in 1 s; closed: %ld in 1 s", idle_rate, closed_rate);
    check(idle_rate > 500 && closed_rate == 0, "a closed stream makes no USB submits", buf);
    // opens again and plays: the track starts from where it was asked, nothing lost, no gap on the desk
    E_START();
    reset_metrics();
    FN(seek)(env, NULL, 60000);
    cap_n = 0; capturing = 1;
    FN(play)(env, NULL);
    sleep_s(1.0);
    capturing = 0;
    long p7c = FN(position)(env, NULL);
    meters m7c = meter(cap, cap_n);
    snprintf(buf, sizeof buf, "position %ld ms after 1 s (expected ~61000), %ld gaps, peak %.3f", p7c, iso_gaps, m7c.peak[0]);
    check(FN(isRunning)(env, NULL) && p7c > 60700 && p7c < 61300 && iso_gaps == 0 && m7c.peak[0] > 0,
          "play from a closed stream starts the track, with sound and no gap", buf);
    FN(stop)(env, NULL);
    sim_close_conn();
#endif

#ifdef ENGINE_HAS_PROBE
    // 7d) the input clock is judged once, at open, before any sound (user, 08/11): a play right after the open waits for
    // the verdict; a host whose timing is only noisy keeps plan A for the whole song; a clock really off goes to plan B
    // before the first note, and the plan never changes in the middle of a song
    printf("[7d] the input clock is judged at open, before any sound\n");
    // a host whose timing has busy moments: a little noise on every URB and, now and then, one URB 4 ms late (a one-second
    // reading off by 0.4%, the OnePlus), with the clock itself right
    pthread_mutex_lock(&dev_mx); in_jitter = 0.0003; in_spike = 0.004; in_spike_n = 400; in_urb_count = 0; pthread_mutex_unlock(&dev_mx);
    E_START_FAST();
    FN(seek)(env, NULL, 60000);
    FN(play)(env, NULL);
    sleep_s(0.8);
    long held = FN(position)(env, NULL);
    snprintf(buf, sizeof buf, "position %ld ms 0.8 s after play, still judging=%d (expected 60000, silent)", held, (int)FN(probing)(env, NULL));
    check(FN(probing)(env, NULL) && held < 60100, "the output is held while the clock is judged", buf);
    wait_probe();
    if (g_fb_bad) { FN(stop)(env, NULL); sim_close_conn(); open_judged_a(60000); }   // a noisy runner: see open_judged_a
    int noisy_plan_a = !g_fb_bad;
    double tol_pct = 100 * g_fb_tol_hz / g_out_rate;
    sleep_s(runner_noisy ? 1.0 : 30.0);
    long p_noisy = FN(position)(env, NULL);
    snprintf(buf, sizeof buf, "plan A at open=%d, plan A after 30 s=%d, tolerance %.2f%%, position %ld ms", noisy_plan_a, !g_fb_bad, tol_pct, p_noisy);
    check(runner_noisy || (noisy_plan_a && !g_fb_bad && p_noisy > 89000), runner_noisy ? "busy moments alone keep plan A for the whole song (skipped: the runner is too noisy)" : "busy moments alone keep plan A for the whole song", buf);
    FN(stop)(env, NULL);
    sim_close_conn();
    pthread_mutex_lock(&dev_mx); in_jitter = 0; in_spike_n = 0; pthread_mutex_unlock(&dev_mx);

    pthread_mutex_lock(&dev_mx); clock_ratio = 1.012; pthread_mutex_unlock(&dev_mx);   // a clock 1.2% fast
    E_START_FAST();
    FN(seek)(env, NULL, 60000);
    FN(play)(env, NULL);
    wait_probe();
    int off_at_open = g_fb_bad && g_fb_open_bad;
    sleep_s(3.0);
    long p_off = FN(position)(env, NULL);
    snprintf(buf, sizeof buf, "off at open=%d, position %ld ms (expected ~63000 at real-time pace)", off_at_open, p_off);
    check(off_at_open && p_off > 62500 && p_off < 63500, "a clock really off goes to plan B before the first note and holds the pace", buf);
    FN(stop)(env, NULL);
    sim_close_conn();

    // the worst case, seen on the budget tablet: the clock is off (-0.6%) AND the readings are noisy: the noise must not
    // buy room for the offset (it did, and the output ran 6 s on a bad clock before leaving plan A)
    pthread_mutex_lock(&dev_mx); clock_ratio = 0.994; in_jitter = 0.006; pthread_mutex_unlock(&dev_mx);
    E_START_FAST();
    FN(seek)(env, NULL, 60000);
    FN(play)(env, NULL);
    wait_probe();
    int worst_off = g_fb_bad && g_fb_open_bad;
    snprintf(buf, sizeof buf, "off at open=%d", worst_off);
    check(worst_off, "an offset clock with noisy readings goes to plan B at open, not 6 s into the song", buf);
    FN(stop)(env, NULL);
    sim_close_conn();
    pthread_mutex_lock(&dev_mx); clock_ratio = 1.0002; in_jitter = 0; pthread_mutex_unlock(&dev_mx);

    // a clean host: nothing in the way, plan A, and a pause and a resume with the stream open are instant
    open_judged_a(60000);
    sleep_s(1.0);
    FN(pause)(env, NULL);
    sleep_s(0.3);
    long before = FN(position)(env, NULL);
    FN(play)(env, NULL);
    sleep_s(0.4);
    long after = FN(position)(env, NULL);
    snprintf(buf, sizeof buf, "plan A=%d, resumed %ld ms in 0.4 s", !g_fb_bad, after - before);
    check(runner_noisy || (!g_fb_bad && after - before > 250), runner_noisy ? "a clean clock is plan A and a resume doesn't wait again (skipped: the runner is too noisy)" : "a clean clock is plan A and a resume doesn't wait again", buf);
    FN(stop)(env, NULL);
    sim_close_conn();
#endif

#ifdef ENGINE_HAS_PROBE
    // 7e) the size of the input URBs is chosen at open, by measuring (user, 08/11): short ones where the host is good (the S26
    // loses its input with long ones), long ones where the host loses a poll or two at every boundary (the budget tablet)
    printf("[7e] the input URBs are short, and long only where the host loses packets\n");
    open_judged_a(60000);
    snprintf(buf, sizeof buf, "input packets per URB %d, plan A=%d", g_in_pkts, !g_fb_bad);
    check(runner_noisy || (g_in_pkts == 16 && !g_fb_bad), runner_noisy ? "a clean host stays on short input URBs (skipped: the runner is too noisy)" : "a clean host stays on short input URBs", buf);
    FN(stop)(env, NULL);
    sim_close_conn();

    pthread_mutex_lock(&dev_mx); in_loss_boundary = 1; pthread_mutex_unlock(&dev_mx);   // one packet lost per URB boundary
    E_START_FAST();
    FN(seek)(env, NULL, 60000);
    FN(play)(env, NULL);
    wait_probe();
    snprintf(buf, sizeof buf, "input packets per URB %d, decided=%d", g_in_pkts, g_fb_decided);
    check(g_in_pkts == 128 && g_fb_decided, "a host that loses a packet per boundary goes to long input URBs and judges again", buf);
    FN(stop)(env, NULL);
    sim_close_conn();
    pthread_mutex_lock(&dev_mx); in_loss_boundary = 0; pthread_mutex_unlock(&dev_mx);

    pthread_mutex_lock(&dev_mx); in_long_dies_s = 3.8; pthread_mutex_unlock(&dev_mx);   // like the S26: long input URBs stop coming back
    open_judged_a(60000);
    sleep_s(8.0);
    long p_s26 = FN(position)(env, NULL);
    snprintf(buf, sizeof buf, "input packets per URB %d, plan A after 8 s=%d, position %ld ms", g_in_pkts, !g_fb_bad, p_s26);
    check(runner_noisy || (g_in_pkts == 16 && !g_fb_bad && p_s26 > 67000), runner_noisy ? "a host whose input dies with long URBs never gets them (skipped: the runner is too noisy)" : "a host whose input dies with long URBs never gets them: plan A the whole time", buf);
    FN(stop)(env, NULL);
    sim_close_conn();
    pthread_mutex_lock(&dev_mx); in_long_dies_s = 0; pthread_mutex_unlock(&dev_mx);

only_starve:
    // the S26 with a high-speed interface (09/11): reading the input stops the output after ~0.2 s, the input goes on.
    // The stream has to end by itself and say why, so Java connects again without the input, and then it plays
    pthread_mutex_lock(&dev_mx); out_dies_with_in_s = 0.2; pthread_mutex_unlock(&dev_mx);
    E_START_FAST();
    FN(seek)(env, NULL, 60000);
    FN(play)(env, NULL);
    sleep_s(1.5);
    int starved_ended = !FN(isRunning)(env, NULL), starved_flag = FN(inputStarvedOutput)(env, NULL);
    snprintf(buf, sizeof buf, "stream ended=%d, input starved the output=%d", starved_ended, starved_flag);
    check(starved_ended && starved_flag, "an output stopped by the input ends the stream and asks for no input", buf);
    FN(stop)(env, NULL);
    sim_close_conn();
    FN(start)(env, NULL, 3, 0x01, 7 * OUT_FB, 4, 4, 1, 1, -1, 0, 0);   // what PlayerService does next: no input
    FN(play)(env, NULL);
    sleep_s(0.3);
    long p_noin0 = FN(position)(env, NULL);
    sleep_s(2.0);
    long p_noin1 = FN(position)(env, NULL);
    snprintf(buf, sizeof buf, "running=%d, %ld ms played in 2 s, starved flag now %d", FN(isRunning)(env, NULL), p_noin1 - p_noin0, FN(inputStarvedOutput)(env, NULL));
    check(FN(isRunning)(env, NULL) && p_noin1 - p_noin0 > 1800 && !FN(inputStarvedOutput)(env, NULL), "without the input that output plays on", buf);
    FN(stop)(env, NULL);
    sim_close_conn();
    pthread_mutex_lock(&dev_mx); out_dies_with_in_s = 0; pthread_mutex_unlock(&dev_mx);

    // 7f) the stall watch's verdict must not outlive the stall (audit, 10/11): its signal can find the USB thread outside
    // the reap (it lost the CPU for a moment), where no EINTR comes of it. The flag stayed set, and the next interrupted
    // reap, whatever its reason, was taken for a stalled queue: half the queue and a connection more
    printf("[7f] a stall verdict the USB thread outlived is dropped\n");
    FN(start)(env, NULL, 3, 0x01, 7 * OUT_FB, 4, 4, 1, 1, -1, 0, 0);
    FN(seek)(env, NULL, 60000);
    FN(play)(env, NULL);
    sleep_s(0.3);
    int q_before = FN(queueMs)(env, NULL);
    int woke = sim_hold_usb();
    sleep_s(0.2);
    sim_pthread_kill(g_usb_thr, SIGUSR2);   // a signal with no verdict behind it: the reap has only to wait again
    sleep_s(0.3);
    snprintf(buf, sizeof buf, "watch signalled the held thread=%d, running=%d, queue %d ms (was %d), status \"%s\"",
             woke, FN(isRunning)(env, NULL), FN(queueMs)(env, NULL), q_before, g_status);
    check(woke && FN(isRunning)(env, NULL) && FN(queueMs)(env, NULL) == q_before, "an interrupted wait after an outlived stall just waits again", buf);
    FN(stop)(env, NULL);
    sim_close_conn();
    FN(setQueue)(env, NULL, q_before);   // a failure here must not shorten the queue of the scenarios below

    // the same outlived stall, and then the input really starves the output: that is what the stream has to say
    E_START_FAST();
    FN(seek)(env, NULL, 60000);
    FN(play)(env, NULL);
    sleep_s(0.3);
    woke = sim_hold_usb();
    sleep_s(0.1);
    pthread_mutex_lock(&dev_mx); out_dies_with_in_s = 1e-9; pthread_mutex_unlock(&dev_mx);   // from now on
    sleep_s(0.8);
    // a runner so slow that the hold outlasted FB_STALL_S: the input was given up and everything stalled, another scenario
    int held_too_long = g_fb_bad;
    snprintf(buf, sizeof buf, "watch signalled the held thread=%d, stream ended=%d, input starved the output=%d, queue %d ms (was %d), status \"%s\"",
             woke, !FN(isRunning)(env, NULL), FN(inputStarvedOutput)(env, NULL), FN(queueMs)(env, NULL), q_before, g_status);
    check(held_too_long || (woke && !FN(isRunning)(env, NULL) && FN(inputStarvedOutput)(env, NULL) && FN(queueMs)(env, NULL) == q_before),
          held_too_long ? "an output starved after an outlived stall is still told apart (skipped: the runner is too noisy)" : "an output starved after an outlived stall is still told apart: no input, the queue as it was", buf);
    FN(stop)(env, NULL);
    sim_close_conn();
    FN(setQueue)(env, NULL, q_before);
    pthread_mutex_lock(&dev_mx); out_dies_with_in_s = 0; pthread_mutex_unlock(&dev_mx);

    // every URB stops coming back and nothing says so: the stream has to end by itself with half the queue, for Java to
    // connect again. With no input first, then with one
    printf("[7g] a queue stalled for good ends the stream with half the queue\n");
    for (int with_in = 0; with_in < 2; with_in++) {
        if (with_in) E_START_FAST(); else FN(start)(env, NULL, 3, 0x01, 7 * OUT_FB, 4, 4, 1, 1, -1, 0, 0);
        FN(seek)(env, NULL, 60000);
        FN(play)(env, NULL);
        sleep_s(0.3);
        // the input's last URB comes back 60 ms after the output's last: longer than a turn of the stall watch (50 ms),
        // so one of them always sees an output quiet for STALL_S beside an input that is not yet. It is still a stalled
        // queue, not an input starving the output: that verdict makes Java give the input up on this device for good
        pthread_mutex_lock(&dev_mx); in_lingers_s = 0.06; all_dead_from = now(); pthread_mutex_unlock(&dev_mx);
        sleep_s(0.8);
        int halved = q_before > QUEUE_MS_MIN ? q_before / 2 : q_before;
        snprintf(buf, sizeof buf, "stream ended=%d, input starved the output=%d, queue %d ms (was %d), status \"%s\"",
                 !FN(isRunning)(env, NULL), FN(inputStarvedOutput)(env, NULL), FN(queueMs)(env, NULL), q_before, g_status);
        check(!FN(isRunning)(env, NULL) && !FN(inputStarvedOutput)(env, NULL) && FN(queueMs)(env, NULL) == halved && strstr(g_status, "queue stalled"),
              with_in ? "a stalled queue with the input on is not taken for an input starving the output" : "a stalled queue ends the stream with half the queue", buf);
        FN(stop)(env, NULL);
        sim_close_conn();
        FN(setQueue)(env, NULL, q_before);
    }
    // 7h) the watch can decide on an old clock: it read the last reap, the USB thread then got a URB back and cleared
    // the verdict, and only then did the watch write it and signal (audit, 10/11). The queue is flowing again, so the
    // interrupted reap must just wait again: no connection more, no halved queue, and no input given up
    printf("[7h] a verdict written after the queue came back is dropped\n");
    for (int which = 0; which < 2; which++) {
        E_START_FAST();
        FN(seek)(env, NULL, 60000);
        FN(play)(env, NULL);
        sleep_s(0.3);
        int q0 = FN(queueMs)(env, NULL);
        for (int k = 0; k < 20 && FN(isRunning)(env, NULL); k++) {
            if (which) g_out_starved = 1; else g_stalled = 1;   // the late write
            sim_pthread_kill(g_usb_thr, SIGUSR2);
            sleep_s(0.02);
        }
        sleep_s(0.2);
        snprintf(buf, sizeof buf, "running=%d, input starved the output=%d, queue %d ms (was %d), status \"%s\"",
                 FN(isRunning)(env, NULL), FN(inputStarvedOutput)(env, NULL), FN(queueMs)(env, NULL), q0, g_status);
        check(FN(isRunning)(env, NULL) && !FN(inputStarvedOutput)(env, NULL) && FN(queueMs)(env, NULL) == q0,
              which ? "a late output-starved verdict on a flowing queue just waits again" : "a late stall verdict on a flowing queue just waits again", buf);
        FN(stop)(env, NULL);
        sim_close_conn();
        FN(setQueue)(env, NULL, q0);
    }
    if (only) { printf("%s: %d failure(s)\n", ENGINE_NAME, fails); return fails ? 1 : 0; }
#endif

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
