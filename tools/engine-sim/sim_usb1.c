// A fake USB Audio 1.0 card for the engine: stereo, 16 bits, 44.1 kHz only, adaptive output, full speed (1 packet per ms).
// Proves what sim.c can't: packets within the card's size, the pace of 44.1 kHz, no input stream, and the song and the
// click keeping their pitch and tempo. sim.c goes on proving that a USB Audio 2.0 desk sounds the same. See run.sh.
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
int fake_ioctl(int fd, unsigned long req, ...);
static int fake_poll(struct pollfd *p, nfds_t n, int ms) { struct timespec t = {0, 1000000}; nanosleep(&t, NULL); return 1; }
#define ioctl fake_ioctl
#define poll fake_poll
#include ENGINE_C
#undef ioctl
#undef poll

#define RATE 44100
#define MAX_PACKET 184
#define FRAME 4                        // 2 channels x 16 bits
#define QMAX 64

static double now(void) { struct timespec t; clock_gettime(CLOCK_MONOTONIC, &t); return t.tv_sec + t.tv_nsec / 1e9; }
static void sleep_s(double s) { struct timespec t = {(time_t)s, (long)((s - (time_t)s) * 1e9)}; nanosleep(&t, NULL); }

typedef struct { struct usbdevfs_urb *u; double due; } qent;
static pthread_mutex_t mx = PTHREAD_MUTEX_INITIALIZER;
static pthread_cond_t cv = PTHREAD_COND_INITIALIZER;
static qent q[QMAX];
static int nq = 0;
static double line = 0;
static long in_urbs = 0, too_big = 0, frames = 0;
static int most_queued = 0;
static int min_nf = 1 << 30, max_nf = 0;
static volatile int capturing = 0;
static float *cap;
static long cap_n = 0, cap_max = RATE * 20L;

static void take(struct usbdevfs_urb *u) {
    uint8_t *b = u->buffer;
    int off = 0;
    for (int k = 0; k < u->number_of_packets; k++) {
        int len = u->iso_frame_desc[k].length, nf = len / FRAME;
        u->iso_frame_desc[k].actual_length = len;
        u->iso_frame_desc[k].status = 0;
        if (capturing) {
            if (len > MAX_PACKET) too_big++;
            if (nf < min_nf) min_nf = nf;
            if (nf > max_nf) max_nf = nf;
            frames += nf;
            for (int f = 0; f < nf && cap_n < cap_max; f++) {
                int16_t v; memcpy(&v, b + off + f * FRAME, 2);
                cap[cap_n++] = v / 32767.0f;
            }
        }
        off += len;
    }
    u->status = 0;
}

int fake_ioctl(int fd, unsigned long req, ...) {
    va_list a; va_start(a, req); void *arg = va_arg(a, void *); va_end(a);
    if (req == USBDEVFS_GET_SPEED) return 2;   // USB_SPEED_FULL
    if (req == USBDEVFS_SETINTERFACE) return 0;
    pthread_mutex_lock(&mx);
    int r = 0;
    if (req == USBDEVFS_SUBMITURB) {
        struct usbdevfs_urb *u = arg;
        if (u->endpoint & 0x80) in_urbs++;
        if (nq == QMAX) { errno = ENOMEM; r = -1; }
        else {
            double t = now();
            if (line < t) line = t;
            line += PKTS_PER_URB / 1000.0;
            q[nq++] = (qent){u, line};
            if (nq > most_queued) most_queued = nq;
            pthread_cond_broadcast(&cv);
        }
    } else if (req == USBDEVFS_REAPURB || req == USBDEVFS_REAPURBNDELAY) {
        for (;;) {
            int best = -1;
            for (int i = 0; i < nq; i++) if (best < 0 || q[i].due < q[best].due) best = i;
            double t = now();
            if (best >= 0 && q[best].due <= t) {
                qent e = q[best]; q[best] = q[--nq];
                take(e.u);
                *(struct usbdevfs_urb **)arg = e.u;
                break;
            }
            if (req == USBDEVFS_REAPURBNDELAY) { errno = EAGAIN; r = -1; break; }
            struct timespec ts; clock_gettime(CLOCK_REALTIME, &ts);
            ts.tv_nsec += 1000000; if (ts.tv_nsec >= 1000000000L) { ts.tv_sec++; ts.tv_nsec -= 1000000000L; }
            pthread_cond_timedwait(&cv, &mx, &ts);
        }
    } else if (req == USBDEVFS_DISCARDURB) {
        r = -1; errno = EINVAL;
        for (int i = 0; i < nq; i++) if (q[i].u == arg) { q[i].due = now(); r = 0; }
        pthread_cond_broadcast(&cv);
    }
    pthread_mutex_unlock(&mx);
    return r;
}

static const char *j_get(JNIEnv *e, jstring s, jboolean *c) { return (const char *)s; }
static void j_rel(JNIEnv *e, jstring s, const char *c) {}
static void j_getf(JNIEnv *e, jfloatArray a, jsize st, jsize n, jfloat *b) { memcpy(b, (float *)a + st, n * sizeof(float)); }
static jsize j_len(JNIEnv *e, jarray a) { return a == (jarray)cap ? 0 : *((jsize *)a - 1); }
static struct JNINativeInterface_ fns;
static const struct JNINativeInterface_ *envp = &fns;
static JNIEnv *env = (JNIEnv *)&envp;

static int fails = 0;
static void check(int ok, const char *what, const char *detail) {
    printf("  %s %s: %s\n", ok ? "OK  " : "FAIL ", what, detail);
    if (!ok) fails++;
}

static void tone_wav(const char *path, int rate, double hz, int secs) {
    uint32_t n = (uint32_t)rate * secs, data = n * 4, riff = 36 + data, br = rate * 4, fmt = 16, sr = rate;
    uint16_t pcm = 1, ch = 2, al = 4, bits = 16;
    FILE *f = fopen(path, "wb");
    fwrite("RIFF", 1, 4, f); fwrite(&riff, 4, 1, f); fwrite("WAVEfmt ", 1, 8, f); fwrite(&fmt, 4, 1, f);
    fwrite(&pcm, 2, 1, f); fwrite(&ch, 2, 1, f); fwrite(&sr, 4, 1, f); fwrite(&br, 4, 1, f); fwrite(&al, 2, 1, f); fwrite(&bits, 2, 1, f);
    fwrite("data", 1, 4, f); fwrite(&data, 4, 1, f);
    for (uint32_t i = 0; i < n; i++) {
        int16_t v = (int16_t)(16000 * sin(2 * M_PI * hz * i / rate));
        fwrite(&v, 2, 1, f); fwrite(&v, 2, 1, f);
    }
    fclose(f);
}

// Frequency by rising zero crossings, away from the fades at the ends.
static double pitch(const float *s, long n) {
    long first = -1, last = -1, count = 0;
    for (long i = 1; i < n; i++) if (s[i - 1] < 0 && s[i] >= 0) { if (first < 0) first = i; last = i; count++; }
    return count > 1 ? (count - 1) * (double)RATE / (last - first) : 0;
}

static void mix(void) {
    float g[8] = {1, 1}, nm[8] = {1, 1}, r[32 * 8] = {0};
    r[0] = 1; r[9] = 1;
    FN(setMix)(env, NULL, (jfloatArray)g, (jfloatArray)nm, 0, (jfloatArray)r);
}

static void play_and_measure(const char *path, const char *what) {
    char b[160];
    FN(load)(env, NULL, (jstring)path);
    mix();
    FN(play)(env, NULL);
    sleep_s(1.0);
    cap_n = 0; frames = 0; min_nf = 1 << 30; max_nf = 0; capturing = 1;
    double t0 = now(), p0 = FN(position)(env, NULL);
    sleep_s(5.0);
    double secs = now() - t0, moved = (FN(position)(env, NULL) - p0) / 1000.0;
    capturing = 0;
    double hz = pitch(cap, cap_n);
    snprintf(b, sizeof b, "%.1f Hz", hz);
    check(fabs(hz - 1000) < 3, what, b);
    snprintf(b, sizeof b, "%.0f frames a second", frames / secs);
    check(fabs(frames / secs - RATE) < RATE * 0.01, "pace of the card", b);
    snprintf(b, sizeof b, "%d to %d frames, %ld over %d bytes", min_nf, max_nf, too_big, MAX_PACKET);
    check(min_nf >= 44 && max_nf <= 46 && too_big == 0, "packets fit the card", b);
    snprintf(b, sizeof b, "%.2f s in %.2f s", moved, secs);
    check(fabs(moved - secs) < 0.1, "position follows real time", b);
    FN(pause)(env, NULL);
    sleep_s(0.1);
}

// A beat is the first loud sample after silence.
static void click_test(void) {
    static struct { jsize n; float s[4800]; } a, c;
    a.n = c.n = 4800;
    for (int i = 0; i < 4800; i++) a.s[i] = c.s[i] = 0.5f * (float)sin(2 * M_PI * 1000.0 * i / 48000);
    cap_n = 0; capturing = 1;
    FN(setClick)(env, NULL, (jfloatArray)a.s, (jfloatArray)c.s, 24000, 0, 1, 0);   // 120 bpm at 48 kHz
    sleep_s(3.3);
    capturing = 0;
    FN(setClick)(env, NULL, NULL, NULL, 0, -1, -1, 0);
    long onset[16]; int n = 0; long quiet = 0;   // what was still sounding when it started is no beat
    for (long i = 0; i < cap_n && n < 16; i++) {
        if (fabsf(cap[i]) > 0.01f) { if (quiet >= 2000) onset[n++] = i; quiet = 0; } else quiet++;
    }
    if (getenv("SIM_VERBOSE")) for (int i = 0; i < n; i++) printf("  beat %d at %ld\n", i, onset[i]);
    char b[160];
    double beat = n > 1 ? (double)(onset[n - 1] - onset[0]) / (n - 1) : 0;
    snprintf(b, sizeof b, "%.0f frames, %d beats", beat, n);
    check(n >= 5 && fabs(beat - 22050) < 2, "click keeps 120 bpm", b);
    double hz = n > 0 ? pitch(cap + onset[0], (long)(0.09 * RATE)) : 0;
    snprintf(b, sizeof b, "%.1f Hz", hz);
    check(fabs(hz - 1000) < 15, "click keeps its pitch", b);

    // count-in "1 . 2 . 1 2 3 4": beats 2 and 4 from the start silent (bits 1 and 3)
    cap_n = 0; capturing = 1;
    FN(setClick)(env, NULL, (jfloatArray)a.s, (jfloatArray)c.s, 24000, 0, 1, 0xA);
    sleep_s(3.3);
    capturing = 0;
    FN(setClick)(env, NULL, NULL, NULL, 0, -1, -1, 0);
    n = 0; quiet = 0;
    for (long i = 0; i < cap_n && n < 16; i++) {
        if (fabsf(cap[i]) > 0.01f) { if (quiet >= 2000) onset[n++] = i; quiet = 0; } else quiet++;
    }
    double g1 = n > 3 ? (double)(onset[1] - onset[0]) / 22050 : 0, g2 = n > 3 ? (double)(onset[2] - onset[1]) / 22050 : 0;
    double g3 = n > 3 ? (double)(onset[3] - onset[2]) / 22050 : 0;
    snprintf(b, sizeof b, "gaps %.2f %.2f %.2f beats, %d beats", g1, g2, g3, n);
    check(n >= 4 && fabs(g1 - 2) < 0.01 && fabs(g2 - 2) < 0.01 && fabs(g3 - 1) < 0.01, "count-in leaves beats 2 and 4 silent", b);
}

int main(int argc, char **argv) {
    char p48[512], p44[512], b[160];
    snprintf(p48, sizeof p48, "%s/tone48.wav", argv[1]);
    snprintf(p44, sizeof p44, "%s/tone44.wav", argv[1]);
    fns.GetStringUTFChars = j_get; fns.ReleaseStringUTFChars = j_rel; fns.GetFloatArrayRegion = j_getf; fns.GetArrayLength = j_len;
    cap = malloc(sizeof(float) * cap_max);
    tone_wav(p48, 48000, 1000, 9);
    tone_wav(p44, 44100, 1000, 9);
    printf("USB Audio 1.0 card (stereo, 16 bits, 44.1 kHz, adaptive)\n");
    FN(setRate)(env, NULL, RATE);
    FN(start)(env, NULL, 3, 0x02, MAX_PACKET, 2, 2, 1, 1, -1, 0, 0);
    play_and_measure(p48, "48 kHz track keeps its pitch");
    play_and_measure(p44, "44.1 kHz track keeps its pitch");
    click_test();
    snprintf(b, sizeof b, "%ld input transfers", in_urbs);
    check(in_urbs == 0, "no input stream", b);
    snprintf(b, sizeof b, "%d queued at most", most_queued);
    check(most_queued <= 8, "a short queue at full speed", b);   // 32 stopped a real phone for good
    FN(stop)(env, NULL);
    FN(setRate)(env, NULL, 48000);
    printf("USB Audio 1.0: %d failure(s)\n", fails);
    return fails != 0;
}
