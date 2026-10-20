"""Multi-asset, low-turnover backtester plus a monthly momentum-rotation allocator."""
from __future__ import annotations

import math
from typing import Callable, Mapping, Sequence

from .backtest import BacktestResult, risk_stats
from .models import Candle

# allocator(history_by_asset, i) -> {asset: weight}; weights >= 0, sum <= 1, rest is cash.
# history_by_asset[a] is the list of closes up to and including index i (nan if no data yet).
Allocator = Callable[[Mapping[str, Sequence[float]]], dict[str, float]]


def align(candles: Mapping[str, Sequence[Candle]]) -> tuple[list[int], dict[str, list[float]], dict[str, list[float]]]:
    """Put every asset on the union timeline. Returns (timestamps, opens, closes); gaps are nan."""
    stamps = sorted({c.start for cs in candles.values() for c in cs})
    opens: dict[str, list[float]] = {}
    closes: dict[str, list[float]] = {}
    for a, cs in candles.items():
        by_ts = {c.start: c for c in cs}
        opens[a] = [by_ts[t].open if t in by_ts else math.nan for t in stamps]
        closes[a] = [by_ts[t].close if t in by_ts else math.nan for t in stamps]
    return stamps, opens, closes


def backtest_portfolio(
    candles: Mapping[str, Sequence[Candle]],
    allocator: Allocator,
    *,
    rebalance_every: int = 30,
    warmup: int = 200,
    initial_cash: float = 10_000.0,
    fee_rate: float = 0.006,
    slippage: float = 0.0005,
    min_trade_weight: float = 0.05,
    benchmark: str = "BTC-USD",
    periods_per_year: float = 365.0,
) -> BacktestResult:
    """Every `rebalance_every` candles ask `allocator` for target weights and trade at the
    next open. Trades smaller than `min_trade_weight` of equity are skipped, so assets that
    stay selected are not churned back to exact weights."""
    stamps, opens, closes = align(candles)
    assets = list(candles)
    cash, units = initial_cash, {a: 0.0 for a in assets}
    fees, trades = 0.0, 0
    equity: list[float] = []
    last_close = {a: math.nan for a in assets}

    for i in range(len(stamps) - 1):
        for a in assets:
            if not math.isnan(closes[a][i]):
                last_close[a] = closes[a][i]
        nxt = i + 1

        if i >= warmup and (i - warmup) % rebalance_every == 0:
            target = allocator({a: closes[a][: i + 1] for a in assets})
            px = {a: opens[a][nxt] for a in assets}
            total = cash + sum(units[a] * px[a] for a in assets if units[a] > 0 and not math.isnan(px[a]))
            delta = {}
            for a in assets:
                if math.isnan(px[a]):
                    continue
                delta[a] = target.get(a, 0.0) * total - units[a] * px[a]
            for a, d in delta.items():  # sells first so cash is available for buys
                exiting = target.get(a, 0.0) == 0.0 and units[a] > 0
                if d < 0 and (-d >= min_trade_weight * total or exiting):
                    sold = min(-d / px[a], units[a])
                    gross = sold * px[a] * (1 - slippage)
                    fee = gross * fee_rate
                    units[a] -= sold
                    cash += gross - fee
                    fees += fee
                    trades += 1
            for a, d in delta.items():
                if d > 0 and d >= min_trade_weight * total and cash > 0:
                    spend = min(d, cash)
                    fee = spend * fee_rate
                    units[a] += (spend - fee) / (px[a] * (1 + slippage))
                    cash -= spend
                    fees += fee
                    trades += 1

        for a in assets:
            if not math.isnan(closes[a][nxt]):
                last_close[a] = closes[a][nxt]
        equity.append(cash + sum(units[a] * last_close[a] for a in assets if units[a] > 0))

    equity = equity[warmup:]  # the warmup period is all cash; don't let it dilute the stats
    sharpe, max_dd = risk_stats(equity, initial_cash, periods_per_year)
    bench = closes[benchmark]
    first = next(i for i in range(len(bench)) if not math.isnan(bench[i]) and i >= warmup + 1)
    return BacktestResult(
        equity=equity,
        trades=trades,
        fees_paid=fees,
        total_return=equity[-1] / initial_cash - 1,
        buy_hold_return=bench[-1] / bench[first] - 1,
        max_drawdown=max_dd,
        sharpe=sharpe,
        periods=len(equity),
    )


def momentum_rotation(
    lookback: int = 90,
    top_k: int = 3,
    market: str = "BTC-USD",
    market_sma: int = 200,
    target_vol: float | None = 0.6,
    vol_window: int = 30,
    periods_per_year: float = 365.0,
    rank_buffer: int = 2,
    scale_step: float = 0.25,
) -> Allocator:
    """Hold the `top_k` assets with the best trailing return, but only those with a positive
    return, each in a 1/top_k slot (empty slots stay in cash). Everything goes to cash while
    `market` is below its `market_sma`-day average. Optionally scale the whole book down to
    `target_vol` annualised volatility.

    To save fees, a holding is kept while it stays within the top `top_k + rank_buffer` and
    its momentum is positive, and the volatility scale snaps down to multiples of `scale_step`.
    """
    held: list[str] = []

    def allocate(hist: Mapping[str, Sequence[float]]) -> dict[str, float]:
        m = hist[market]
        if len(m) < market_sma or math.isnan(m[-market_sma]) or m[-1] <= sum(m[-market_sma:]) / market_sma:
            held.clear()
            return {}
        scores = {}
        for a, h in hist.items():
            if len(h) > lookback and not math.isnan(h[-lookback - 1]) and not math.isnan(h[-1]):
                r = h[-1] / h[-lookback - 1] - 1
                if r > 0:
                    scores[a] = r
        ranked = sorted(scores, key=scores.get, reverse=True)
        keep = [a for a in held if a in ranked[: top_k + rank_buffer]][:top_k]
        picks = keep + [a for a in ranked if a not in keep][: top_k - len(keep)]
        held[:] = picks
        weights = {a: 1.0 / top_k for a in picks}
        if target_vol and weights:
            # realised vol of the chosen basket over the recent window
            series = []
            for t in range(-vol_window, 0):
                series.append(sum(w * math.log(hist[a][t] / hist[a][t - 1]) for a, w in weights.items()
                                  if not math.isnan(hist[a][t - 1])))
            mean = sum(series) / len(series)
            vol = math.sqrt(sum((x - mean) ** 2 for x in series) / (len(series) - 1)) * math.sqrt(periods_per_year)
            scale = min(1.0, target_vol / vol) if vol > 0 else 1.0
            if scale_step:
                scale = max(scale_step, math.floor(scale / scale_step) * scale_step)
            weights = {a: w * scale for a, w in weights.items()}
        return weights

    return allocate
