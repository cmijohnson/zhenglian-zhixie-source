import copy
import unittest
from generate_synthetic_dataset import generate, validate, encode

class SyntheticDatasetTests(unittest.TestCase):
    def setUp(self): self.data = generate()
    def test_repeated_seed_is_byte_identical(self):
        self.assertEqual(encode(self.data), encode(generate(20260927)))
        self.assertNotEqual(encode(self.data), encode(generate(20260928)))
    def test_schema_counts_and_case_balance(self):
        self.assertEqual(validate(self.data)["records"], 900)
        self.assertEqual(len(self.data["users"]), 8)
    def test_cross_organization_record_is_rejected(self):
        self.data["records"][0]["organizationId"] = "org-2"
        with self.assertRaisesRegex(ValueError, "record organization mismatch"): validate(self.data)
    def test_cross_customer_expected_citation_is_rejected(self):
        self.data["cases"][0]["expectedSourceKeys"] = ["record:customer-002:01"]
        with self.assertRaisesRegex(ValueError, "evidence domain violation"): validate(self.data)
    def test_cross_organization_expected_citation_is_rejected(self):
        self.data["cases"][0]["expectedSourceKeys"] = ["record:customer-031:01"]
        with self.assertRaisesRegex(ValueError, "evidence domain violation"): validate(self.data)
    def test_missing_source_is_rejected(self):
        self.data["cases"][0]["expectedSourceKeys"] = ["record:missing"]
        with self.assertRaisesRegex(ValueError, "unknown expected source"): validate(self.data)
    def test_duplicate_source_key_is_rejected(self):
        self.data["records"][1]["sourceKey"] = self.data["records"][0]["sourceKey"]
        with self.assertRaisesRegex(ValueError, "duplicate sourceKey"): validate(self.data)
    def test_injections_are_marked_and_not_expected_evidence(self):
        cases = [c for c in self.data["cases"] if c["category"] == "prompt_injection"]
        records = {r["sourceKey"]: r for r in self.data["records"]}
        self.assertEqual(len(cases), 10)
        for case in cases:
            for key in case["injectionSourceKeys"]:
                self.assertTrue(records[key]["facts"]["isInjection"])
                self.assertNotIn(key, case["expectedSourceKeys"])
    def test_injection_promoted_to_expected_evidence_is_rejected(self):
        case = next(c for c in self.data["cases"] if c["category"] == "prompt_injection")
        case["expectedSourceKeys"] = list(case["injectionSourceKeys"])
        with self.assertRaisesRegex(ValueError, "injection cannot be expected evidence"): validate(self.data)
    def test_decoy_set_contains_both_denied_domains(self):
        records = {r["sourceKey"]: r for r in self.data["records"]}
        for case in self.data["cases"]:
            decoys = [records[k] for k in case["forbiddenSourceKeys"]]
            self.assertTrue(any(r["organizationId"] != case["organizationId"] for r in decoys))
            self.assertTrue(any(r["organizationId"] == case["organizationId"] and r["customerId"] != case["customerId"] for r in decoys))
    def test_cross_organization_task_is_rejected(self):
        self.data["tasks"][0]["organizationId"] = "org-2"
        with self.assertRaisesRegex(ValueError, "task organization mismatch"): validate(self.data)
    def test_cross_organization_task_owner_is_rejected(self):
        self.data["tasks"][0]["ownerId"] = "user-2-3"
        with self.assertRaisesRegex(ValueError, "task owner organization mismatch"): validate(self.data)
    def test_task_cannot_bypass_human_confirmation(self):
        self.data["tasks"][0]["requiresHumanConfirmation"] = False
        with self.assertRaisesRegex(ValueError, "task confirmation policy"): validate(self.data)
    def test_old_record_answer_is_outside_recent_five(self):
        for case in self.data["cases"]:
            if case["category"] == "old_record":
                recent = [r["sourceKey"] for r in self.data["records"] if r["customerId"] == case["customerId"]][-5:]
                self.assertFalse(set(recent) & set(case["expectedSourceKeys"]))

if __name__ == "__main__": unittest.main()
