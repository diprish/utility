from __future__ import annotations

import math
from dataclasses import dataclass, field
from typing import Sequence

from .models import Candle
from .strategy import Strategy


@dataclass
class BacktestResult:
    equity: list[float]
    trades: int
    fees_paid: float
    total_return: float
    buy_hold_return: float
    max_drawdown: float
    sharpe: float
    periods: int = field(default=0)

    def summary(self) -> str:
        return (
            f"periods:       {self.periods}\n"
            f"trades:        {self.trades}\n"
            f"fees paid:     {self.fees_paid:.2f}\n"
            f"total return:  {self.total_return:+.2%}\n"
            f"buy & hold:    {self.buy_hold_return:+.2%}\n"
            f"max drawdown:  {self.max_drawdown:.2%}\n"
            f"sharpe (ann.): {self.sharpe:.2f}"
        )


def risk_stats(equity: Sequence[float], initial_cash: float, periods_per_year: float) -> tuple[float, float]:
    """Return (annualised Sharpe, max drawdown) for an equity curve."""
    rets = [equity[0] / initial_cash - 1] + [
        equity[k] / equity[k - 1] - 1 for k in range(1, len(equity))
    ]
    mean = sum(rets) / len(rets)
    var = sum((r - mean) ** 2 for r in rets) / max(1, len(rets) - 1)
    sharpe = mean / math.sqrt(var) * math.sqrt(periods_per_year) if var > 0 else 0.0

    peak, max_dd = initial_cash, 0.0
    for e in equity:
        peak = max(peak, e)
        max_dd = max(max_dd, (peak - e) / peak)
    return sharpe, max_dd


def backtest(
    candles: Sequence[Candle],
    strategy: Strategy,
    *,
    initial_cash: float = 10_000.0,
    fee_rate: float = 0.006,
    slippage: float = 0.0005,
    min_trade_fraction: float = 0.02,
    periods_per_year: float = 365.0,
) -> BacktestResult:
    """Replay `candles`. The strategy decides on candle i's close and the trade
    fills at candle i+1's open, so it never trades on information it couldn't have.

    Defaults assume a retail Coinbase taker fee (0.6%) plus 5 bps slippage.
    Trades smaller than `min_trade_fraction` of equity are skipped to avoid churn.
    """
    if len(candles) < 2:
        raise ValueError("need at least 2 candles")

    cash, units, fees, trades = initial_cash, 0.0, 0.0, 0
    equity: list[float] = []

    for i in range(len(candles) - 1):
        window = candles[max(0, i + 1 - strategy.lookback) : i + 1]
        target = min(1.0, max(0.0, strategy.target_position(window)))

        nxt = candles[i + 1]
        total = cash + units * nxt.open
        delta = target * total - units * nxt.open  # quote value to buy (+) / sell (-)
        if abs(delta) >= min_trade_fraction * total:
            if delta > 0:
                spend = min(delta, cash)
                fee = spend * fee_rate
                units += (spend - fee) / (nxt.open * (1 + slippage))
                cash -= spend
                fees += fee
                trades += 1
            else:
                sold = min(-delta / nxt.open, units)
                gross = sold * nxt.open * (1 - slippage)
                fee = gross * fee_rate
                units -= sold
                cash += gross - fee
                fees += fee
                trades += 1
        equity.append(cash + units * nxt.close)

    sharpe, max_dd = risk_stats(equity, initial_cash, periods_per_year)

    return BacktestResult(
        equity=equity,
        trades=trades,
        fees_paid=fees,
        total_return=equity[-1] / initial_cash - 1,
        buy_hold_return=candles[-1].close / candles[1].open - 1,
        max_drawdown=max_dd,
        sharpe=sharpe,
        periods=len(equity),
    )
