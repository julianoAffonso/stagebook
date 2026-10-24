// Motor do Stagebook: decodifica OGG (stb_vorbis), MP3/WAV (dr_mp3/dr_wav), reamostra para 48 kHz e envia
// os canais da faixa direto para a interface USB Audio Class 2 via usbfs (sem o mixer do Android).
// O fluxo USB fica sempre ativo (silêncio quando pausado): a interface repete o último
// trecho recebido quando o fluxo para, o que vira um apito contínuo.
#include <jni.h>
#include <errno.h>
#include <math.h>
#include <pthread.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <strings.h>
#include <time.h>
#include <unistd.h>
#include <sys/ioctl.h>
#include <sys/resource.h>
#include <linux/usbdevice_fs.h>
#include <linux/usb/ch9.h>
#include <android/log.h>

#define STB_VORBIS_HEADER_ONLY
#include "stb_vorbis.c"
// MP3 e WAV (Stagebook, autorizado pelo usuário em 24/10): decodificadores de arquivo único, domínio público
#define DR_MP3_IMPLEMENTATION
#include "dr_mp3.h"
#define DR_WAV_IMPLEMENTATION
#include "dr_wav.h"

#define TAG "stagebook"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
// Diagnóstico (resumo a cada 5 s, pacotes de entrada): 1 para religar.
#define DIAG 0
#define LOGD(...) do { if (DIAG) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__); } while (0)

#define OUT_RATE 48000
#define OUT_CH 4
// canais da fonte guardados no ring (Stagebook): até 8, cada um como veio; a mistura padrão remonta os 4 de antes
#define LANES_MAX 8
// 32 URBs x 8 pacotes = 32 ms na fila do USB. Com 8 (8 ms), o TCL ocupado (another app tocando no
// alto-falante) atrasava a reposição em 10-16 ms e a interface ficava sem áudio: solavanco (19/10/2026).
#define NURBS 32
#define PKTS_PER_URB 8
#define TAIL_URBS 500                 // ~500 ms de silêncio antes de soltar o endpoint
#define RING_FRAMES OUT_RATE          // 1 s de folga entre decoder e USB (era 250 ms: com a câmera do QR,
                                      // importação e telas ao mesmo tempo, o decoder atrasava e engasgava; 19/10/2026)
#define FADE_FRAMES 240               // 5 ms de fade em play/pausa

// ---- estado compartilhado ----
// g_vmx: o arquivo (stb_vorbis) e o reamostrador — abrir, buscar e decodificar levam tempo (disco).
// g_mx: o ring e os contadores, que a thread USB lê a cada 1 ms; só fica preso por cópias curtas.
// Ordem: g_vmx antes de g_mx. A thread USB só pega g_mx.
static pthread_mutex_t g_vmx = PTHREAD_MUTEX_INITIALIZER;
static pthread_mutex_t g_mx = PTHREAD_MUTEX_INITIALIZER;
static float g_ring[RING_FRAMES * LANES_MAX];
static int g_ring_r = 0, g_ring_w = 0, g_ring_n = 0;

// ---- fonte de áudio: OGG (stb_vorbis, exatamente as chamadas da versão validada), MP3 ou WAV ----
typedef struct { stb_vorbis *v; drmp3 *mp3; drwav *wav; } src_t;

static int src_ok(const src_t *s) { return s->v || s->mp3 || s->wav; }

static int ends_with(const char *p, const char *ext) {
    size_t n = strlen(p), m = strlen(ext);
    return n >= m && strcasecmp(p + n - m, ext) == 0;
}

// abre pelo tipo do arquivo; OGG primeiro, como sempre (rate, canais e duração em quadros da fonte)
static src_t src_open(const char *path, int *err, int *rate, int *ch, long *len) {
    src_t s = {0};
    if (ends_with(path, ".mp3")) {
        s.mp3 = malloc(sizeof(drmp3));
        if (s.mp3 && drmp3_init_file(s.mp3, path, NULL)) {
            *rate = (int)s.mp3->sampleRate; *ch = (int)s.mp3->channels;
            *len = (long)drmp3_get_pcm_frame_count(s.mp3);   // percorre o arquivo: fora do g_vmx
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
    // até 8 canais (os buffers de decodificação cabem 8)
    if (src_ok(&s) && *ch > 8) {
        if (s.v) stb_vorbis_close(s.v);
        if (s.mp3) { drmp3_uninit(s.mp3); free(s.mp3); }
        if (s.wav) { drwav_uninit(s.wav); free(s.wav); }
        memset(&s, 0, sizeof s); *err = -4;
    }
    return s;
}

// quadros lidos (0 = fim): mesmo contrato do stb_vorbis_get_samples_float_interleaved
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
#define ENGINE_HAS_SRC 1   // o simulador lê a faixa por aqui (OGG, MP3 ou WAV)
#define ENGINE_HAS_MIX 1   // a mistura é só por canal (setMix): o simulador manda a configuração equivalente
static int g_src_rate = 0, g_src_ch = 0;
static long g_src_len = 0;            // duração em quadros da fonte
static int g_eof = 0;

static volatile int g_playing = 0;

// Equalizador do backing (canais 1/2): 5 biquads em série (RBJ), protegidos por g_mx.
#define EQ_BANDS 5
static const double EQ_FREQ[EQ_BANDS] = {80, 250, 1000, 3500, 10000};
typedef struct { float b0, b1, b2, a1, a2; } eq_coef;
static eq_coef g_eq[EQ_BANDS];
static float g_eq_z[EQ_BANDS][LANES_MAX][2];      // estado [banda][canal][z1,z2]
static int g_eq_on = 0;
// Limitador do backing com o EQ ligado (19/10/2026): o reforço não abaixa mais o volume todo; só os
// picos que passariam de LIM_CEIL são segurados, na hora (sem estourar), e o ganho volta em ~150 ms.
// Antes, a proteção abaixava a entrada do EQ no tamanho do maior reforço (+6 dB = backing 6 dB mais
// baixo), e o usuário achou que abaixava demais. Com o EQ desligado o som é o validado, bit a bit.
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


// Medidores ao vivo (20/10/2026), só com a chave "Monitorar ao vivo" da tela. Desligada, o laço USB e
// o decoder fazem o mesmo de antes: leem g_meter_on uma vez por URB / por bloco e pulam a medição.
// Só leem o sinal: nunca mudam o que vai para a mesa.
static volatile int g_meter_on = 0;
static float g_pk[OUT_CH];                // pico de cada canal na saída (ganhos aplicados, antes do corte), com g_mx
// Espectro do backing: FFT no decoder (fora do laço USB), em mono, antes do EQ e dos ganhos.
// SP_BINS faixas logarítmicas de SP_LO a SP_HI Hz (a tela usa as mesmas bordas). Uma FFT de SP_N pontos
// a cada SP_HOP quadros. O decoder anda até 1 s na frente da saída: cada bloco guarda o quadro (desde o
// seek) em que termina, e a leitura só junta os blocos que a USB já tocou.
#define SP_N 4096                         // 85 ms, 11,7 Hz por raia: separa os graves
#define SP_HOP 1024                       // ~47 FFTs por segundo de música
#define SP_BINS 48
#define SP_LO 30.0
#define SP_HI 16000.0
#define MB_BLOCKS 64                      // ~1,4 s de blocos de ~21 ms: cobre o ring de 1 s
static struct { long end; float pk[SP_BINS]; } g_mb[MB_BLOCKS];   // com g_mx
static int g_mb_w = 0;
static long g_mb_read = 0;                // último quadro já entregue à tela
// só o decoder usa (com g_vmx); tabelas prontas em sp_init
static float g_sp_in[SP_N];               // últimas SP_N amostras (circular)
static int g_sp_pos = 0, g_sp_new = 0;    // onde entra a próxima; quantas chegaram desde a última FFT
static float g_sp_win[SP_N], g_sp_re[SP_N], g_sp_im[SP_N], g_sp_cos[SP_N / 2], g_sp_sin[SP_N / 2];
static int g_sp_k0[SP_BINS], g_sp_k1[SP_BINS];   // raias da FFT de cada faixa (inclusive)

static void sp_init(void) {
    for (int i = 0; i < SP_N; i++) g_sp_win[i] = (float)(0.5 - 0.5 * cos(2 * M_PI * i / SP_N));   // Hann
    for (int i = 0; i < SP_N / 2; i++) { g_sp_cos[i] = (float)cos(2 * M_PI * i / SP_N); g_sp_sin[i] = (float)-sin(2 * M_PI * i / SP_N); }
    for (int b = 0; b < SP_BINS; b++) {
        double lo = SP_LO * pow(SP_HI / SP_LO, (double)b / SP_BINS), hi = SP_LO * pow(SP_HI / SP_LO, (double)(b + 1) / SP_BINS);
        int k0 = (int)ceil(lo * SP_N / OUT_RATE), k1 = (int)floor(hi * SP_N / OUT_RATE);
        if (k1 < k0) k0 = k1 = (int)lround(sqrt(lo * hi) * SP_N / OUT_RATE);   // faixa mais estreita que uma raia
        g_sp_k0[b] = k0; g_sp_k1[b] = k1;
    }
}

// FFT das últimas SP_N amostras (janela de Hann) e o pico de cada faixa; 1 = seno de 0 dBFS
static void sp_run(float *out) {
    for (int i = 0; i < SP_N; i++) { g_sp_re[i] = g_sp_in[(g_sp_pos + i) % SP_N] * g_sp_win[i]; g_sp_im[i] = 0; }
    for (int i = 1, j = 0; i < SP_N; i++) {   // ordem de bits invertida
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
        out[b] = sqrtf(m) * (4.0f / SP_N);    // Hann: seno de amplitude A dá A·N/4 na raia
    }
}

static void mb_reset_locked(void) /* com g_mx */ {
    for (int i = 0; i < MB_BLOCKS; i++) g_mb[i].end = -1;
    g_mb_read = 0;
}

static long g_base_ms = 0;           // posição (ms) do último seek
static long g_consumed = 0;           // quadros de saída tocados desde o seek
static float g_fade = 0.0f;           // 0..1, rampa para evitar estalo

// reamostrador (Catmull-Rom) — histórico de 4 quadros da fonte
static float g_hist[4][LANES_MAX];
static int g_lanes = 4;               // canais da fonte no ring (até LANES_MAX), definido ao carregar
static double g_frac = 0.0;
static int g_hist_fill = 0;

static volatile int g_usb_stop = 0, g_usb_running = 0, g_dec_stop = 0;
// diagnóstico de engasgos: zerados a cada janela de log
static long st_underrun = 0, st_pkt_err = 0, st_urb_err = 0;
static int st_ring_min = RING_FRAMES;
static double st_gap_max = 0;
// Feedback implícito: a entrada da interface chega no ritmo do relógio dela. Comparamos o
// total de quadros que ela produziu com o total que enviamos; se a saída se adianta
// (ou atrasa) mais de FB_HI quadros, os pacotes passam a levar 5 (ou 7) quadros em vez
// de 6 até a diferença voltar a FB_LO. Nenhum quadro de música é descartado.
static int g_in_ep = -1, g_in_maxpkt = 0, g_in_fb = 0;
static long g_tot_in = 0, g_tot_out = 0, g_fb_off = 0;
static int g_fb_locked = 0, g_fb_mode = 0;
#define FB_HI 64
#define FB_LO 16
static long st_fb_adj = 0, st_in_frames = 0, st_out_frames = 0, st_fb_d = 0;
static pthread_t g_usb_thr, g_dec_thr;
static int g_fd = -1, g_ep = 0, g_maxpkt = 0, g_subslot = 4, g_dev_ch = 4, g_intf = 1, g_alt = 1;

// Envia o URB; se o endpoint sumiu (alguém trocou a alternate setting da interface),
// reativa a alt de streaming e tenta de novo.
static int submit(struct usbdevfs_urb *u) {
    if (ioctl(g_fd, USBDEVFS_SUBMITURB, u) == 0) return 0;
    if (errno != ENOENT && errno != EINVAL) return -1;
    struct usbdevfs_setinterface si = { .interface = g_intf, .altsetting = g_alt };
    if (ioctl(g_fd, USBDEVFS_SETINTERFACE, &si) < 0) return -1;
    LOGI("alt setting restaurada (if%d alt%d)", g_intf, g_alt);
    return ioctl(g_fd, USBDEVFS_SUBMITURB, u);
}
static char g_status[256] = "parado";

static double now_s(void) {
    struct timespec ts;
    clock_gettime(CLOCK_MONOTONIC, &ts);
    return ts.tv_sec + ts.tv_nsec / 1e9;
}

// ---- mistura por canal (Stagebook, 24/10/2026) ----
// A única mistura do Stagebook (decisão do usuário: num app público, supor "1-2 backing, 3-4 click" é um risco).
// O app manda a configuração de cada backing track (setMix); sem ela, cada canal vai para a saída de mesmo número,
// com ganho 1 — nada é adivinhado. Com a configuração equivalente à antiga, o som é idêntico ao validado na mesa
// (simulador). Protegida por g_mx.
static float g_ch_gain[LANES_MAX] = {1, 1, 1, 1, 1, 1, 1, 1};   // fader × liga/desliga de cada canal da fonte
static float g_ch_norm[LANES_MAX] = {1, 1, 1, 1, 1, 1, 1, 1};   // normalização de cada canal
static int g_ch_eq = 0;                     // bits: canais que passam pelo EQ (cada um separado)
static float g_route[LANES_MAX][LANES_MAX] = {   // [saída][canal da fonte]: quanto do canal vai para a saída
    {1}, {0, 1}, {0, 0, 1}, {0, 0, 0, 1}, {0, 0, 0, 0, 1}, {0, 0, 0, 0, 0, 1}, {0, 0, 0, 0, 0, 0, 1}, {0, 0, 0, 0, 0, 0, 0, 1},
};

// canais da fonte → saídas: ganho e normalização por canal, EQ nos marcados (limitador junto em todos eles,
// para não deslocar a imagem, como no par do backing) e soma pela matriz de roteamento
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
    for (int o = 0; o < LANES_MAX; o++) {
        float a = 0;
        for (int c = 0; c < g_lanes; c++) a += g_route[o][c] * s[c];
        out[o] = a;
    }
}

// ---- ring (protegido por g_mx) ----
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


static void resampler_reset_locked(void)   /* com g_vmx */ {
    g_frac = 0.0; g_hist_fill = 0; memset(g_hist, 0, sizeof g_hist);
    memset(g_sp_in, 0, sizeof g_sp_in); g_sp_pos = 0; g_sp_new = 0;
}

// ---- thread do decoder ----
// decodifica e reamostra com g_vmx (sem travar a USB) e só pega g_mx para copiar no ring
static float g_dec_out[4096 * LANES_MAX];   // só a thread do decoder usa

static void *dec_main(void *arg) {
    float buf[1024 * 8];
    // prioridade de áudio do Android (-16): na frente da tela, da câmera e da importação, abaixo do USB (-19)
    if (setpriority(PRIO_PROCESS, gettid(), -16) < 0) LOGI("decoder: prioridade negada (%s)", strerror(errno));
    while (!g_dec_stop) {
        pthread_mutex_lock(&g_vmx);
        pthread_mutex_lock(&g_mx);
        int active = src_ok(&g_src) && g_playing && !g_eof;
        int work = active && ring_space_locked() > 2048;
        pthread_mutex_unlock(&g_mx);
        // tocando, o ring (250 ms) só esvazia 2048 quadros em ~43 ms: 5 ms de folga sobra;
        // parado, 20 ms basta (o play só espera o primeiro bloco) e a CPU acorda bem menos
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
            // empurra o quadro novo no histórico
            memmove(g_hist[0], g_hist[1], sizeof(float) * LANES_MAX * 3);
            // os canais da fonte como vieram (a mistura é na saída)
            for (int c = 0; c < g_lanes; c++) g_hist[3][c] = buf[i * g_src_ch + c];
            if (g_hist_fill < 4) { g_hist_fill++; if (g_hist_fill < 4) continue; }
            if (g_src_rate == OUT_RATE) {
                if (n < cap) memcpy(&g_dec_out[n++ * LANES_MAX], g_hist[2], sizeof(float) * LANES_MAX);
                continue;
            }
            // gera saídas enquanto a posição fracionária estiver entre hist[1] e hist[2]
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
            for (int i = 0; i < n; i++) {
                // backing (1/2) como na mistura padrão: fonte mono = o mesmo canal dos dois lados
                float *d = &g_dec_out[i * LANES_MAX];
                g_sp_in[g_sp_pos] = 0.5f * (d[0] + (g_lanes > 1 ? d[1] : d[0]));
                g_sp_pos = (g_sp_pos + 1) % SP_N;
            }
            g_sp_new += n;
            if (g_sp_new >= SP_HOP) { sp_run(spk); g_sp_new = 0; fft = 1; }
        }
        pthread_mutex_lock(&g_mx);
        for (int i = 0; i < n && ring_space_locked() > 0; i++) ring_push_locked(&g_dec_out[i * LANES_MAX]);
        if (fft) {
            g_mb[g_mb_w].end = g_consumed + g_ring_n;   // quadro (desde o seek) em que este bloco acaba de tocar
            memcpy(g_mb[g_mb_w].pk, spk, sizeof spk);
            g_mb_w = (g_mb_w + 1) % MB_BLOCKS;
        }
        pthread_mutex_unlock(&g_mx);
        pthread_mutex_unlock(&g_vmx);
    }
    return NULL;
}

// ---- amostra para o formato da interface ----
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
    int inflight = 0, n_in = 0;          // URBs com o kernel; só se libera memória com zero

    // prioridade de áudio (a mesma do AudioTrack): sem ela, com a CPU ocupada ou quente, a
    // thread pode perder a vez e a fila de 8 ms de URBs esvaziar; se o sistema negar, segue normal
    if (setpriority(PRIO_PROCESS, gettid(), -19) < 0) LOGI("usb: prioridade negada (%s)", strerror(errno));

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
                float lane[LANES_MAX] = {0}; \
                int want_play = g_playing && !tail; \
                if (want_play && g_fade < 1.0f) g_fade += 1.0f / FADE_FRAMES; \
                if (!want_play && g_fade > 0.0f) g_fade -= 1.0f / FADE_FRAMES; \
                if (g_fade < 0) g_fade = 0; if (g_fade > 1) g_fade = 1; \
                if (g_fade > 0) { if (ring_pop_locked(lane)) g_consumed++; else if (want_play && !g_eof) st_underrun++; } \
                if (want_play && g_ring_n < st_ring_min) st_ring_min = g_ring_n; \
                float outv[LANES_MAX]; \
                mix_custom(lane, outv); \
                for (int c = 0; c < g_dev_ch; c++) { \
                    float s = c < LANES_MAX ? outv[c] : 0; \
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
                LOGI("feedback desligado: entrada não aceitou URB (%s)", strerror(errno));
                g_in_ep = -1; break;
            }
            inflight++;
        }
    }
    for (int u = 0; u < NURBS; u++) {
        FILL(urbs[u]);
        if (submit(urbs[u]) < 0) {
            snprintf(g_status, sizeof g_status, "erro USB ao iniciar: %s", strerror(errno));
            goto out;
        }
        inflight++;
    }
    snprintf(g_status, sizeof g_status, "USB ativo");

    while (tail_urbs < TAIL_URBS + NURBS - 1) {
        if (!tail && g_usb_stop) tail = 1;
        struct usbdevfs_urb *done = NULL;
        if (ioctl(g_fd, USBDEVFS_REAPURB, &done) < 0) {
            if (errno == EINTR) continue;
            snprintf(g_status, sizeof g_status, "USB caiu: %s", strerror(errno));
            goto out;
        }
        inflight--;
        if (done->endpoint & 0x80) {
            if (DIAG) {   // diagnóstico: os primeiros URBs de entrada
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
            if (ioctl(g_fd, USBDEVFS_SUBMITURB, done) < 0) { LOGI("feedback caiu: %s", strerror(errno)); g_in_ep = -1; }
            else inflight++;
            continue;
        }
        reaped++;
        if (done->status != 0) { errors++; st_urb_err++; }
        for (int k = 0; k < done->number_of_packets; k++) if (done->iso_frame_desc[k].status) st_pkt_err++;
        if (DIAG) {   // estatísticas a cada 5 s: com DIAG 0 nem lê o relógio
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
        if (tail_urbs >= TAIL_URBS) continue;   // drena o que ainda está na fila
        FILL(done);
        if (submit(done) < 0) {
            snprintf(g_status, sizeof g_status, "USB caiu: %s", strerror(errno));
            goto out;
        }
        inflight++;
    }
    snprintf(g_status, sizeof g_status, "parado (urbs=%ld erros=%d)", reaped, errors);

out:
    for (int u = 0; u < NURBS; u++) ioctl(g_fd, USBDEVFS_DISCARDURB, urbs[u]);
    for (int u = 0; u < n_in; u++) ioctl(g_fd, USBDEVFS_DISCARDURB, in_urbs[u]);
    // espera o kernel devolver cada URB (descartado ou com a mesa desconectada, volta na hora);
    // um erro aqui quer dizer que não sobrou nenhum com ele
    while (inflight > 0) {
        struct usbdevfs_urb *d;
        if (ioctl(g_fd, USBDEVFS_REAPURB, &d) == 0) inflight--;
        else if (errno != EINTR) break;
    }
    if (inflight == 0) {
        for (int u = 0; u < NURBS; u++) { free(urbs[u]->buffer); free(urbs[u]); }
        for (int u = 0; u < n_in; u++) { free(in_urbs[u]->buffer); free(in_urbs[u]); }
    }
    LOGI("usb fim: %s", g_status);
    g_usb_running = 0;
    return NULL;
}


// ---- saída do aparelho (AAudio): sem interface USB, toca pelo Android ----
// Caminho à parte: a USB acima não muda. Mesma mistura (mix_custom), mesmo fade e mesma posição; as duas
// primeiras saídas do roteamento vão para os dois lados do aparelho. O simulador (sem AAudio) não compila isto.
#if __has_include(<aaudio/AAudio.h>)
#include <aaudio/AAudio.h>
#define ENGINE_HAS_DEVICE 1
static AAudioStream *g_dev = NULL;
static volatile int g_dev_running = 0;

static aaudio_data_callback_result_t dev_cb(AAudioStream *st, void *ud, void *data, int32_t nf) {
    float *o = (float *)data;
    int ch = AAudioStream_getChannelCount(st);
    pthread_mutex_lock(&g_mx);
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
        for (int c = 0; c < ch; c++) {
            float v = c < LANES_MAX ? outv[c] : 0;
            if (mon && c < OUT_CH && fabsf(v) > g_pk[c]) g_pk[c] = fabsf(v);
            o[f * ch + c] = v > 1.0f ? 1.0f : (v < -1.0f ? -1.0f : v);
        }
    }
    pthread_mutex_unlock(&g_mx);
    return AAUDIO_CALLBACK_RESULT_CONTINUE;
}

// fone tirado, Bluetooth caiu...: o fluxo morre; o vigia do app vê isRunning() falso e abre de novo
static void dev_error(AAudioStream *st, void *ud, aaudio_result_t err) {
    snprintf(g_status, sizeof g_status, "saída do aparelho caiu: %s", AAudio_convertResultToText(err));
    g_dev_running = 0;
}

static void dev_close(void) {
    g_playing = 0; g_meter_on = 0;
    usleep(20000);   // o fade de 5 ms termina antes de fechar: sem estalo
    if (g_dev) { AAudioStream_requestStop(g_dev); AAudioStream_close(g_dev); g_dev = NULL; }
    g_dev_running = 0;
    g_dec_stop = 1;
    pthread_join(g_dec_thr, NULL);
}
#endif

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

// Abre a saída do aparelho (48 kHz, estéreo, float). Falso se o Android não abriu ou não deu 48 kHz.
JNIEXPORT jboolean JNICALL FN(startDevice)(JNIEnv *e, jclass c) {
#ifdef ENGINE_HAS_DEVICE
    if (g_usb_running || g_dev) return g_dev ? JNI_TRUE : JNI_FALSE;
    AAudioStreamBuilder *b = NULL;
    if (AAudio_createStreamBuilder(&b) != AAUDIO_OK) return JNI_FALSE;
    AAudioStreamBuilder_setSampleRate(b, OUT_RATE);
    AAudioStreamBuilder_setChannelCount(b, 2);
    AAudioStreamBuilder_setFormat(b, AAUDIO_FORMAT_PCM_FLOAT);
    AAudioStreamBuilder_setUsage(b, AAUDIO_USAGE_MEDIA);
    AAudioStreamBuilder_setContentType(b, AAUDIO_CONTENT_TYPE_MUSIC);
    AAudioStreamBuilder_setDataCallback(b, dev_cb, NULL);
    AAudioStreamBuilder_setErrorCallback(b, dev_error, NULL);
    aaudio_result_t r = AAudioStreamBuilder_openStream(b, &g_dev);
    AAudioStreamBuilder_delete(b);
    if (r != AAUDIO_OK || AAudioStream_getSampleRate(g_dev) != OUT_RATE) {
        snprintf(g_status, sizeof g_status, "saída do aparelho não abriu: %s", AAudio_convertResultToText(r));
        if (g_dev) { AAudioStream_close(g_dev); g_dev = NULL; }
        return JNI_FALSE;
    }
    g_dec_stop = 0;
    pthread_create(&g_dec_thr, NULL, dec_main, NULL);
    if (AAudioStream_requestStart(g_dev) != AAUDIO_OK) { dev_close(); return JNI_FALSE; }
    g_dev_running = 1;
    snprintf(g_status, sizeof g_status, "saída do aparelho ativa");
    return JNI_TRUE;
#else
    return JNI_FALSE;
#endif
}

JNIEXPORT jlong JNICALL FN(load)(JNIEnv *e, jclass c, jstring jpath) {
    const char *path = (*e)->GetStringUTFChars(e, jpath, NULL);
    int err = 0, rate = 0, ch = 0;
    long len = 0;
    src_t v = src_open(path, &err, &rate, &ch, &len);   // lê o disco: antes do g_vmx, a música atual segue tocando
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
        LOGI("load falhou %s: err=%d", path, err);
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
        src_seek(&g_src, frame);   // lê o disco: só com g_vmx, a USB segue tocando
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


JNIEXPORT void JNICALL FN(setEq)(JNIEnv *e, jclass c, jboolean on, jfloatArray jg) {
    float g[EQ_BANDS] = {0};
    (*e)->GetFloatArrayRegion(e, jg, 0, EQ_BANDS, g);
    pthread_mutex_lock(&g_mx);
    for (int i = 0; i < EQ_BANDS; i++) eq_design(i, g[i]);
    if (on && !g_eq_on) { memset(g_eq_z, 0, sizeof g_eq_z); g_lim = 1.0f; }
    g_eq_on = on;
    pthread_mutex_unlock(&g_mx);
}


// Mistura: gains/norms por canal da fonte, eqMask com os canais que passam pelo EQ (cada um separado),
// route[saída * LANES_MAX + canal] (LANES_MAX × LANES_MAX).
JNIEXPORT void JNICALL FN(setMix)(JNIEnv *e, jclass c, jfloatArray jgain, jfloatArray jnorm, jint eqMask, jfloatArray jroute) {
    float gain[LANES_MAX] = {0}, norm[LANES_MAX] = {0}, route[LANES_MAX * LANES_MAX] = {0};
    if (jgain) (*e)->GetFloatArrayRegion(e, jgain, 0, LANES_MAX, gain);
    if (jnorm) (*e)->GetFloatArrayRegion(e, jnorm, 0, LANES_MAX, norm);
    if (jroute) (*e)->GetFloatArrayRegion(e, jroute, 0, LANES_MAX * LANES_MAX, route);
    pthread_mutex_lock(&g_mx);
    memcpy(g_ch_gain, gain, sizeof gain);
    memcpy(g_ch_norm, norm, sizeof norm);
    memcpy(g_route, route, sizeof route);
    // EQ ligando em outro canal: começa do zero (estado velho de outro canal estalaria)
    if (g_ch_eq != (int)eqMask) { memset(g_eq_z, 0, sizeof g_eq_z); g_lim = 1.0f; }
    g_ch_eq = (int)eqMask;
    pthread_mutex_unlock(&g_mx);
}

// canais da fonte guardados (para a tela montar o mixer): até LANES_MAX
JNIEXPORT jint JNICALL FN(lanes)(JNIEnv *e, jclass c) { return g_lanes; }

JNIEXPORT jboolean JNICALL FN(isRunning)(JNIEnv *e, jclass c) {
#ifdef ENGINE_HAS_DEVICE
    if (g_dev) return g_dev_running ? JNI_TRUE : JNI_FALSE;
#endif
    return g_usb_running ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jstring JNICALL FN(status)(JNIEnv *e, jclass c) { return (*e)->NewStringUTF(e, g_status); }

// medidores ao vivo: liga/desliga a medição (desligada, o motor faz o mesmo de antes)
JNIEXPORT void JNICALL FN(setMeter)(JNIEnv *e, jclass c, jboolean on) {
    if (!on) { g_meter_on = 0; return; }
    if (g_meter_on) return;
    static int ready = 0;
    if (!ready) { sp_init(); ready = 1; }
    pthread_mutex_lock(&g_vmx);           // o decoder só usa o espectro com g_vmx
    memset(g_sp_in, 0, sizeof g_sp_in); g_sp_pos = 0; g_sp_new = 0;
    pthread_mutex_lock(&g_mx);
    memset(g_pk, 0, sizeof g_pk);
    mb_reset_locked();
    g_mb_read = g_consumed;
    g_meter_on = 1;
    pthread_mutex_unlock(&g_mx);
    pthread_mutex_unlock(&g_vmx);
}

// picos desde a leitura anterior (lineares, 1 = 0 dBFS; acima de 1 a mesa recebe cortado):
// {canal 1, 2, 3, 4, espectro do backing em SP_BINS faixas de SP_LO a SP_HI Hz}. O espectro já com o
// volume e a normalização do backing, sem o EQ (a tela soma a resposta do EQ). Zera depois de ler.
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
    float gb = g_ch_gain[0] * g_ch_norm[0];   // o espectro é do canal 1 (antes dos ganhos)
    for (int b = 0; b < SP_BINS; b++) out[OUT_CH + b] *= gb;
    jfloatArray a = (*e)->NewFloatArray(e, OUT_CH + SP_BINS);
    if (a) (*e)->SetFloatArrayRegion(e, a, 0, OUT_CH + SP_BINS, out);
    return a;
}

// relógio da interface (feedback pela entrada): 1 travado, 0 esperando, -1 sem entrada. Só leitura.
JNIEXPORT jint JNICALL FN(feedback)(JNIEnv *e, jclass c) { return g_in_ep < 0 ? -1 : g_fb_locked; }

// ---- normalização ----
// Mede a loudness integrada (LUFS, ITU-R BS.1770: filtro K + gating) dos canais 1/2
// e o pico dos canais 3/4. Retorna {lufsBacking, picoBacking, picoClick} ou null.
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

// Volume de um conjunto de canais (bits em mask): {lufs, pico}. É a mesma medição do backing acima (K-weighting,
// blocos de 400 ms, portões de -70 e -10), só que nos canais escolhidos — cada canal ou par do mixer por canal.
JNIEXPORT jfloatArray JNICALL FN(analyzeMask)(JNIEnv *e, jclass c, jstring jpath, jint mask) {
    const char *path = (*e)->GetStringUTFChars(e, jpath, NULL);
    int err = 0, ch = 0, fs = 0;
    long len = 0;
    src_t v = src_open(path, &err, &fs, &ch, &len);
    (*e)->ReleaseStringUTFChars(e, jpath, path);
    if (!src_ok(&v)) return NULL;
    biquad sh[LANES_MAX], hp[LANES_MAX];
    for (int i = 0; i < LANES_MAX; i++) kweight_init(&sh[i], &hp[i], fs);
    int seg = fs / 10;
    size_t cap = 4096, nseg = 0;
    double *segs = malloc(cap * sizeof(double));
    double acc = 0, peak = 0;
    int inseg = 0;
    float buf[4096 * 8];
    int got;
    while ((got = src_read(&v, ch, buf, 4096)) > 0) {
        for (int i = 0; i < got; i++) {
            float *fr = &buf[i * ch];
            for (int k = 0; k < ch && k < LANES_MAX; k++) {
                if (!(mask & (1 << k))) continue;
                double x = fr[k];
                if (fabs(x) > peak) peak = fabs(x);
                double y = bq(&hp[k], bq(&sh[k], x));
                acc += y * y;
            }
            if (++inseg == seg) {
                if (nseg == cap) { cap *= 2; segs = realloc(segs, cap * sizeof(double)); }
                segs[nseg++] = acc / seg;
                acc = 0; inseg = 0;
            }
        }
    }
    src_close(&v);
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
    float out[2] = {lufs, (float)peak};
    jfloatArray arr = (*e)->NewFloatArray(e, 2);
    (*e)->SetFloatArrayRegion(e, arr, 0, 2, out);
    return arr;
}

JNIEXPORT jfloatArray JNICALL FN(analyze)(JNIEnv *e, jclass c, jstring jpath) {
    const char *path = (*e)->GetStringUTFChars(e, jpath, NULL);
    int err = 0, ch = 0, fs = 0;
    long len = 0;
    src_t v = src_open(path, &err, &fs, &ch, &len);   // recusa mais de 8 canais (buf abaixo cabe até 8)
    (*e)->ReleaseStringUTFChars(e, jpath, path);
    if (!src_ok(&v)) return NULL;
    int nb = ch >= 2 ? 2 : 1;                 // canais do backing
    biquad sh[2], hp[2];
    for (int i = 0; i < 2; i++) kweight_init(&sh[i], &hp[i], fs);

    int seg = fs / 10;                         // segmentos de 100 ms
    size_t cap = 4096, nseg = 0;
    double *segs = malloc(cap * sizeof(double));
    double acc = 0, peakB = 0, peakC = 0;
    int inseg = 0;
    float buf[4096 * 8];
    int got;
    while ((got = src_read(&v, ch, buf, 4096)) > 0) {
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
    src_close(&v);

    // blocos de 400 ms (4 segmentos) com 75% de sobreposição
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
