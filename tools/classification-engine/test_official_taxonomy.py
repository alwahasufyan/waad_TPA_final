import json
import os
import re
import unittest

os.environ.setdefault("PYTHONDONTWRITEBYTECODE", "1")

import build_odoo_kb
import tpa_service_mapper as mapper


class OfficialTaxonomyTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.categories = mapper.load_approved_categories(mapper.DEFAULT_CATEGORIES_FILE)
        cls.classifier = mapper.CategoryClassifier(cls.categories)

    def test_exactly_33_official_cat_categories_and_5_benefits(self):
        with open(mapper.DEFAULT_CATEGORIES_FILE, encoding="utf-8") as handle:
            payload = json.load(handle)
        self.assertEqual(33, len(payload["medical_categories"]))
        self.assertEqual(5, len(payload["special_financial_benefits"]))
        self.assertEqual(33, len(self.categories))
        self.assertTrue(all(code.startswith("CAT-") for code in self.categories))
        self.assertFalse(any(code.startswith("BEN-") for code in self.categories))
        self.assertNotIn("OUTPATIENT", self.categories)
        self.assertNotIn("INPATIENT", self.categories)

    def test_dental_categories_remain_separate(self):
        cases = {
            "حشو اسنان": "CAT-DENT-ROUTINE",
            "تركيب تاج اسنان": "CAT-DENT-PROSTHO",
            "تقويم اسنان": "CAT-DENT-ORTHO",
            "زراعة سن": "CAT-DENT-IMPLANT",
            "طوارئ اسنان": "CAT-DENT-EMERG",
        }
        for text, expected in cases.items():
            with self.subTest(text=text):
                self.assertEqual(expected, self.classifier.classify_rules(text)[2])

    def test_psychiatric_drugs_are_not_sessions(self):
        self.assertEqual("CAT-PSYCH-DRUG", self.classifier.classify_rules("دواء نفسي")[2])
        self.assertEqual("CAT-PSYCH-SESS", self.classifier.classify_rules("جلسة نفسية")[2])

    def test_ambiguous_text_has_no_default_category(self):
        self.assertIsNone(self.classifier.classify_rules("خدمة غامضة بلا دلالة"))
        self.assertEqual(("", "", None), self.classifier.classify("خدمة غامضة بلا دلالة"))

    def test_generators_cannot_emit_non_official_codes(self):
        valid = set(self.categories)
        self.assertEqual(valid, build_odoo_kb.OFFICIAL_CODES)
        self.assertTrue(all(code is None or code in valid
                            for code in build_odoo_kb.CATEGORY_MAP.values()))
        self.assertTrue(all(code in valid for code, _ in build_odoo_kb.TAG_RULES))

    def test_operational_python_contains_no_legacy_category_codes(self):
        for filename in ("tpa_service_mapper.py", "build_odoo_kb.py"):
            with open(os.path.join(os.path.dirname(__file__), filename),
                      encoding="utf-8") as handle:
                source = handle.read()
            self.assertIsNone(re.search(r"\bCAT\d{3}\b", source), filename)
            self.assertIsNone(re.search(r"\bCAT-IP\b", source), filename)
            self.assertIsNone(re.search(r"\bCAT-OP\b", source), filename)


if __name__ == "__main__":
    unittest.main()
