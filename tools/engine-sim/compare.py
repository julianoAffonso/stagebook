"""Compara a saída gravada pelo simulador: motor validado x motor atual.

As duas versões partem em instantes um pouco diferentes (threads), então os trechos são alinhados
pelo primeiro quadro com som antes de comparar amostra a amostra.
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
    """Primeiro quadro com som a partir de 'after' (faixas reais começam com silêncio)."""
    for i in range(after, frames(a)):
        if sum(abs(a[i * 4 + c]) for c in range(4)) > 1e-3:
            return i
    return after


# o fim de cada gravação é a pausa: o fade de 5 ms começa num quadro que depende do tempo das
# threads, então fica de fora (é o mesmo nas duas versões, só não cai no mesmo quadro)
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
        print(f"  {'OK  ' if same else 'FALHA'} {name} {label}: {n} quadros comparados, maior diferença {maxd:.2e}")
    return ok


if __name__ == "__main__":
    d = sys.argv[1]
    ok = True
    a = open(f"{d}/lufs-validado.txt").read().split()
    # "medidor": o motor atual com os medidores ao vivo ligados
    # "canal": a mistura por canal configurada igual à padrão
    for v in ("atual", "medidor", "canal"):
        ok &= compare(f"[{v}] 44,1 kHz 4 canais", f"{d}/saida-validado.f32", f"{d}/saida-{v}.f32",
                      [("depois de buscar 1:00", 48000 * 3)])
        ok &= compare(f"[{v}] 48 kHz", f"{d}/saida48-validado.f32", f"{d}/saida48-{v}.f32", [("início", 0)])
        b = open(f"{d}/lufs-{v}.txt").read().split()
        same = a == b
        ok &= same
        print(f"  {'OK  ' if same else 'FALHA'} [{v}] normalização idêntica: {' '.join(a)} x {' '.join(b)}")
    sys.exit(0 if ok else 1)
