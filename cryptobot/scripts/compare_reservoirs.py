"""Walk-forward comparison: connectome reservoir vs controls vs plain linear vs buy & hold.

Trains the readout on the first 60% of candles, trades the last 40%, over several seeds.
usage: python scripts/compare_reservoirs.py visual_reservoir.npz candles.csv [--seeds 5]
"""
from __future__ import annotations

import argparse
import statistics as st

import numpy as np

from cryptobot.backtest import backtest
from cryptobot.data import load_csv
from cryptobot.reservoir import (Reservoir, ReservoirStrategy, design, features, fit_ridge,
                                 gaussian_wiring, load_wiring, next_return, shuffled_wiring)
from cryptobot.strategy import BuyAndHold, Strategy

WASHOUT = 100
ALPHAS = (1.0, 10.0, 100.0, 1000.0)


def pick_alpha(X, y):
    """Choose ridge strength on a time-ordered 70/30 split of the training data only."""
    cut = int(len(X) * 0.7)
    best = min(ALPHAS, key=lambda a: np.mean((X[cut:] @ fit_ridge(X[:cut], y[:cut], a) - y[cut:]) ** 2))
    return best


def evaluate(make_states, candles, split, label):
    """make_states(U) -> state matrix; returns (total_return, sharpe, max_dd, trades) on the test slice."""
    U, y = features(candles), next_return(candles)
    X = design(make_states(U))
    tr = slice(WASHOUT, split - 1)
    y_tr = y[tr] - y[tr].mean()  # demean, else the model just learns the drift and stays long
    alpha = pick_alpha(X[tr], y_tr)
    w = fit_ridge(X[tr], y_tr, alpha)
    pos = (X @ w > 0).astype(float)
    return w, pos


class Precomputed(Strategy):
    """Replays precomputed positions so every model goes through the same backtester."""

    def __init__(self, pos, offset):
        self.pos, self.offset, self.lookback = pos, offset, 1
        self.i = -1

    def target_position(self, history):
        self.i += 1
        return self.pos[self.offset + self.i]


def run(pos, candles, split, fee=0.006, slip=0.0005):
    test = candles[split:]
    r = backtest(test, Precomputed(pos, split), fee_rate=fee, slippage=slip)
    return r


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("wiring")
    ap.add_argument("csv")
    ap.add_argument("--seeds", type=int, default=5)
    ap.add_argument("--fee", type=float, default=0.006)
    ap.add_argument("--slippage", type=float, default=0.0005)
    a = ap.parse_args()

    candles = load_csv(a.csv)
    split = int(len(candles) * 0.6)
    W, inputs = load_wiring(a.wiring)
    n_in = len(inputs)
    print(f"{a.csv}: {len(candles)} candles, test = last {len(candles) - split}; "
          f"reservoir N={W.shape[0]}, {W.nnz} edges, {n_in} input nodes\n")

    bh = backtest(candles[split:], BuyAndHold(), fee_rate=a.fee, slippage=a.slippage)
    rows = {"buy & hold": [(bh.total_return, bh.sharpe, bh.max_drawdown, bh.trades)]}

    def add(name, pos):
        r = run(pos, candles, split, a.fee, a.slippage)
        rows.setdefault(name, []).append((r.total_return, r.sharpe, r.max_drawdown, r.trades))

    # plain linear model on the raw (unreserved) features, for reference
    _, pos = evaluate(lambda U: U, candles, split, "linear")
    add("linear on raw inputs", pos)

    for seed in range(a.seeds):
        rng = np.random.default_rng(1000 + seed)
        rand_in = rng.choice(W.shape[0], n_in, replace=False)
        variants = {
            "connectome (photoreceptor inputs)": (W, inputs),
            "connectome (random inputs)": (W, rand_in),
            "shuffled wiring": (shuffled_wiring(W, rng), rand_in),
            "gaussian random": (gaussian_wiring(W, rng), rand_in),
        }
        for name, (Wv, inp) in variants.items():
            res = Reservoir(Wv, inp, seed=seed)
            _, pos = evaluate(res.run, candles, split, name)
            add(name, pos)

    print(f"{'model':36s} {'return':>9s} {'sharpe':>7s} {'maxDD':>6s} {'trades':>7s}")
    for name, vals in rows.items():
        m = [st.mean(v[i] for v in vals) for i in range(4)]
        spread = f" ±{st.pstdev(v[0] for v in vals):.0%}" if len(vals) > 1 else ""
        print(f"{name:36s} {m[0]:+9.1%} {m[1]:7.2f} {m[2]:6.0%} {m[3]:7.0f}{spread}")


if __name__ == "__main__":
    main()
