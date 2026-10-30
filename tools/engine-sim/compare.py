"""Compares the output recorded by the simulator: validated engine x current engine.

The two versions start at slightly different moments (threads), so the stretches are aligned
by the first frame with sound before comparing sample by sample.
"""
import array
import sys


def load(path):
    a = array.array("f")
    with open(path, "rb") as f:
        a.frombytes(f.read())
    return a


def frames(a):
    return len(a) // 4


def first_sound(a, after):
    """First frame with sound from 'after' on (real tracks start with silence)."""
    for i in range(after, frames(a)):
        if sum(abs(a[i * 4 + c]) for c in range(4)) > 1e-3:
            return i
    return after


# each recording ends with the pause: the 5 ms fade starts on a frame that depends on thread timing,
# so it's left out (it's the same in both versions, it just doesn't land on the same frame)
TAIL = 48000 * 3 // 10


def compare(name, old_path, new_path, starts):
    old, new = load(old_path), load(new_path)
    ok = True
    for label, after in starts:
        start = first_sound(new, after)
        o = first_sound(old, after)
        n = min(frames(new) - start, frames(old) - o) - TAIL
        maxd = max(abs(old[(o + i) * 4 + c] - new[(start + i) * 4 + c]) for i in range(n) for c in range(4))
        same = maxd < 2e-7
        ok &= same
        print(f"  {'OK  ' if same else 'FAIL'} {name} {label}: {n} frames compared, largest difference {maxd:.2e}")
    return ok


if __name__ == "__main__":
    d = sys.argv[1]
    ok = True
    a = open(f"{d}/lufs-validated.txt").read().split()
    # "meter": the current engine with live meters on
    for v in ("current", "meter"):
        ok &= compare(f"[{v}] 44.1 kHz 4 channels", f"{d}/output-validated.f32", f"{d}/output-{v}.f32",
                      [("after seeking to 1:00", 48000 * 3)])
        ok &= compare(f"[{v}] 48 kHz", f"{d}/output48-validated.f32", f"{d}/output48-{v}.f32", [("start", 0)])
        b = open(f"{d}/lufs-{v}.txt").read().split()
        same = a == b
        ok &= same
        print(f"  {'OK  ' if same else 'FAIL'} [{v}] identical normalization: {' '.join(a)} x {' '.join(b)}")
    sys.exit(0 if ok else 1)
