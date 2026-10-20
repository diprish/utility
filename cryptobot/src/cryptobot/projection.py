from __future__ import annotations


def net_annual_return(gross: float, fee_per_side: float, round_trips_per_year: float) -> float:
    """Annual return after fees: each round trip costs two sides of fee, compounding."""
    return (1 + gross) * (1 - fee_per_side) ** (2 * round_trips_per_year) - 1


def project(start: float, monthly_deposit: float, annual_return: float, years: int) -> list[float]:
    """Balance at the end of each year, compounding monthly, deposit added at month end."""
    monthly = (1 + annual_return) ** (1 / 12) - 1 if annual_return > -1 else -1.0
    balance, out = start, []
    for _ in range(years):
        for _ in range(12):
            balance = balance * (1 + monthly) + monthly_deposit
        out.append(balance)
    return out


def weekly_target(start: float, weekly_return: float, weeks: int) -> float:
    return start * (1 + weekly_return) ** weeks
