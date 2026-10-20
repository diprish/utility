import pytest

from cryptobot.backtest import backtest
from cryptobot.broker import PaperBroker
from cryptobot.models import Candle
from cryptobot.risk import Halt, RiskLimits
from cryptobot.runner import step
from cryptobot.strategy import BuyAndHold, Flat, Strategy


def candles(prices):
    return [Candle(i * 86400, p, p, p, p, 1.0) for i, p in enumerate(prices)]


def test_flat_never_trades():
    r = backtest(candles([100, 110, 90, 120]), Flat())
    assert r.trades == 0 and r.total_return == 0 and r.fees_paid == 0


def test_buy_and_hold_pays_fees_and_tracks_market():
    r = backtest(candles([100, 100, 110, 121]), BuyAndHold(), fee_rate=0.0, slippage=0.0)
    assert r.trades == 1
    assert r.total_return == pytest.approx(0.21)
    assert r.buy_hold_return == pytest.approx(0.21)


def test_fees_reduce_return():
    free = backtest(candles([100, 100, 120]), BuyAndHold(), fee_rate=0, slippage=0)
    paid = backtest(candles([100, 100, 120]), BuyAndHold(), fee_rate=0.006, slippage=0)
    assert paid.total_return < free.total_return and paid.fees_paid > 0


def test_no_lookahead():
    seen = []

    class Spy(Strategy):
        lookback = 1000

        def target_position(self, history):
            seen.append(len(history))
            return 0.0

    backtest(candles([1, 2, 3, 4]), Spy())
    assert seen == [1, 2, 3]  # never sees the candle it trades on


def test_max_drawdown():
    r = backtest(candles([100, 100, 50, 100]), BuyAndHold(), fee_rate=0, slippage=0)
    assert r.max_drawdown == pytest.approx(0.5)


def test_step_respects_risk_caps(tmp_path):
    limits = RiskLimits(50, 20, 1000, str(tmp_path / "KILL"))
    broker = PaperBroker(cash=1000)
    fill = step(broker, BuyAndHold(), candles([100]), 100.0, limits, 1000)
    assert fill.side == "BUY"
    assert broker.cash == pytest.approx(980)  # clamped to max_order_usd


def test_step_position_cap(tmp_path):
    limits = RiskLimits(max_position_usd=30, max_order_usd=100, max_daily_loss_usd=1000,
                        kill_switch_file=str(tmp_path / "KILL"))
    broker = PaperBroker(cash=1000)
    step(broker, BuyAndHold(), candles([100]), 100.0, limits, 1000)
    assert step(broker, BuyAndHold(), candles([100]), 100.0, limits, 1000) is None


def test_step_sells_to_flat(tmp_path):
    limits = RiskLimits(1000, 1000, 1000, str(tmp_path / "KILL"))
    broker = PaperBroker(cash=0)
    broker.units = 1.0
    fill = step(broker, Flat(), candles([100]), 100.0, limits, 100)
    assert fill.side == "SELL" and broker.units == pytest.approx(0, abs=1e-9)


def test_kill_switch_and_daily_loss(tmp_path):
    kill = tmp_path / "KILL"
    limits = RiskLimits(kill_switch_file=str(kill), max_daily_loss_usd=25)
    broker = PaperBroker(cash=1000)
    with pytest.raises(Halt):
        step(broker, Flat(), candles([100]), 100.0, limits, day_start_equity=1100)
    kill.write_text("")
    with pytest.raises(Halt):
        step(broker, Flat(), candles([100]), 100.0, limits, day_start_equity=1000)


def test_sma_crossover_signals():
    from cryptobot.strategy import SmaCrossover

    s = SmaCrossover(fast=2, slow=4)
    assert s.target_position(candles([1, 1, 1])) == 0.0  # not enough history
    assert s.target_position(candles([1, 2, 3, 4])) == 1.0  # uptrend
    assert s.target_position(candles([4, 3, 2, 1])) == 0.0  # downtrend
    with pytest.raises(ValueError):
        SmaCrossover(fast=5, slow=5)
