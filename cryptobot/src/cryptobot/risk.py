from __future__ import annotations

import os
from dataclasses import dataclass


class Halt(Exception):
    """Raised when a risk limit says trading must stop."""


@dataclass
class RiskLimits:
    max_position_usd: float = 100.0  # cap on value held in the asset
    max_order_usd: float = 50.0  # cap on a single order
    max_daily_loss_usd: float = 25.0  # stop if equity falls this far below the day's start
    kill_switch_file: str = "KILL"  # create this file to stop the bot immediately

    def check_halt(self, equity: float, day_start_equity: float) -> None:
        if os.path.exists(self.kill_switch_file):
            raise Halt(f"kill switch file '{self.kill_switch_file}' present")
        if day_start_equity - equity > self.max_daily_loss_usd:
            raise Halt(
                f"daily loss limit hit: equity {equity:.2f} vs start {day_start_equity:.2f}"
            )

    def clamp_buy(self, want_usd: float, position_usd: float) -> float:
        room = max(0.0, self.max_position_usd - position_usd)
        return max(0.0, min(want_usd, room, self.max_order_usd))

    def clamp_sell(self, want_usd: float) -> float:
        return max(0.0, min(want_usd, self.max_order_usd))
