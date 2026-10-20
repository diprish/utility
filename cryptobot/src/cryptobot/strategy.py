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


STRATEGIES: dict[str, type[Strategy]] = {"flat": Flat, "buy-and-hold": BuyAndHold}
