#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""معالجة الملفات القديمة في جذر «جاهز» بالتنسيق الجديد (من مصادرها في «تحتاج تصنيف قديمة»)."""
import os
import sys
import traceback

import tpa_service_mapper as mapper

BASE = os.path.dirname(os.path.abspath(__file__))
SRC_DIR = os.path.join(BASE, "تحتاج تصنيف قديمة")
OUT_DIR = os.path.join(BASE, "جاهز")
REFERENCE = os.path.join(BASE, "official_reference_layer.xlsx")
SYNONYMS = os.path.join(BASE, "medical_synonyms.json")

# (اسم ملف المصدر, اسم ملف المخرج بدون امتداد, تلميح المجموعة)
JOBS = [
    ("اشاري كراسة اسعار ابن سينا 3.1.2026.xlsx", "نتيجة_ابن_سينا", None),
    ("مستشفى الاستشاري.xlsx",                    "نتيجة_الاستشاري", None),
    ("مصحة الحكمة 2مصرف الوحدة 2026.pptx",        "نتيجة_الحكمة_مصرف_الوحدة", None),
    ("الخدمات 2026.pdf",                          "نتيجة_الخدمات_2026", None),
    ("د. تهاني.pdf",                              "نتيجة_القيصريات_د_تهاني", None),
    ("مستشفى بيروت.xlsx",                         "نتيجة_بيروت", None),
    ("اسعار التحاليل خاص مستشفى الصفوه.pdf",       "نتيجة_تحاليل_الصفوة", None),
    ("جراحة الصدر د. مصطفى البرجو.pdf",            "نتيجة_جراحة_الصدر", None),
    ("اكواد جمعة الزوي.pdf",                       "نتيجة_جمعة_الزوي", None),
    ("مصحة الحكمة.pptx",                          "نتيجة_دار_الحكمة_الخدمات", None),
    ("عرض كامل خاص مستشفى الصفوه.pdf",             "نتيجة_عرض_كامل_الصفوة", None),
    ("عمليات 2026.pdf",                           "نتيجة_عمليات_2026", None),
    ("عمليات الشبكية (عيون).pdf",                  "نتيجة_عمليات_الشبكية", "optics"),
]


def main():
    ok, failed = 0, []
    for i, (src, out_base, hint) in enumerate(JOBS, 1):
        src_path = os.path.join(SRC_DIR, src)
        out_path = os.path.join(OUT_DIR, out_base + ".xlsx")
        print(f"\n[{i}/{len(JOBS)}] {src}")
        if not os.path.exists(src_path):
            print(f"  ✖ المصدر غير موجود: {src_path}")
            failed.append(src)
            continue
        try:
            mapper.process(REFERENCE, src_path, out_path, SYNONYMS, 85,
                           group_hint=hint)
            ok += 1
        except Exception:
            traceback.print_exc()
            failed.append(src)

    print("\n" + "=" * 60)
    print(f"اكتمل: {ok} ملفًا بنجاح | فشل: {len(failed)}")
    for f in failed:
        print(f"  ✖ {f}")
    sys.exit(1 if failed else 0)


if __name__ == "__main__":
    main()
