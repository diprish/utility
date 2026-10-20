"""Per-coin buy & hold stats over a common trailing window, from a folder of daily CSVs.
usage: python scripts/coin_table.py DIR [--days 1095] [--start 100] [--fee 0.012]
"""
from __future__ import annotations

import argparse
import math
import statistics as st
from pathlib import Path

from cryptobot.data import load_csv


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("dir")
    ap.add_argument("--days", type=int, default=1095)
    ap.add_argument("--start", type=float, default=100.0)
    ap.add_argument("--fee", type=float, default=0.012, help="per side")
    a = ap.parse_args()

    print(f"last {a.days} days, {a.start:g} invested at the start and sold at the end, fee {a.fee:.2%} per side\n")
    print(f"{'coin':6s} {'end value':>10s} {'per year':>9s} {'volatility':>11s} {'worst drop':>11s}")
    rows = []
    for p in sorted(Path(a.dir).glob("*-USD.csv")):
        c = load_csv(p)[-a.days:]
        if len(c) < a.days:
            continue
        closes = [x.close for x in c]
        rets = [math.log(b / x) for x, b in zip(closes, closes[1:])]
        vol = st.pstdev(rets) * math.sqrt(365)
        peak, dd = closes[0], 0.0
        for v in closes:
            peak = max(peak, v)
            dd = max(dd, 1 - v / peak)
        end = a.start * (1 - a.fee) * closes[-1] / closes[0] * (1 - a.fee)
        years = a.days / 365
        rows.append((p.stem.split("-")[0], end, (end / a.start) ** (1 / years) - 1, vol, dd))
    for name, end, cagr, vol, dd in sorted(rows, key=lambda r: -r[1]):
        print(f"{name:6s} {end:10.0f} {cagr:9.0%} {vol:11.0%} {dd:11.0%}")


if __name__ == "__main__":
    main()
