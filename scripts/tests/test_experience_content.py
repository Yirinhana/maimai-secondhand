"""Pure content-planning tests: no database, credentials, subprocess or network."""

from copy import deepcopy
import importlib.util
import json
from pathlib import Path
from types import SimpleNamespace
import unittest
from unittest import mock


ROOT = Path(__file__).resolve().parents[2]
spec = importlib.util.spec_from_file_location("experience_content", ROOT / "scripts/enrich-experience-content.py")
maintenance = importlib.util.module_from_spec(spec)
spec.loader.exec_module(maintenance)


class ExperienceContentTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.package = json.loads((ROOT / "scripts/data/experience-content.json").read_text("utf-8"))
        cls.baseline = json.loads((ROOT / "deploy/catalog-experience-v070.json").read_text("utf-8"))

    def setUp(self):
        self.item = deepcopy(self.package["items"][0])
        self.old = deepcopy(self.baseline["items"][0])
        self.product = {"id": 11, "seller": 20, "title": self.item["title"], "source": maintenance.SOURCE,
                        "description": "原始导入内容", "supplyNote": self.old["supplyNote"],
                        "seedDescription": "原始导入内容", "seedSupplyNote": self.old["supplyNote"],
                        "status": "ON_SALE", "version": 7, "revisionVersion": 9, "sellerActive": True,
                        "sellerRole": True, "sellerApproval": "APPROVED", "_fingerprint": "a" * 64,
                        "content": {"title": self.item["title"], "description": "原始导入内容", "supplyNote": self.old["supplyNote"],
                                    "priceCents": 12000, "specifications": {},
                                    "images": [{"id": n, "path": p, "sort": n} for n, p in enumerate(sorted(maintenance.expected_images(self.item["key"])), 1)]}}
        self.order = {"id": 31, "orderNo": "MX045001", "source": maintenance.SOURCE, "buyer": 10,
                      "seller": 20, "buyerActive": True, "sellerActive": True, "fulfillment": "COMPLETED",
                      "payStatus": "PAID", "refundStatus": "NONE", "completedAt": "2026-09-20 00:00:00",
                      "total": 12000, "items": [{"product": 11, "seller": 20, "quantity": 1}],
                      "receipts": [{"id": 99, "status": "PAID", "amount": 12000, "simulated": True}], "_fingerprint": "b" * 64}
        self.rating = {"id": 41, "order": 31, "rater": 10, "ratee": 20, "rating": self.old["rating"],
                       "comment": self.old["review"], "hidden": False, "reports": 0, "followup": None,
                       "images": 0, "descriptionRating": None, "communicationRating": None,
                       "fulfillmentRating": None, "_fingerprint": "c" * 64}
        self.question = {"id": 51, "product": 11, "author": 10, "replyTo": None, "content": self.old["question"],
                         "status": "PUBLISHED", "reports": 0, "_fingerprint": "d" * 64}
        self.answer = {"id": 52, "product": 11, "author": 20, "replyTo": 51, "content": self.old["answer"],
                       "status": "PUBLISHED", "reports": 0, "_fingerprint": "e" * 64}
        self.snapshot = {"PRODUCT": [self.product], "ORDER": [self.order], "RATING": [self.rating], "COMMENT": [self.question, self.answer]}

    def plan(self):
        return maintenance.plan_content([self.item], [self.old], self.snapshot)

    def test_package_covers_all_items_and_preserves_ratings_and_sources(self):
        items = maintenance.validate_package(self.package, self.baseline)
        self.assertEqual(65, len(items))
        for field in ("description", "supplyNote", "question", "answer", "review"):
            self.assertEqual(65, len({p[field] for p in items}))
        self.assertTrue(all("体验核对" not in p["review"] and "元数据" not in p["review"] for p in items))
        self.assertIn("体验", self.package["notice"])
        self.assertIn("不参与信誉等级", self.package["notice"])

    def test_normal_history_has_four_targeted_changes_without_mutating_input(self):
        unchanged = deepcopy(self.snapshot)
        plan = self.plan()
        self.assertEqual(["PRODUCT", "RATING", "COMMENT", "COMMENT"], [p["kind"] for p in plan["changes"]])
        self.assertEqual("READY", plan["outcomes"][0]["status"])
        self.assertEqual(unchanged, self.snapshot)
        self.assertEqual({"description", "supplyNote"}, set(plan["changes"][0]["after"]))

    def test_already_updated_is_zero_changes(self):
        self.product.update(description=self.item["description"], supplyNote=self.item["supplyNote"])
        self.rating["comment"] = self.item["review"]
        self.question["content"] = self.item["question"]
        self.answer["content"] = self.item["answer"]
        plan = self.plan()
        self.assertEqual([], plan["changes"])
        self.assertEqual("ALREADY_CURRENT", plan["outcomes"][0]["status"])
        self.assertEqual("", maintenance.build_apply_sql(plan, 1))

    def test_later_product_edit_protects_entire_item(self):
        self.product["description"] = "用户后来补充的真实描述"
        plan = self.plan()
        self.assertEqual([], plan["changes"])
        self.assertIn("product-content-edited-after-import", plan["outcomes"][0]["skipped"])

    def test_ordinary_product_is_never_modified(self):
        self.product["source"] = None
        self.assertEqual([], self.plan()["changes"])

    def test_duplicate_title_is_skipped(self):
        duplicate = deepcopy(self.product)
        duplicate["id"] = 101
        self.snapshot["PRODUCT"].append(duplicate)
        self.assertEqual([], self.plan()["changes"])

    def test_added_or_replaced_image_is_a_conflict(self):
        self.product["content"]["images"].append({"id": 4, "path": "/uploads/products/user-photo.jpg", "sort": 4})
        self.assertEqual([], self.plan()["changes"])

    def test_edited_rating_is_preserved_while_independent_content_is_planned(self):
        self.rating["comment"] = "该用户后来自己写的评价"
        plan = self.plan()
        self.assertEqual(["PRODUCT", "COMMENT", "COMMENT"], [p["kind"] for p in plan["changes"]])
        self.assertEqual("PARTIAL", plan["outcomes"][0]["status"])

    def test_reported_rating_is_not_rewritten(self):
        self.rating["reports"] = 1
        self.assertFalse(any(p["kind"] == "RATING" for p in self.plan()["changes"]))

    def test_existing_followup_or_dimension_is_not_rewritten(self):
        for field, value in (("followup", "用户追评"), ("descriptionRating", 3), ("images", 1)):
            with self.subTest(field=field):
                previous = self.rating[field]
                self.rating[field] = value
                self.assertFalse(any(p["kind"] == "RATING" for p in self.plan()["changes"]))
                self.rating[field] = previous

    def test_other_users_same_question_is_not_touched(self):
        third_party = deepcopy(self.question)
        third_party.update(id=155, author=99)
        self.snapshot["COMMENT"].append(third_party)
        planned = self.plan()["changes"]
        self.assertNotIn(155, [p["id"] for p in planned if p["kind"] == "COMMENT"])
        self.assertEqual(4, len(planned))

    def test_duplicate_question_skips_both_sides_of_discussion(self):
        another = deepcopy(self.question)
        another["id"] = 151
        self.snapshot["COMMENT"].append(another)
        self.assertFalse(any(p["kind"] == "COMMENT" for p in self.plan()["changes"]))

    def test_edited_or_wrong_parent_answer_keeps_question_unchanged(self):
        for field, value in (("content", "卖家后来自己补充了答案"), ("replyTo", 999)):
            with self.subTest(field=field):
                previous = self.answer[field]
                self.answer[field] = value
                self.assertFalse(any(p["kind"] == "COMMENT" for p in self.plan()["changes"]))
                self.answer[field] = previous

    def test_hidden_comment_preserves_pair(self):
        self.answer["status"] = "HIDDEN"
        self.assertFalse(any(p["kind"] == "COMMENT" for p in self.plan()["changes"]))

    def test_wrong_order_party_or_product_rejects_batch(self):
        for field, value in (("buyer", 20), ("seller", 99), ("source", None), ("fulfillment", "SHIPPED")):
            with self.subTest(field=field):
                previous = self.order[field]
                self.order[field] = value
                with self.assertRaisesRegex(maintenance.MaintenanceError, "HISTORICAL_ORDER_RELATION_INVALID"):
                    self.plan()
                self.order[field] = previous
        self.order["items"][0]["product"] = 999
        with self.assertRaisesRegex(maintenance.MaintenanceError, "HISTORICAL_ORDER_RELATION_INVALID"):
            self.plan()

    def test_unapproved_seller_rejects_batch(self):
        self.product["sellerApproval"] = "PENDING"
        with self.assertRaisesRegex(maintenance.MaintenanceError, "SELLER_RELATION_INVALID"):
            self.plan()

    def test_wrong_rating_party_rejects_batch(self):
        self.rating["ratee"] = 99
        with self.assertRaisesRegex(maintenance.MaintenanceError, "RATING_PARTICIPANT_INVALID"):
            self.plan()

    def test_real_or_wrong_amount_receipt_rejects_batch(self):
        for field, value in (("simulated", False), ("amount", 1)):
            with self.subTest(field=field):
                previous = self.order["receipts"][0][field]
                self.order["receipts"][0][field] = value
                with self.assertRaisesRegex(maintenance.MaintenanceError, "HISTORICAL_PAYMENT_RELATION_INVALID"):
                    self.plan()
                self.order["receipts"][0][field] = previous

    def test_apply_sql_contains_locks_guards_revision_and_hash_audits(self):
        sql = maintenance.build_apply_sql(self.plan(), 1)
        self.assertIn("START TRANSACTION", sql)
        self.assertIn("FOR UPDATE", sql)
        self.assertIn("MIN(SHA2(record,256))", sql)
        self.assertIn("version=version+1", sql)
        self.assertIn("INSERT INTO product_revisions", sql)
        self.assertIn("INSERT INTO admin_audit_logs", sql)
        self.assertIn("COMMIT", sql)
        for forbidden in ("UPDATE orders", "UPDATE users", "UPDATE payment_requests", "stock_available=", "rating=", "DELETE FROM", "ai_governance_signals"):
            self.assertNotIn(forbidden, sql)

    def test_fingerprint_is_required_and_sql_values_are_hex_literals(self):
        plan = self.plan()
        plan["guards"][("PRODUCT", 11)].pop("_fingerprint")
        with self.assertRaisesRegex(maintenance.MaintenanceError, "SNAPSHOT_FINGERPRINT_MISSING"):
            maintenance.build_apply_sql(plan, 1)
        value = "用户的'文本\\以及\n换行"
        encoded = maintenance.literal(value)
        self.assertNotIn(value, encoded)
        self.assertIn(value.encode("utf-8").hex(), encoded)

    def test_summary_never_contains_record_text(self):
        output = json.dumps(maintenance.summary(self.plan()), ensure_ascii=False)
        for secret_text in (self.item["description"], self.item["review"], self.old["review"], self.old["question"]):
            self.assertNotIn(secret_text, output)

    def test_mysql_uses_relative_defaults_file_inside_unicode_workspace(self):
        workspace = ROOT / "中文项目 路径"
        client = workspace / ".local/private/mysql-client.ini"
        with (mock.patch.object(maintenance, "ROOT", workspace),
              mock.patch.object(Path, "is_file", return_value=True),
              mock.patch.object(Path, "open", side_effect=AssertionError("Must not open a client file")),
              mock.patch.object(maintenance.subprocess, "run", return_value=SimpleNamespace(returncode=0, stdout=b"1\n")) as run):
            database = maintenance.LocalDatabase(workspace / "tools/mysql.exe", client)
            self.assertEqual("1", database.sql("SELECT 1;"))
        command = run.call_args.args[0]
        self.assertEqual("--defaults-file=" + str(Path(".local/private/mysql-client.ini")), command[1])
        self.assertNotIn(str(client), command[1])
        self.assertEqual(workspace, run.call_args.kwargs["cwd"])
        self.assertIn("--connect-timeout=5", command)
        self.assertIn("--host=127.0.0.1", command)
        self.assertIn("--port=3307", command)
        self.assertEqual("maimai", command[-1])

    def test_mysqldump_does_not_receive_mysql_only_connect_timeout(self):
        workspace = ROOT / "中文项目 路径"
        stream = mock.MagicMock()
        stream.__enter__.return_value = mock.sentinel.backup_stream
        with (mock.patch.object(maintenance, "ROOT", workspace),
              mock.patch.object(Path, "resolve", lambda path: path),
              mock.patch.object(Path, "is_file", return_value=True),
              mock.patch.object(Path, "mkdir") as mkdir,
              mock.patch.object(Path, "chmod"),
              mock.patch.object(Path, "stat", return_value=SimpleNamespace(st_size=512)),
              mock.patch.object(Path, "open", side_effect=AssertionError("Must not open a client file")),
              mock.patch.object(maintenance.os, "open", return_value=7) as open_backup,
              mock.patch.object(maintenance.os, "fdopen", return_value=stream),
              mock.patch.object(maintenance.subprocess, "check_output", return_value="test-user"),
              mock.patch.object(maintenance.subprocess, "run", return_value=SimpleNamespace(returncode=0)) as run):
            database = maintenance.LocalDatabase(workspace / "tools/mysql.exe", workspace / ".local/private/mysql-client.ini")
            backup = database.backup()
        commands = [call.args[0] for call in run.call_args_list]
        dump_commands = [command for command in commands if Path(command[0]).name == "mysqldump.exe"]
        self.assertEqual(1, len(dump_commands))
        command = dump_commands[0]
        self.assertFalse(any(option.startswith("--connect-timeout") for option in command))
        self.assertEqual("--defaults-file=" + str(Path(".local/private/mysql-client.ini")), command[1])
        for option in ("--host=127.0.0.1", "--port=3307", "--protocol=TCP", "--single-transaction", "--no-tablespaces", "--set-gtid-purged=OFF"):
            self.assertIn(option, command)
        self.assertEqual("maimai", command[-1])
        self.assertEqual(workspace, run.call_args.kwargs["cwd"])
        self.assertIs(mock.sentinel.backup_stream, run.call_args.kwargs["stdout"])
        self.assertTrue(backup.is_relative_to(workspace / ".local/private/experience-content-backups"))
        mkdir.assert_called_once()
        open_backup.assert_called_once()


if __name__ == "__main__":
    unittest.main()
