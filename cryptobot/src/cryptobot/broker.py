from __future__ import annotations

import json
import os
import secrets
import time
import urllib.error
import urllib.request
import uuid
from abc import ABC, abstractmethod

from .models import Fill

API_HOST = "api.coinbase.com"


class Broker(ABC):
    @abstractmethod
    def balances(self) -> tuple[float, float]:
        """Return (quote cash, base units)."""

    @abstractmethod
    def market_buy(self, quote_amount: float, price_hint: float) -> Fill | None: ...

    @abstractmethod
    def market_sell(self, base_units: float, price_hint: float) -> Fill | None: ...


class PaperBroker(Broker):
    """Simulated account. Never talks to Coinbase."""

    def __init__(self, cash: float = 1_000.0, fee_rate: float = 0.006, slippage: float = 0.0005):
        self.cash, self.units = cash, 0.0
        self.fee_rate, self.slippage = fee_rate, slippage

    def balances(self) -> tuple[float, float]:
        return self.cash, self.units

    def market_buy(self, quote_amount: float, price_hint: float) -> Fill | None:
        spend = min(quote_amount, self.cash)
        if spend <= 0:
            return None
        price = price_hint * (1 + self.slippage)
        fee = spend * self.fee_rate
        units = (spend - fee) / price
        self.cash -= spend
        self.units += units
        return Fill("BUY", units, price, fee)

    def market_sell(self, base_units: float, price_hint: float) -> Fill | None:
        sold = min(base_units, self.units)
        if sold <= 0:
            return None
        price = price_hint * (1 - self.slippage)
        gross = sold * price
        fee = gross * self.fee_rate
        self.units -= sold
        self.cash += gross - fee
        return Fill("SELL", sold, price, fee)


class CoinbaseBroker(Broker):
    """Live broker using the Coinbase Advanced Trade API with CDP API keys.

    Needs `pip install '.[live]'` and two env vars:
      COINBASE_API_KEY_NAME     e.g. organizations/.../apiKeys/...
      COINBASE_API_PRIVATE_KEY  the PEM EC private key (literal "\\n" is accepted)

    Create the key with only View + Trade permissions. Never enable Transfer.
    """

    def __init__(self, product: str):
        self.product = product
        self.base, self.quote = product.split("-")
        self.key_name = os.environ["COINBASE_API_KEY_NAME"]
        self.private_key = os.environ["COINBASE_API_PRIVATE_KEY"].replace("\\n", "\n")

    def _jwt(self, method: str, path: str) -> str:
        import jwt  # PyJWT, from the `live` extra

        now = int(time.time())
        return jwt.encode(
            {
                "sub": self.key_name,
                "iss": "cdp",
                "nbf": now,
                "exp": now + 120,
                "uri": f"{method} {API_HOST}{path}",
            },
            self.private_key,
            algorithm="ES256",
            headers={"kid": self.key_name, "nonce": secrets.token_hex(16)},
        )

    def _request(self, method: str, path: str, body: dict | None = None) -> dict:
        # The JWT "uri" claim must exclude the query string.
        token = self._jwt(method, path.split("?")[0])
        req = urllib.request.Request(
            f"https://{API_HOST}{path}",
            method=method,
            data=json.dumps(body).encode() if body is not None else None,
            headers={"Authorization": f"Bearer {token}", "Content-Type": "application/json"},
        )
        try:
            with urllib.request.urlopen(req, timeout=30) as resp:
                return json.load(resp)
        except urllib.error.HTTPError as e:
            raise RuntimeError(f"Coinbase {method} {path} -> {e.code}: {e.read().decode()}") from e

    def balances(self) -> tuple[float, float]:
        data = self._request("GET", "/api/v3/brokerage/accounts?limit=250")
        held = {a["currency"]: float(a["available_balance"]["value"]) for a in data["accounts"]}
        return held.get(self.quote, 0.0), held.get(self.base, 0.0)

    def _place(self, side: str, config: dict) -> str:
        data = self._request(
            "POST",
            "/api/v3/brokerage/orders",
            {
                "client_order_id": str(uuid.uuid4()),
                "product_id": self.product,
                "side": side,
                "order_configuration": {"market_market_ioc": config},
            },
        )
        if not data.get("success"):
            raise RuntimeError(f"order rejected: {data.get('error_response') or data}")
        return data["success_response"]["order_id"]

    def _await_fill(self, order_id: str, side: str, price_hint: float) -> Fill | None:
        for _ in range(10):
            order = self._request("GET", f"/api/v3/brokerage/orders/historical/{order_id}")["order"]
            if order["status"] in ("FILLED", "CANCELLED", "EXPIRED", "FAILED"):
                units = float(order["filled_size"] or 0)
                if units <= 0:
                    return None
                price = float(order["average_filled_price"] or price_hint)
                return Fill(side, units, price, float(order["total_fees"] or 0))
            time.sleep(1)
        raise RuntimeError(f"order {order_id} still open after 10s; check Coinbase manually")

    def market_buy(self, quote_amount: float, price_hint: float) -> Fill | None:
        oid = self._place("BUY", {"quote_size": f"{quote_amount:.2f}"})
        return self._await_fill(oid, "BUY", price_hint)

    def market_sell(self, base_units: float, price_hint: float) -> Fill | None:
        oid = self._place("SELL", {"base_size": f"{base_units:.8f}"})
        return self._await_fill(oid, "SELL", price_hint)
