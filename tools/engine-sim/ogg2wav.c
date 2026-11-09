// Converts an OGG to 32-bit float WAV with the SAME decoder as the engine (stb_vorbis): the samples come out identical.
// The simulator uses it to prove the engine's WAV path gives exactly the same sound as the OGG.
// ogg2wav <input.ogg> <output.wav>
#include <stdio.h>
#include <stdlib.h>
#include "stb_vorbis.c"
#define DR_WAV_IMPLEMENTATION
#include "dr_wav.h"

int main(int argc, char **argv) {
    if (argc != 3) { fprintf(stderr, "usage: ogg2wav <input.ogg> <output.wav>\n"); return 2; }
    int err = 0;
    stb_vorbis *v = stb_vorbis_open_filename(argv[1], &err, NULL);
    if (!v) { fprintf(stderr, "could not open %s (%d)\n", argv[1], err); return 1; }
    stb_vorbis_info inf = stb_vorbis_get_info(v);
    drwav_data_format fmt = { drwav_container_riff, DR_WAVE_FORMAT_IEEE_FLOAT, (drwav_uint32)inf.channels, (drwav_uint32)inf.sample_rate, 32 };
    drwav w;
    if (!drwav_init_file_write(&w, argv[2], &fmt, NULL)) { fprintf(stderr, "could not write %s\n", argv[2]); return 1; }
    float buf[4096 * 8];
    int got;
    while ((got = stb_vorbis_get_samples_float_interleaved(v, inf.channels, buf, 4096 * inf.channels)) > 0) drwav_write_pcm_frames(&w, got, buf);
    drwav_uninit(&w);
    stb_vorbis_close(v);
    return 0;
}
