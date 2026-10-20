from __future__ import annotations

import argparse
import logging
import os
import sys
import time

from .backtest import backtest
from .broker import CoinbaseBroker, PaperBroker
from .data import fetch_candles, load_csv, save_csv
from .models import GRANULARITY_SECONDS
from .risk import Halt, RiskLimits
from .runner import run_loop
from .strategy import STRATEGIES


def _strategy(name: str):
    if name not in STRATEGIES:
        sys.exit(f"unknown strategy '{name}'; choose from {', '.join(STRATEGIES)}")
    return STRATEGIES[name]()


def cmd_fetch(a):
    end = int(time.time())
    candles = fetch_candles(a.product, a.granularity, end - a.days * 86400, end)
    save_csv(a.out, candles)
    print(f"saved {len(candles)} candles to {a.out}")


def cmd_backtest(a):
    candles = load_csv(a.csv)
    ppy = 365 * 86400 / GRANULARITY_SECONDS[a.granularity]
    res = backtest(candles, _strategy(a.strategy), fee_rate=a.fee, periods_per_year=ppy)
    print(res.summary())


def cmd_run(a):
    logging.basicConfig(level=logging.INFO, format="%(asctime)s %(message)s")
    limits = RiskLimits(a.max_position, a.max_order, a.max_daily_loss)
    if a.live:
        if os.environ.get("CRYPTOBOT_CONFIRM_LIVE") != "yes":
            sys.exit("live trading needs CRYPTOBOT_CONFIRM_LIVE=yes in the environment")
        broker = CoinbaseBroker(a.product)
        print(f"LIVE trading {a.product} with limits {limits}")
    else:
        broker = PaperBroker(cash=a.paper_cash)
        print(f"DRY RUN (paper account, no orders sent) on {a.product}")
    try:
        run_loop(broker, _strategy(a.strategy), a.product, a.granularity, limits)
    except Halt as h:
        sys.exit(f"HALTED: {h}")
    except KeyboardInterrupt:
        pass


def main(argv=None):
    p = argparse.ArgumentParser(prog="cryptobot")
    sub = p.add_subparsers(dest="cmd", required=True)
    gran = dict(choices=list(GRANULARITY_SECONDS), default="ONE_DAY")

    f = sub.add_parser("fetch", help="download historical candles to CSV")
    f.add_argument("product", help="e.g. BTC-USD")
    f.add_argument("--granularity", **gran)
    f.add_argument("--days", type=int, default=365)
    f.add_argument("--out", default="candles.csv")
    f.set_defaults(fn=cmd_fetch)

    b = sub.add_parser("backtest", help="replay a strategy over a CSV")
    b.add_argument("csv")
    b.add_argument("--strategy", default="buy-and-hold")
    b.add_argument("--granularity", **gran)
    b.add_argument("--fee", type=float, default=0.006)
    b.set_defaults(fn=cmd_backtest)

    r = sub.add_parser("run", help="trade in real time (dry-run unless --live)")
    r.add_argument("product")
    r.add_argument("--strategy", default="flat")
    r.add_argument("--granularity", **gran)
    r.add_argument("--live", action="store_true", help="send real orders to Coinbase")
    r.add_argument("--paper-cash", type=float, default=1000.0)
    r.add_argument("--max-position", type=float, default=100.0)
    r.add_argument("--max-order", type=float, default=50.0)
    r.add_argument("--max-daily-loss", type=float, default=25.0)
    r.set_defaults(fn=cmd_run)

    a = p.parse_args(argv)
    a.fn(a)


if __name__ == "__main__":
    main()
