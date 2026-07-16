#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
=============================================================================
 classify_json.py — JSON entry point for the WAAD Medical Classification
                    Engine (MC-0)
=============================================================================
A9 CONTRACT (binding): this file is a NEW entry point that sits BESIDE the
authoritative script. It does NOT modify tpa_service_mapper.py / ingest.py
or any of their behavior. It guarantees 100% parity with the script by
construction: it calls the script's own process() to produce the standard
Excel output into a temp file, then converts that output to JSON.
The folder's existing CLI/Excel/batch workflows remain byte-identical.

Usage (called by the WAAD backend via ProcessBuilder):
    python -X utf8 classify_json.py --request request.json --out result.json

request.json:
{
  "channel":      "PRICE_LIST",          // carried through, informational
  "input_file":   "C:/path/list.xlsx",   // xlsx/xls/csv/pdf/pptx (required)
  "reference":    null,                  // optional; default = bundled reference
  "synonyms":     null,                  // optional; default = bundled synonyms
  "categories":   null,                  // optional; default = bundled approved list
  "threshold":    85,                    // optional
  "hint":         null,                  // optional: dental | optics | physio
  "code_prefix":  "NEW"                  // optional
}

result.json (written to --out; process exit code 0 on success, 1 on failure):
{
  "ok": true,
  "engine_version": "...",
  "fuzz_engine": "rapidfuzz|index",
  "channel": "PRICE_LIST",
  "summary": { "<البند>": "<القيمة>", ... },     // the script's Summary sheet
  "lines": [ { ...one object per output row... } ]
}
=============================================================================
"""

import argparse
import hashlib
import json
import os
import sys
import tempfile
import time
import traceback

_BASE_DIR = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, _BASE_DIR)

ENGINE_VERSION = "waad-mce-cli/1.0.0"

# Status text emitted by the authoritative script (imported, not duplicated)
import tpa_service_mapper as mapper  # noqa: E402


def _read_request(path):
    with open(path, "r", encoding="utf-8") as f:
        return json.load(f)


def _split_display_name(display_name):
    """process() joins bilingual names as 'name | name_alt' — split them back."""
    s = "" if display_name is None else str(display_name)
    if " | " in s:
        a, b = s.split(" | ", 1)
        return a.strip(), b.strip()
    return s.strip(), ""


def _cell(v):
    """JSON-safe scalar."""
    if v is None:
        return None
    try:
        import math
        if isinstance(v, float) and math.isnan(v):
            return None
    except Exception:
        pass
    return v


def _file_provenance(path):
    """sha256 + size for a knowledge file (None-safe)."""
    if not path or not os.path.isfile(path):
        return None
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            h.update(chunk)
    return {"file": os.path.basename(path),
            "sha256": h.hexdigest(),
            "size": os.path.getsize(path)}


def run(req):
    started = time.monotonic()
    input_file = req["input_file"]
    if not os.path.isfile(input_file):
        raise FileNotFoundError(f"input_file not found: {input_file}")

    reference = req.get("reference") or os.path.join(
        _BASE_DIR, "Price_List_Contract_1_Output.xlsx")
    synonyms = req.get("synonyms")
    if synonyms is None:
        default_syn = os.path.join(_BASE_DIR, "medical_synonyms.json")
        synonyms = default_syn if os.path.isfile(default_syn) else None
    categories = req.get("categories")  # None -> script default (approved list)
    threshold = float(req.get("threshold") or mapper.DEFAULT_THRESHOLD)
    hint = req.get("hint") or None
    code_prefix = req.get("code_prefix") or "NEW"

    # 0) Optional LAB reference layer (MED-DICT-9): additive, flag-gated,
    #    CAT-LAB only. Disabled (default) => reference path is returned
    #    unchanged, so the run is byte-identical to the current baseline.
    import waad_lab_reference as lab_layer
    lab_workdir = None
    lab_layer_info = None
    if lab_layer.is_enabled():
        lab_workdir = tempfile.mkdtemp(prefix="mce_lab_")
        reference, lab_layer_info = lab_layer.augmented_reference_path(
            reference, lab_workdir)

    # 1) Run the AUTHORITATIVE pipeline into a temp Excel (zero logic duplication)
    tmp = tempfile.NamedTemporaryFile(
        suffix=".xlsx", prefix="mce_", delete=False)
    tmp_path = tmp.name
    tmp.close()
    try:
        mapper.process(reference, input_file, tmp_path, synonyms,
                       threshold, code_prefix, categories, hint)

        # 2) Convert the script's own output to JSON
        import pandas as pd
        df = pd.read_excel(tmp_path, sheet_name=mapper.DATA_SHEET)
        summary_df = pd.read_excel(tmp_path, sheet_name="ملخص Summary")
    finally:
        try:
            os.unlink(tmp_path)
        except OSError:
            pass
        if lab_workdir:
            for root, _dirs, files in os.walk(lab_workdir, topdown=False):
                for name in files:
                    try:
                        os.unlink(os.path.join(root, name))
                    except OSError:
                        pass
            try:
                os.rmdir(lab_workdir)
            except OSError:
                pass

    lines = []
    for i, row in df.iterrows():
        name, name_alt = _split_display_name(row.get(mapper.COL_NAME))
        status = str(row.get(mapper.COL_STATUS) or "")
        needs_review = mapper.STATUS_REVIEW in status or "راجِع" in status
        lines.append({
            "row_no": int(i) + 1,                       # position in output order
            "raw_name": name,
            "raw_name_alt": name_alt or None,
            "service_code": _cell(row.get(mapper.COL_CODE)),
            "price": _cell(row.get(mapper.COL_PRICE)),
            "main_category": _cell(row.get(mapper.COL_MAIN)),
            "sub_category": _cell(row.get(mapper.COL_SUB)),
            "note": _cell(row.get(mapper.COL_NOTE)),
            "status": status,
            "needs_review": bool(needs_review),
            "reason": _cell(row.get(mapper.COL_REASON)),
            "confidence": _cell(row.get(mapper.COL_CONF)),
            "match_method": _cell(row.get(mapper.COL_METHOD)),
            "reference_match": _cell(row.get(mapper.COL_REF)),
            "source_sheet": _cell(row.get(mapper.COL_SRC)),
        })

    summary = {}
    try:
        k_col, v_col = summary_df.columns[0], summary_df.columns[1]
        for _, r in summary_df.iterrows():
            summary[str(r[k_col])] = _cell(r[v_col])
    except Exception:
        summary = {}

    # Full knowledge provenance (owner condition #2, MC-1): exactly which
    # dictionaries/reference produced this classification, plus timing.
    categories_effective = categories or mapper.DEFAULT_CATEGORIES_FILE
    knowledge = {
        "reference": _file_provenance(reference),
        "categories": _file_provenance(categories_effective),
        "synonyms": _file_provenance(synonyms),
        "odoo_kb": _file_provenance(mapper.DEFAULT_KB_FILE),
        "lab_reference_layer": lab_layer_info,
    }

    return {
        "ok": True,
        "engine_version": ENGINE_VERSION,
        "fuzz_engine": mapper._FUZZ_ENGINE,
        "channel": req.get("channel") or "PRICE_LIST",
        "threshold": threshold,
        "hint": hint,
        "knowledge": knowledge,
        "execution_ms": int((time.monotonic() - started) * 1000),
        "summary": summary,
        "total_lines": len(lines),
        "needs_review_count": sum(1 for l in lines if l["needs_review"]),
        "lines": lines,
    }


def main():
    p = argparse.ArgumentParser(
        description="WAAD Medical Classification Engine — JSON entry point")
    p.add_argument("--request", required=True, help="path to request.json")
    p.add_argument("--out", required=True, help="path to write result.json")
    args = p.parse_args()

    try:
        req = _read_request(args.request)
        result = run(req)
        with open(args.out, "w", encoding="utf-8") as f:
            json.dump(result, f, ensure_ascii=False)
        print(f"OK lines={result['total_lines']} "
              f"review={result['needs_review_count']}")
        sys.exit(0)
    except Exception as e:  # error result still written as JSON for the caller
        err = {"ok": False, "engine_version": ENGINE_VERSION,
               "error": f"{type(e).__name__}: {e}"}
        try:
            with open(args.out, "w", encoding="utf-8") as f:
                json.dump(err, f, ensure_ascii=False)
        except OSError:
            pass
        traceback.print_exc()
        sys.exit(1)


if __name__ == "__main__":
    main()
