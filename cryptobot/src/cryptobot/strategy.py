from __future__ import annotations

import math
from abc import ABC, abstractmethod
from typing import Sequence

from .models import Candle


class Strategy(ABC):
    """Long-only spot strategy.

    `target_position` returns the fraction (0..1) of equity that should be held
    in the asset after seeing `history` (oldest first, last candle is closed).
    """

    #: how many trailing candles the strategy needs; the backtester and the live
    #: runner pass at most this many.
    lookback: int = 1

    @abstractmethod
    def target_position(self, history: Sequence[Candle]) -> float: ...


class Flat(Strategy):
    """Never holds anything. Useful for wiring checks."""

    def target_position(self, history: Sequence[Candle]) -> float:
        return 0.0


class BuyAndHold(Strategy):
    """Fully invested. The benchmark every other strategy must beat."""

    def target_position(self, history: Sequence[Candle]) -> float:
        return 1.0


class SmaCrossover(Strategy):
    """Fully long while the fast simple moving average is above the slow one, else flat."""

    def __init__(self, fast: int = 20, slow: int = 100):
        if not 0 < fast < slow:
            raise ValueError("need 0 < fast < slow")
        self.fast, self.slow = fast, slow
        self.lookback = slow

    def target_position(self, history: Sequence[Candle]) -> float:
        if len(history) < self.slow:
            return 0.0
        closes = [c.close for c in history[-self.slow :]]
        fast_ma = sum(closes[-self.fast :]) / self.fast
        slow_ma = sum(closes) / self.slow
        return 1.0 if fast_ma > slow_ma else 0.0


class VolTarget(Strategy):
    """Scale another strategy's position down when recent volatility exceeds `target_vol`
    (annualised). Never levers up: the position stays within 0..1."""

    def __init__(self, inner: Strategy, target_vol: float = 0.6, window: int = 30,
                 periods_per_year: float = 365.0, step: float = 0.25):
        self.inner, self.target_vol, self.window, self.ppy = inner, target_vol, window, periods_per_year
        self.step = step  # sizes snap down to multiples of this, so tiny vol moves cause no trades
        self.lookback = max(inner.lookback, window + 1)

    def target_position(self, history: Sequence[Candle]) -> float:
        want = self.inner.target_position(history[-self.inner.lookback :])
        if want <= 0 or len(history) <= self.window:
            return 0.0  # flat, or not enough history to size a position
        closes = [c.close for c in history[-self.window - 1 :]]
        rets = [math.log(b / a) for a, b in zip(closes, closes[1:])]
        mean = sum(rets) / len(rets)
        vol = math.sqrt(sum((r - mean) ** 2 for r in rets) / (len(rets) - 1)) * math.sqrt(self.ppy)
        if vol <= 0:
            return want
        scale = min(1.0, self.target_vol / vol)
        if self.step:
            scale = max(self.step, math.floor(scale / self.step) * self.step)
        return want * scale


class Throttled(Strategy):
    """Cut turnover of another strategy.

    - band: ignore target changes smaller than this fraction of equity.
    - cooldown: after any change, don't *increase* the position for this many bars
      (reductions are always allowed, so the strategy can still exit promptly).

    Stateful: keep one instance for the life of a run (the runner and backtester do).
    """

    def __init__(self, inner: Strategy, band: float = 0.25, cooldown: int = 7):
        self.inner, self.band, self.cooldown = inner, band, cooldown
        self.lookback = inner.lookback
        self.current = 0.0
        self.since_change = cooldown

    def target_position(self, history: Sequence[Candle]) -> float:
        want = self.inner.target_position(history)
        self.since_change += 1
        move = want - self.current
        if abs(move) >= self.band and (move < 0 or self.since_change > self.cooldown):
            self.current, self.since_change = want, 0
        return self.current


STRATEGIES: dict[str, type[Strategy]] = {
    "flat": Flat,
    "buy-and-hold": BuyAndHold,
    "sma-crossover": SmaCrossover,
}
