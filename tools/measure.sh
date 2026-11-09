#!/bin/bash
# Measures Stagebook on a device for one scenario: CPU % every second, memory (PSS, Java, native, graphics) every 5 s,
# threads, and at the end frames (gfxinfo), temperature and battery. Phase D of the device test (CPU and RAM).
#   tools/measure.sh <serial> <scenario> <seconds> [label]
# The scenario is only a name for the files: set it up on the device first (screen, essential mode on or off).
# Output: ~/stagebook-teste/medidas/<device>-<scenario>[-label].csv and a summary (mean, p95, peak) on screen and in .txt.
set -euo pipefail
SERIAL=${1:?serial}; SCENARIO=${2:?scenario}; SECS=${3:?seconds}; LABEL=${4:-}
ADB=(adb -s "$SERIAL")
PKG=br.app.stagebook
DEV=$("${ADB[@]}" shell getprop ro.product.model | tr -d '\r' | tr ' ' '_')
OUT="$HOME/stagebook-teste/medidas"; mkdir -p "$OUT"
BASE="$OUT/$DEV-$SCENARIO${LABEL:+-$LABEL}"
PID=$("${ADB[@]}" shell pidof "$PKG" | tr -d '\r')
[ -n "$PID" ] || { echo "the app is not open on the device"; exit 1; }
"${ADB[@]}" shell dumpsys gfxinfo "$PKG" reset >/dev/null
CORES=$("${ADB[@]}" shell nproc | tr -d '\r')
echo "t,cpu_pct,pss_kb,java_kb,native_kb,graphics_kb,threads" > "$BASE.csv"
pss=""; java=""; nat=""; gfx=""
for ((t = 0; t < SECS; t++)); do
    # CPU of the process, as top shows it (100 = one full core)
    cpu=$("${ADB[@]}" shell top -b -n 1 -p "$PID" 2>/dev/null | awk -v p="$PID" '$1 == p { print $9; exit }' | tr -d '\r')
    if (( t % 5 == 0 )); then
        mem=$("${ADB[@]}" shell dumpsys meminfo "$PKG" | tr -d '\r')
        pss=$(awk '/TOTAL PSS:/ { print $3; exit } /^ *TOTAL / { print $2; exit }' <<<"$mem")
        java=$(awk '/Java Heap:/ { print $3; exit }' <<<"$mem")
        nat=$(awk '/Native Heap:/ { print $3; exit }' <<<"$mem")
        gfx=$(awk '/Graphics:/ { print $2; exit }' <<<"$mem")
    fi
    thr=$("${ADB[@]}" shell "grep Threads /proc/$PID/status" 2>/dev/null | awk '{ print $2 }' | tr -d '\r')
    echo "$t,${cpu:-},${pss:-},${java:-},${nat:-},${gfx:-},${thr:-}" >> "$BASE.csv"
    sleep 1
done
frames=$("${ADB[@]}" shell dumpsys gfxinfo "$PKG" | tr -d '\r' | grep -E "Total frames rendered|Janky frames|50th percentile|90th percentile|95th percentile|99th percentile" | head -6)
temp=$("${ADB[@]}" shell dumpsys battery | tr -d '\r' | awk '/^ *temperature:/ && !t { t = $2 } /^ *level:/ && !l { l = $2 } END { printf "%.1f °C · battery %s%%", t / 10, l }')
python3 - "$BASE.csv" "$CORES" "$DEV" "$SCENARIO${LABEL:+ ($LABEL)}" "$SECS" <<'EOF' > "$BASE.txt"
import csv, statistics, sys
path, cores, dev, scen, secs = sys.argv[1:6]
rows = list(csv.DictReader(open(path)))
def col(name):
    return [float(r[name]) for r in rows if r[name] not in ("", None)]
def stats(v):
    if not v: return "—"
    s = sorted(v); p95 = s[min(len(s) - 1, int(round(0.95 * (len(s) - 1))))]
    return f"mean {statistics.mean(v):.1f} · p95 {p95:.1f} · peak {max(v):.1f}"
print(f"{dev} · {scen} · {secs} s · {cores} cores")
print(f"CPU (% of one core): {stats(col('cpu_pct'))}")
for name, label in (("pss_kb", "Total memory (PSS)"), ("java_kb", "Java"), ("native_kb", "Native"), ("graphics_kb", "Graphics")):
    v = [x / 1024 for x in col(name)]
    print(f"{label} (MB): {stats(v)}")
print(f"Threads: {stats(col('threads'))}")
EOF
{ echo "Frames:"; echo "$frames" | sed 's/^/  /'; echo "Device: $temp"; } >> "$BASE.txt"
cat "$BASE.txt"
echo "data: $BASE.csv"
