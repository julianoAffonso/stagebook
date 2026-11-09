// Stagebook's engine: decodes OGG (stb_vorbis), MP3/WAV (dr_mp3/dr_wav), resamples to 48 kHz and sends the track's
// channels straight to the USB Audio Class 2 interface through usbfs (bypassing Android's mixer). The USB stream always
// stays active (silence when paused): the interface repeats the last chunk it got when the stream stops, which becomes
// a continuous whine.
#include <jni.h>
#include <errno.h>
#include <math.h>
#include <poll.h>
#include <signal.h>
#include <pthread.h>
#include <stdarg.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <strings.h>
#include <time.h>
#include <unistd.h>
#include <sys/ioctl.h>
#include <sys/resource.h>
#ifdef __ANDROID__
#include <sys/system_properties.h>   // DIAG: debug.stagebook.rate
#endif
#include <linux/usbdevice_fs.h>
#include <linux/usb/ch9.h>
#include <android/log.h>

#define STB_VORBIS_HEADER_ONLY
#include "stb_vorbis.c"
// MP3 and WAV: single-file decoders, public domain
#define DR_MP3_IMPLEMENTATION
#include "dr_mp3.h"
#define DR_WAV_IMPLEMENTATION
#include "dr_wav.h"

#define TAG "stagebook"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
// Diagnostics (summary every 5 s, input packets): 1 to turn back on.
#define DIAG 0
#define LOGD(...) do { if (DIAG) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__); } while (0)

#define BASE_RATE 48000               // what the tracks' tools and the click are made for
static int g_queue_ms = 64;            // what this interface takes on this device, see QUEUE_MS
static int g_out_rate = BASE_RATE;     // the interface's rate: a USB Audio 1.0 card may only have 44.1 kHz
#define OUT_RATE g_out_rate
#define OUT_CH 4
// source channels kept in the ring (Stagebook): up to 8, each as it came; the default mix rebuilds the 4 from before
#define LANES_MAX 8
// 32 URBs x 8 packets = 32 ms in the USB queue. With 8 (8 ms), a busy TCL (another app playing on the speaker) delayed
// refilling by 10-16 ms and the interface ran out of audio: hiccup (19/10/2026).
#define NURBS 32                      // most URBs in the kernel's queue: 32 ms at high speed
#define QUEUE_MS_MIN 8                // below this a busy device would run dry
#define STALL_S 0.15                  // URBs in the kernel and none back for this long: the queue stalled
#define QUEUE_MS 64                   // and most audio: at full speed 32 URBs are 256 ms, and the S26 stopped returning them for good
// Packets per URB: 1 ms at high speed, as the kernel's own driver does. Each URB is a system call, and 16 (2 ms)
// halved that cost; but on one tablet's host controller 16-packet URBs reached the interface with hiss under the
// music and thumps, and 8 came out clean (07/11/2026). The queue stays 32 ms.
#define PKTS_PER_URB 8
// Input URBs are long: on some host controllers every URB boundary on the input loses a poll or two (the frames of
// those microframes are gone: 8 packets lost 40% of them, 16 lost 9%, 64 lost 2%), so the fewer boundaries the better;
// the input is only a clock, so its latency doesn't matter.
#define IN_PKTS_PER_URB 128
#define IN_NURBS 8
#define TAIL_MS 500                   // silence before releasing the endpoint
#define RING_FRAMES BASE_RATE          // 1 s of slack between decoder and USB (it was 250 ms: with the QR camera,
                                      // import and screens at the same time, the decoder fell behind and stuttered;
                                      // 19/10/2026)
#define FADE_FRAMES 240               // 5 ms fade on play/pause

// ---- shared state ----
// g_vmx: the file (stb_vorbis) and the resampler; opening, seeking and decoding take time (disk).
// g_mx: the ring and the counters, which the USB thread reads every 1 ms; held only for short copies.
// Order: g_vmx before g_mx. The USB thread only takes g_mx.
static pthread_mutex_t g_vmx = PTHREAD_MUTEX_INITIALIZER;
static pthread_mutex_t g_mx = PTHREAD_MUTEX_INITIALIZER;

// Names the calling thread: top, ps and traces show it instead of the app's name.
static void name_thread(const char *name) {
#ifdef __APPLE__
    pthread_setname_np(name);
#else
    pthread_setname_np(pthread_self(), name);
#endif
}

// Names the calling thread and gives it its priority.
static void thread_setup(const char *name, int nice) {
    name_thread(name);
    if (setpriority(PRIO_PROCESS, gettid(), nice) < 0) LOGI("%s: priority denied (%s)", name, strerror(errno));
}
static float g_ring[RING_FRAMES * LANES_MAX];
static int g_ring_r = 0, g_ring_w = 0, g_ring_n = 0;

// ---- audio source: OGG (stb_vorbis, exactly the validated version's calls), MP3 or WAV ----
typedef struct { stb_vorbis *v; drmp3 *mp3; drwav *wav; } src_t;

static int src_ok(const src_t *s) { return s->v || s->mp3 || s->wav; }

static int ends_with(const char *p, const char *ext) {
    size_t n = strlen(p), m = strlen(ext);
    return n >= m && strcasecmp(p + n - m, ext) == 0;
}

// opens by file type; OGG first, as always (rate, channels and duration in source frames)
static src_t src_open(const char *path, int *err, int *rate, int *ch, long *len) {
    src_t s = {0};
    if (ends_with(path, ".mp3")) {
        s.mp3 = malloc(sizeof(drmp3));
        if (s.mp3 && drmp3_init_file(s.mp3, path, NULL)) {
            *rate = (int)s.mp3->sampleRate; *ch = (int)s.mp3->channels;
            *len = (long)drmp3_get_pcm_frame_count(s.mp3);   // walks the file: outside g_vmx
            drmp3_seek_to_pcm_frame(s.mp3, 0);
        } else { free(s.mp3); s.mp3 = NULL; *err = -2; }
    } else if (ends_with(path, ".wav")) {
        s.wav = malloc(sizeof(drwav));
        if (s.wav && drwav_init_file(s.wav, path, NULL)) {
            *rate = (int)s.wav->sampleRate; *ch = (int)s.wav->channels; *len = (long)s.wav->totalPCMFrameCount;
        } else { free(s.wav); s.wav = NULL; *err = -3; }
    } else {
        s.v = stb_vorbis_open_filename(path, err, NULL);
        if (s.v) {
            stb_vorbis_info inf = stb_vorbis_get_info(s.v);
            *rate = inf.sample_rate; *ch = inf.channels;
            *len = stb_vorbis_stream_length_in_samples(s.v);
        }
    }
    // up to 8 channels (the decode buffers hold 8)
    if (src_ok(&s) && *ch > 8) {
        if (s.v) stb_vorbis_close(s.v);
        if (s.mp3) { drmp3_uninit(s.mp3); free(s.mp3); }
        if (s.wav) { drwav_uninit(s.wav); free(s.wav); }
        memset(&s, 0, sizeof s); *err = -4;
    }
    return s;
}

// frames read (0 = end): same contract as stb_vorbis_get_samples_float_interleaved
static int src_read(src_t *s, int ch, float *buf, int frames) {
    if (s->v) return stb_vorbis_get_samples_float_interleaved(s->v, ch, buf, frames * ch);
    if (s->mp3) return (int)drmp3_read_pcm_frames_f32(s->mp3, (drmp3_uint64)frames, buf);
    if (s->wav) return (int)drwav_read_pcm_frames_f32(s->wav, (drwav_uint64)frames, buf);
    return 0;
}

static void src_seek(src_t *s, unsigned int frame) {
    if (s->v) stb_vorbis_seek_frame(s->v, frame);
    if (s->mp3) drmp3_seek_to_pcm_frame(s->mp3, frame);
    if (s->wav) drwav_seek_to_pcm_frame(s->wav, frame);
}

static void src_close(src_t *s) {
    if (s->v) stb_vorbis_close(s->v);
    if (s->mp3) { drmp3_uninit(s->mp3); free(s->mp3); }
    if (s->wav) { drwav_uninit(s->wav); free(s->wav); }
    memset(s, 0, sizeof *s);
}

static src_t g_src = {0};
#define ENGINE_HAS_SRC 1   // the simulator reads the track through here (OGG, MP3 or WAV)
#define ENGINE_HAS_MIX 1   // mixing is per channel only (setMix): the simulator sends the equivalent setup
static int g_src_rate = 0, g_src_ch = 0;
static long g_src_len = 0;            // duration in source frames
static int g_eof = 0;

static volatile int g_playing = 0;

// Backing equalizer (channels 1/2): 5 biquads in series (RBJ), protected by g_mx.
#define EQ_BANDS 5
static const double EQ_FREQ[EQ_BANDS] = {80, 250, 1000, 3500, 10000};
typedef struct { float b0, b1, b2, a1, a2; } eq_coef;
static eq_coef g_eq[EQ_BANDS];
static float g_eq_z[EQ_BANDS][LANES_MAX][2];      // estado [banda][canal][z1,z2]
static int g_eq_on = 0;
// Backing limiter with the EQ on (19/10/2026): the boost no longer lowers the whole volume; only peaks that would go
// over LIM_CEIL are held, instantly (without clipping), and the gain comes back in ~150 ms. Before, the protection
// lowered the EQ input by the size of the largest boost (+6 dB = backing 6 dB lower), which lowered it too
// much. With the EQ off the sound is the validated one, bit for bit.
#define LIM_CEIL 0.891f                    // -1 dBFS
#define LIM_REL (1.0f / (0.15f * OUT_RATE))
static float g_lim = 1.0f;

static void eq_design(int i, double gain_db) {
    double A = pow(10.0, gain_db / 40.0), w = 2 * M_PI * EQ_FREQ[i] / OUT_RATE;
    double cs = cos(w), sn = sin(w), b0, b1, b2, a0, a1, a2;
    if (i == 0 || i == EQ_BANDS - 1) {      // prateleiras (S = 1)
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
    } else {                                 // sino, Q = 1
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


// Live meters (20/10/2026), only with the screen's "Monitor live" switch. Off, the USB loop and the decoder do the same
// as before: they read g_meter_on once per URB / per block and skip measuring. They only read the signal: they never
// change what goes to the desk.
static volatile int g_meter_on = 0;
static float g_pk[OUT_CH];                // peak of each channel at the output (gains applied, before clipping), with g_mx
static float g_lpk[LANES_MAX];            // peak of each file channel after fader, normalization and EQ (the mixer meter), with g_mx
// Backing spectrum: FFT in the decoder (outside the USB loop), in mono, before the EQ and gains. SP_BINS log bands from
// SP_LO to SP_HI Hz (the screen uses the same edges). One SP_N-point FFT every SP_HOP frames. The decoder runs up to 1
// s ahead of the output: each block keeps the frame (since the seek) where it ends, and reading only gathers the blocks
// USB has already played.
#define SP_N 4096                         // 85 ms, 11.7 Hz per bin: separates the lows
#define SP_HOP 1024                       // ~47 FFTs per second of music
#define SP_BINS 48
#define SP_LO 30.0
#define SP_HI 16000.0
#define MB_BLOCKS 64                      // ~1.4 s of ~21 ms blocks: covers the 1 s ring
static struct { long end; float pk[SP_BINS]; } g_mb[MB_BLOCKS];   // with g_mx
static int g_mb_w = 0;
static long g_mb_read = 0;                // last frame already delivered to the screen
// used only by the decoder (with g_vmx); tables ready in sp_init
static float g_sp_in[SP_N];               // last SP_N samples (circular)
static int g_sp_pos = 0, g_sp_new = 0;    // where the next one goes; how many arrived since the last FFT
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
static float g_hist[4][LANES_MAX];
static int g_lanes = 4;               // source channels in the ring (up to LANES_MAX), set on load
static double g_frac = 0.0;
static int g_hist_fill = 0;

static volatile int g_usb_stop = 0, g_usb_running = 0, g_dec_stop = 0;
static volatile int g_fast_stop = 0;   // stopping to reconnect: no silent tail (the stream is about to start again)
#define ENGINE_HAS_FASTSTOP 1
// stutter diagnostics: reset every log window
static long st_underrun = 0, st_pkt_err = 0, st_urb_err = 0;
static long st_out_short = 0, st_out_short_bytes = 0;   // DIAG: output packets the host sent short or not at all
static int st_ring_min = RING_FRAMES;
static double st_gap_max = 0;
// Implicit feedback: the interface's input arrives at its clock's pace. We compare the total frames it produced with
// the total we sent; if the output runs ahead (or behind) by more than FB_HI frames, packets carry 5 (or 7) frames
// instead of 6 until the difference is back to FB_LO. No music frame is dropped.
static int g_in_ep = -1, g_in_maxpkt = 0, g_in_fb = 0;
static long g_tot_in = 0, g_tot_out = 0, g_fb_off = 0;
static int g_fb_locked = 0, g_fb_mode = 0;
// The input is only a clock if it keeps time: on some devices input packets go missing, and following what's left turned
// into thousands of corrections a second, heard as noise. Far from the nominal rate, the output keeps its own pace.
#define FB_MAX_OFF 0.003
#define FB_BACK_S 5                   // seconds of a count that keeps time again before the input is followed again
#define FB_PROBE_S 60.0               // with the input marked off, how often it is read again to see if it's back
static int g_fb_bad = 0, g_fb_good = 0, g_fb_probing = 0;
static double g_fb_probe_t = 0;
#define FB_STALL_S 0.3                // a locked input that returns nothing for this long has stopped: it isn't a clock
#define FB_MAX_D 4800                 // or the count and what was sent drifted 100 ms apart: no honest clock gets there
static double g_in_last = 0;          // when the last input URB came back
static double g_fb_win = 0;
static long g_fb_win_in = 0;
#define FB_HI 64
#define FB_LO 16
// The clock is judged once, when the stream opens, before any sound (user, 08/11): the input is read for FB_PROBE_SUBS
// quarter-seconds with the output silent, and the stream goes to plan A (follow the input) or plan B (own pace) for as
// long as it stays open. A host whose timing is merely noisy (the reading of a one-second window moves by a few tenths
// of a percent while the clock is fine) keeps plan A with a tolerance that fits its noise; a clock that is really off
// goes to plan B before the first note. Once decided, plan A is left only for a real failure (FB_LEAVE_N windows more
// than FB_LEAVE_OFF away), never for noise in the middle of a song.
#define ENGINE_HAS_PROBE 1
#define FB_PROBE_SUB_S 0.25
#define FB_PROBE_SUBS 8
#define FB_PROBE_MAX_S 3.0            // the output is held at most this long waiting for the input
#define FB_LEAVE_OFF 0.01
#define FB_LEAVE_N 3
#define FB_TOL_MAX 0.01
static volatile int g_probing = 0;    // the clock is being judged: the output is silent
static int g_fb_decided = 0, g_fb_open_bad = 0, g_fb_off_n = 0, g_pr_n = 0;
static double g_fb_tol_hz = 0, g_pr_hz[FB_PROBE_SUBS], g_pr_time = 0, g_probe_start = 0;
static long g_pr_frames = 0;
// With an input clock that exists but can't be followed (g_fb_bad), the output is paced against the system's monotonic
// clock instead of the bus: on some hosts the microframe clock runs off nominal (~900 ppm slow seen on one tablet) and
// the interface, consuming at its own crystal, runs its small queue dry every few seconds. A PI loop on the frames sent
// ahead of real time holds the rate at the nominal 48 000/s. Only there: an adaptive output follows our pace anyway, and
// an input that keeps time still has the last word.
static long g_pace_sent0 = 0;       // frames sent when the loop's clock was anchored: its error counts from there
static double g_pace_t0 = 0, g_pace_err = 0, g_pace_off = 0, g_pace_ratio = 0, g_pace_int = 0, g_pace_last = 0;
static int g_pace_locked = 0;
#define PACE_EMA 0.002               // error smoothing at ~1000 output URBs/s: a time constant of ~0.5 s
#define PACE_TP 4.0                  // proportional: an error of e frames is removed over TP seconds
#define PACE_TI 8.0                  // integral: a steady error feeds the ratio over TI seconds
#define PACE_FAST_S 20.0             // after this long locked, the slow gains below
#define PACE_EMA_SLOW 0.0005
#define PACE_TP_SLOW 60.0
#define PACE_TI_SLOW 120.0
static double g_pace_lock_t = 0;
static int g_pace_slow = 0;
#define PACE_MAX 0.01                // never more than 1% off nominal: beyond that something else is wrong
static double g_pace_rate = 0;       // DIAG: the rate the pace loop aims at (debug.stagebook.rate), else g_out_rate
static void pace_reset(void) {       // start over from where the output is now (the loop's error was about another regime)
    g_pace_t0 = g_pace_err = g_pace_off = g_pace_ratio = g_pace_int = g_pace_last = g_pace_lock_t = 0;
    g_pace_locked = 0; g_pace_slow = 0;
}
#define PACE_RATE (g_pace_rate > 0 ? g_pace_rate : g_out_rate)
static long st_fb_adj = 0, st_in_frames = 0, st_out_frames = 0, st_fb_d = 0, st_in_lost = 0;
static pthread_t g_usb_thr, g_dec_thr;
static int g_fd = -1, g_ep = 0, g_maxpkt = 0, g_subslot = 4, g_dev_ch = 4, g_intf = 1, g_alt = 1;

// Submits the URB; if the endpoint vanished (someone changed the interface's alternate setting), reactivates the
// streaming alt and tries again.
static int submit(struct usbdevfs_urb *u) {
    if (ioctl(g_fd, USBDEVFS_SUBMITURB, u) == 0) return 0;
    if (errno != ENOENT && errno != EINVAL) return -1;
    struct usbdevfs_setinterface si = { .interface = g_intf, .altsetting = g_alt };
    if (ioctl(g_fd, USBDEVFS_SETINTERFACE, &si) < 0) return -1;
    LOGI("alt setting restored (if%d alt%d)", g_intf, g_alt);
    return ioctl(g_fd, USBDEVFS_SUBMITURB, u);
}
static char g_status[256] = "parado";
// the status is written by the USB thread and AAudio's error callback and read from Java: its own small lock, never
// the audio's (audio audit A4: a status read while USB dropped could come out mixed)
static pthread_mutex_t g_stmx = PTHREAD_MUTEX_INITIALIZER;
static void set_status(const char *fmt, ...) {
    char b[sizeof g_status];
    va_list ap; va_start(ap, fmt); vsnprintf(b, sizeof b, fmt, ap); va_end(ap);
    pthread_mutex_lock(&g_stmx); memcpy(g_status, b, sizeof b); pthread_mutex_unlock(&g_stmx);
}

static double now_s(void) {
    struct timespec ts;
    clock_gettime(CLOCK_MONOTONIC, &ts);
    return ts.tv_sec + ts.tv_nsec / 1e9;
}

// ---- per-channel mixing (Stagebook, 24/10/2026) ----
// Stagebook's only mix (user's decision: in a public app, assuming "1-2 backing, 3-4 click" is a risk). The app sends
// each backing track's setup (setMix); without it, each channel goes to the output with the same number, at gain 1:
// nothing is guessed. With the setup equivalent to the old one, the sound is identical to the one validated on the desk
// (simulator). Protected by g_mx.
static float g_ch_gain[LANES_MAX] = {1, 1, 1, 1, 1, 1, 1, 1};   // fader × on/off of each source channel
static float g_ch_norm[LANES_MAX] = {1, 1, 1, 1, 1, 1, 1, 1};   // normalization of each channel
static int g_ch_eq = 0;                     // bits: channels that go through the EQ (each separate)
static float g_route[LANES_MAX][LANES_MAX] = {   // [output][source channel]: how much of the channel goes to the output
    {1}, {0, 1}, {0, 0, 1}, {0, 0, 0, 1}, {0, 0, 0, 0, 1}, {0, 0, 0, 0, 0, 1}, {0, 0, 0, 0, 0, 0, 1}, {0, 0, 0, 0, 0, 0, 0, 1},
};

// source channels → outputs: gain and normalization per channel, EQ on the marked ones (with the limiter shared by all
// of them, so the image doesn't shift, like the backing pair) and summed through the routing matrix
// ---- metronome click into USB outputs (user, 02/11): the drummer hears it through the desk. Added after the song's
// mix, on the interface's clock, sample by sample like MetronomeClick; off (g_clk_spb 0) nothing changes. Under g_mx.
static float *g_clk_buf[2] = {0, 0};   // normal and accent click, 48 kHz
static int g_clk_len[2] = {0, 0};
static long g_clk_spb = 0;              // samples per beat; 0 = off
static long g_clk_pos = 0;              // samples since the click started
static int g_clk_out[2] = {-1, -1};     // outputs it goes to (-1 = none)
static long long g_clk_skip = 0;        // beats from the start left silent, bit n = beat n (count-in "1 . 2 . 1 2 3 4")

static inline void click_add_locked(float *out) {
    if (g_clk_spb <= 0) return;
    long beat = g_clk_pos / g_clk_spb, at = g_clk_pos % g_clk_spb;
    int k = beat % 4 == 0 || (g_clk_skip && beat == 2) ? 1 : 0;   // the first beat of each bar sounds higher, and so
                                        // does the count-in's second slow click: the two sound the same
    g_clk_pos++;
    if (beat < 64 && ((g_clk_skip >> beat) & 1)) return;
    if (at >= g_clk_len[k]) return;
    for (int q = 0; q < 2; q++)
        if (g_clk_out[q] >= 0 && g_clk_out[q] < LANES_MAX) out[g_clk_out[q]] += g_clk_buf[k][at];
}

static inline void mix_custom(const float *lane, float *out) {
    float s[LANES_MAX];
    float p = 0;
    for (int c = 0; c < g_lanes; c++) {
        float g = g_ch_gain[c] * g_ch_norm[c] * g_fade;
        if (g_eq_on && (g_ch_eq & (1 << c))) {
            s[c] = eq_run(c, lane[c]) * g;
            if (fabsf(s[c]) > p) p = fabsf(s[c]);
        } else {
            s[c] = lane[c] * g;
        }
    }
    if (g_eq_on && g_ch_eq) {
        if (p * g_lim > LIM_CEIL) g_lim = LIM_CEIL / p;
        for (int c = 0; c < g_lanes; c++) if (g_ch_eq & (1 << c)) s[c] *= g_lim;
        g_lim += (1.0f - g_lim) * LIM_REL;
    }
    if (g_meter_on) for (int c = 0; c < g_lanes; c++) if (fabsf(s[c]) > g_lpk[c]) g_lpk[c] = fabsf(s[c]);
    for (int o = 0; o < LANES_MAX; o++) {
        float a = 0;
        for (int c = 0; c < g_lanes; c++) a += g_route[o][c] * s[c];
        out[o] = a;
    }
}

// ---- ring (protected by g_mx) ----
static void ring_clear_locked(void) { g_ring_r = g_ring_w = g_ring_n = 0; }

static int ring_space_locked(void) { return RING_FRAMES - g_ring_n; }

static void ring_push_locked(const float *fr) {
    memcpy(&g_ring[g_ring_w * LANES_MAX], fr, sizeof(float) * LANES_MAX);
    g_ring_w = (g_ring_w + 1) % RING_FRAMES;
    g_ring_n++;
}

static int ring_pop_locked(float *fr) {
    if (g_ring_n == 0) return 0;
    memcpy(fr, &g_ring[g_ring_r * LANES_MAX], sizeof(float) * LANES_MAX);
    g_ring_r = (g_ring_r + 1) % RING_FRAMES;
    g_ring_n--;
    return 1;
}


static void resampler_reset_locked(void)   /* with g_vmx */ {
    g_frac = 0.0; g_hist_fill = 0; memset(g_hist, 0, sizeof g_hist);
    memset(g_sp_in, 0, sizeof g_sp_in); g_sp_pos = 0; g_sp_new = 0;
}

// ---- decoder thread ----
// decodes and resamples with g_vmx (without blocking USB) and only takes g_mx to copy into the ring
static float g_dec_out[4096 * LANES_MAX];   // used only by the decoder thread

static void *dec_main(void *arg) {
    float buf[1024 * 8];
    // Android audio priority (-16): ahead of the screen, camera and import, below USB (-19)
    thread_setup("sb-decoder", -16);
    while (!g_dec_stop) {
        pthread_mutex_lock(&g_vmx);
        pthread_mutex_lock(&g_mx);
        int active = src_ok(&g_src) && g_playing && !g_eof;
        int work = active && ring_space_locked() > 2048;
        pthread_mutex_unlock(&g_mx);
        // while playing, the ring (1 s) only drains 2048 frames in ~43 ms: 5 ms of slack is plenty; stopped, 20 ms
        // is enough (play only waits for the first block) and the CPU wakes far less
        if (!work) { pthread_mutex_unlock(&g_vmx); usleep(active ? 5000 : 20000); continue; }

        int want = 512;
        int got = src_read(&g_src, g_src_ch, buf, want);
        if (got <= 0) {
            pthread_mutex_lock(&g_mx); g_eof = 1; pthread_mutex_unlock(&g_mx);
            pthread_mutex_unlock(&g_vmx);
            continue;
        }

        int n = 0, cap = (int)(sizeof g_dec_out / sizeof(float) / LANES_MAX);
        double step = (double)g_src_rate / OUT_RATE;
        for (int i = 0; i < got; i++) {
            // pushes the new frame into the history
            memmove(g_hist[0], g_hist[1], sizeof(float) * LANES_MAX * 3);
            // the source channels as they came (mixing happens at the output)
            for (int c = 0; c < g_lanes; c++) g_hist[3][c] = buf[i * g_src_ch + c];
            if (g_hist_fill < 4) { g_hist_fill++; if (g_hist_fill < 4) continue; }
            if (g_src_rate == OUT_RATE) {
                if (n < cap) memcpy(&g_dec_out[n++ * LANES_MAX], g_hist[2], sizeof(float) * LANES_MAX);
                continue;
            }
            // produces outputs while the fractional position is between hist[1] and hist[2]
            while (g_frac < 1.0) {
                float t = (float)g_frac, *out = &g_dec_out[n * LANES_MAX];
                if (n < cap) {
                    for (int c = 0; c < g_lanes; c++) {
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
            // the spectrum is of the channels that go through the EQ (their average); with none marked, channels 1/2
            int mask = g_ch_eq, cnt = 0;
            for (int c = 0; c < g_lanes; c++) if (mask & (1 << c)) cnt++;
            for (int i = 0; i < n; i++) {
                float *d = &g_dec_out[i * LANES_MAX];
                if (cnt == 0) {
                    g_sp_in[g_sp_pos] = 0.5f * (d[0] + (g_lanes > 1 ? d[1] : d[0]));   // mono source: the same channel on both sides
                } else {
                    float a = 0;
                    for (int c = 0; c < g_lanes; c++) if (mask & (1 << c)) a += d[c];
                    g_sp_in[g_sp_pos] = a / cnt;
                }
                g_sp_pos = (g_sp_pos + 1) % SP_N;
            }
            g_sp_new += n;
            if (g_sp_new >= SP_HOP) { sp_run(spk); g_sp_new = 0; fft = 1; }
        }
        pthread_mutex_lock(&g_mx);
        for (int i = 0; i < n && ring_space_locked() > 0; i++) ring_push_locked(&g_dec_out[i * LANES_MAX]);
        if (fft) {
            g_mb[g_mb_w].end = g_consumed + g_ring_n;   // frame (since the seek) at which this block finishes playing
            memcpy(g_mb[g_mb_w].pk, spk, sizeof spk);
            g_mb_w = (g_mb_w + 1) % MB_BLOCKS;
        }
        pthread_mutex_unlock(&g_mx);
        pthread_mutex_unlock(&g_vmx);
    }
    return NULL;
}

// ---- sample to the interface format ----
static void put_sample(uint8_t *p, float s) {
    if (s > 1) s = 1;
    if (s < -1) s = -1;
    if (g_subslot == 2) { int16_t v = (int16_t)(s * 32767); memcpy(p, &v, 2); }
    else if (g_subslot == 3) { int32_t v = (int32_t)(s * 8388607); p[0] = v; p[1] = v >> 8; p[2] = v >> 16; }
    else { int32_t v = (int32_t)(s * 8388607) << 8; memcpy(p, &v, 4); }
}

// URBs the kernel didn't hand back because the interface vanished (REAPURB keeps answering ENODEV after an unplug):
// before, they were simply left behind, a leak on every drop. They're freed on the next start, after Java has closed
// the old connection: from then on the kernel no longer touches them (audio audit P2).
static struct usbdevfs_urb *g_parked[2 * NURBS];
static int g_nparked = 0;

static void park_urb(struct usbdevfs_urb *u) { if (g_nparked < 2 * NURBS) g_parked[g_nparked++] = u; }

static void free_parked(void) {
    for (int i = 0; i < g_nparked; i++) { free(g_parked[i]->buffer); free(g_parked[i]); }
    g_nparked = 0;
}

// REAPURB never returns from a queue the device stopped serving. It stays the way to wait, the cheapest one (polling
// took a tablet from 15% to 61% of a core): this watcher only looks at the clock and wakes it with a signal.
static volatile double g_last_reap = 0;
static volatile int g_stalled = 0, g_watching = 0;
static pthread_t g_watch_thr;
static void wake_usb(int sig) {}
static void *stall_watch(void *arg) {
    name_thread("sb-watch");
    while (g_watching) {
        struct timespec t = {0, 50000000}; nanosleep(&t, NULL);
        if (g_watching && now_s() - g_last_reap > STALL_S) { g_stalled = 1; pthread_kill(g_usb_thr, SIGUSR2); }
    }
    return NULL;
}

// Waits for what's left when closing, never for ever.
#define REAP_STALLED -2
static int reap_wait(struct usbdevfs_urb **out) {
    double from = now_s();
    for (;;) {
        if (ioctl(g_fd, USBDEVFS_REAPURBNDELAY, out) == 0) return 0;
        if (errno != EAGAIN) return -1;
        if (now_s() - from > STALL_S) return REAP_STALLED;
        struct pollfd p = { .fd = g_fd, .events = POLLOUT };
        poll(&p, 1, 20);
    }
}

// ---- thread USB ----
// An input URB came back during the probe. Readings of FB_PROBE_SUB_S; after FB_PROBE_SUBS the clock is judged. Returns
// 1 when it is off (plan B for this stream). The timing noise of a reading is the same in absolute terms whatever its
// length, so its effect on the rate shrinks with the window: the spread of the whole probe's mean is the spread of a
// reading times SUB/total, and of a one-second window, times SUB.
static int fb_probe(double t, int rate) {
    if (g_fb_win == 0) { g_fb_win = t; g_fb_win_in = 0; return 0; }
    double dt = t - g_fb_win;
    if (dt < FB_PROBE_SUB_S) return 0;
    g_pr_hz[g_pr_n++] = g_fb_win_in / dt; g_pr_frames += g_fb_win_in; g_pr_time += dt;
    g_fb_win = t; g_fb_win_in = 0;
    if (g_pr_n < FB_PROBE_SUBS) return 0;
    double mean = g_pr_frames / g_pr_time, m = 0, v = 0;
    for (int i = 0; i < g_pr_n; i++) m += g_pr_hz[i];
    m /= g_pr_n;
    for (int i = 0; i < g_pr_n; i++) v += (g_pr_hz[i] - m) * (g_pr_hz[i] - m);
    double sd = sqrt(v / (g_pr_n - 1));
    double sd_mean = sd * FB_PROBE_SUB_S / g_pr_time, sd_win = sd * FB_PROBE_SUB_S;
    double nominal = rate * FB_MAX_OFF;
    g_fb_decided = 1; g_probing = 0;
    if (fabs(mean - rate) > fmax(nominal, 3 * sd_mean)) {
        LOGI("usb: input clock off at open (%.0f Hz), keeping our own pace", mean);
        return 1;
    }
    g_fb_tol_hz = fmin(fmax(nominal, 4 * sd_win), rate * FB_TOL_MAX);
    LOGI("usb: input clock on at open (%.0f Hz, tolerance %.2f%%)", mean, 100 * g_fb_tol_hz / rate);
    return 0;
}

static void *usb_main(void *arg) {
    int speed = ioctl(g_fd, USBDEVFS_GET_SPEED);
    int pkts_per_sec = (speed >= USB_SPEED_HIGH) ? 8000 : 1000;
    int nurbs = g_queue_ms * pkts_per_sec / (1000 * PKTS_PER_URB);
    if (nurbs > NURBS) nurbs = NURBS;
    if (nurbs < 2) nurbs = 2;
    int tail_max = TAIL_MS * pkts_per_sec / (1000 * PKTS_PER_URB);
    g_queue_ms = nurbs * 1000 * PKTS_PER_URB / pkts_per_sec;   // what it really is: halving a cap would change nothing
    int frame_bytes = g_dev_ch * g_subslot;
    size_t urb_sz = sizeof(struct usbdevfs_urb) + PKTS_PER_URB * sizeof(struct usbdevfs_iso_packet_desc);
    size_t in_urb_sz = sizeof(struct usbdevfs_urb) + IN_PKTS_PER_URB * sizeof(struct usbdevfs_iso_packet_desc);
    struct usbdevfs_urb *urbs[NURBS];
    double acc = 0, lost_acc = 0;
    int quiet_was = 0;   // the last packet was written as silence without the mixer
    int tail = 0, tail_urbs = 0, errors = 0;
    long reaped = 0;
    int inflight = 0, n_in = 0, in_idle = 0;   // URBs held by the kernel (memory is only freed at zero); input URBs parked

    // audio priority (the same as AudioTrack): without it, with the CPU busy or hot, the thread can lose its turn and
    // the 8 ms URB queue can run dry; if the system refuses, it carries on
    thread_setup("sb-usb", -19);

    for (int u = 0; u < nurbs; u++) {
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
        int hold = g_probing;   /* judging the input clock: silent until it's decided */ \
        for (int k = 0; k < PKTS_PER_URB; k++) { \
            acc += (double)OUT_RATE / pkts_per_sec * (g_fb_bad ? 1.0 + g_pace_ratio : 1.0); int nf = (int)acc; acc -= nf; \
            if (g_in_ep >= 0 && g_fb_locked && !g_fb_bad) { \
                long d = (g_tot_out - g_tot_in) - g_fb_off; st_fb_d = d; \
                if (g_fb_mode == 0 && d > FB_HI) g_fb_mode = -1; \
                else if (g_fb_mode == 0 && d < -FB_HI) g_fb_mode = 1; \
                else if (g_fb_mode == -1 && d < FB_LO) g_fb_mode = 0; \
                else if (g_fb_mode == 1 && d > -FB_LO) g_fb_mode = 0; \
                if (g_fb_mode) { nf += g_fb_mode; st_fb_adj++; } \
            } \
            /* the packet's ceiling first: a frame cut here isn't counted as sent (audio audit A2) */ \
            if (nf * frame_bytes > g_maxpkt) nf = g_maxpkt / frame_bytes; \
            g_tot_out += nf; st_out_frames += nf; \
            /* Nothing playing, faded out and no click: the output is exactly zero, so the mixer, the EQ and the click, */ \
            /* which only produce those zeros, are skipped (the idle player cost ~19% of a core, audit 07/11). The EQ's */ \
            /* state and the limiter are reset once, as the silence would have let them decay. */ \
            int quiet = (!(g_playing && !tail) || hold) && g_fade <= 0.0f && (g_clk_spb <= 0 || hold); \
            if (quiet) { \
                if (!quiet_was) { memset(g_eq_z, 0, sizeof g_eq_z); g_lim = 1.0f; quiet_was = 1; } \
                memset(b + off, 0, (size_t)nf * frame_bytes); off += nf * frame_bytes; \
            } else { quiet_was = 0; \
            for (int f = 0; f < nf; f++) { \
                float lane[LANES_MAX] = {0}; \
                int want_play = g_playing && !tail && !hold; \
                if (want_play && g_fade < 1.0f) g_fade += 1.0f / FADE_FRAMES; \
                if (!want_play && g_fade > 0.0f) g_fade -= 1.0f / FADE_FRAMES; \
                if (g_fade < 0) g_fade = 0; if (g_fade > 1) g_fade = 1; \
                if (g_fade > 0) { if (ring_pop_locked(lane)) g_consumed++; else if (want_play && !g_eof) st_underrun++; } \
                if (want_play && g_ring_n < st_ring_min) st_ring_min = g_ring_n; \
                float outv[LANES_MAX]; \
                mix_custom(lane, outv); \
                click_add_locked(outv); \
                for (int c = 0; c < g_dev_ch; c++) { \
                    float s = c < LANES_MAX ? outv[c] : 0; \
                    if (mon && c < OUT_CH && fabsf(s) > g_pk[c]) g_pk[c] = fabsf(s); \
                    put_sample(b + off + c * g_subslot, s); \
                } \
                off += frame_bytes; \
            } \
            } \
            (urb)->iso_frame_desc[k].length = nf * frame_bytes; \
        } \
        pthread_mutex_unlock(&g_mx); \
        (urb)->buffer_length = off; \
    } while (0)

    struct usbdevfs_urb *in_urbs[IN_NURBS];
    if (g_in_ep >= 0) {
        for (int u = 0; u < IN_NURBS; u++) {
            in_urbs[u] = calloc(1, in_urb_sz);
            in_urbs[u]->buffer = calloc(1, g_in_maxpkt * IN_PKTS_PER_URB);
            struct usbdevfs_urb *iu = in_urbs[u];
            iu->type = USBDEVFS_URB_TYPE_ISO; iu->endpoint = g_in_ep; iu->flags = USBDEVFS_URB_ISO_ASAP;
            iu->number_of_packets = IN_PKTS_PER_URB; iu->buffer_length = g_in_maxpkt * IN_PKTS_PER_URB;
            for (int k = 0; k < IN_PKTS_PER_URB; k++) iu->iso_frame_desc[k].length = g_in_maxpkt;
            n_in = u + 1;
            if (ioctl(g_fd, USBDEVFS_SUBMITURB, iu) < 0) {
                LOGI("feedback off: the input refused a URB (%s)", strerror(errno));
                g_in_ep = -1; break;
            }
            inflight++;
        }
    }
    for (int u = 0; u < nurbs; u++) {
        FILL(urbs[u]);
        if (submit(urbs[u]) < 0) {
            set_status("USB error on start: %s", strerror(errno));
            goto out;
        }
        inflight++;
    }
    set_status("USB active");
    g_stalled = 0; g_last_reap = now_s(); g_watching = 1;
    { struct sigaction sa = {0}; sa.sa_handler = wake_usb; sigaction(SIGUSR2, &sa, NULL); }   // no SA_RESTART: REAPURB returns
    pthread_create(&g_watch_thr, NULL, stall_watch, NULL);

    while (tail_urbs < tail_max + nurbs - 1) {
        if (!tail && g_usb_stop) { tail = 1; if (g_fast_stop) tail_max = 0; }   // fast: only what is already queued drains
        struct usbdevfs_urb *done = NULL;
        if (ioctl(g_fd, USBDEVFS_REAPURB, &done) < 0) {
            if (errno == EINTR && g_stalled) {
                // some devices stop returning URBs for good with a long queue, with no error: half of it, connecting again
                if (g_queue_ms > QUEUE_MS_MIN) g_queue_ms /= 2;
                LOGI("usb: queue stalled, trying %d ms", g_queue_ms);
                set_status("USB dropped: queue stalled");
                goto out;
            }
            if (errno == EINTR) continue;
            set_status("USB dropped: %s", strerror(errno));
            goto out;
        }
        inflight--;
        g_last_reap = now_s();
        if (done->endpoint & 0x80) {
            if (DIAG) {   // diagnostics: the first input URBs
                static int dbg = 0;
                if (dbg < 6) {
                    dbg++;
                    char b[160]; int o = 0;
                    for (int k = 0; k < done->number_of_packets && o < 140; k++)
                        o += snprintf(b + o, sizeof b - o, "%u/%d ", done->iso_frame_desc[k].actual_length, done->iso_frame_desc[k].status);
                    LOGD("input: status=%d actual=%d err=%d packets(len/status)=%s", done->status, done->actual_length, done->error_count, b);
                }
            }
            g_in_last = now_s();
            for (int k = 0; k < done->number_of_packets; k++) {
                int n = done->iso_frame_desc[k].status ? 0 : (int)done->iso_frame_desc[k].actual_length / g_in_fb;
                // a packet lost on the way still went by on the desk's clock: it counts for what it would have brought
                if (n == 0) { lost_acc += (double)g_out_rate / pkts_per_sec; n = (int)lost_acc; lost_acc -= n; st_in_lost++; }
                g_tot_in += n; st_in_frames += n; g_fb_win_in += n;
            }
            if (!g_fb_locked && g_tot_in > 0) { g_fb_off = g_tot_out - g_tot_in; g_fb_locked = 1; g_fb_win = 0; }
            if (g_fb_locked) {
                double t = now_s();
                if (!g_fb_decided) {
                    if (fb_probe(t, g_out_rate)) {
                        g_fb_bad = 1; g_fb_mode = 0; g_fb_good = 0; g_fb_probing = 0; g_fb_probe_t = t; g_fb_open_bad = 1;
                        pace_reset();
                    }
                } else if (g_fb_win == 0) { g_fb_win = t; g_fb_win_in = 0; }
                else if (t - g_fb_win >= 1.0) {
                    double hz = g_fb_win_in / (t - g_fb_win);
                    double dev = fabs(hz - g_out_rate);
                    if (!g_fb_bad) {
                        // plan A: only a real failure takes it off (not the noise of one window)
                        if (dev <= g_out_rate * FB_LEAVE_OFF) g_fb_off_n = 0;
                        else if (++g_fb_off_n >= FB_LEAVE_N) {
                            g_fb_bad = 1; g_fb_mode = 0; g_fb_good = 0; g_fb_probing = 0; g_fb_probe_t = t;
                            LOGI("usb: input clock off (%.0f Hz), keeping our own pace", hz);
                        }
                    } else if (!g_fb_open_bad) {
                        // it left plan A during the stream: once the count keeps time again for FB_BACK_S seconds, the
                        // input is the clock again, from where we are (a stream judged off at open stays on plan B)
                        if (dev > g_fb_tol_hz) { g_fb_good = 0; g_fb_probing = 0; g_fb_probe_t = t; }
                        else if (++g_fb_good >= FB_BACK_S) {
                            g_fb_bad = 0; g_fb_mode = 0; g_fb_off = g_tot_out - g_tot_in; g_fb_off_n = 0;
                            LOGI("usb: input clock back (%.0f Hz), following it again", hz);
                        }
                    }
                    g_fb_win = t; g_fb_win_in = 0;
                }
            }
            // An input that can't be followed is only read again to probe it (FB_PROBE_S): on the host that loses its
            // packets, reading it at all costs the output (hiss and thumps heard with the input running, 07/11/2026)
            if (tail || g_in_ep < 0 || (g_fb_bad && !g_fb_probing && !DIAG)) { in_idle++; continue; }
            int npk = done->number_of_packets;
            done->status = 0; done->actual_length = 0; done->start_frame = 0; done->error_count = 0;
            for (int k = 0; k < npk; k++) { done->iso_frame_desc[k].length = g_in_maxpkt; done->iso_frame_desc[k].actual_length = 0; done->iso_frame_desc[k].status = 0; }
            if (ioctl(g_fd, USBDEVFS_SUBMITURB, done) < 0) { LOGI("feedback dropped: %s", strerror(errno)); g_in_ep = -1; }
            else inflight++;
            continue;
        }
        reaped++;
        {   // frames sent ahead of the monotonic clock, smoothed, and the PI loop on it (see g_pace_ratio)
            double t = now_s();
            if (g_probing && t - g_probe_start > FB_PROBE_MAX_S) {
                g_probing = 0; g_fb_decided = 1; g_fb_tol_hz = g_out_rate * FB_MAX_OFF;   // the input didn't come: no longer held
                LOGI("usb: input clock not heard at open, going on");
            }
            if (!g_fb_bad && g_fb_locked && g_in_ep >= 0 && !tail && g_in_last > 0 &&
                (t - g_in_last > FB_STALL_S || labs((g_tot_out - g_tot_in) - g_fb_off) > FB_MAX_D)) {
                // seen on a phone: the input answered for the first second and then went quiet for good; following
                // its frozen count made the output crawl at 5 frames per packet (-17%, slow and distorted)
                g_fb_bad = 1; g_fb_mode = 0; g_fb_good = 0; g_fb_probing = 0; g_fb_probe_t = t;
                pace_reset();
                LOGI("usb: input stopped answering, keeping our own pace");
            }
            if (g_fb_bad && !g_fb_open_bad && !g_fb_probing && !tail && g_in_ep >= 0 && in_idle == n_in && t - g_fb_probe_t >= FB_PROBE_S) {
                // probe: read the input again until a second's count is off (stop) or FB_BACK_S are good (follow it)
                g_fb_probing = 1; g_fb_good = 0; g_fb_win = 0; in_idle = 0;
                for (int u = 0; u < n_in; u++) {
                    struct usbdevfs_urb *iu = in_urbs[u];
                    iu->status = 0; iu->actual_length = 0; iu->start_frame = 0; iu->error_count = 0;
                    for (int k = 0; k < IN_PKTS_PER_URB; k++) { iu->iso_frame_desc[k].length = g_in_maxpkt; iu->iso_frame_desc[k].actual_length = 0; iu->iso_frame_desc[k].status = 0; }
                    if (ioctl(g_fd, USBDEVFS_SUBMITURB, iu) < 0) { LOGI("feedback dropped: %s", strerror(errno)); g_in_ep = -1; break; }
                    inflight++;
                }
            }
            pthread_mutex_lock(&g_mx);
            long sent = g_tot_out;
            pthread_mutex_unlock(&g_mx);
            if (g_pace_t0 == 0) { g_pace_t0 = t; g_pace_last = t; g_pace_sent0 = sent; }
            double err = (sent - g_pace_sent0) - (t - g_pace_t0) * PACE_RATE;
            // the host's clock offset is a constant: the loop hunts it fast for PACE_FAST_S after locking, then goes
            // nearly rigid (an interface's queue may hold only ~2 ms: every step of the ratio is a step it must absorb)
            int slow = g_pace_locked && t - g_pace_lock_t > PACE_FAST_S;
            if (slow && !g_pace_slow) { g_pace_slow = 1; g_pace_int = g_pace_ratio; }   // what P was holding, I holds from now on
            g_pace_err += (err - g_pace_err) * (slow ? PACE_EMA_SLOW : PACE_EMA);
            // the first second settles the queue: what's ahead by then is the working point, not an error
            if (!g_pace_locked && t - g_pace_t0 >= 1.0) { g_pace_locked = 1; g_pace_lock_t = t; g_pace_off = g_pace_err; g_pace_last = t; }
            if (g_pace_locked && t - g_pace_last >= 0.25) {
                double e = g_pace_err - g_pace_off, dt = t - g_pace_last;
                double ti = slow ? PACE_TI_SLOW : PACE_TI, tp = slow ? PACE_TP_SLOW : PACE_TP;
                g_pace_last = t;
                g_pace_int -= e / (ti * g_out_rate) * dt;
                if (g_pace_int > PACE_MAX) g_pace_int = PACE_MAX;
                if (g_pace_int < -PACE_MAX) g_pace_int = -PACE_MAX;
                double r = g_pace_int - e / (tp * g_out_rate);
                if (r > PACE_MAX) r = PACE_MAX;
                if (r < -PACE_MAX) r = -PACE_MAX;
                pthread_mutex_lock(&g_mx);
                g_pace_ratio = r;
                pthread_mutex_unlock(&g_mx);
            }
        }
        if (done->status != 0) { errors++; st_urb_err++; }
        for (int k = 0; k < done->number_of_packets; k++) if (done->iso_frame_desc[k].status) st_pkt_err++;
        if (DIAG) for (int k = 0; k < done->number_of_packets; k++)
            if (done->iso_frame_desc[k].actual_length < done->iso_frame_desc[k].length) {
                st_out_short++; st_out_short_bytes += done->iso_frame_desc[k].length - done->iso_frame_desc[k].actual_length;
            }
        if (DIAG) {   // stats every 5 s (the pace block above already read the clock)
            static double last = 0, win = 0;
            double t = now_s();
            if (last > 0 && t - last > st_gap_max) st_gap_max = t - last;
            last = t;
            if (win == 0) win = t;
            if (t - win >= 5.0) {
#if DIAG && defined(__ANDROID__)
                {   // DIAG: a new target rate re-anchors the pace clock so the error doesn't jump
                    char v[PROP_VALUE_MAX] = "";
                    double want = __system_property_get("debug.stagebook.rate", v) > 0 ? atof(v) : 0;
                    if (want < 40000 || want > 56000) want = 0;
                    if (want != g_pace_rate) {
                        double old = PACE_RATE;
                        g_pace_rate = want;
                        if (g_pace_t0 > 0) g_pace_t0 = t - (t - g_pace_t0) * old / PACE_RATE;
                        g_pace_int += (PACE_RATE - old) / old;   // the ratio jumps with the target; the loop then only trims
                        LOGD("pace: target %.0f Hz", PACE_RATE);
                    }
                }
#endif
                pthread_mutex_lock(&g_mx);
                int ring = g_ring_n, rmin = st_ring_min;
                pthread_mutex_unlock(&g_mx);
                LOGD("stats: playing=%d urbs=%ld urb_err=%ld pkt_err=%ld underrun=%ld ring=%dms ring_min=%dms gap_max=%.1fms "
                     "fb=%s adjusts=%ld diff=%ld desk=%.2fHz out=%.2fHz pace=%+.6f short=%ld/%ldB",
                     g_playing, reaped, st_urb_err, st_pkt_err, st_underrun, ring * 1000 / OUT_RATE,
                     rmin * 1000 / OUT_RATE, st_gap_max * 1000,
                     g_in_ep >= 0 ? (g_fb_bad ? "bad" : g_fb_locked ? "locked" : "waiting") : "off",
                     st_fb_adj, st_fb_d, st_in_frames / (t - win), st_out_frames / (t - win), g_pace_ratio,
                     st_out_short, st_out_short_bytes);
                st_out_short = st_out_short_bytes = 0;
                st_underrun = st_pkt_err = st_urb_err = 0; st_ring_min = RING_FRAMES; st_gap_max = 0; win = t;
                st_fb_adj = st_in_frames = st_out_frames = 0;
            }
        }
        if (tail) tail_urbs++;
        if (tail_urbs >= tail_max) continue;   // drains what is still in the queue
        FILL(done);
        if (submit(done) < 0) {
            set_status("USB dropped: %s", strerror(errno));
            goto out;
        }
        inflight++;
    }
    set_status("stopped (urbs=%ld errors=%d)", reaped, errors);

out:
    if (g_watching) { g_watching = 0; pthread_join(g_watch_thr, NULL); }
    for (int u = 0; u < nurbs; u++) ioctl(g_fd, USBDEVFS_DISCARDURB, urbs[u]);
    for (int u = 0; u < n_in; u++) ioctl(g_fd, USBDEVFS_DISCARDURB, in_urbs[u]);
    // waits for the kernel to return each URB (discarded or with the desk disconnected, it returns right away); an
    // error here means none are left with it
    while (inflight > 0) {
        struct usbdevfs_urb *d;
        int got = reap_wait(&d);
        if (got == 0) inflight--;
        else if (got == REAP_STALLED || errno != EINTR) break;
    }
    if (inflight == 0) {
        for (int u = 0; u < nurbs; u++) { free(urbs[u]->buffer); free(urbs[u]); }
        for (int u = 0; u < n_in; u++) { free(in_urbs[u]->buffer); free(in_urbs[u]); }
    } else {
        for (int u = 0; u < nurbs; u++) park_urb(urbs[u]);
        for (int u = 0; u < n_in; u++) park_urb(in_urbs[u]);
    }
    pthread_mutex_lock(&g_stmx); LOGI("usb end: %s", g_status); pthread_mutex_unlock(&g_stmx);
    g_usb_running = 0;
    return NULL;
}


// ---- device output (AAudio): without a USB interface, plays through Android ----
// A separate path: the USB above doesn't change. Same mix (mix_custom), same fade and same position; the first two
// routing outputs go to the device's two sides. The simulator (no AAudio) doesn't compile this.
#if __has_include(<aaudio/AAudio.h>)
#include <aaudio/AAudio.h>
#define ENGINE_HAS_DEVICE 1
static AAudioStream *g_dev = NULL;
static volatile int g_dev_running = 0;

static aaudio_data_callback_result_t dev_cb(AAudioStream *st, void *ud, void *data, int32_t nf) {
    float *o = (float *)data;
    int ch = AAudioStream_getChannelCount(st);
    // AAudio's real-time callback never waits without a limit (audio audit P4): the lock is held only for short copies
    // (microseconds), so it tries for up to 1 ms, the same as before in practice; if something held it longer, this
    // block goes out silent instead of stalling Android's audio
    int locked = 0;
    for (double until = now_s() + 0.001; !(locked = pthread_mutex_trylock(&g_mx) == 0) && now_s() < until; ) {}
    if (!locked) {
        memset(o, 0, sizeof(float) * (size_t)nf * (size_t)ch);
        return AAUDIO_CALLBACK_RESULT_CONTINUE;
    }
    int mon = g_meter_on;
    for (int f = 0; f < nf; f++) {
        float lane[LANES_MAX] = {0};
        int want_play = g_playing;
        if (want_play && g_fade < 1.0f) g_fade += 1.0f / FADE_FRAMES;
        if (!want_play && g_fade > 0.0f) g_fade -= 1.0f / FADE_FRAMES;
        if (g_fade < 0) g_fade = 0; if (g_fade > 1) g_fade = 1;
        if (g_fade > 0 && ring_pop_locked(lane)) g_consumed++;
        float outv[LANES_MAX];
        mix_custom(lane, outv);
        click_add_locked(outv);   // only set when this output is a USB interface (StagePlayer.usbClick)
        for (int c = 0; c < ch; c++) {
            float v = c < LANES_MAX ? outv[c] : 0;
            if (mon && c < OUT_CH && fabsf(v) > g_pk[c]) g_pk[c] = fabsf(v);
            o[f * ch + c] = v > 1.0f ? 1.0f : (v < -1.0f ? -1.0f : v);
        }
    }
    pthread_mutex_unlock(&g_mx);
    return AAUDIO_CALLBACK_RESULT_CONTINUE;
}

// headphones unplugged, Bluetooth dropped...: the stream dies; the app's watchdog sees isRunning() false and opens
// again
static void dev_error(AAudioStream *st, void *ud, aaudio_result_t err) {
    set_status("device output dropped: %s", AAudio_convertResultToText(err));
    g_dev_running = 0;
}

static void dev_close(void) {
    g_playing = 0; g_meter_on = 0;
    usleep(20000);   // the 5 ms fade finishes before closing: no click
    if (g_dev) { AAudioStream_requestStop(g_dev); AAudioStream_close(g_dev); g_dev = NULL; }
    g_dev_running = 0;
    g_dec_stop = 1;
    pthread_join(g_dec_thr, NULL);
}
#endif

// ---- JNI ----
#define FN(name) Java_br_app_stagebook_player_Engine_##name

JNIEXPORT jboolean JNICALL FN(start)(JNIEnv *e, jclass c, jint fd, jint ep, jint maxpkt, jint ch, jint subslot,
                                     jint intf, jint alt, jint in_ep, jint in_maxpkt, jint in_fb) {
    if (g_usb_running) return JNI_TRUE;
#ifdef ENGINE_HAS_DEVICE
    if (g_dev) dev_close();   // a device output still open: the caller skipped stop()
#endif
    if (g_fd >= 0) {          // the USB thread ended by itself and nobody called stop(): join both before starting again
        g_usb_stop = 1; pthread_join(g_usb_thr, NULL);
        g_dec_stop = 1; pthread_join(g_dec_thr, NULL);
        g_fd = -1;
    }
    free_parked();   // the previous connection is closed by now (PlayerService closes it before connecting again)
    g_in_ep = (in_ep >= 0 && in_fb > 0) ? in_ep : -1; g_in_maxpkt = in_maxpkt; g_in_fb = in_fb;
    g_tot_in = g_tot_out = g_fb_off = 0; g_fb_locked = 0; g_fb_mode = 0; g_fb_bad = 0; g_fb_good = 0; g_fb_probing = 0; g_fb_probe_t = 0;
    g_fb_decided = g_in_ep < 0; g_fb_open_bad = 0; g_fb_off_n = 0; g_pr_n = 0; g_pr_frames = 0; g_pr_time = 0; g_fb_win = 0; g_fb_win_in = 0;
    g_fb_tol_hz = OUT_RATE * FB_MAX_OFF; g_probe_start = now_s(); g_probing = g_in_ep >= 0;
    g_pace_t0 = g_pace_err = g_pace_off = g_pace_ratio = g_pace_int = g_pace_last = g_pace_lock_t = 0; g_pace_locked = 0; g_pace_slow = 0; g_in_last = 0;
    g_fd = fd; g_ep = ep; g_maxpkt = maxpkt; g_dev_ch = ch; g_subslot = subslot;
    g_intf = intf; g_alt = alt;
    g_usb_stop = 0; g_dec_stop = 0; g_fast_stop = 0; g_usb_running = 1;
    pthread_create(&g_dec_thr, NULL, dec_main, NULL);
    pthread_create(&g_usb_thr, NULL, usb_main, NULL);
    return JNI_TRUE;
}

JNIEXPORT void JNICALL FN(stop)(JNIEnv *e, jclass c) {
#ifdef ENGINE_HAS_DEVICE
    if (g_dev) { dev_close(); return; }
#endif
    if (!g_usb_running && g_fd < 0) return;
    g_playing = 0; g_meter_on = 0;
    g_usb_stop = 1;
    pthread_join(g_usb_thr, NULL);
    g_dec_stop = 1;
    pthread_join(g_dec_thr, NULL);
    g_fd = -1;
}

// Stop for a reconnection: the silent tail (0.5 s, so the interface doesn't repeat the last chunk) is skipped, because
// the stream starts again right away.
JNIEXPORT void JNICALL FN(stopFast)(JNIEnv *e, jclass c) { g_fast_stop = 1; FN(stop)(e, c); }

// Opens an output through Android (48 kHz, float): the device's own (deviceId 0, stereo) or a USB interface by its
// Android id with its channels, for devices where talking to USB directly gets no packets back (S26, B-33: the
// other apps play there through Android). False if Android didn't open it or didn't give 48 kHz.
JNIEXPORT jboolean JNICALL FN(startDevice)(JNIEnv *e, jclass c, jint deviceId, jint channels) {
#ifdef ENGINE_HAS_DEVICE
    if (g_usb_running || g_dev) return g_dev ? JNI_TRUE : JNI_FALSE;
    AAudioStreamBuilder *b = NULL;
    if (AAudio_createStreamBuilder(&b) != AAUDIO_OK) return JNI_FALSE;
    AAudioStreamBuilder_setSampleRate(b, OUT_RATE);
    if (deviceId > 0) AAudioStreamBuilder_setDeviceId(b, deviceId);
    AAudioStreamBuilder_setChannelCount(b, channels > 0 ? channels : 2);
    AAudioStreamBuilder_setFormat(b, AAUDIO_FORMAT_PCM_FLOAT);
    AAudioStreamBuilder_setUsage(b, AAUDIO_USAGE_MEDIA);
    AAudioStreamBuilder_setContentType(b, AAUDIO_CONTENT_TYPE_MUSIC);
    AAudioStreamBuilder_setDataCallback(b, dev_cb, NULL);
    AAudioStreamBuilder_setErrorCallback(b, dev_error, NULL);
    aaudio_result_t r = AAudioStreamBuilder_openStream(b, &g_dev);
    AAudioStreamBuilder_delete(b);
    if (r != AAUDIO_OK || AAudioStream_getSampleRate(g_dev) != OUT_RATE) {
        set_status("device output didn't open: %s", AAudio_convertResultToText(r));
        if (g_dev) { AAudioStream_close(g_dev); g_dev = NULL; }
        return JNI_FALSE;
    }
    g_dec_stop = 0;
    pthread_create(&g_dec_thr, NULL, dec_main, NULL);
    if (AAudioStream_requestStart(g_dev) != AAUDIO_OK) { dev_close(); return JNI_FALSE; }
    g_dev_running = 1;
    set_status("device output active");
    return JNI_TRUE;
#else
    return JNI_FALSE;
#endif
}

JNIEXPORT jlong JNICALL FN(load)(JNIEnv *e, jclass c, jstring jpath) {
    const char *path = (*e)->GetStringUTFChars(e, jpath, NULL);
    int err = 0, rate = 0, ch = 0;
    long len = 0;
    src_t v = src_open(path, &err, &rate, &ch, &len);   // reads the disk: before g_vmx, the current song keeps playing
    pthread_mutex_lock(&g_vmx);
    pthread_mutex_lock(&g_mx);
    g_playing = 0; g_fade = 0; g_eof = 0;
    ring_clear_locked();
    g_base_ms = 0; g_consumed = 0;
    mb_reset_locked();
    pthread_mutex_unlock(&g_mx);
    src_close(&g_src);
    g_src = v;
    resampler_reset_locked();
    long dur_ms = -1;
    if (src_ok(&v)) {
        g_src_rate = rate; g_src_ch = ch;
        g_lanes = ch < LANES_MAX ? ch : LANES_MAX;
        g_src_len = len;
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
    if (src_ok(&g_src)) {
        if (ms < 0) ms = 0;
        unsigned int frame = (unsigned int)((double)ms * g_src_rate / 1000.0);
        if (g_src_len > 0 && frame >= (unsigned)g_src_len) frame = g_src_len - 1;
        src_seek(&g_src, frame);   // reads the disk: only with g_vmx, USB keeps playing
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


static float g_eq_gain[EQ_BANDS];

// The output's rate, before start or startDevice: the song, the EQ and the click follow it. 48 kHz changes nothing.
// The queue this interface took before on this device, before start; queueMs says what it ended with.
JNIEXPORT void JNICALL FN(setQueue)(JNIEnv *e, jclass c, jint ms) { if (ms >= QUEUE_MS_MIN && ms <= QUEUE_MS) g_queue_ms = ms; }
JNIEXPORT jint JNICALL FN(queueMs)(JNIEnv *e, jclass c) { return g_queue_ms; }

JNIEXPORT void JNICALL FN(setRate)(JNIEnv *e, jclass c, jint rate) {
    pthread_mutex_lock(&g_mx);
    if (rate > 0 && rate != g_out_rate) {
        g_out_rate = rate;
        for (int i = 0; i < EQ_BANDS; i++) eq_design(i, g_eq_gain[i]);
    }
    pthread_mutex_unlock(&g_mx);
}

JNIEXPORT void JNICALL FN(setEq)(JNIEnv *e, jclass c, jboolean on, jfloatArray jg) {
    float g[EQ_BANDS] = {0};
    (*e)->GetFloatArrayRegion(e, jg, 0, EQ_BANDS, g);
    pthread_mutex_lock(&g_mx);
    memcpy(g_eq_gain, g, sizeof g_eq_gain);
    for (int i = 0; i < EQ_BANDS; i++) eq_design(i, g[i]);
    if (on && !g_eq_on) { memset(g_eq_z, 0, sizeof g_eq_z); g_lim = 1.0f; }
    g_eq_on = on;
    pthread_mutex_unlock(&g_mx);
}


// Mix: gains/norms per source channel, eqMask with the channels that go through the EQ (each separate), route[output *
// LANES_MAX + channel] (LANES_MAX × LANES_MAX).
JNIEXPORT void JNICALL FN(setMix)(JNIEnv *e, jclass c, jfloatArray jgain, jfloatArray jnorm, jint eqMask, jfloatArray jroute) {
    float gain[LANES_MAX] = {0}, norm[LANES_MAX] = {0}, route[LANES_MAX * LANES_MAX] = {0};
    if (jgain) (*e)->GetFloatArrayRegion(e, jgain, 0, LANES_MAX, gain);
    if (jnorm) (*e)->GetFloatArrayRegion(e, jnorm, 0, LANES_MAX, norm);
    if (jroute) (*e)->GetFloatArrayRegion(e, jroute, 0, LANES_MAX * LANES_MAX, route);
    pthread_mutex_lock(&g_mx);
    memcpy(g_ch_gain, gain, sizeof gain);
    memcpy(g_ch_norm, norm, sizeof norm);
    memcpy(g_route, route, sizeof route);
    // EQ turning on for another channel: starts from zero (another channel's old state would click)
    if (g_ch_eq != (int)eqMask) { memset(g_eq_z, 0, sizeof g_eq_z); g_lim = 1.0f; }
    g_ch_eq = (int)eqMask;
    pthread_mutex_unlock(&g_mx);
}

// A 48 kHz click at the output's rate, by linear interpolation: a few milliseconds of sound, once per tempo.
static float *click_at_rate(float *in, int *n) {
    int m = (int)((long)*n * g_out_rate / BASE_RATE);
    float *out = malloc(sizeof(float) * (m > 0 ? m : 1));
    if (!out) return in;
    for (int i = 0; i < m; i++) {
        double p = (double)i * BASE_RATE / g_out_rate;
        int k = (int)p; float f = (float)(p - k);
        out[i] = in[k] + (k + 1 < *n ? (in[k + 1] - in[k]) * f : 0);
    }
    free(in); *n = m;
    return out;
}

// Metronome click into USB outputs: {normal, accent} 48 kHz, samples per beat (0 = off) and up to two outputs (-1 =
// none), and the first beats left silent (bit n = beat n). Starts on beat 1 now. The arrays are copied before the lock;
// the old ones are freed after it.
JNIEXPORT void JNICALL FN(setClick)(JNIEnv *e, jclass c, jfloatArray jn, jfloatArray ja, jlong spb, jint out1, jint out2, jlong skip) {
    float *nb = NULL, *ab = NULL;
    int nn = 0, na = 0;
    if (spb > 0 && jn && ja) {
        nn = (*e)->GetArrayLength(e, jn); na = (*e)->GetArrayLength(e, ja);
        nb = malloc(sizeof(float) * (nn > 0 ? nn : 1)); ab = malloc(sizeof(float) * (na > 0 ? na : 1));
        if (nb && ab) { (*e)->GetFloatArrayRegion(e, jn, 0, nn, nb); (*e)->GetFloatArrayRegion(e, ja, 0, na, ab); }
        else { free(nb); free(ab); nb = ab = NULL; }
        // the click is made at 48 kHz: at another rate it keeps its pitch and the beat its tempo
        if (nb && ab && g_out_rate != BASE_RATE) {
            nb = click_at_rate(nb, &nn); ab = click_at_rate(ab, &na);
            spb = (long)llround((double)spb * g_out_rate / BASE_RATE);
        }
    }
    pthread_mutex_lock(&g_mx);
    float *old0 = g_clk_buf[0], *old1 = g_clk_buf[1];
    g_clk_buf[0] = nb; g_clk_buf[1] = ab;
    g_clk_len[0] = nb ? nn : 0; g_clk_len[1] = ab ? na : 0;
    g_clk_spb = nb && ab ? (long)spb : 0;
    g_clk_pos = 0;
    g_clk_out[0] = out1; g_clk_out[1] = out2;
    g_clk_skip = skip;
    pthread_mutex_unlock(&g_mx);
    free(old0); free(old1);
}

// source channels kept (for the screen to build the mixer): up to LANES_MAX
JNIEXPORT jint JNICALL FN(lanes)(JNIEnv *e, jclass c) { return g_lanes; }

JNIEXPORT jboolean JNICALL FN(isRunning)(JNIEnv *e, jclass c) {
#ifdef ENGINE_HAS_DEVICE
    if (g_dev) return g_dev_running ? JNI_TRUE : JNI_FALSE;
#endif
    return g_usb_running ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jstring JNICALL FN(status)(JNIEnv *e, jclass c) {
    char b[sizeof g_status];
    pthread_mutex_lock(&g_stmx); memcpy(b, g_status, sizeof b); pthread_mutex_unlock(&g_stmx);
    return (*e)->NewStringUTF(e, b);
}

// live meters: turns measuring on/off (off, the engine does the same as before)
JNIEXPORT void JNICALL FN(setMeter)(JNIEnv *e, jclass c, jboolean on) {
    if (!on) { g_meter_on = 0; return; }
    if (g_meter_on) return;
    static int ready = 0;
    if (!ready) { sp_init(); ready = 1; }
    pthread_mutex_lock(&g_vmx);           // the decoder only uses the spectrum with g_vmx
    memset(g_sp_in, 0, sizeof g_sp_in); g_sp_pos = 0; g_sp_new = 0;
    pthread_mutex_lock(&g_mx);
    memset(g_pk, 0, sizeof g_pk);
    memset(g_lpk, 0, sizeof g_lpk);
    mb_reset_locked();
    g_mb_read = g_consumed;
    g_meter_on = 1;
    pthread_mutex_unlock(&g_mx);
    pthread_mutex_unlock(&g_vmx);
}

// peaks since the previous read (linear, 1 = 0 dBFS; above 1 the desk gets clipped): {channel 1, 2, 3, 4, backing
// spectrum in SP_BINS bands from SP_LO to SP_HI Hz}. The spectrum already has the backing's volume and normalization,
// without the EQ (the screen adds the EQ response). Reset after reading.
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
    int first = 0;                            // the spectrum comes before the gains: uses those of the 1st EQ channel (or channel 1)
    while (first < LANES_MAX - 1 && g_ch_eq && !(g_ch_eq & (1 << first))) first++;
    float gb = g_ch_gain[first] * g_ch_norm[first];
    for (int b = 0; b < SP_BINS; b++) out[OUT_CH + b] *= gb;
    jfloatArray a = (*e)->NewFloatArray(e, OUT_CH + SP_BINS);
    if (a) (*e)->SetFloatArrayRegion(e, a, 0, OUT_CH + SP_BINS, out);
    return a;
}

// peaks of each file channel since the previous read (linear, after fader, normalization and EQ), for the mixer meters.
// Reset after reading.
JNIEXPORT jfloatArray JNICALL FN(lanePeaks)(JNIEnv *e, jclass c) {
    float out[LANES_MAX];
    pthread_mutex_lock(&g_mx);
    for (int i = 0; i < LANES_MAX; i++) { out[i] = g_lpk[i]; g_lpk[i] = 0; }
    pthread_mutex_unlock(&g_mx);
    jfloatArray a = (*e)->NewFloatArray(e, LANES_MAX);
    if (a) (*e)->SetFloatArrayRegion(e, a, 0, LANES_MAX, out);
    return a;
}

// interface clock (feedback through the input): 1 locked, 0 waiting, -1 no input. Read only.
JNIEXPORT jint JNICALL FN(feedback)(JNIEnv *e, jclass c) { return g_in_ep < 0 ? -1 : g_fb_locked; }
// 1 while the interface's clock is being judged at open (the output is silent), else 0.
JNIEXPORT jint JNICALL FN(probing)(JNIEnv *e, jclass c) { return g_probing; }

// ---- normalization ----
// Measures the integrated loudness (LUFS, ITU-R BS.1770: K filter + gating) of channels 1/2 and the peak of channels
// 3/4. Returns {lufsBacking, peakBacking, peakClick} or null.
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

// Integrated loudness from the 100 ms segments' mean squares: 400 ms blocks (4 segments, 75% overlap), the absolute
// gate at -70 LUFS and the relative one 10 LU below (ITU-R BS.1770). Shared by analyze and analyzeMask (audio audit A3).
static float gated_lufs(const double *segs, size_t nseg) {
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
    return lufs;
}

// Walks the file once (K-weighting, 400 ms blocks, -70 and -10 gates: ITU-R BS.1770): the integrated loudness and the
// peak of the channels in mask, and, when asked, the peak of channels 3/4 (the click pair). Shared by analyze (the
// backing, channels 1/2) and analyzeMask (each mixer channel or pair). 0 if the file didn't open.
static int measure(const char *path, int mask, float *lufs, float *peak, float *peak_click) {
    int err = 0, ch = 0, fs = 0;
    long len = 0;
    src_t v = src_open(path, &err, &fs, &ch, &len);   // refuses more than 8 channels (buf below holds up to 8)
    if (!src_ok(&v)) return 0;
    biquad sh[LANES_MAX], hp[LANES_MAX];
    for (int i = 0; i < LANES_MAX; i++) kweight_init(&sh[i], &hp[i], fs);
    int seg = fs / 10;                         // 100 ms segments
    size_t cap = 4096, nseg = 0;
    double *segs = malloc(cap * sizeof(double));
    double acc = 0, pk = 0, pkc = 0;
    int inseg = 0;
    float buf[4096 * 8];
    int got;
    while ((got = src_read(&v, ch, buf, 4096)) > 0) {
        for (int i = 0; i < got; i++) {
            float *fr = &buf[i * ch];
            for (int k = 0; k < ch && k < LANES_MAX; k++) {
                if (!(mask & (1 << k))) continue;
                double x = fr[k];
                if (fabs(x) > pk) pk = fabs(x);
                double y = bq(&hp[k], bq(&sh[k], x));
                acc += y * y;
            }
            if (peak_click) for (int k = 2; k < ch && k < 4; k++) if (fabs(fr[k]) > pkc) pkc = fabs(fr[k]);
            if (++inseg == seg) {
                if (nseg == cap) { cap *= 2; segs = realloc(segs, cap * sizeof(double)); }
                segs[nseg++] = acc / seg;
                acc = 0; inseg = 0;
            }
        }
    }
    src_close(&v);
    *lufs = gated_lufs(segs, nseg);
    free(segs);
    *peak = (float)pk;
    if (peak_click) *peak_click = (float)pkc;
    return 1;
}

// Loudness of a set of channels (bits in mask): {lufs, peak}, or null.
JNIEXPORT jfloatArray JNICALL FN(analyzeMask)(JNIEnv *e, jclass c, jstring jpath, jint mask) {
    const char *path = (*e)->GetStringUTFChars(e, jpath, NULL);
    float out[2];
    int ok = measure(path, (int)mask, &out[0], &out[1], NULL);
    (*e)->ReleaseStringUTFChars(e, jpath, path);
    if (!ok) return NULL;
    jfloatArray arr = (*e)->NewFloatArray(e, 2);
    (*e)->SetFloatArrayRegion(e, arr, 0, 2, out);
    return arr;
}

// The backing (channels 1/2) and the click pair (3/4): {lufsBacking, peakBacking, peakClick}, or null.
JNIEXPORT jfloatArray JNICALL FN(analyze)(JNIEnv *e, jclass c, jstring jpath) {
    const char *path = (*e)->GetStringUTFChars(e, jpath, NULL);
    float out[3];
    int ok = measure(path, 0x3, &out[0], &out[1], &out[2]);
    (*e)->ReleaseStringUTFChars(e, jpath, path);
    if (!ok) return NULL;
    jfloatArray arr = (*e)->NewFloatArray(e, 3);
    (*e)->SetFloatArrayRegion(e, arr, 0, 3, out);
    return arr;
}
