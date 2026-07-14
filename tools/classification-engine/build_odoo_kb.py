#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
=============================================================================
  build_odoo_kb.py  |  بناء قاعدة معرفة TAX-1 الرسمية من مصادر مراجعة
=============================================================================
يقرأ تصديري أودو:
    - متغير المنتج  (product.product).xlsx
    - الخدمات الصحية (product.template).xlsx
(~11,800 خدمة فريدة)، ولا يكتب إلا أكواد CAT-* الموجودة في
`official_taxonomy.json` إلى `official_knowledge.json`:

    { "اسم الخدمة المُطبَّع": {"cat": "CAT-LAB", "name": "...", "src": "..."} }

يستخدمه tpa_service_mapper.py تلقائيًا لتصنيف خدمات «تحتاج مراجعة» التي
يوجد اسمها في النظام السابق — تصنيف موثق بدل تخمين الكلمات المفتاحية.
لا تُسجَّل إلا الحالات الواثقة؛ الباقي يُترك للمصنّف الطبي.

التشغيل (مرة واحدة، أو عند تحديث ملفات أودو):
    .venv\\Scripts\\python.exe -X utf8 build_odoo_kb.py
=============================================================================
"""
import json
import os
from collections import Counter

import pandas as pd

from tpa_service_mapper import (
    DEFAULT_CATEGORIES_FILE,
    load_approved_categories,
    normalize,
)

BASE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(BASE, "official_knowledge.json")
OFFICIAL_CODES = set(load_approved_categories(DEFAULT_CATEGORIES_FILE))

# TAX-1 mappings only. Ambiguous legacy buckets remain None and require review.
CATEGORY_MAP = {
    "الإيواء والعلاج غرفة خاصة": "CAT-ROOM",
    "التصوير بالأشعة السينية والرنين المغناطيسي والمقطعي والمسح الذري": None,
    "التحاليل والمختبرات والاشعة السينية والاشعة التشخيصية": None,
    "التحاليل الطبية": "CAT-LAB",
    "علاج الاسنان الروتيني (كشف - خلع- حشو - علاجات قنوات الجدور )": "CAT-DENT-ROUTINE",
    "العيادات الخارجية (كشوفات طبية وتحاليل وصور)": None,
    "( تركيب-تقويم -زراعة ) علاج الاسنان التركيبات": None,
    "المسح المقطعي": "CAT-IMG-ADV",
    "الولادة الطبيعية والقيصرية": None,
    "الأشعة السينية": "CAT-IMG-DIAG",
    "العلاجات والادوية وفق الوصفة الطبية من الطبيب المختص": "CAT-DRUG",
    "الكشوفات التشخيصية (المريض بالمستشفى والعلاج اليومي فقط) العلاج": "CAT-DIAGNOSTIC",
    "رسوم الاخصائيين، وممارسين مهنة الطب": "CAT-PRACT-FEE",
    "الاجهزة والمعدات الطبية وفق تقرير الطبيب المختص": "CAT-DME",
    "العلاج الطبيعي": "CAT-PHYSIO",
    "أشعة تشخيصية": "CAT-IMG-DIAG",
    "العلاج الطبيعي المقرر": "CAT-PHYSIO",
    "الرنين المغناطيسي": "CAT-IMG-ADV",
    "كشوفات العيون (النظارات الطبية) نظارة واحدة في السنة": None,
    "فحوص تشخيصية": "CAT-DIAGNOSTIC",
    "علاجات الأورام وأدوية الكيماوي": "CAT-ONCOLOGY",
    "الدواء والمستلزمات الطبية": None,
    "تحليل العينات": "CAT-LAB",
    "All": None,
    "على حساب المريض": None,
}

TAG_RULES = [
    ("CAT-DENT-IMPLANT", ["زراعه سن", "زرعه سن", "implant"]),
    ("CAT-DENT-ORTHO", ["تقويم اسنان", "orthodont"]),
    ("CAT-DENT-PROSTHO", ["تركيب اسنان", "طقم اسنان", "crown", "bridge", "prosth"]),
    ("CAT-DENT-ROUTINE", ["حشو", "خلع", "تنظيف اسنان", "root canal", "dental"]),
    ("CAT-PSYCH-DRUG", ["دواء نفسي", "ادويه نفسيه", "antidepress", "antipsychotic"]),
    ("CAT-PSYCH-SESS", ["جلسه نفسيه", "psychotherapy", "counsel"]),
    ("CAT-MAT-CS", ["قيصري", "cesarean"]),
    ("CAT-MAT-NORMAL", ["ولاده طبيعيه", "normal delivery"]),
    ("CAT-ICU", ["عنايه فايقه", "عنايه مركزه", "icu", "nicu"]),
    ("CAT-CCU", ["عنايه القلب", "ccu"]),
    ("CAT-IMG-ADV", ["رنين", "مقطعي", "mri", "ct scan"]),
    ("CAT-IMG-DIAG", ["اشعه سينيه", "اشعه تشخيصيه", "x ray", "سونار"]),
    ("CAT-LAB", ["تحليل", "مختبر", "lab", "cbc", "pcr"]),
    ("CAT-ANESTHESIA", ["تخدير", "بنج", "anesth"]),
    ("CAT-SURGERY", ["جراح", "عمليه", "surgery", "surgical"]),
    ("CAT-AMBULANCE", ["اسعاف", "ambulance"]),
    ("CAT-PHYSIO", ["علاج طبيعي", "تاهيل", "physio"]),
    ("CAT-ONCOLOGY", ["اورام", "كيماوي", "oncolog"]),
    ("CAT-DIALYSIS", ["غسيل كلوي", "dialysis"]),
    ("CAT-OPT", ["نظاره", "نظارات", "spectacle", "eyeglass"]),
    ("CAT-EYE-EXAM", ["كشف عيون", "eye exam", "ophthal"]),
    ("CAT-DRUG", ["دواء", "ادويه", "صيدل", "pharmacy"]),
    ("CAT-MED-SUP", ["مستلزمات طبيه", "medical supplies"]),
    ("CAT-ROOM", ["ايواء", "اقامه", "غرفه خاصه"]),
]

_configured_codes = ({code for code in CATEGORY_MAP.values() if code}
                     | {code for code, _ in TAG_RULES})
_invalid_configured_codes = sorted(_configured_codes - OFFICIAL_CODES)
if _invalid_configured_codes:
    raise RuntimeError(
        f"Non-official category codes configured: {_invalid_configured_codes}"
    )


def tag_to_cat(tag_norm):
    for cat, kws in TAG_RULES:
        for kw in kws:
            if kw in tag_norm:
                return cat
    return None


def main():
    frames = []
    for fn in ["متغير المنتج  (product.product).xlsx",
               "الخدمات الصحية (product.template).xlsx"]:
        p = os.path.join(BASE, fn)
        if os.path.exists(p):
            df = pd.read_excel(p, dtype=str)
            frames.append(df[["الاسم", "فئة المنتج", "علامة تصنيف قالب المنتج"]])
            print(f"• قرأت {fn}: {len(df)} صفًا")
    if not frames:
        raise SystemExit("لم أجد ملفات أودو بجانب السكربت.")

    allrows = pd.concat(frames, ignore_index=True)
    allrows = allrows[allrows["الاسم"].notna()]

    # لكل اسم مُطبَّع: صوّت على التصنيف (الاسم قد يتكرر بين الملفين/الفئات)
    votes = {}      # name_norm -> Counter({cat: n})
    display = {}    # name_norm -> الاسم الأصلي
    n_by_cat, n_by_tag, n_skipped = 0, 0, 0
    for _, r in allrows.iterrows():
        name = str(r["الاسم"]).strip()
        nn = normalize(name)
        if not nn or len(nn) < 3:
            continue
        cat_raw = str(r["فئة المنتج"]).strip() if pd.notna(r["فئة المنتج"]) else ""
        cat = CATEGORY_MAP.get(cat_raw)
        if cat:
            n_by_cat += 1
        else:
            tag = r["علامة تصنيف قالب المنتج"]
            tag_norm = normalize(tag) if pd.notna(tag) else ""
            cat = tag_to_cat(tag_norm) if tag_norm else None
            if cat:
                n_by_tag += 1
        if cat not in OFFICIAL_CODES:
            n_skipped += 1
            continue
        votes.setdefault(nn, Counter())[cat] += 1
        display.setdefault(nn, name)

    kb = {}
    for nn, cnt in votes.items():
        cat, n = cnt.most_common(1)[0]
        # تجاهل الأسماء المتنازَع عليها بشدة (لا أغلبية واضحة)
        if len(cnt) > 1 and n < sum(cnt.values()) * 0.6:
            continue
        if cat in OFFICIAL_CODES:
            kb[nn] = {"cat": cat, "name": display[nn]}

    invalid = sorted({v["cat"] for v in kb.values()} - OFFICIAL_CODES)
    if invalid:
        raise RuntimeError(f"Non-official category codes generated: {invalid}")

    with open(OUT, "w", encoding="utf-8") as f:
        json.dump(kb, f, ensure_ascii=False, indent=1)

    print(f"• صُنّف عبر الفئة: {n_by_cat} | عبر العلامة: {n_by_tag} | "
          f"تُرك للمصنّف الطبي: {n_skipped}")
    print(f"✓ قاعدة المعرفة: {len(kb)} اسم خدمة -> {OUT}")
    dist = Counter(v["cat"] for v in kb.values())
    for cat, n in dist.most_common():
        print(f"   {cat}: {n}")


if __name__ == "__main__":
    main()
