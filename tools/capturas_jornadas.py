"""Sorts the captures by journey (T39): <dest>/<language>/<NN-journey>/<capture>.png."""
import re
import shutil
import sys
from pathlib import Path

# first match wins: (journey folder, name pattern)
JOURNEYS = [
    ("01-musicas", r"^(celular|tablet|tablet_em_pe)_1_|^jornada_musicas_|^ingles_1"),
    ("02-importar", r"^(celular|tablet|tablet_em_pe)_10_|^jornada_importar"),
    ("03-editor", r"^(celular|tablet|tablet_em_pe)_4_"),
    ("04-setlists", r"^(celular|tablet|tablet_em_pe)_[239]_|^jornada_conferencia_|^jornada_setlist_"),
    ("05-compartilhar-e-qr", r"^jornada_compartilhar_|^jornada_qr_"),
    ("06-palco", r"^(celular|tablet|tablet_em_pe)_[67]_|^acorde_"),
    ("07-backing-track", r"^(celular|tablet|tablet_em_pe)_11_|^jornada_faixa_|^jornada_pedal"),
    ("08-mixer", r"^jornada_mixer_|^jornada_eq_"),
    ("09-banda", r"^jornada_banda_|^banda_"),
    ("10-ajustes", r"^(celular|tablet|tablet_em_pe)_5_|^ingles_2"),
    ("11-atualizacao", r"^(celular|tablet|tablet_em_pe)_8_"),
]


def journey(name: str) -> str:
    for folder, pattern in JOURNEYS:
        if re.search(pattern, name):
            return folder
    return "99-outras"


def main(work: str, dest: str) -> None:
    out = Path(dest)
    for lang_dir in sorted(Path(work).iterdir()):
        if not lang_dir.is_dir():
            continue
        target = out / lang_dir.name
        if target.exists():
            shutil.rmtree(target)   # a new capture replaces the old one: nothing stale stays behind
        for png in sorted(lang_dir.glob("*.png")):
            if png.stem.endswith(("_compare", "_actual")):
                continue
            folder = target / journey(png.stem)
            folder.mkdir(parents=True, exist_ok=True)
            shutil.copy2(png, folder / png.name)


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
