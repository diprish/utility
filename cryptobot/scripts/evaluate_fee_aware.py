"""Fee-aware strategy study on a folder of daily candle CSVs named like BTC-USD.csv.

A) one coin at a time: trend filter -> + vol targeting -> + throttling, vs buy & hold
B) monthly momentum rotation across all coins, vs BTC and an equal-weight basket

Caveat: the coins are today's survivors, which flatters anything that holds them.
usage: python scripts/evaluate_fee_aware.py DIR [--fee 0.006] [--slippage 0.0005]
"""
from __future__ import annotations

import argparse
import itertools
import math
import statistics as st
from pathlib import Path

from cryptobot.backtest import backtest
from cryptobot.data import load_csv
from cryptobot.portfolio import backtest_portfolio, momentum_rotation
from cryptobot.strategy import BuyAndHold, SmaCrossover, Throttled, VolTarget

DAY = 86400


def single_asset_configs(fast, slow):
    return {
        "buy & hold": lambda: BuyAndHold(),
        f"SMA {fast}/{slow}": lambda: SmaCrossover(fast, slow),
        "  + throttle": lambda: Throttled(SmaCrossover(fast, slow), band=0.25, cooldown=7),
        "  + vol target + throttle": lambda: Throttled(VolTarget(SmaCrossover(fast, slow), 0.6), band=0.25, cooldown=7),
    }


def part_a(data, fee, slip):
    for fast, slow in ((20, 100), (50, 200)):
        cfgs = single_asset_configs(fast, slow)
        res = {n: {} for n in cfgs}
        for coin, candles in data.items():
            for n, make in cfgs.items():
                res[n][coin] = backtest(candles, make(), fee_rate=fee, slippage=slip)
        print(f"\n=== A) single coin, trend {fast}/{slow}, {len(data)} coins (median across coins) ===")
        print(f"{'':28s} {'return':>8s} {'sharpe':>7s} {'maxDD':>6s} {'trades':>7s} {'fees%':>6s} {'beats B&H sharpe':>17s}")
        base = res["buy & hold"]
        for n, r in res.items():
            med = lambda f: st.median(f(x) for x in r.values())
            beats = sum(r[c].sharpe > base[c].sharpe for c in data)
            print(f"{n:28s} {med(lambda x: x.total_return):+8.0%} {med(lambda x: x.sharpe):7.2f} "
                  f"{med(lambda x: x.max_drawdown):6.0%} {med(lambda x: x.trades):7.0f} "
                  f"{med(lambda x: x.fees_paid / 100):6.1f} {beats:>13d}/{len(data)}")


def slice_from(data, start_ts, warmup):
    cut = start_ts - warmup * DAY
    return {a: [c for c in cs if c.start >= cut] for a, cs in data.items() if any(c.start >= cut for c in cs)}


def equal_weight(hist):
    avail = [a for a, h in hist.items() if not math.isnan(h[-1])]
    return {a: 1 / len(avail) for a in avail}


def part_b(data, fee, slip):
    warm = 200
    kw = dict(fee_rate=fee, slippage=slip, warmup=warm)
    start = min(c[0].start for c in data.values())
    end = max(c[-1].start for c in data.values())
    mid = start + (end - start) * 6 // 10
    periods = {"full": start, "after 60% mark": mid}

    grid = list(itertools.product((30, 60, 90, 180), (2, 3, 5), (14, 30)))
    print(f"\n=== B) multi-coin rotation ({len(data)} coins, fees {fee:.2%} + slippage {slip:.2%}) ===")
    for label, t0 in periods.items():
        d = data if label == "full" else slice_from(data, t0, warm)
        btc = backtest_portfolio(d, lambda h: {"BTC-USD": 1.0}, rebalance_every=10_000, **kw)
        ew = backtest_portfolio(d, equal_weight, rebalance_every=30, **kw)
        print(f"\n-- {label}: {len(d)} coins, {btc.periods} days --")
        print(f"{'':44s} {'return':>8s} {'sharpe':>7s} {'maxDD':>6s} {'trades':>7s} {'fees $':>8s}")
        row = lambda n, r: print(f"{n:44s} {r.total_return:+8.0%} {r.sharpe:7.2f} {r.max_drawdown:6.0%} {r.trades:7d} {r.fees_paid:8.0f}")
        row("BTC buy & hold", btc)
        row("equal-weight basket, monthly", ew)
        naive = backtest_portfolio(d, momentum_rotation(90, 3, rank_buffer=0, scale_step=0), rebalance_every=30, **kw)
        row("rotation 90d top 3, no fee-saving tricks", naive)
        default = backtest_portfolio(d, momentum_rotation(90, 3), rebalance_every=30, **kw)
        row("rotation 90d top 3, rank buffer + vol steps", default)
        novol = backtest_portfolio(d, momentum_rotation(90, 3, target_vol=None), rebalance_every=30, **kw)
        row("  same, no vol targeting", novol)
        runs = [backtest_portfolio(d, momentum_rotation(lb, k), rebalance_every=reb, **kw) for lb, k, reb in grid]
        print(f"grid of {len(grid)} settings: beat BTC sharpe {sum(r.sharpe > btc.sharpe for r in runs)}, "
              f"beat BTC return {sum(r.total_return > btc.total_return for r in runs)}, "
              f"lower drawdown than BTC {sum(r.max_drawdown < btc.max_drawdown for r in runs)}; "
              f"median sharpe {st.median(r.sharpe for r in runs):.2f}, median maxDD {st.median(r.max_drawdown for r in runs):.0%}")

    # honest out-of-sample: pick the setting on the first 60%, report it on the rest
    train_end = mid
    train = {a: [c for c in cs if c.start < train_end] for a, cs in data.items()}
    train = {a: cs for a, cs in train.items() if len(cs) > warm + 50}
    scores = [(backtest_portfolio(train, momentum_rotation(lb, k), rebalance_every=reb, **kw).sharpe, (lb, k, reb))
              for lb, k, reb in grid]
    best = max(scores)[1]
    test = slice_from(data, mid, warm)
    r = backtest_portfolio(test, momentum_rotation(best[0], best[1]), rebalance_every=best[2], **kw)
    print(f"\nwalk-forward: best on first 60% = lookback {best[0]}d, top {best[1]}, rebalance {best[2]}d "
          f"(train sharpe {max(scores)[0]:.2f})")
    print(f"  out-of-sample: return {r.total_return:+.0%}, sharpe {r.sharpe:.2f}, maxDD {r.max_drawdown:.0%}, trades {r.trades}")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("dir")
    ap.add_argument("--fee", type=float, default=0.006)
    ap.add_argument("--slippage", type=float, default=0.0005)
    a = ap.parse_args()
    data = {p.stem: load_csv(p) for p in sorted(Path(a.dir).glob("*-USD.csv"))}
    print(f"{len(data)} coins: " + ", ".join(f"{k.split('-')[0]}({len(v)}d)" for k, v in data.items()))
    part_a(data, a.fee, a.slippage)
    part_b(data, a.fee, a.slippage)


if __name__ == "__main__":
    main()
