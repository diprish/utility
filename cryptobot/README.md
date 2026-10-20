# cryptobot

Coinbase spot trading bot: pluggable strategies, a backtester, and a dry-run mode.
Pure standard library except for live trading (`pip install '.[live]'`).

## Quick start

```sh
pip install -e '.[dev]'
cryptobot fetch BTC-USD --days 365 --out candles.csv     # public data, no API key
cryptobot backtest candles.csv --strategy buy-and-hold   # fees + slippage included
cryptobot run BTC-USD --strategy flat                    # DRY RUN on a paper account
pytest
```

## Writing a strategy

Subclass `Strategy` in `strategy.py`, return the fraction of equity (0..1) to hold,
and register it in `STRATEGIES`:

```python
class MyStrategy(Strategy):
    lookback = 50
    def target_position(self, history):  # oldest first, last candle is closed
        ...
```

The backtester decides on candle *i*'s close and fills at candle *i+1*'s open, so
there is no lookahead. Defaults: 0.6% taker fee, 5 bps slippage.

## Going live (only after paper trading looks sane)

1. Create a Coinbase CDP API key with **View + Trade only**. Never enable Transfer.
2. `export COINBASE_API_KEY_NAME=... COINBASE_API_PRIVATE_KEY="$(cat key.pem)"`
3. `export CRYPTOBOT_CONFIRM_LIVE=yes`, then `cryptobot run BTC-USD --strategy ... --live`

Safety rails (flags on `run`): `--max-position`, `--max-order`, `--max-daily-loss`
(defaults $100 / $50 / $25), plus a kill switch: `touch KILL` in the working
directory halts the bot on the next cycle. Orders are market IOC, one per closed candle.

Not financial advice. Trading can lose money; start with amounts you can afford to lose.
