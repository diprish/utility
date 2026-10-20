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


def test_throttled_limits_flipping():
    from cryptobot.strategy import Throttled

    class Flip(Strategy):
        lookback = 1
        n = 0

        def target_position(self, history):
            self.n += 1
            return float(self.n % 2)  # flips every bar

    t = Throttled(Flip(), band=0.5, cooldown=3)
    out = [t.target_position(candles([1])) for _ in range(12)]
    assert sum(1 for a, b in zip(out, out[1:]) if a != b) < 6  # far fewer than 11 flips
    # exits are never delayed by the cooldown
    t2 = Throttled(BuyAndHold(), band=0.5, cooldown=100)
    assert t2.target_position(candles([1])) == 1.0


def test_vol_target_scales_down():
    from cryptobot.strategy import VolTarget

    calm = candles([100 + 0.1 * i for i in range(50)])
    wild = candles([100 * (1.1 if i % 2 else 0.9) for i in range(50)])
    s = VolTarget(BuyAndHold(), target_vol=0.5, window=30)
    assert s.target_position(calm) == pytest.approx(1.0)
    assert 0 < s.target_position(wild) < 0.5


def test_portfolio_momentum_holds_winner_and_goes_to_cash():
    from cryptobot.portfolio import backtest_portfolio, momentum_rotation

    n = 400
    up = candles([100 * 1.004**i for i in range(n)])
    flat = candles([100.0] * n)
    alloc = momentum_rotation(lookback=20, top_k=1, market="UP", market_sma=50, target_vol=None)
    r = backtest_portfolio({"UP": up, "FLAT": flat}, alloc, rebalance_every=10, warmup=60,
                           fee_rate=0.0, slippage=0.0, benchmark="UP")
    assert r.total_return > 0.5
    assert r.trades >= 1
    # market filter: a falling market means nothing is ever bought
    down = candles([100 * 0.995**i for i in range(n)])
    alloc2 = momentum_rotation(lookback=20, top_k=1, market="DOWN", market_sma=50, target_vol=None)
    r2 = backtest_portfolio({"DOWN": down}, alloc2, rebalance_every=10, warmup=60,
                            fee_rate=0.0, slippage=0.0, benchmark="DOWN")
    assert r2.trades == 0 and r2.total_return == 0


def test_projection_math():
    from cryptobot.projection import net_annual_return, project, weekly_target

    assert project(100, 0, 0.10, 2)[-1] == pytest.approx(121.0)
    assert project(0, 10, 0.0, 1)[-1] == pytest.approx(120.0)
    assert net_annual_return(0.0, 0.01, 12) < 0  # fees alone shrink the balance
    assert weekly_target(10, 0.10, 52) == pytest.approx(10 * 1.1**52)
