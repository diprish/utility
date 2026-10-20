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


def _strategy(name: str, params: str = ""):
    if name not in STRATEGIES:
        sys.exit(f"unknown strategy '{name}'; choose from {', '.join(STRATEGIES)}")
    kwargs = {k: int(v) for k, v in (kv.split("=") for kv in params.split(",") if kv)}
    return STRATEGIES[name](**kwargs)


def cmd_fetch(a):
    end = int(time.time())
    candles = fetch_candles(a.product, a.granularity, end - a.days * 86400, end)
    save_csv(a.out, candles)
    print(f"saved {len(candles)} candles to {a.out}")


def cmd_backtest(a):
    candles = load_csv(a.csv)
    ppy = 365 * 86400 / GRANULARITY_SECONDS[a.granularity]
    res = backtest(candles, _strategy(a.strategy, a.params), fee_rate=a.fee, periods_per_year=ppy)
    print(res.summary())


def cmd_project(a):
    from .projection import net_annual_return, project, weekly_target

    gross = [float(x) / 100 for x in a.gross.split(",")]
    cols = [(g, net_annual_return(g, a.fee, a.round_trips)) for g in gross]
    print(f"Start {a.start:,.2f} {a.currency}, +{a.deposit:,.2f}/month, fee {a.fee:.2%} per side, "
          f"{a.round_trips:g} round trips/year\n")
    print(f"{'gross/yr':>9s} {'net/yr':>8s} " + " ".join(f"{'year ' + str(y):>12s}" for y in range(1, a.years + 1)))
    for g, net in cols:
        row = project(a.start, a.deposit, net, a.years)
        print(f"{g:9.0%} {net:8.1%} " + " ".join(f"{v:12,.2f}" for v in row))
    deposited = a.start + a.deposit * 12 * a.years
    print(f"\n(total put in over {a.years} years: {deposited:,.2f} {a.currency})")
    w = weekly_target(a.start, 0.10, 52)
    print(f"For comparison, 10%/week for one year would turn {a.start:,.2f} into {w:,.2f} "
          f"({w / a.start:,.0f}x); after two years {weekly_target(a.start, 0.10, 104):,.0f}. "
          f"No strategy has delivered this.")


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
        run_loop(broker, _strategy(a.strategy, a.params), a.product, a.granularity, limits)
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
    b.add_argument("--params", default="", help="integer strategy params, e.g. fast=20,slow=100")
    b.add_argument("--granularity", **gran)
    b.add_argument("--fee", type=float, default=0.006)
    b.set_defaults(fn=cmd_backtest)

    j = sub.add_parser("project", help="what a given annual return does to your balance, after fees")
    j.add_argument("--start", type=float, default=10.0)
    j.add_argument("--deposit", type=float, default=0.0, help="added every month")
    j.add_argument("--years", type=int, default=5)
    j.add_argument("--gross", default="0,10,25,50,100", help="gross annual returns in %%, comma separated")
    j.add_argument("--fee", type=float, default=0.012, help="fee per side, check your Coinbase tier")
    j.add_argument("--round-trips", type=float, default=12, help="buy+sell pairs per year")
    j.add_argument("--currency", default="CAD")
    j.set_defaults(fn=cmd_project)

    r = sub.add_parser("run", help="trade in real time (dry-run unless --live)")
    r.add_argument("product")
    r.add_argument("--strategy", default="flat")
    r.add_argument("--params", default="", help="integer strategy params, e.g. fast=20,slow=100")
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
