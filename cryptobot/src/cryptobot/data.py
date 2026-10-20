from __future__ import annotations

import csv
import json
import time
import urllib.parse
import urllib.request
from pathlib import Path

from .models import GRANULARITY_SECONDS, Candle

PUBLIC_CANDLES_URL = "https://api.coinbase.com/api/v3/brokerage/market/products/{product}/candles"
MAX_CANDLES_PER_REQUEST = 300


def _parse(raw: dict) -> Candle:
    return Candle(
        start=int(raw["start"]),
        open=float(raw["open"]),
        high=float(raw["high"]),
        low=float(raw["low"]),
        close=float(raw["close"]),
        volume=float(raw["volume"]),
    )


def fetch_candles(product: str, granularity: str, start: int, end: int) -> list[Candle]:
    """Fetch candles from Coinbase's public market-data endpoint (no API key needed).

    Returns oldest-first, de-duplicated, covering [start, end).
    """
    step = GRANULARITY_SECONDS[granularity]
    out: dict[int, Candle] = {}
    window = step * MAX_CANDLES_PER_REQUEST
    cursor = start
    while cursor < end:
        chunk_end = min(cursor + window, end)
        query = urllib.parse.urlencode(
            {"start": cursor, "end": chunk_end, "granularity": granularity}
        )
        url = PUBLIC_CANDLES_URL.format(product=product) + "?" + query
        req = urllib.request.Request(url, headers={"User-Agent": "cryptobot/0.1"})
        with urllib.request.urlopen(req, timeout=30) as resp:
            payload = json.load(resp)
        for raw in payload.get("candles", []):
            c = _parse(raw)
            out[c.start] = c
        cursor = chunk_end
        time.sleep(0.2)  # stay well under the public rate limit
    return [out[k] for k in sorted(out)]


def save_csv(path: str | Path, candles: list[Candle]) -> None:
    with open(path, "w", newline="") as f:
        w = csv.writer(f)
        w.writerow(["start", "open", "high", "low", "close", "volume"])
        for c in candles:
            w.writerow([c.start, c.open, c.high, c.low, c.close, c.volume])


def load_csv(path: str | Path) -> list[Candle]:
    with open(path, newline="") as f:
        return [_parse(row) for row in csv.DictReader(f)]
