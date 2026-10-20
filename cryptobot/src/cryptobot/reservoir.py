"""Echo-state ("reservoir") strategy whose recurrent wiring is a real connectome patch.

Needs the `reservoir` extra (numpy, scipy). Only the linear readout is trained; the
recurrent weights are fixed to the connectome (or a control) and rescaled to a target
spectral radius.
"""
from __future__ import annotations

from dataclasses import dataclass
from typing import Sequence

import numpy as np
import scipy.sparse as sp

from .models import Candle
from .strategy import Strategy

N_FEATURES = 3
VOL_WINDOW = 60


def load_wiring(path: str) -> tuple[sp.csr_matrix, np.ndarray]:
    """Load a patch written by scripts/prepare_visual_reservoir.py -> (W, input node indices)."""
    z = np.load(path)
    W = sp.csr_matrix((z["data"], z["indices"], z["indptr"]), shape=tuple(z["shape"]))
    return W, z["inputs"]


def shuffled_wiring(W: sp.csr_matrix, rng: np.random.Generator) -> sp.csr_matrix:
    """Control: same neurons, same edge weights (incl. sign), random endpoints."""
    coo = W.tocoo()
    n = W.shape[0]
    flat = rng.choice(n * n, size=coo.nnz, replace=False)
    return sp.csr_matrix((coo.data, (flat // n, flat % n)), shape=W.shape)


def gaussian_wiring(W: sp.csr_matrix, rng: np.random.Generator) -> sp.csr_matrix:
    """Control: same density, N(0,1) weights on random edges."""
    coo = W.tocoo()
    n = W.shape[0]
    flat = rng.choice(n * n, size=coo.nnz, replace=False)
    return sp.csr_matrix((rng.standard_normal(coo.nnz), (flat // n, flat % n)), shape=W.shape)


def features(candles: Sequence[Candle]) -> np.ndarray:
    """Causal per-candle inputs: scaled return, scaled |return|, volume surprise. (T, 3)."""
    close = np.array([c.close for c in candles])
    vol = np.array([c.volume for c in candles])
    r = np.diff(np.log(close), prepend=np.log(close[0]))
    sd = np.array([r[max(0, t - VOL_WINDOW + 1) : t + 1].std() for t in range(len(r))])
    sd = np.where(sd > 1e-8, sd, 1.0)
    z = np.clip(r / sd, -5, 5)
    ma = np.array([vol[max(0, t - 19) : t + 1].mean() for t in range(len(vol))])
    vs = np.clip(np.log((vol + 1e-9) / (ma + 1e-9)), -2, 2)
    U = np.stack([z, np.abs(z) - 0.8, vs], axis=1)
    U[:VOL_WINDOW] = 0.0  # not enough history for a stable scale yet
    return U


@dataclass
class Reservoir:
    W: sp.csr_matrix
    input_nodes: np.ndarray
    rho: float = 0.9  # spectral radius after rescaling
    leak: float = 0.3
    input_scale: float = 0.5
    seed: int = 0

    def __post_init__(self):
        rng = np.random.default_rng(self.seed)
        lam = np.max(np.abs(np.linalg.eigvals(self.W.toarray())))
        self.Wn = (self.W * (self.rho / lam)).tocsr() if lam > 0 else self.W
        n = self.W.shape[0]
        self.W_in = np.zeros((n, N_FEATURES))
        self.W_in[self.input_nodes] = rng.uniform(-1, 1, (len(self.input_nodes), N_FEATURES))
        self.W_in *= self.input_scale

    @property
    def size(self) -> int:
        return self.W.shape[0]

    def run(self, U: np.ndarray) -> np.ndarray:
        """Drive with inputs (T, 3); return states (T, N)."""
        x = np.zeros(self.size)
        out = np.empty((len(U), self.size))
        drive = U @ self.W_in.T
        for t in range(len(U)):
            x = (1 - self.leak) * x + self.leak * np.tanh(self.Wn @ x + drive[t])
            out[t] = x
        return out


def design(states: np.ndarray) -> np.ndarray:
    return np.hstack([states, np.ones((len(states), 1))])


def fit_ridge(X: np.ndarray, y: np.ndarray, alpha: float) -> np.ndarray:
    A = X.T @ X + alpha * np.eye(X.shape[1])
    return np.linalg.solve(A, X.T @ y)


def next_return(candles: Sequence[Candle]) -> np.ndarray:
    """Target aligned to candle t: the log return from t's close to t+1's close (last = nan)."""
    close = np.array([c.close for c in candles])
    y = np.full(len(close), np.nan)
    y[:-1] = np.diff(np.log(close))
    return y


class ReservoirStrategy(Strategy):
    """Long when the trained readout predicts a positive next-candle return (plus `margin`)."""

    def __init__(self, reservoir: Reservoir, readout: np.ndarray, lookback: int = 300, margin: float = 0.0):
        self.reservoir, self.readout, self.margin = reservoir, readout, margin
        self.lookback = lookback

    def target_position(self, history: Sequence[Candle]) -> float:
        if len(history) < self.lookback:
            return 0.0
        states = self.reservoir.run(features(history))
        pred = float(design(states[-1:]) @ self.readout)
        return 1.0 if pred > self.margin else 0.0
