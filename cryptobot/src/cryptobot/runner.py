from __future__ import annotations

import logging
import time
from typing import Sequence

from .broker import Broker
from .data import fetch_candles
from .models import GRANULARITY_SECONDS, Candle, Fill
from .risk import RiskLimits
from .strategy import Strategy

log = logging.getLogger("cryptobot")


def step(
    broker: Broker,
    strategy: Strategy,
    history: Sequence[Candle],
    price: float,
    limits: RiskLimits,
    day_start_equity: float,
    min_trade_usd: float = 5.0,
) -> Fill | None:
    """One decision cycle: strategy -> target -> risk-clamped order."""
    cash, units = broker.balances()
    position_usd = units * price
    equity = cash + position_usd
    limits.check_halt(equity, day_start_equity)

    target = min(1.0, max(0.0, strategy.target_position(history[-strategy.lookback :])))
    delta = target * equity - position_usd
    log.info("equity=%.2f position=%.2f target=%.0f%% delta=%+.2f", equity, position_usd, target * 100, delta)

    if delta >= min_trade_usd:
        amount = min(limits.clamp_buy(delta, position_usd), cash)
        if amount >= min_trade_usd:
            return broker.market_buy(amount, price)
    elif -delta >= min_trade_usd:
        usd = limits.clamp_sell(-delta)
        if usd >= min_trade_usd:
            return broker.market_sell(min(usd / price, units), price)
    return None


def run_loop(
    broker: Broker,
    strategy: Strategy,
    product: str,
    granularity: str,
    limits: RiskLimits,
) -> None:
    """Act once per closed candle until a risk limit halts the bot."""
    step_s = GRANULARITY_SECONDS[granularity]
    cash, units = broker.balances()
    day_start, day = None, None

    while True:
        now = int(time.time())
        closed_until = now - now % step_s  # open time of the still-forming candle
        candles = fetch_candles(
            product, granularity, closed_until - step_s * (strategy.lookback + 1), closed_until
        )
        if len(candles) >= strategy.lookback:
            price = candles[-1].close
            cash, units = broker.balances()
            equity = cash + units * price
            today = time.strftime("%Y-%m-%d", time.gmtime(now))
            if day != today:
                day, day_start = today, equity
            fill = step(broker, strategy, candles, price, limits, day_start)
            if fill:
                log.info("%s %.8f @ %.2f fee=%.4f", fill.side, fill.units, fill.price, fill.fee)
        else:
            log.warning("only %d candles available, need %d", len(candles), strategy.lookback)
        time.sleep(closed_until + step_s - time.time() + 5)  # 5s after next close
