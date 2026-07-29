#!/usr/bin/env python3
"""Build an official-code Odoo product knowledge base.

The legacy exporter used CAT023/CAT004-style codes.  The production engine
accepts only TAX-1 codes (CAT-LAB, CAT-SURGERY, ...), so this importer uses
service-name rules first and the Odoo category only as a conservative fallback.
"""
import argparse
import json
import re
from collections import Counter
from pathlib import Path

import pandas as pd

from tpa_service_mapper import normalize


RULES = [
    ("CAT-DENT-IMPLANT", ["implant", "زراعة أسنان", "زراعه اسنان"]),
    ("CAT-DENT-ORTHO", ["orthodont", "تقويم أسنان", "تقويم اسنان"]),
    ("CAT-DENT-PROSTHO", ["crown", "bridge", "denture", "تركيب أسنان", "تركيبات"]),
    ("CAT-DENT-ROUTINE", ["dental", "أسنان", "اسنان", "root canal", "filling", "extraction"]),
    ("CAT-IMG-ADV", ["ct", "mri", "magnetic resonance", "رنين", "مقطعي", "مسح مقطعي"]),
    ("CAT-IMG-DIAG", ["x-ray", "x ray", "ultrasound", "doppler", "أشعة", "اشعة", "سونار"]),
    ("CAT-LAB", ["lab", "laboratory", "تحليل", "تحاليل", "مختبر", "cbc", "pcr", "urine", "culture"]),
    ("CAT-ANESTHESIA", ["anesth", "تخدير", "بنج"]),
    ("CAT-SURGERY", ["surgery", "surgical", "operation", "جراحة", "جراحية", "استئصال"]),
    ("CAT-PHYSIO", ["physio", "rehab", "علاج طبيعي", "تأهيل"]),
    ("CAT-ONCOLOGY", ["oncolog", "chemother", "أورام", "كيماوي"]),
    ("CAT-DIALYSIS", ["dialysis", "غسيل كلوي", "غسيل الكلى"]),
    ("CAT-DRUG", ["drug", "medicine", "pharmacy", "دواء", "أدوية", "صيدلية"]),
    ("CAT-OPT", ["glasses", "spectacle", "نظارات", "عدسات"]),
    ("CAT-EYE-EXAM", ["eye exam", "فحص عيون", "كشف عيون"]),
    ("CAT-MAT-CS", ["cesarean", "قيصرية"]),
    ("CAT-MAT-NORMAL", ["normal delivery", "ولادة طبيعية"]),
    ("CAT-ICU", ["icu", "intensive care", "عناية مركزة"]),
    ("CAT-AMBULANCE", ["ambulance", "إسعاف", "اسعاف"]),
    ("CAT-ROOM", ["room", "ward", "إقامة", "اقامة", "غرفة", "جناح"]),
]

CATEGORY_FALLBACKS = {
    "المسح المقطعي": "CAT-IMG-ADV",
    "الأشعة السينية": "CAT-IMG-DIAG",
    "أشعة تشخيصية": "CAT-IMG-DIAG",
    "الرنين المغناطيسي": "CAT-IMG-ADV",
    "التحاليل الطبية": "CAT-LAB",
    "تحليل العينات": "CAT-LAB",
    "العلاج الطبيعي": "CAT-PHYSIO",
    "العلاج الطبيعي المقرر": "CAT-PHYSIO",
    "علاجات الأورام وأدوية الكيماوي": "CAT-ONCOLOGY",
    "الولادة الطبيعية والقيصرية": None,
    "كشوفات العيون (النظارات الطبية) نظارة واحدة في السنة": "CAT-EYE-EXAM",
}


def clean(value):
    if value is None or (isinstance(value, float) and pd.isna(value)):
        return ""
    return str(value).replace("\u00a0", " ").strip()


def classify(name, category):
    # CLASSIFICATION-KNOWLEDGE-GENERATOR-FIX-1: keyword rules must only ever
    # see the service NAME. The previous version concatenated the raw Odoo
    # category/folder text into the same search string, so any product filed
    # under an Odoo category folder that happened to contain a rule keyword
    # (e.g. a folder mentioning "room"/"ward"/"اقامة") was mis-tagged with
    # that category regardless of what the product itself actually was —
    # confirmed to mislabel drugs, stents, and surgeries as CAT-ROOM. The
    # Odoo category is now only consulted through the bounded, explicit
    # CATEGORY_FALLBACKS lookup (exact dict match, never free-text search).
    # Word-boundary check (padded with spaces): normalize() already collapses
    # everything to space-separated tokens, so a raw substring test on short
    # keywords like "ct" false-matches inside unrelated words ("conjunctival",
    # "Bactec", "prolactin"). Padding both sides with a space forces whole-
    # token/whole-phrase matches only.
    name_text = f" {normalize(name)} "
    for code, needles in RULES:
        if any(f" {normalize(n)} " in name_text for n in needles):
            return code, "name_rule"
    if category in CATEGORY_FALLBACKS and CATEGORY_FALLBACKS[category]:
        return CATEGORY_FALLBACKS[category], "odoo_category"
    return None, None


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("input", type=Path)
    ap.add_argument("--output", type=Path, default=Path(__file__).with_name("odoo_knowledge.json"))
    args = ap.parse_args()
    df = pd.read_excel(args.input, dtype=str)
    required = {"name", "categ_id"}
    missing = required - set(df.columns)
    if missing:
        raise SystemExit(f"Missing columns: {sorted(missing)}")

    kb, stats = {}, Counter()
    for _, row in df.iterrows():
        name, category = clean(row.get("name")), clean(row.get("categ_id"))
        if not name or len(normalize(name)) < 3:
            continue
        code, source = classify(name, category)
        if not code:
            stats["unresolved"] += 1
            continue
        key = normalize(name)
        existing = kb.get(key)
        if existing and existing["cat"] != code:
            stats["conflict"] += 1
            continue
        kb[key] = {
            "cat": code,
            "name": name,
            "source": "ODOO_PRODUCT_EXPORT",
            "sourceRule": source,
            "odooCategory": category or None,
        }
        stats[source] += 1

    args.output.write_text(json.dumps(kb, ensure_ascii=False, indent=1), encoding="utf-8")
    print(json.dumps({"entries": len(kb), **stats, "categories": Counter(v["cat"] for v in kb.values())}, ensure_ascii=False))


if __name__ == "__main__":
    main()
