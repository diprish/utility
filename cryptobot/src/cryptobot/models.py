from __future__ import annotations

from dataclasses import dataclass

GRANULARITY_SECONDS = {
    "ONE_MINUTE": 60,
    "FIVE_MINUTE": 300,
    "FIFTEEN_MINUTE": 900,
    "THIRTY_MINUTE": 1800,
    "ONE_HOUR": 3600,
    "TWO_HOUR": 7200,
    "SIX_HOUR": 21600,
    "ONE_DAY": 86400,
}


@dataclass(frozen=True)
class Candle:
    start: int  # unix seconds, candle open time
    open: float
    high: float
    low: float
    close: float
    volume: float


@dataclass(frozen=True)
class Fill:
    side: str  # "BUY" or "SELL"
    units: float  # base currency amount
    price: float
    fee: float  # quote currency
