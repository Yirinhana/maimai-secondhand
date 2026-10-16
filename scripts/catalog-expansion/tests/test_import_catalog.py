import importlib.util
import json
from pathlib import Path
import tempfile
import unittest
from copy import deepcopy
from unittest.mock import patch

from PIL import Image

MODULE = Path(__file__).resolve().parents[1] / "import_catalog.py"
spec = importlib.util.spec_from_file_location("catalog_expansion", MODULE)
app = importlib.util.module_from_spec(spec)
spec.loader.exec_module(app)


class FakeDatabase:
    def __init__(self):
        self.existing = []
        self.owner = dict(id=50, seller=21, region="上海市", version=2, source=app.ANCHOR_SOURCE,
                          status="ACTIVE", role=1, application=17, approval="APPROVED", channel="QUALIFIED", images=3, matchedImages=3)
        self.category = dict(parentId=5, parentStatus="ACTIVE", id=25, status="ACTIVE", children=0)
        self.queries = []

    def rows(self, query):
        self.queries.append(query)
        if "FROM admin_audit_logs a" in query:
            return deepcopy(self.existing)
        if "FROM products p" in query:
            return [deepcopy(self.owner)]
        if "FROM categories p" in query:
            return [deepcopy(self.category)]
        raise AssertionError("Unexpected query")


class ImportTests(unittest.TestCase):
    def setUp(self):
        self.data = json.loads((MODULE.parent / "manifest.json").read_text("utf-8"))
        self.items = app.validate_manifest(self.data)
        self.item = self.items[0]
        self.db = FakeDatabase()
        self.anchors = {self.item["sellerAnchor"]: {"key": self.item["sellerAnchor"], "title": "已有体验商品"}}
        self.images = {self.item["key"]: dict(file=self.item["key"]+"-front.jpg", sha256="a"*64, pixelHash="b"*64, reviewed=True, bytes=9000, width=512, height=512)}

    def plan(self, **options):
        return app.plan_import(self.db, [self.item], self.images, self.anchors, **options)

    def test_real_manifest_has_42_unique_items_and_eight_parent_groups(self):
        self.assertEqual(42, len(self.items))
        self.assertEqual(8, len({p["parent"] for p in self.items}))
        self.assertEqual(3, sum(p["parent"] == "数码电子" for p in self.items))
        self.assertTrue(all(type(p["images"][0]["reviewed"]) is bool for p in self.items))

    def test_invalid_money_stock_and_condition_are_rejected(self):
        for field, value in [("priceCents", 0), ("priceCents", True), ("stock", -1), ("freightCents", -5), ("condition", "UNKNOWN")]:
            with self.subTest(field=field, value=value):
                data=deepcopy(self.data);data["items"][0][field]=value
                with self.assertRaises(app.ImportErrorSafe): app.validate_manifest(data)

    def test_foreign_specification_fields_are_rejected(self):
        self.data["items"][0]["specifications"]["battery"] = "98%"
        with self.assertRaisesRegex(app.ImportErrorSafe, "SPECIFICATION"): app.validate_manifest(self.data)

    def test_image_path_cannot_escape_or_mismatch_item(self):
        self.data["items"][0]["images"][0]["file"] = "../exp021-002-front.jpg"
        with self.assertRaisesRegex(app.ImportErrorSafe, "IMAGE_IDENTITY"): app.validate_manifest(self.data)

    def test_duplicate_keys_titles_and_unknown_delivery_are_rejected(self):
        for field, value in [("key", self.data["items"][1]["key"]), ("title", self.data["items"][1]["title"]), ("deliveryMethods", ["COURIER"])]:
            with self.subTest(field=field):
                data=deepcopy(self.data);data["items"][0][field]=value
                with self.assertRaises(app.ImportErrorSafe): app.validate_manifest(data)

    def test_meetup_freight_must_be_zero(self):
        self.data["items"][0]["deliveryMethods"]=["MEETUP"]
        with self.assertRaisesRegex(app.ImportErrorSafe, "FREIGHT"): app.validate_manifest(self.data)

    def test_selection_supports_gapped_completed_photos(self):
        selection=app.select_items(self.items, "exp021-003,exp021-001")
        self.assertEqual(["exp021-001","exp021-003"], [p["key"] for p in selection])
        self.assertEqual(2, len(app.select_items(self.items, limit=2)))

    def test_unknown_duplicate_and_conflicting_selection_rejected(self):
        for ids, limit in [("exp021-001,exp021-001", None), ("exp021-999", None), ("exp021-001", 2), (None,0)]:
            with self.subTest(ids=ids,limit=limit),self.assertRaises(app.ImportErrorSafe): app.select_items(self.items,ids,limit)

    def test_correct_local_identity_only(self):
        good=dict(port=3307,database="maimai",datadir=str(app.ROOT/".local/mysql-data"),readOnly=0)
        app.validate_local_identity(good)
        for field,value in [("port",3306),("database","production"),("datadir","/var/lib/mysql"),("readOnly",1)]:
            bad=dict(good);bad[field]=value
            with self.subTest(field=field),self.assertRaises(app.ImportErrorSafe):app.validate_local_identity(bad)

    def test_preview_makes_only_selects_and_resolves_current_owner(self):
        plan=self.plan()
        self.assertEqual(21,plan["new"][0]["owner"]["seller"])
        self.assertTrue(all(q.startswith("SELECT ") for q in self.db.queries))

    def test_inactive_unapproved_or_unknown_channel_seller_rejected(self):
        for field,value in [("status","DISABLED"),("role",0),("approval","PENDING"),("channel","UNKNOWN"),("channel",None),("source","other"),("matchedImages",2)]:
            with self.subTest(field=field):
                old=self.db.owner[field];self.db.owner[field]=value
                with self.assertRaisesRegex(app.ImportErrorSafe,"NOT_ELIGIBLE"):self.plan()
                self.db.owner[field]=old

    def test_all_known_channels_allow_only_simulated_inventory_without_changing_qualification(self):
        for channel in ("PENDING", "QUALIFIED", "UNQUALIFIED"):
            with self.subTest(channel=channel):
                self.db.owner["channel"]=channel
                plan=self.plan();row=plan["new"][0]
                self.assertEqual(channel,row["owner"]["channel"])
                sql=app.build_sql(plan,19,approve=True)
                self.assertIn(app.literal(app.BATCH),sql)
                self.assertIn(app.guard_equal(row["anchorQuery"],row["owner"]),sql)
                for forbidden in ("UPDATE seller_applications", "INSERT INTO seller_applications", "UPDATE user_roles", "INSERT INTO user_roles", "UPDATE users"):
                    self.assertNotIn(forbidden,sql)
                self.assertEqual(channel,self.db.owner["channel"])

    def test_pending_channel_snapshot_remains_guarded_if_channel_changes_after_preview(self):
        self.db.owner["channel"]="PENDING"
        plan=self.plan();row=plan["new"][0];expected=app.guard_equal(row["anchorQuery"],deepcopy(row["owner"]))
        self.db.owner["channel"]="QUALIFIED"
        sql=app.build_sql(plan,19,approve=True)
        self.assertEqual("PENDING",row["owner"]["channel"])
        self.assertIn(expected,sql)
        self.assertIn("SELECT id FROM seller_applications",sql)
        self.assertIn("FOR UPDATE",sql)

    def test_nonapproved_application_rejected_for_every_known_channel(self):
        for approval in ("PENDING", "REJECTED", None):
            for channel in ("PENDING", "QUALIFIED", "UNQUALIFIED"):
                with self.subTest(approval=approval,channel=channel):
                    self.db.owner.update(approval=approval,channel=channel)
                    with self.assertRaisesRegex(app.ImportErrorSafe,"NOT_ELIGIBLE"):self.plan()

    def test_simulated_inventory_policy_cannot_be_relabelled_as_another_batch(self):
        for field in ("batch","source"):
            data=deepcopy(self.data);data[field]="ordinary-listings"
            with self.subTest(field=field),self.assertRaisesRegex(app.ImportErrorSafe,"BATCH"):
                app.validate_manifest(data)

    def test_anchor_ambiguity_rejected(self):
        with patch.object(self.db,"rows",side_effect=[[],[self.db.owner,self.db.owner]]):
            with self.assertRaisesRegex(app.ImportErrorSafe,"AMBIGUOUS"):self.plan()

    def test_private_address_not_used_as_public_region(self):
        self.db.owner["region"]="上海市测试路18号201室"
        with self.assertRaisesRegex(app.ImportErrorSafe,"PUBLIC_REGION"):self.plan()

    def test_active_exact_parent_and_leaf_required(self):
        for field,value in [("parentStatus","DISABLED"),("status","DISABLED"),("children",1)]:
            with self.subTest(field=field):
                old=self.db.category[field];self.db.category[field]=value
                with self.assertRaises(app.ImportErrorSafe):self.plan()
                self.db.category[field]=old

    def test_missing_leaf_requires_explicit_flag(self):
        self.db.category.update(id=None,status=None)
        with self.assertRaisesRegex(app.ImportErrorSafe,"EXPLICIT_FLAG"):self.plan()
        plan=self.plan(allow_new_categories=True)
        sql=app.build_sql(plan,19)
        self.assertIn("INSERT INTO categories",sql)
        self.assertNotIn("UPDATE categories",sql)
        self.assertIn("AS JSON",sql)

    def test_previously_imported_product_preserved_without_revalidating_old_content(self):
        self.db.existing=[dict(key=app.marker(self.item["key"]),id=701,present=701,source=app.BATCH,status="OFF_SHELF",audit="{}")]
        self.db.owner["status"]="DISABLED"
        plan=self.plan()
        self.assertEqual([],plan["new"])
        self.assertEqual(701,plan["skipped"][0]["id"])
        self.assertEqual("",app.build_sql(plan,19))
        self.assertEqual(1,len(self.db.queries))

    def test_missing_mapped_product_or_duplicate_map_never_recreates(self):
        record=dict(key=app.marker(self.item["key"]),id=701,present=None,source=app.BATCH,status=None,audit="{}")
        self.db.existing=[record]
        with self.assertRaisesRegex(app.ImportErrorSafe,"ID_MAP"):self.plan()
        record["present"]=701;self.db.existing=[record,record]
        with self.assertRaisesRegex(app.ImportErrorSafe,"ID_MAP"):self.plan()

    def test_transaction_contains_rechecks_and_only_new_record_mutations(self):
        sql=app.build_sql(self.plan(),19)
        self.assertLess(sql.index("@@datadir"),sql.index("INSERT INTO products"))
        self.assertLess(sql.index("START TRANSACTION"),sql.index("INSERT INTO products"))
        self.assertGreater(sql.index("COMMIT"),sql.index("INSERT INTO ai_governance_signals"))
        self.assertIn("FOR UPDATE",sql)
        self.assertIn("GET_LOCK",sql)
        self.assertIn("COUNT(*)=0",sql)
        self.assertIn("WHERE id=@product",sql)
        for forbidden in ["DELETE ","UPDATE users","INSERT INTO users","INSERT INTO orders","INSERT INTO community_order_ratings","UPDATE seller_applications"]:
            self.assertNotIn(forbidden,sql)
        self.assertNotIn("'ON_SALE'",sql)

    def test_explicit_approval_keeps_review_audit_revision_notification(self):
        sql=app.build_sql(self.plan(),19,approve=True)
        for expected in ["'ON_SALE'","'APPROVE'","INSERT INTO product_review_logs","INSERT INTO notifications","PRODUCT_REVIEW_APPROVE","'SUBMIT_REVIEW'","'CREATE'"]:
            self.assertIn(expected,sql)

    def test_text_with_quotes_is_hex_encoded_not_sql_interpolated(self):
        self.item["description"]="test'; DELETE FROM users; --中文"
        sql=app.build_sql(self.plan(),19)
        self.assertNotIn("DELETE FROM users",sql)
        self.assertIn(self.item["description"].encode().hex(),sql)


class ImageTests(unittest.TestCase):
    def setUp(self):
        self.tmp=tempfile.TemporaryDirectory();self.root=Path(self.tmp.name)
        self.media=self.root/"media";self.media.mkdir();self.old=self.root/"old";self.old.mkdir()
        self.items=app.validate_manifest(json.loads((MODULE.parent/"manifest.json").read_text("utf-8")))[:2]
        for item in self.items:
            item["images"][0].update(sha256=None,reviewed=False)

    def tearDown(self):self.tmp.cleanup()

    def make(self,item,index=1):
        path=self.media/item["images"][0]["file"]
        im=Image.new("RGB",(320,320),(20*index,40,80));im.save(path,format="JPEG",quality=85)
        return path

    def check(self,selected=None,require=False):
        return app.check_images(selected or self.items,self.items,self.media,self.old,require)

    def test_missing_image_rejected_before_database(self):
        with self.assertRaisesRegex(app.ImportErrorSafe,"MISSING"):self.check()

    def test_previous_catalog_directory_must_exist(self):
        self.make(self.items[0])
        with self.assertRaisesRegex(app.ImportErrorSafe,"PREVIOUS_CATALOG"):
            app.check_images([self.items[0]],self.items,self.media,self.root/"missing")

    def test_unselected_missing_file_does_not_block_ready_subset(self):
        self.make(self.items[0]);self.assertEqual(1,len(self.check([self.items[0]])))

    def test_apply_requires_review_and_frozen_hash(self):
        path=self.make(self.items[0]);item=self.items[0]
        with self.assertRaisesRegex(app.ImportErrorSafe,"REVIEW_REQUIRED"):self.check([item],True)
        item["images"][0].update(reviewed=True,sha256=app.digest(path.read_bytes()))
        self.assertEqual(1,len(self.check([item],True)))
        item["images"][0]["sha256"]="f"*64
        with self.assertRaisesRegex(app.ImportErrorSafe,"HASH_CHANGED"):self.check([item],True)

    def test_duplicate_new_images_rejected_even_outside_selected_subset(self):
        self.make(self.items[0]);self.make(self.items[1])
        with self.assertRaisesRegex(app.ImportErrorSafe,"REUSED"):self.check([self.items[0]])

    def test_old_catalog_image_cannot_be_relabelled(self):
        path=self.make(self.items[0]);(self.old/"old.jpg").write_bytes(path.read_bytes())
        with self.assertRaisesRegex(app.ImportErrorSafe,"REUSED"):self.check([self.items[0]])

    def test_distinct_images_accepted(self):
        self.make(self.items[0],1);self.make(self.items[1],2)
        result=self.check();self.assertEqual(2,len(result));self.assertNotEqual(*[i["sha256"] for i in result.values()])

    def test_invalid_and_truncated_jpeg_rejected(self):
        path=self.make(self.items[0]);raw=path.read_bytes();path.write_bytes(raw[:-20])
        with self.assertRaisesRegex(app.ImportErrorSafe,"JPEG_INVALID"):self.check([self.items[0]])

    def test_too_small_image_rejected(self):
        path=self.media/self.items[0]["images"][0]["file"]
        Image.new("RGB",(128,128)).save(path,format="JPEG")
        with self.assertRaises(app.ImportErrorSafe):self.check([self.items[0]])

    def test_existing_destination_is_never_overwritten(self):
        path=self.make(self.items[0]);info=app.photo(path);info["file"]=path.name
        uploads=self.root/"uploads";uploads.mkdir();dest=uploads/path.name;dest.write_bytes(b"keep old file")
        with self.assertRaisesRegex(app.ImportErrorSafe,"DESTINATION_CONFLICT"):
            app.stage_images({"new":[{"image":info}]},self.media,uploads)
        self.assertEqual(b"keep old file",dest.read_bytes())

    def test_mutation_after_review_cannot_be_staged(self):
        path=self.make(self.items[0]);info=app.photo(path);info["file"]=path.name;path.write_bytes(b"changed")
        with self.assertRaisesRegex(app.ImportErrorSafe,"CHANGED_AFTER_PREVIEW"):
            app.stage_images({"new":[{"image":info}]},self.media,self.root/"uploads")


if __name__ == "__main__":unittest.main()
