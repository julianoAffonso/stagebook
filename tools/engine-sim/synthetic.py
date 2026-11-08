"""Makes the two tracks the simulator needs out of noise, for where real tracks can't go (they never go to git).

    python3 tools/engine-sim/synthetic.py <folder>   -> <folder>/t44100.ogg (4 channels), <folder>/t48000.ogg (2 channels)

Needs numpy and soundfile.
"""
import sys

import numpy as np
import soundfile as sf

SECONDS = 210   # the simulator wants a song-sized track
SMOOTH = 32


def noise(rate, channels, seed):
    x = np.random.default_rng(seed).standard_normal((rate * SECONDS + SMOOTH, channels)).cumsum(axis=0)
    # most of the energy low, as in music: resampling cuts the top, and the level checks would read that as a loss
    x = x[SMOOTH:] - x[:-SMOOTH]
    x = (0.5 * x / np.abs(x).max()).astype("float32")
    x[:rate // 2] = 0   # like a song, it starts with silence: the comparison lines up on the first sound
    return x


def write(path, x, rate):
    # in blocks: libsndfile crashes writing a long Vorbis file in one call
    with sf.SoundFile(path, "w", rate, x.shape[1], format="OGG", subtype="VORBIS") as f:
        for i in range(0, len(x), 4096):
            f.write(x[i:i + 4096])


write(f"{sys.argv[1]}/t44100.ogg", noise(44100, 4, 1), 44100)
write(f"{sys.argv[1]}/t48000.ogg", noise(48000, 2, 2), 48000)
