from __future__ import annotations

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


STRATEGIES: dict[str, type[Strategy]] = {
    "flat": Flat,
    "buy-and-hold": BuyAndHold,
    "sma-crossover": SmaCrossover,
}
