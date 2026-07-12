#!/usr/bin/env python3
# -*- coding: utf-8 -*-
r"""
=============================================================================
  process_all.py  |  معالجة دفعية لكل قوائم «تحتاج تصنيف» دفعة واحدة
=============================================================================
يمرّ على كل الملفات داخل مجلد «تحتاج تصنيف» (بمجلداته الفرعية: اسنان،
بصريات، علاج طبيعي، مختبرات، مصحات)، يشغّل المُطابِق على كل ملف، ويضع
النتيجة في مجلد «جاهز» بنفس التنظيم:

    جاهز\<اسم المجموعة>\نتيجة_<اسم الملف>.xlsx

كل المخرجات مرتبطة بالتصنيفات المعتمدة حصرًا (قائمة التصنيفات المعتمدة.xlsx).

التشغيل:
    .venv\\Scripts\\python.exe -X utf8 process_all.py
    (اختياري) --threshold 85  |  --only "مختبرات"  لمعالجة مجموعة واحدة
=============================================================================
"""
import argparse
import os
import sys
import traceback

import tpa_service_mapper as mapper

BASE = os.path.dirname(os.path.abspath(__file__))
IN_DIR = os.path.join(BASE, "تحتاج تصنيف")
OUT_DIR = os.path.join(BASE, "جاهز")
REFERENCE = os.path.join(BASE, "Price_List_Contract_1_Output.xlsx")
SYNONYMS = os.path.join(BASE, "medical_synonyms.json")

# ملفات وصفية لا تُعالج (ملخصات الدفعات وليست قوائم أسعار)
SKIP_PREFIXES = ("ملخص", "~$")

# تلميح نوع المرفق حسب اسم المجموعة (يضبط التصنيف الافتراضي/الإجباري)
GROUP_HINT_BY_NAME = [("اسنان", "dental"), ("بصريات", "optics"),
                      ("علاج طبيعي", "physio")]


def hint_for(group):
    for kw, hint in GROUP_HINT_BY_NAME:
        if kw in group:
            return hint
    return None


def main():
    p = argparse.ArgumentParser(description="معالجة دفعية لقوائم الأسعار")
    p.add_argument("--threshold", type=float, default=85)
    p.add_argument("--only", default=None,
                   help="اسم مجموعة فرعية واحدة للمعالجة (اختياري)")
    args = p.parse_args()

    jobs = []
    for group in sorted(os.listdir(IN_DIR)):
        gdir = os.path.join(IN_DIR, group)
        if not os.path.isdir(gdir):
            continue
        if args.only and args.only not in group:
            continue
        for fn in sorted(os.listdir(gdir)):
            if not fn.lower().endswith((".xlsx", ".xls", ".csv", ".pdf", ".pptx")):
                continue
            if fn.startswith(SKIP_PREFIXES):
                continue
            jobs.append((group, os.path.join(gdir, fn)))

    print(f"سيتم معالجة {len(jobs)} ملفًا\n" + "=" * 60)
    ok, failed = 0, []
    for i, (group, path) in enumerate(jobs, 1):
        base = os.path.splitext(os.path.basename(path))[0]
        base = base.replace("_منظم", "").replace("_منظمة", "")
        out_group = os.path.join(OUT_DIR, group)
        os.makedirs(out_group, exist_ok=True)
        out_path = os.path.join(out_group, f"نتيجة_{base}.xlsx")
        print(f"\n[{i}/{len(jobs)}] {group} / {os.path.basename(path)}")
        try:
            mapper.process(REFERENCE, path, out_path, SYNONYMS,
                           args.threshold, group_hint=hint_for(group))
            ok += 1
        except Exception:
            traceback.print_exc()
            failed.append(os.path.basename(path))

    print("\n" + "=" * 60)
    print(f"اكتمل: {ok} ملفًا بنجاح | فشل: {len(failed)}")
    for f in failed:
        print(f"  ✖ {f}")
    sys.exit(1 if failed else 0)


if __name__ == "__main__":
    main()
