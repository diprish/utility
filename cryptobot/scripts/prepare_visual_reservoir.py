"""Cut a small, connected patch of the right optic lobe out of the male CNS connectome.

Input : the three Janelia flat files (annotations, weights, neurotransmitters), see README.
Output: an .npz holding a signed sparse weight matrix and the photoreceptor input nodes.

Data: Janelia FlyEM male CNS v1.0 (CC-BY 4.0), https://male-cns.janelia.org
"""
from __future__ import annotations

import argparse
from pathlib import Path

import numpy as np
import pyarrow as pa
import pyarrow.compute as pc
import pyarrow.feather as feather
import scipy.sparse as sp

VISUAL = ["ol_sensory", "ol_intrinsic", "visual_projection"]
# Fly optic-lobe convention: acetylcholine excites; GABA and glutamate (via GluCl) inhibit.
# Monoamines/peptides are modulatory, so they are treated as excitatory here.
SIGN = {"gaba": -1.0, "glutamate": -1.0}


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("data_dir", type=Path, help="folder holding the downloaded .feather files")
    ap.add_argument("--out", type=Path, default=Path("visual_reservoir.npz"))
    ap.add_argument("--size", type=int, default=2000, help="neurons to keep")
    ap.add_argument("--seed-photoreceptors", type=int, default=5)
    ap.add_argument("--step", type=int, default=25, help="neurons added per growth round")
    a = ap.parse_args()

    ann = feather.read_table(a.data_dir / "body-annotations-male-cns-v1.0-minconf-0.5.feather",
                             columns=["bodyId", "superclass", "type", "instance"]).to_pandas()
    ann = ann[ann.superclass.isin(VISUAL) & ann.instance.fillna("").str.endswith("_R")]
    ann = ann.drop_duplicates("bodyId").reset_index(drop=True)
    ids = ann.bodyId.to_numpy()
    index = {b: i for i, b in enumerate(ids)}
    print(f"right optic lobe: {len(ids)} neurons")

    # Stream the 152M-row weights file batch by batch, keeping edges inside the set.
    ids_arr = pa.array(ids)
    rows, cols, vals = [], [], []
    tbl = feather.read_table(a.data_dir / "connectome-weights-male-cns-v1.0-minconf-0.5.feather",
                             memory_map=True)
    for batch in tbl.to_batches(max_chunksize=5_000_000):
        keep = pc.and_(pc.is_in(batch["body_pre"], ids_arr), pc.is_in(batch["body_post"], ids_arr))
        b = batch.filter(keep)
        rows.append(b["body_pre"].to_numpy()); cols.append(b["body_post"].to_numpy())
        vals.append(b["weight"].to_numpy())
    pre, post, w = (np.concatenate(x) for x in (rows, cols, vals))
    lookup = np.vectorize(index.__getitem__)
    A = sp.csr_matrix((w.astype(np.float32), (lookup(pre), lookup(post))), shape=(len(ids),) * 2)
    print(f"edges inside set: {A.nnz}")

    # Grow a connected patch: start at a few photoreceptors and repeatedly add the neurons
    # most strongly wired (either direction) to what we already have.
    sym = (A + A.T).tocsr()
    is_pr = (ann.superclass == "ol_sensory") & ann.type.fillna("").str.match(r"^R[1-8]")
    pr_idx = np.flatnonzero(is_pr)
    seeds = pr_idx[np.argsort(-np.asarray(sym[pr_idx].sum(axis=1)).ravel())[: a.seed_photoreceptors]]
    chosen = np.zeros(len(ids), bool)
    chosen[seeds] = True
    while chosen.sum() < a.size:
        score = np.asarray(sym[:, np.flatnonzero(chosen)].sum(axis=1)).ravel()
        score[chosen] = -1
        take = np.argsort(-score)[: min(a.step, a.size - chosen.sum())]
        chosen[take] = True
    sel = np.flatnonzero(chosen)

    nt = feather.read_table(a.data_dir / "body-neurotransmitters-male-cns-v1.0.feather",
                            columns=["body", "consensus_nt"]).to_pandas().set_index("body").consensus_nt
    sign = np.array([SIGN.get(str(nt.get(b, "")).lower(), 1.0) for b in ids[sel]], np.float32)

    W = A[sel][:, sel].tocoo()
    W = sp.csr_matrix((W.data * sign[W.row], (W.row, W.col)), shape=W.shape)  # sign by presynaptic neuron
    inputs = np.flatnonzero(is_pr.to_numpy()[sel])
    print(f"kept {len(sel)} neurons, {W.nnz} edges, {len(inputs)} photoreceptor inputs, "
          f"{(sign < 0).mean():.0%} inhibitory")
    np.savez_compressed(
        a.out, data=W.data, indices=W.indices, indptr=W.indptr, shape=np.array(W.shape),
        inputs=inputs, body_ids=ids[sel], types=ann.type.fillna("").to_numpy()[sel].astype(str),
    )


if __name__ == "__main__":
    main()
