// Reference engine (validated on the desk): decodes OGG (stb_vorbis), resamples to 48 kHz and sends
// 4 channels straight to the USB Audio Class 2 interface through usbfs (bypassing Android's mixer).
// The USB stream always stays active (silence when paused): the interface repeats the last
// chunk it got when the stream stops, which becomes a continuous whine.
#include <jni.h>
#include <errno.h>
#include <math.h>
#include <pthread.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>
#include <unistd.h>
#include <sys/ioctl.h>
#include <sys/resource.h>
#include <linux/usbdevice_fs.h>
#include <linux/usb/ch9.h>
#include <android/log.h>

#define STB_VORBIS_HEADER_ONLY
#include "stb_vorbis.c"

#define TAG "stagebook"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
// Diagnostics (summary every 5 s, input packets): 1 to turn back on.
#define DIAG 0
#define LOGD(...) do { if (DIAG) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__); } while (0)

#define OUT_RATE 48000
#define OUT_CH 4
// 32 URBs x 8 packets = 32 ms in the USB queue. With 8 (8 ms), a busy tablet (another app playing on the
// speaker) delayed the refill by 10-16 ms and the interface ran out of audio: a stutter (19/10/2026).
#define NURBS 32
#define PKTS_PER_URB 8
#define TAIL_URBS 500                 // ~500 ms of silence before releasing the endpoint
#define RING_FRAMES OUT_RATE          // 1 s of slack between decoder and USB (it was 250 ms: with the QR camera,
                                      // import and screens at once, the decoder fell behind and stuttered; 19/10/2026)
#define FADE_FRAMES 240               // 5 ms fade on play/pause

// ---- shared state ----
// g_vmx: the file (stb_vorbis) and the resampler — opening, seeking and decoding take time (disk).
// g_mx: the ring and the counters, which the USB thread reads every 1 ms; held only for short copies.
// Order: g_vmx before g_mx. The USB thread only takes g_mx.
static pthread_mutex_t g_vmx = PTHREAD_MUTEX_INITIALIZER;
static pthread_mutex_t g_mx = PTHREAD_MUTEX_INITIALIZER;
static float g_ring[RING_FRAMES * OUT_CH];
static int g_ring_r = 0, g_ring_w = 0, g_ring_n = 0;

static stb_vorbis *g_vorb = NULL;
static int g_src_rate = 0, g_src_ch = 0;
static long g_src_len = 0;            // source length in frames
static int g_eof = 0;

static volatile int g_playing = 0;
static volatile float g_gain_backing = 1.0f, g_gain_click = 1.0f;
static volatile float g_norm_backing = 1.0f, g_norm_click = 1.0f;   // normalization gain of the current song

// Backing equalizer (channels 1/2): 5 biquads in series (RBJ), guarded by g_mx.
#define EQ_BANDS 5
static const double EQ_FREQ[EQ_BANDS] = {80, 250, 1000, 3500, 10000};
typedef struct { float b0, b1, b2, a1, a2; } eq_coef;
static eq_coef g_eq[EQ_BANDS];
static float g_eq_z[EQ_BANDS][2][2];      // state [band][channel][z1,z2]
static int g_eq_on = 0;
// Backing limiter with the EQ on (19/10/2026): a boost no longer lowers the whole volume; only the
// peaks that would go over LIM_CEIL are held, instantly (no clipping), and the gain recovers in ~150 ms.
// Before, the protection lowered the EQ input by the largest boost (+6 dB = backing 6 dB
// quieter), and the user found it too much. With the EQ off the sound is the validated one, bit for bit.
#define LIM_CEIL 0.891f                    // -1 dBFS
#define LIM_REL (1.0f / (0.15f * OUT_RATE))
static float g_lim = 1.0f;

static void eq_design(int i, double gain_db) {
    double A = pow(10.0, gain_db / 40.0), w = 2 * M_PI * EQ_FREQ[i] / OUT_RATE;
    double cs = cos(w), sn = sin(w), b0, b1, b2, a0, a1, a2;
    if (i == 0 || i == EQ_BANDS - 1) {      // shelves (S = 1)
        double al = sn / 2 * sqrt(2.0), sq = 2 * sqrt(A) * al;
        if (i == 0) {
            b0 = A * ((A + 1) - (A - 1) * cs + sq); b1 = 2 * A * ((A - 1) - (A + 1) * cs);
            b2 = A * ((A + 1) - (A - 1) * cs - sq); a0 = (A + 1) + (A - 1) * cs + sq;
            a1 = -2 * ((A - 1) + (A + 1) * cs); a2 = (A + 1) + (A - 1) * cs - sq;
        } else {
            b0 = A * ((A + 1) + (A - 1) * cs + sq); b1 = -2 * A * ((A - 1) + (A + 1) * cs);
            b2 = A * ((A + 1) + (A - 1) * cs - sq); a0 = (A + 1) - (A - 1) * cs + sq;
            a1 = 2 * ((A - 1) - (A + 1) * cs); a2 = (A + 1) - (A - 1) * cs - sq;
        }
    } else {                                 // bell, Q = 1
        double al = sn / 2.0;
        b0 = 1 + al * A; b1 = -2 * cs; b2 = 1 - al * A; a0 = 1 + al / A; a1 = -2 * cs; a2 = 1 - al / A;
    }
    g_eq[i] = (eq_coef){b0 / a0, b1 / a0, b2 / a0, a1 / a0, a2 / a0};
}

static inline float eq_run(int ch, float x) {
    for (int i = 0; i < EQ_BANDS; i++) {
        eq_coef *q = &g_eq[i]; float *z = g_eq_z[i][ch];
        float y = q->b0 * x + z[0];
        z[0] = q->b1 * x - q->a1 * y + z[1];
        z[1] = q->b2 * x - q->a2 * y;
        x = y;
    }
    return x;
}

// backing pair (L/R) with EQ and gains applied: one gain for both sides, so the image doesn't shift
static inline void lim_run(float *s) {
    float p = fabsf(s[0]);
    if (fabsf(s[1]) > p) p = fabsf(s[1]);
    if (p * g_lim > LIM_CEIL) g_lim = LIM_CEIL / p;
    s[0] *= g_lim; s[1] *= g_lim;
    g_lim += (1.0f - g_lim) * LIM_REL;
}

// Live meters (20/10/2026), only with the screen's "Live monitor" switch. Off, the USB loop and the
// decoder do the same as before: they read g_meter_on once per URB / per block and skip the metering.
// They only read the signal: they never change what goes to the desk.
static volatile int g_meter_on = 0;
static float g_pk[OUT_CH];                // peak of each output channel (gains applied, before clipping), with g_mx
// Backing spectrum: FFT in the decoder (outside the USB loop), in mono, before the EQ and gains.
// SP_BINS logarithmic bands from SP_LO to SP_HI Hz (the screen uses the same edges). One SP_N-point FFT
// every SP_HOP frames. The decoder runs up to 1 s ahead of the output: each block keeps the frame (since the
// seek) where it ends, and the read only gathers the blocks the USB has already played.
#define SP_N 4096                         // 85 ms, 11.7 Hz per bin: separates the lows
#define SP_HOP 1024                       // ~47 FFTs per second of music
#define SP_BINS 48
#define SP_LO 30.0
#define SP_HI 16000.0
#define MB_BLOCKS 64                      // ~1.4 s of ~21 ms blocks: covers the 1 s ring
static struct { long end; float pk[SP_BINS]; } g_mb[MB_BLOCKS];   // with g_mx
static int g_mb_w = 0;
static long g_mb_read = 0;                // last frame already handed to the screen
// used only by the decoder (with g_vmx); tables built in sp_init
static float g_sp_in[SP_N];               // last SP_N samples (circular)
static int g_sp_pos = 0, g_sp_new = 0;    // where the next goes; how many arrived since the last FFT
static float g_sp_win[SP_N], g_sp_re[SP_N], g_sp_im[SP_N], g_sp_cos[SP_N / 2], g_sp_sin[SP_N / 2];
static int g_sp_k0[SP_BINS], g_sp_k1[SP_BINS];   // FFT bins of each band (inclusive)

static void sp_init(void) {
    for (int i = 0; i < SP_N; i++) g_sp_win[i] = (float)(0.5 - 0.5 * cos(2 * M_PI * i / SP_N));   // Hann
    for (int i = 0; i < SP_N / 2; i++) { g_sp_cos[i] = (float)cos(2 * M_PI * i / SP_N); g_sp_sin[i] = (float)-sin(2 * M_PI * i / SP_N); }
    for (int b = 0; b < SP_BINS; b++) {
        double lo = SP_LO * pow(SP_HI / SP_LO, (double)b / SP_BINS), hi = SP_LO * pow(SP_HI / SP_LO, (double)(b + 1) / SP_BINS);
        int k0 = (int)ceil(lo * SP_N / OUT_RATE), k1 = (int)floor(hi * SP_N / OUT_RATE);
        if (k1 < k0) k0 = k1 = (int)lround(sqrt(lo * hi) * SP_N / OUT_RATE);   // band narrower than one bin
        g_sp_k0[b] = k0; g_sp_k1[b] = k1;
    }
}

// FFT of the last SP_N samples (Hann window) and the peak of each band; 1 = 0 dBFS sine
static void sp_run(float *out) {
    for (int i = 0; i < SP_N; i++) { g_sp_re[i] = g_sp_in[(g_sp_pos + i) % SP_N] * g_sp_win[i]; g_sp_im[i] = 0; }
    for (int i = 1, j = 0; i < SP_N; i++) {   // bit-reversed order
        int bit = SP_N >> 1;
        for (; j & bit; bit >>= 1) j ^= bit;
        j ^= bit;
        if (i < j) { float t = g_sp_re[i]; g_sp_re[i] = g_sp_re[j]; g_sp_re[j] = t; }
    }
    for (int len = 2; len <= SP_N; len <<= 1) {
        int step = SP_N / len;
        for (int i = 0; i < SP_N; i += len)
            for (int k = 0; k < len / 2; k++) {
                float wr = g_sp_cos[k * step], wi = g_sp_sin[k * step];
                float *ar = &g_sp_re[i + k], *ai = &g_sp_im[i + k], *br = &g_sp_re[i + k + len / 2], *bi = &g_sp_im[i + k + len / 2];
                float tr = *br * wr - *bi * wi, ti = *br * wi + *bi * wr;
                *br = *ar - tr; *bi = *ai - ti; *ar += tr; *ai += ti;
            }
    }
    for (int b = 0; b < SP_BINS; b++) {
        float m = 0;
        for (int k = g_sp_k0[b]; k <= g_sp_k1[b]; k++) {
            float v = g_sp_re[k] * g_sp_re[k] + g_sp_im[k] * g_sp_im[k];
            if (v > m) m = v;
        }
        out[b] = sqrtf(m) * (4.0f / SP_N);    // Hann: a sine of amplitude A gives A·N/4 on the bin
    }
}

static void mb_reset_locked(void) /* with g_mx */ {
    for (int i = 0; i < MB_BLOCKS; i++) g_mb[i].end = -1;
    g_mb_read = 0;
}

static long g_base_ms = 0;           // position (ms) of the last seek
static long g_consumed = 0;           // output frames played since the seek
static float g_fade = 0.0f;           // 0..1, ramp to avoid clicks

// resampler (Catmull-Rom): history of 4 source frames
static float g_hist[4][OUT_CH];
static double g_frac = 0.0;
static int g_hist_fill = 0;

static volatile int g_usb_stop = 0, g_usb_running = 0, g_dec_stop = 0;
// stutter diagnostics: reset every log window
static long st_underrun = 0, st_pkt_err = 0, st_urb_err = 0;
static int st_ring_min = RING_FRAMES;
static double st_gap_max = 0;
// Implicit feedback: the interface's input arrives at its clock's pace. We compare the
// total frames it produced with the total we sent; if the output gets ahead
// (or behind) by more than FB_HI frames, packets carry 5 (or 7) frames instead
// of 6 until the gap is back to FB_LO. No music frame is dropped.
static int g_in_ep = -1, g_in_maxpkt = 0, g_in_fb = 0;
static long g_tot_in = 0, g_tot_out = 0, g_fb_off = 0;
static int g_fb_locked = 0, g_fb_mode = 0;
#define FB_HI 64
#define FB_LO 16
static long st_fb_adj = 0, st_in_frames = 0, st_out_frames = 0, st_fb_d = 0;
static pthread_t g_usb_thr, g_dec_thr;
static int g_fd = -1, g_ep = 0, g_maxpkt = 0, g_subslot = 4, g_dev_ch = 4, g_intf = 1, g_alt = 1;

// Submits the URB; if the endpoint is gone (someone changed the interface's alternate setting),
// re-enables the streaming alt and tries again.
static int submit(struct usbdevfs_urb *u) {
    if (ioctl(g_fd, USBDEVFS_SUBMITURB, u) == 0) return 0;
    if (errno != ENOENT && errno != EINVAL) return -1;
    struct usbdevfs_setinterface si = { .interface = g_intf, .altsetting = g_alt };
    if (ioctl(g_fd, USBDEVFS_SETINTERFACE, &si) < 0) return -1;
    LOGI("alt setting restored (if%d alt%d)", g_intf, g_alt);
    return ioctl(g_fd, USBDEVFS_SUBMITURB, u);
}
static char g_status[256] = "stopped";

static double now_s(void) {
    struct timespec ts;
    clock_gettime(CLOCK_MONOTONIC, &ts);
    return ts.tv_sec + ts.tv_nsec / 1e9;
}

// ---- ring (guarded by g_mx) ----
static void ring_clear_locked(void) { g_ring_r = g_ring_w = g_ring_n = 0; }

static int ring_space_locked(void) { return RING_FRAMES - g_ring_n; }

static void ring_push_locked(const float *fr) {
    memcpy(&g_ring[g_ring_w * OUT_CH], fr, sizeof(float) * OUT_CH);
    g_ring_w = (g_ring_w + 1) % RING_FRAMES;
    g_ring_n++;
}

static int ring_pop_locked(float *fr) {
    if (g_ring_n == 0) return 0;
    memcpy(fr, &g_ring[g_ring_r * OUT_CH], sizeof(float) * OUT_CH);
    g_ring_r = (g_ring_r + 1) % RING_FRAMES;
    g_ring_n--;
    return 1;
}

// maps the source frame to 4 channels: 4ch = identity; 2ch = backing; 1ch = backing
static void map_src(const float *in, float *out) {
    if (g_src_ch >= 4) { out[0] = in[0]; out[1] = in[1]; out[2] = in[2]; out[3] = in[3]; }
    else if (g_src_ch == 2) { out[0] = in[0]; out[1] = in[1]; out[2] = out[3] = 0; }
    else { out[0] = out[1] = in[0]; out[2] = out[3] = 0; }
}

static void resampler_reset_locked(void)   /* with g_vmx */ {
    g_frac = 0.0; g_hist_fill = 0; memset(g_hist, 0, sizeof g_hist);
    memset(g_sp_in, 0, sizeof g_sp_in); g_sp_pos = 0; g_sp_new = 0;
}

// ---- decoder thread ----
// decodes and resamples with g_vmx (without blocking the USB) and takes g_mx only to copy into the ring
static float g_dec_out[4096 * OUT_CH];   // used only by the decoder thread

static void *dec_main(void *arg) {
    float buf[1024 * 8];
    // Android's audio priority (-16): ahead of the screen, the camera and the import, below the USB (-19)
    if (setpriority(PRIO_PROCESS, gettid(), -16) < 0) LOGI("decoder: priority denied (%s)", strerror(errno));
    while (!g_dec_stop) {
        pthread_mutex_lock(&g_vmx);
        pthread_mutex_lock(&g_mx);
        int active = g_vorb && g_playing && !g_eof;
        int work = active && ring_space_locked() > 2048;
        pthread_mutex_unlock(&g_mx);
        // playing, the ring (250 ms) only drains 2048 frames in ~43 ms: 5 ms of slack is plenty;
        // stopped, 20 ms is enough (play only waits for the first block) and the CPU wakes up far less
        if (!work) { pthread_mutex_unlock(&g_vmx); usleep(active ? 5000 : 20000); continue; }

        int want = 512;
        int got = stb_vorbis_get_samples_float_interleaved(g_vorb, g_src_ch, buf, want * g_src_ch);
        if (got <= 0) {
            pthread_mutex_lock(&g_mx); g_eof = 1; pthread_mutex_unlock(&g_mx);
            pthread_mutex_unlock(&g_vmx);
            continue;
        }

        int n = 0, cap = (int)(sizeof g_dec_out / sizeof(float) / OUT_CH);
        double step = (double)g_src_rate / OUT_RATE;
        for (int i = 0; i < got; i++) {
            // pushes the new frame into the history
            memmove(g_hist[0], g_hist[1], sizeof(float) * OUT_CH * 3);
            map_src(&buf[i * g_src_ch], g_hist[3]);
            if (g_hist_fill < 4) { g_hist_fill++; if (g_hist_fill < 4) continue; }
            if (g_src_rate == OUT_RATE) {
                if (n < cap) memcpy(&g_dec_out[n++ * OUT_CH], g_hist[2], sizeof(float) * OUT_CH);
                continue;
            }
            // produces output while the fractional position is between hist[1] and hist[2]
            while (g_frac < 1.0) {
                float t = (float)g_frac, *out = &g_dec_out[n * OUT_CH];
                if (n < cap) {
                    for (int c = 0; c < OUT_CH; c++) {
                        float p0 = g_hist[0][c], p1 = g_hist[1][c], p2 = g_hist[2][c], p3 = g_hist[3][c];
                        out[c] = p1 + 0.5f * t * (p2 - p0 + t * (2 * p0 - 5 * p1 + 4 * p2 - p3 + t * (3 * (p1 - p2) + p3 - p0)));
                    }
                    n++;
                }
                g_frac += step;
            }
            g_frac -= 1.0;
        }
        int fft = 0;
        float spk[SP_BINS];
        if (g_meter_on) {
            for (int i = 0; i < n; i++) {
                g_sp_in[g_sp_pos] = 0.5f * (g_dec_out[i * OUT_CH] + g_dec_out[i * OUT_CH + 1]);
                g_sp_pos = (g_sp_pos + 1) % SP_N;
            }
            g_sp_new += n;
            if (g_sp_new >= SP_HOP) { sp_run(spk); g_sp_new = 0; fft = 1; }
        }
        pthread_mutex_lock(&g_mx);
        for (int i = 0; i < n && ring_space_locked() > 0; i++) ring_push_locked(&g_dec_out[i * OUT_CH]);
        if (fft) {
            g_mb[g_mb_w].end = g_consumed + g_ring_n;   // frame (since the seek) where this block finishes playing
            memcpy(g_mb[g_mb_w].pk, spk, sizeof spk);
            g_mb_w = (g_mb_w + 1) % MB_BLOCKS;
        }
        pthread_mutex_unlock(&g_mx);
        pthread_mutex_unlock(&g_vmx);
    }
    return NULL;
}

// ---- sample to the interface's format ----
static void put_sample(uint8_t *p, float s) {
    if (s > 1) s = 1;
    if (s < -1) s = -1;
    if (g_subslot == 2) { int16_t v = (int16_t)(s * 32767); memcpy(p, &v, 2); }
    else if (g_subslot == 3) { int32_t v = (int32_t)(s * 8388607); p[0] = v; p[1] = v >> 8; p[2] = v >> 16; }
    else { int32_t v = (int32_t)(s * 8388607) << 8; memcpy(p, &v, 4); }
}

// ---- thread USB ----
static void *usb_main(void *arg) {
    int speed = ioctl(g_fd, USBDEVFS_GET_SPEED);
    int pkts_per_sec = (speed >= USB_SPEED_HIGH) ? 8000 : 1000;
    int frame_bytes = g_dev_ch * g_subslot;
    size_t urb_sz = sizeof(struct usbdevfs_urb) + PKTS_PER_URB * sizeof(struct usbdevfs_iso_packet_desc);
    struct usbdevfs_urb *urbs[NURBS];
    double acc = 0;
    int tail = 0, tail_urbs = 0, errors = 0;
    long reaped = 0;
    int inflight = 0, n_in = 0;          // URBs held by the kernel; memory is freed only at zero

    // audio priority (the same as AudioTrack's): without it, with the CPU busy or hot, the
    // thread may miss its turn and the 8 ms URB queue run dry; if the system refuses, carry on normally
    if (setpriority(PRIO_PROCESS, gettid(), -19) < 0) LOGI("usb: priority denied (%s)", strerror(errno));

    for (int u = 0; u < NURBS; u++) {
        urbs[u] = calloc(1, urb_sz);
        urbs[u]->buffer = calloc(1, g_maxpkt * PKTS_PER_URB);
    }
    LOGI("usb: speed=%d pkts/s=%d ch=%d subslot=%d maxpkt=%d", speed, pkts_per_sec, g_dev_ch, g_subslot, g_maxpkt);

    #define FILL(urb) do { \
        uint8_t *b = (uint8_t *)(urb)->buffer; int off = 0; \
        memset(urb, 0, sizeof(struct usbdevfs_urb)); \
        (urb)->buffer = b; (urb)->type = USBDEVFS_URB_TYPE_ISO; (urb)->endpoint = g_ep; \
        (urb)->flags = USBDEVFS_URB_ISO_ASAP; (urb)->number_of_packets = PKTS_PER_URB; \
        pthread_mutex_lock(&g_mx); \
        int mon = g_meter_on; \
        for (int k = 0; k < PKTS_PER_URB; k++) { \
            acc += (double)OUT_RATE / pkts_per_sec; int nf = (int)acc; acc -= nf; \
            if (g_in_ep >= 0 && g_fb_locked) { \
                long d = (g_tot_out - g_tot_in) - g_fb_off; st_fb_d = d; \
                if (g_fb_mode == 0 && d > FB_HI) g_fb_mode = -1; \
                else if (g_fb_mode == 0 && d < -FB_HI) g_fb_mode = 1; \
                else if (g_fb_mode == -1 && d < FB_LO) g_fb_mode = 0; \
                else if (g_fb_mode == 1 && d > -FB_LO) g_fb_mode = 0; \
                if (g_fb_mode) { nf += g_fb_mode; st_fb_adj++; } \
            } \
            g_tot_out += nf; st_out_frames += nf; \
            if (nf * frame_bytes > g_maxpkt) nf = g_maxpkt / frame_bytes; \
            for (int f = 0; f < nf; f++) { \
                float fr[OUT_CH] = {0, 0, 0, 0}; \
                int want_play = g_playing && !tail; \
                if (want_play && g_fade < 1.0f) g_fade += 1.0f / FADE_FRAMES; \
                if (!want_play && g_fade > 0.0f) g_fade -= 1.0f / FADE_FRAMES; \
                if (g_fade < 0) g_fade = 0; if (g_fade > 1) g_fade = 1; \
                if (g_fade > 0) { if (ring_pop_locked(fr)) g_consumed++; else if (want_play && !g_eof) st_underrun++; } \
                if (want_play && g_ring_n < st_ring_min) st_ring_min = g_ring_n; \
                float gb = g_gain_backing * g_norm_backing * g_fade, gc = g_gain_click * g_norm_click * g_fade; \
                float sb[2] = {fr[0] * gb, fr[1] * gb}; \
                if (g_eq_on) { sb[0] = eq_run(0, fr[0]) * gb; sb[1] = eq_run(1, fr[1]) * gb; lim_run(sb); } \
                for (int c = 0; c < g_dev_ch; c++) { \
                    float s = c < 2 ? sb[c] : (c < 4 ? fr[c] * gc : 0); \
                    if (mon && c < OUT_CH && fabsf(s) > g_pk[c]) g_pk[c] = fabsf(s); \
                    put_sample(b + off + c * g_subslot, s); \
                } \
                off += frame_bytes; \
            } \
            (urb)->iso_frame_desc[k].length = nf * frame_bytes; \
        } \
        pthread_mutex_unlock(&g_mx); \
        (urb)->buffer_length = off; \
    } while (0)

    struct usbdevfs_urb *in_urbs[NURBS];
    if (g_in_ep >= 0) {
        for (int u = 0; u < NURBS; u++) {
            in_urbs[u] = calloc(1, urb_sz);
            in_urbs[u]->buffer = calloc(1, g_in_maxpkt * PKTS_PER_URB);
            struct usbdevfs_urb *iu = in_urbs[u];
            iu->type = USBDEVFS_URB_TYPE_ISO; iu->endpoint = g_in_ep; iu->flags = USBDEVFS_URB_ISO_ASAP;
            iu->number_of_packets = PKTS_PER_URB; iu->buffer_length = g_in_maxpkt * PKTS_PER_URB;
            for (int k = 0; k < PKTS_PER_URB; k++) iu->iso_frame_desc[k].length = g_in_maxpkt;
            n_in = u + 1;
            if (ioctl(g_fd, USBDEVFS_SUBMITURB, iu) < 0) {
                LOGI("feedback off: the input refused a URB (%s)", strerror(errno));
                g_in_ep = -1; break;
            }
            inflight++;
        }
    }
    for (int u = 0; u < NURBS; u++) {
        FILL(urbs[u]);
        if (submit(urbs[u]) < 0) {
            snprintf(g_status, sizeof g_status, "USB error on start: %s", strerror(errno));
            goto out;
        }
        inflight++;
    }
    snprintf(g_status, sizeof g_status, "USB active");

    while (tail_urbs < TAIL_URBS + NURBS - 1) {
        if (!tail && g_usb_stop) tail = 1;
        struct usbdevfs_urb *done = NULL;
        if (ioctl(g_fd, USBDEVFS_REAPURB, &done) < 0) {
            if (errno == EINTR) continue;
            snprintf(g_status, sizeof g_status, "USB dropped: %s", strerror(errno));
            goto out;
        }
        inflight--;
        if (done->endpoint & 0x80) {
            if (DIAG) {   // diagnostics: the first input URBs
                static int dbg = 0;
                if (dbg < 6) {
                    dbg++;
                    char b[160]; int o = 0;
                    for (int k = 0; k < done->number_of_packets && o < 140; k++)
                        o += snprintf(b + o, sizeof b - o, "%u/%d ", done->iso_frame_desc[k].actual_length, done->iso_frame_desc[k].status);
                    LOGD("entrada: status=%d actual=%d err=%d pacotes(len/status)=%s", done->status, done->actual_length, done->error_count, b);
                }
            }
            for (int k = 0; k < done->number_of_packets; k++) {
                int n = done->iso_frame_desc[k].status ? -1 : (int)done->iso_frame_desc[k].actual_length / g_in_fb;
                if (n >= 0) { g_tot_in += n; st_in_frames += n; }
            }
            if (!g_fb_locked && g_tot_in > 0) { g_fb_off = g_tot_out - g_tot_in; g_fb_locked = 1; }
            if (tail || g_in_ep < 0) continue;
            int npk = done->number_of_packets;
            done->status = 0; done->actual_length = 0; done->start_frame = 0; done->error_count = 0;
            for (int k = 0; k < npk; k++) { done->iso_frame_desc[k].length = g_in_maxpkt; done->iso_frame_desc[k].actual_length = 0; done->iso_frame_desc[k].status = 0; }
            if (ioctl(g_fd, USBDEVFS_SUBMITURB, done) < 0) { LOGI("feedback dropped: %s", strerror(errno)); g_in_ep = -1; }
            else inflight++;
            continue;
        }
        reaped++;
        if (done->status != 0) { errors++; st_urb_err++; }
        for (int k = 0; k < done->number_of_packets; k++) if (done->iso_frame_desc[k].status) st_pkt_err++;
        if (DIAG) {   // stats every 5 s: with DIAG 0 it doesn't even read the clock
            static double last = 0, win = 0;
            double t = now_s();
            if (last > 0 && t - last > st_gap_max) st_gap_max = t - last;
            last = t;
            if (win == 0) win = t;
            if (t - win >= 5.0) {
                pthread_mutex_lock(&g_mx);
                int ring = g_ring_n, rmin = st_ring_min;
                pthread_mutex_unlock(&g_mx);
                LOGD("stats: tocando=%d urbs=%ld urb_err=%ld pkt_err=%ld underrun=%ld ring=%dms ring_min=%dms gap_max=%.1fms "
                     "fb=%s ajustes=%ld dif=%ld mesa=%.2fHz saida=%.2fHz",
                     g_playing, reaped, st_urb_err, st_pkt_err, st_underrun, ring * 1000 / OUT_RATE,
                     rmin * 1000 / OUT_RATE, st_gap_max * 1000, g_in_ep >= 0 ? (g_fb_locked ? "travado" : "esperando") : "desligado",
                     st_fb_adj, st_fb_d, st_in_frames / (t - win), st_out_frames / (t - win));
                st_underrun = st_pkt_err = st_urb_err = 0; st_ring_min = RING_FRAMES; st_gap_max = 0; win = t;
                st_fb_adj = st_in_frames = st_out_frames = 0;
            }
        }
        if (tail) tail_urbs++;
        if (tail_urbs >= TAIL_URBS) continue;   // drains what is still queued
        FILL(done);
        if (submit(done) < 0) {
            snprintf(g_status, sizeof g_status, "USB dropped: %s", strerror(errno));
            goto out;
        }
        inflight++;
    }
    snprintf(g_status, sizeof g_status, "stopped (urbs=%ld errors=%d)", reaped, errors);

out:
    for (int u = 0; u < NURBS; u++) ioctl(g_fd, USBDEVFS_DISCARDURB, urbs[u]);
    for (int u = 0; u < n_in; u++) ioctl(g_fd, USBDEVFS_DISCARDURB, in_urbs[u]);
    // waits for the kernel to return each URB (discarded or with the desk unplugged, it comes back at once);
    // an error here means none is left with it
    while (inflight > 0) {
        struct usbdevfs_urb *d;
        if (ioctl(g_fd, USBDEVFS_REAPURB, &d) == 0) inflight--;
        else if (errno != EINTR) break;
    }
    if (inflight == 0) {
        for (int u = 0; u < NURBS; u++) { free(urbs[u]->buffer); free(urbs[u]); }
        for (int u = 0; u < n_in; u++) { free(in_urbs[u]->buffer); free(in_urbs[u]); }
    }
    LOGI("usb end: %s", g_status);
    g_usb_running = 0;
    return NULL;
}

// ---- JNI ----
#define FN(name) Java_com_juliano_stagebook_player_Engine_##name

JNIEXPORT jboolean JNICALL FN(start)(JNIEnv *e, jclass c, jint fd, jint ep, jint maxpkt, jint ch, jint subslot,
                                     jint intf, jint alt, jint in_ep, jint in_maxpkt, jint in_fb) {
    if (g_usb_running) return JNI_TRUE;
    g_in_ep = (in_ep >= 0 && in_fb > 0) ? in_ep : -1; g_in_maxpkt = in_maxpkt; g_in_fb = in_fb;
    g_tot_in = g_tot_out = g_fb_off = 0; g_fb_locked = 0; g_fb_mode = 0;
    g_fd = fd; g_ep = ep; g_maxpkt = maxpkt; g_dev_ch = ch; g_subslot = subslot;
    g_intf = intf; g_alt = alt;
    g_usb_stop = 0; g_dec_stop = 0; g_usb_running = 1;
    pthread_create(&g_dec_thr, NULL, dec_main, NULL);
    pthread_create(&g_usb_thr, NULL, usb_main, NULL);
    return JNI_TRUE;
}

JNIEXPORT void JNICALL FN(stop)(JNIEnv *e, jclass c) {
    if (!g_usb_running && g_fd < 0) return;
    g_playing = 0; g_meter_on = 0;
    g_usb_stop = 1;
    pthread_join(g_usb_thr, NULL);
    g_dec_stop = 1;
    pthread_join(g_dec_thr, NULL);
    g_fd = -1;
}

JNIEXPORT jlong JNICALL FN(load)(JNIEnv *e, jclass c, jstring jpath) {
    const char *path = (*e)->GetStringUTFChars(e, jpath, NULL);
    int err = 0;
    stb_vorbis *v = stb_vorbis_open_filename(path, &err, NULL);
    pthread_mutex_lock(&g_vmx);
    pthread_mutex_lock(&g_mx);
    g_playing = 0; g_fade = 0; g_eof = 0;
    ring_clear_locked();
    g_base_ms = 0; g_consumed = 0;
    mb_reset_locked();
    pthread_mutex_unlock(&g_mx);
    if (g_vorb) stb_vorbis_close(g_vorb);
    g_vorb = v;
    resampler_reset_locked();
    long dur_ms = -1;
    if (v) {
        stb_vorbis_info inf = stb_vorbis_get_info(v);
        g_src_rate = inf.sample_rate; g_src_ch = inf.channels;
        g_src_len = stb_vorbis_stream_length_in_samples(v);
        dur_ms = g_src_len * 1000L / g_src_rate;
        LOGI("load %s: %d Hz, %d ch, %ld ms", path, g_src_rate, g_src_ch, dur_ms);
    } else {
        LOGI("load failed %s: err=%d", path, err);
    }
    pthread_mutex_unlock(&g_vmx);
    (*e)->ReleaseStringUTFChars(e, jpath, path);
    return dur_ms;
}

JNIEXPORT void JNICALL FN(play)(JNIEnv *e, jclass c) { g_playing = 1; }
JNIEXPORT void JNICALL FN(pause)(JNIEnv *e, jclass c) { g_playing = 0; }

JNIEXPORT void JNICALL FN(seek)(JNIEnv *e, jclass c, jlong ms) {
    pthread_mutex_lock(&g_vmx);
    if (g_vorb) {
        if (ms < 0) ms = 0;
        unsigned int frame = (unsigned int)((double)ms * g_src_rate / 1000.0);
        if (g_src_len > 0 && frame >= (unsigned)g_src_len) frame = g_src_len - 1;
        stb_vorbis_seek_frame(g_vorb, frame);   // reads the disk: only with g_vmx, the USB keeps playing
        resampler_reset_locked();
        pthread_mutex_lock(&g_mx);
        ring_clear_locked();
        g_eof = 0; g_base_ms = ms; g_consumed = 0;
        mb_reset_locked();
        pthread_mutex_unlock(&g_mx);
    }
    pthread_mutex_unlock(&g_vmx);
}

JNIEXPORT jlong JNICALL FN(position)(JNIEnv *e, jclass c) {
    pthread_mutex_lock(&g_mx);
    long p = g_base_ms + g_consumed * 1000L / OUT_RATE;
    pthread_mutex_unlock(&g_mx);
    return p;
}

JNIEXPORT void JNICALL FN(setGains)(JNIEnv *e, jclass c, jfloat backing, jfloat click) {
    g_gain_backing = backing; g_gain_click = click;
}

JNIEXPORT void JNICALL FN(setEq)(JNIEnv *e, jclass c, jboolean on, jfloatArray jg) {
    float g[EQ_BANDS] = {0};
    (*e)->GetFloatArrayRegion(e, jg, 0, EQ_BANDS, g);
    pthread_mutex_lock(&g_mx);
    for (int i = 0; i < EQ_BANDS; i++) eq_design(i, g[i]);
    if (on && !g_eq_on) { memset(g_eq_z, 0, sizeof g_eq_z); g_lim = 1.0f; }
    g_eq_on = on;
    pthread_mutex_unlock(&g_mx);
}

JNIEXPORT void JNICALL FN(setNorm)(JNIEnv *e, jclass c, jfloat backing, jfloat click) {
    g_norm_backing = backing; g_norm_click = click;
}

JNIEXPORT jboolean JNICALL FN(isRunning)(JNIEnv *e, jclass c) { return g_usb_running ? JNI_TRUE : JNI_FALSE; }

JNIEXPORT jstring JNICALL FN(status)(JNIEnv *e, jclass c) { return (*e)->NewStringUTF(e, g_status); }

// live meters: turns metering on/off (off, the engine does the same as before)
JNIEXPORT void JNICALL FN(setMeter)(JNIEnv *e, jclass c, jboolean on) {
    if (!on) { g_meter_on = 0; return; }
    if (g_meter_on) return;
    static int ready = 0;
    if (!ready) { sp_init(); ready = 1; }
    pthread_mutex_lock(&g_vmx);           // the decoder uses the spectrum only with g_vmx
    memset(g_sp_in, 0, sizeof g_sp_in); g_sp_pos = 0; g_sp_new = 0;
    pthread_mutex_lock(&g_mx);
    memset(g_pk, 0, sizeof g_pk);
    mb_reset_locked();
    g_mb_read = g_consumed;
    g_meter_on = 1;
    pthread_mutex_unlock(&g_mx);
    pthread_mutex_unlock(&g_vmx);
}

// peaks since the previous read (linear, 1 = 0 dBFS; above 1 the desk gets it clipped):
// {channel 1, 2, 3, 4, backing spectrum in SP_BINS bands from SP_LO to SP_HI Hz}. The spectrum includes the
// backing's volume and normalization, without the EQ (the screen adds the EQ response). Resets after reading.
JNIEXPORT jfloatArray JNICALL FN(peaks)(JNIEnv *e, jclass c) {
    float out[OUT_CH + SP_BINS] = {0};
    pthread_mutex_lock(&g_mx);
    for (int i = 0; i < OUT_CH; i++) { out[i] = g_pk[i]; g_pk[i] = 0; }
    long cur = g_consumed;
    if (cur < g_mb_read) g_mb_read = 0;
    for (int i = 0; i < MB_BLOCKS; i++) {
        if (g_mb[i].end <= g_mb_read || g_mb[i].end > cur) continue;
        for (int b = 0; b < SP_BINS; b++) if (g_mb[i].pk[b] > out[OUT_CH + b]) out[OUT_CH + b] = g_mb[i].pk[b];
    }
    g_mb_read = cur;
    pthread_mutex_unlock(&g_mx);
    float gb = g_gain_backing * g_norm_backing;
    for (int b = 0; b < SP_BINS; b++) out[OUT_CH + b] *= gb;
    jfloatArray a = (*e)->NewFloatArray(e, OUT_CH + SP_BINS);
    if (a) (*e)->SetFloatArrayRegion(e, a, 0, OUT_CH + SP_BINS, out);
    return a;
}

// interface clock (feedback through the input): 1 locked, 0 waiting, -1 no input. Read-only.
JNIEXPORT jint JNICALL FN(feedback)(JNIEnv *e, jclass c) { return g_in_ep < 0 ? -1 : g_fb_locked; }

// ---- normalization ----
// Measures the integrated loudness (LUFS, ITU-R BS.1770: K filter + gating) of channels 1/2
// and the peak of channels 3/4. Returns {lufsBacking, peakBacking, peakClick} or null.
typedef struct { double b0, b1, b2, a1, a2, z1, z2; } biquad;

static double bq(biquad *f, double x) {
    double y = f->b0 * x + f->z1;
    f->z1 = f->b1 * x - f->a1 * y + f->z2;
    f->z2 = f->b2 * x - f->a2 * y;
    return y;
}

static void kweight_init(biquad *shelf, biquad *hp, double fs) {
    double f0 = 1681.974450955533, G = 3.999843853973347, Q = 0.7071752369554196;
    double K = tan(M_PI * f0 / fs), Vh = pow(10.0, G / 20.0), Vb = pow(Vh, 0.4996667741545416);
    double a0 = 1.0 + K / Q + K * K;
    *shelf = (biquad){(Vh + Vb * K / Q + K * K) / a0, 2.0 * (K * K - Vh) / a0, (Vh - Vb * K / Q + K * K) / a0,
                      2.0 * (K * K - 1.0) / a0, (1.0 - K / Q + K * K) / a0, 0, 0};
    f0 = 38.13547087602444; Q = 0.5003270373238773;
    K = tan(M_PI * f0 / fs);
    a0 = 1.0 + K / Q + K * K;
    *hp = (biquad){1.0, -2.0, 1.0, 2.0 * (K * K - 1.0) / a0, (1.0 - K / Q + K * K) / a0, 0, 0};
}

JNIEXPORT jfloatArray JNICALL FN(analyze)(JNIEnv *e, jclass c, jstring jpath) {
    const char *path = (*e)->GetStringUTFChars(e, jpath, NULL);
    int err = 0;
    stb_vorbis *v = stb_vorbis_open_filename(path, &err, NULL);
    (*e)->ReleaseStringUTFChars(e, jpath, path);
    if (!v) return NULL;
    stb_vorbis_info inf = stb_vorbis_get_info(v);
    int ch = inf.channels, fs = inf.sample_rate;
    if (ch > 8) { stb_vorbis_close(v); return NULL; }   // buf below holds up to 8 channels
    int nb = ch >= 2 ? 2 : 1;                 // backing channels
    biquad sh[2], hp[2];
    for (int i = 0; i < 2; i++) kweight_init(&sh[i], &hp[i], fs);

    int seg = fs / 10;                         // 100 ms segments
    size_t cap = 4096, nseg = 0;
    double *segs = malloc(cap * sizeof(double));
    double acc = 0, peakB = 0, peakC = 0;
    int inseg = 0;
    float buf[4096 * 8];
    int got;
    while ((got = stb_vorbis_get_samples_float_interleaved(v, ch, buf, 4096 * ch)) > 0) {
        for (int i = 0; i < got; i++) {
            float *fr = &buf[i * ch];
            for (int k = 0; k < nb; k++) {
                double x = fr[k];
                if (fabs(x) > peakB) peakB = fabs(x);
                double y = bq(&hp[k], bq(&sh[k], x));
                acc += y * y;
            }
            for (int k = 2; k < ch && k < 4; k++) if (fabs(fr[k]) > peakC) peakC = fabs(fr[k]);
            if (++inseg == seg) {
                if (nseg == cap) { cap *= 2; segs = realloc(segs, cap * sizeof(double)); }
                segs[nseg++] = acc / seg;
                acc = 0; inseg = 0;
            }
        }
    }
    stb_vorbis_close(v);

    // 400 ms blocks (4 segments) with 75% overlap
    double sumAbs = 0; long nAbs = 0;
    for (size_t i = 0; i + 3 < nseg; i++) {
        double z = (segs[i] + segs[i + 1] + segs[i + 2] + segs[i + 3]) / 4.0;
        if (z > 0 && -0.691 + 10 * log10(z) > -70.0) { sumAbs += z; nAbs++; }
    }
    float lufs = -70.0f;
    if (nAbs > 0) {
        double rel = -0.691 + 10 * log10(sumAbs / nAbs) - 10.0, sum = 0;
        long n = 0;
        for (size_t i = 0; i + 3 < nseg; i++) {
            double z = (segs[i] + segs[i + 1] + segs[i + 2] + segs[i + 3]) / 4.0;
            if (z <= 0) continue;
            double l = -0.691 + 10 * log10(z);
            if (l > -70.0 && l > rel) { sum += z; n++; }
        }
        if (n > 0) lufs = (float)(-0.691 + 10 * log10(sum / n));
    }
    free(segs);
    float out[3] = {lufs, (float)peakB, (float)peakC};
    jfloatArray arr = (*e)->NewFloatArray(e, 3);
    (*e)->SetFloatArrayRegion(e, arr, 0, 3, out);
    return arr;
}
