#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
waad_lab_reference.py — optional, additive LAB reference/knowledge layer.

MED-DICT-9. This module lets the engine recognise laboratory service names from
the approved WAAD_COMPRESSED LAB AUTO concepts *without* touching the
authoritative script (tpa_service_mapper.py stays byte-identical) and *without*
modifying the base contract reference or medical_synonyms.json.

How it works: when enabled, we read the base reference workbook, append the LAB
layer rows (each carrying sub_category = "CAT-LAB - <name>"), and hand the
augmented workbook to the unchanged mapper.process(). Disabled => the base
reference path is returned unchanged, so behaviour is byte-identical to baseline.

Safety invariants:
  * additive only — never mutates the base reference or the layer file;
  * emits CAT-LAB exclusively (every layer row's sub_category is CAT-LAB);
  * the approved official_taxonomy.json remains the only source of categories;
  * disabled + missing file  => silent no-op (baseline);
  * enabled  + missing file  => clear failure (raised to the caller).

Env contract:
  ENGINE_WAAD_LAB_REFERENCE_ENABLED  ("1"/"true"/"yes"/"on" => enabled; default off)
  ENGINE_WAAD_LAB_REFERENCE_PATH     (absolute path; default: beside this module)
"""

import os

_BASE_DIR = os.path.dirname(os.path.abspath(__file__))
DEFAULT_LAYER_PATH = os.path.join(_BASE_DIR, "waad_lab_reference_layer_v1.json")
ENV_ENABLED = "ENGINE_WAAD_LAB_REFERENCE_ENABLED"
ENV_PATH = "ENGINE_WAAD_LAB_REFERENCE_PATH"

_TRUE = {"1", "true", "yes", "on"}


def is_enabled():
    return os.environ.get(ENV_ENABLED, "").strip().lower() in _TRUE


def layer_path():
    return os.environ.get(ENV_PATH, "").strip() or DEFAULT_LAYER_PATH


def _load_layer(path):
    import json
    with open(path, "r", encoding="utf-8") as f:
        layer = json.load(f)
    # Defensive: this layer may only ever emit CAT-LAB.
    cat = layer.get("category")
    if cat != "CAT-LAB":
        raise ValueError(
            f"WAAD LAB layer must have category CAT-LAB, found {cat!r}")
    for c in layer.get("concepts", []):
        if c.get("category", "CAT-LAB") != "CAT-LAB":
            raise ValueError(
                "WAAD LAB layer contains a non-CAT-LAB concept: "
                + str(c.get("master_id")))
    return layer


def augmented_reference_path(base_ref_path, workdir):
    """Return (reference_path, info).

    If disabled: (base_ref_path, None) — baseline, no file written.
    If enabled: writes an augmented reference workbook into `workdir` and
    returns its path plus a provenance dict. Raises if enabled but the layer
    file is absent or malformed (fail clearly, per engine convention).
    """
    if not is_enabled():
        return base_ref_path, None

    path = layer_path()
    if not os.path.isfile(path):
        raise FileNotFoundError(
            f"{ENV_ENABLED} is on but LAB reference layer not found: {path}")

    import pandas as pd
    layer = _load_layer(path)

    base = pd.read_excel(base_ref_path, dtype=str)
    cols = list(base.columns)
    if len(cols) < 6:
        raise ValueError(
            "Base reference must have >=6 columns to append the LAB layer")

    rows = []
    for c in layer.get("concepts", []):
        canonical = c.get("canonical_ar") or c.get("canonical_en") or ""
        sub = f"CAT-LAB - {canonical}".strip()
        names = []
        if c.get("canonical_ar"):
            names.append(c["canonical_ar"])
        if c.get("canonical_en"):
            names.append(c["canonical_en"])
        for a in c.get("aliases", []):
            alias = a.get("alias")
            if alias:
                names.append(alias)
        code = c.get("master_id", "")
        for nm in names:
            if not nm or not str(nm).strip():
                continue
            rows.append({
                cols[0]: nm,          # service_name
                cols[1]: code,        # service_code = Master_ID (provenance)
                cols[2]: "",          # contract_price — intentionally empty
                cols[3]: "",          # main_category — engine derives it
                cols[4]: sub,         # sub_category = CAT-LAB
                cols[5]: layer.get("source", "WAAD_COMPRESSED_AUTO_REFERENCE"),
            })

    augmented = pd.concat([base, pd.DataFrame(rows, columns=cols)],
                          ignore_index=True)
    os.makedirs(workdir, exist_ok=True)
    out_path = os.path.join(workdir, "reference_with_lab_layer.xlsx")
    augmented.to_excel(out_path, index=False)

    info = {
        "enabled": True,
        "layer": layer.get("layer"),
        "source": layer.get("source"),
        "source_version": layer.get("source_version"),
        "generated_at": layer.get("generated_at"),
        "concepts": layer.get("counts", {}).get("concepts"),
        "aliases": layer.get("counts", {}).get("aliases"),
        "reference_rows_base": int(len(base)),
        "reference_rows_added": int(len(rows)),
        "path": path,
    }
    return out_path, info
