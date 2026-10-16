"""Append reviewed experience listings to this project's local development DB only.

No network model calls, new accounts, orders, ratings, destructive rollback or production mode.
Images must be reviewed and hashed in manifest.json before --apply. Preview is read-only.
"""
from __future__ import annotations

import argparse
from copy import deepcopy
from datetime import datetime, timezone
import hashlib
import io
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import uuid

ROOT = Path(__file__).resolve().parents[2]
HERE = Path(__file__).resolve().parent
MANIFEST = HERE / "manifest.json"
MEDIA = ROOT / "backend/src/main/resources/demo/catalog-v3"
OLD_MEDIA = ROOT / "backend/src/main/resources/demo/catalog-v2"
BATCH = "maimai-experience-catalog-021"
ANCHOR_SOURCE = "maimai-experience-045"
ACTION = "CATALOG_EXPANSION_ITEM"
MYSQL = Path("C:/Program Files/MySQL/MySQL Server 8.0/bin/mysql.exe")
CLIENT = ROOT / ".local/private/mysql-client.ini"
UPLOADS = ROOT / ".local/uploads/products"


class ImportErrorSafe(RuntimeError):
    pass


def fail(code):
    raise ImportErrorSafe(code)


def packed(value):
    return json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":"))


def digest(value):
    return hashlib.sha256(value if isinstance(value, bytes) else packed(value).encode()).hexdigest()


def literal(value):
    return "CONVERT(0x" + str(value).encode("utf-8").hex() + " USING utf8mb4)" if str(value) else "''"


def text(value, maximum, minimum=1):
    if not isinstance(value, str) or not minimum <= len(value.strip()) <= maximum or "\0" in value:
        fail("INVALID_TEXT_FIELD")
    return value.strip()


def integer(value, low, high):
    if type(value) is not int or not low <= value <= high:
        fail("INVALID_NUMBER_FIELD")
    return value


def spec_keys(parent, category):
    """Matches ProductSpecifications.fields; keep server-side field whitelist authoritative."""
    path = parent + "/" + category
    base = {"brand", "accessories"}
    if any(t in path for t in ("手机", "电脑", "数码")):
        return base | {"capacity", "battery", "repair"}
    if any(t in path for t in ("家居", "家具")):
        return base | {"dimensions", "disassembly", "transport"}
    if any(t in path for t in ("服饰", "鞋包")):
        return base | {"size", "material", "care"}
    if any(t in path for t in ("图书", "教材")):
        return base | {"edition", "annotations"}
    return base | {"dimensions", "usage"}


def validate_manifest(data):
    if data.get("schemaVersion") != 1 or data.get("batch") != BATCH or data.get("source") != BATCH:
        fail("MANIFEST_BATCH_INVALID")
    items = deepcopy(data.get("items"))
    if not isinstance(items, list) or len(items) != 42:
        fail("EXPECTED_42_ITEMS")
    keys, titles = set(), set()
    for item in items:
        key = item.get("key", "")
        if not re.fullmatch(r"exp021-\d{3}", key) or key in keys:
            fail("ITEM_KEY_INVALID")
        keys.add(key)
        for name, maximum in (("title", 120), ("parent", 50), ("category", 50), ("description", 12000),
                              ("defects", 500), ("supplyNote", 500), ("returnPromise", 200)):
            item[name] = text(item.get(name), maximum)
        if item["title"] in titles:
            fail("DUPLICATE_TITLE")
        titles.add(item["title"])
        if not re.fullmatch(r"item-\d{3}", item.get("sellerAnchor", "")):
            fail("ANCHOR_KEY_INVALID")
        if item.get("condition") not in {"NEW", "LIKE_NEW", "GOOD", "FAIR", "POOR"}:
            fail("CONDITION_INVALID")
        integer(item.get("priceCents"), 1, 99999999)
        integer(item.get("stock"), 1, 10)
        integer(item.get("freightCents"), 0, 100000)
        methods = item.get("deliveryMethods")
        if not isinstance(methods, list) or not methods or len(methods) != len(set(methods)) or not set(methods) <= {"EXPRESS", "MEETUP"}:
            fail("DELIVERY_INVALID")
        if "EXPRESS" not in methods and item["freightCents"]:
            fail("MEETUP_FREIGHT_MUST_BE_ZERO")
        # New batch intentionally uses no restricted shipping coverage or guessed coordinates.
        if item.get("shippingProvinces") != []:
            fail("THIS_BATCH_REQUIRES_UNRESTRICTED_SHIPPING")
        specs = item.get("specifications")
        if not isinstance(specs, dict) or not set(specs) <= spec_keys(item["parent"], item["category"]):
            fail("SPECIFICATION_FIELD_INVALID")
        for value in specs.values():
            text(value, 200)
        images = item.get("images")
        if not isinstance(images, list) or len(images) != 1:
            fail("THIS_BATCH_REQUIRES_ONE_FRONT_PHOTO")
        image = images[0]
        if image.get("view") != "front" or image.get("file") != key + "-front.jpg":
            fail("IMAGE_IDENTITY_INVALID")
        if image.get("sha256") is not None and not re.fullmatch(r"[a-f0-9]{64}", image["sha256"]):
            fail("IMAGE_HASH_INVALID")
        if type(image.get("reviewed")) is not bool:
            fail("IMAGE_REVIEW_INVALID")
    if keys != {f"exp021-{i:03d}" for i in range(1, 43)}:
        fail("ITEM_KEYS_INCOMPLETE")
    return items


def select_items(items, ids=None, limit=None):
    if ids is not None and limit is not None:
        fail("IDS_AND_LIMIT_ARE_EXCLUSIVE")
    if ids is not None:
        wanted = ids.split(",")
        if not wanted or len(wanted) != len(set(wanted)) or not set(wanted) <= {p["key"] for p in items}:
            fail("SELECTION_INVALID")
        return [p for p in items if p["key"] in wanted]
    if limit is not None:
        integer(limit, 1, 42)
        return items[:limit]
    return items


def photo(path):
    if path.is_symlink() or not path.is_file():
        fail("IMAGE_MISSING_OR_LINK:" + path.name)
    raw = path.read_bytes()
    if not 1024 <= len(raw) <= 1048576 or not raw.startswith(b"\xff\xd8") or not raw.endswith(b"\xff\xd9"):
        fail("IMAGE_JPEG_INVALID:" + path.name)
    try:
        from PIL import Image
    except ModuleNotFoundError:
        fail("PILLOW_REQUIRED_USE_EXISTING_PROJECT_PYTHON")
    try:
        with Image.open(io.BytesIO(raw)) as image:
            if image.format != "JPEG" or min(image.size) < 256 or max(image.size) > 1600 or image.width * image.height > 2560000:
                fail("IMAGE_DIMENSIONS_INVALID:" + path.name)
            if image.getexif():
                fail("IMAGE_EXIF_MUST_BE_REMOVED:" + path.name)
            image.load()
            pixel_hash = digest(str(image.size).encode() + image.convert("RGB").tobytes())
            width, height = image.size
    except ImportErrorSafe:
        raise
    except Exception:
        fail("IMAGE_DECODE_FAILED:" + path.name)
    return {"sha256": digest(raw), "pixelHash": pixel_hash, "bytes": len(raw), "width": width, "height": height}


def check_images(selected, all_items, media=MEDIA, old_media=OLD_MEDIA, require_review=False):
    if media.is_symlink() or media.resolve() != media.absolute():
        fail("MEDIA_DIRECTORY_LINK_FORBIDDEN")
    if not old_media.is_dir() or old_media.is_symlink() or old_media.resolve() != old_media.absolute():
        fail("PREVIOUS_CATALOG_DIRECTORY_REQUIRED")
    known = {}
    for path in sorted(old_media.glob("*.jpg")):
        info = photo(path)
        known[info["sha256"]] = known[info["pixelHash"]] = "previous-catalog"
    selected_keys = {p["key"] for p in selected}
    result = {}
    # Inspect all supplied new photos, so selecting disjoint batches cannot hide duplication.
    for item in all_items:
        image = item["images"][0]
        path = media / image["file"]
        if not path.exists() and item["key"] not in selected_keys:
            continue
        info = photo(path)
        if info["sha256"] in known or info["pixelHash"] in known:
            fail("IMAGE_REUSED:" + item["key"])
        known[info["sha256"]] = known[info["pixelHash"]] = item["key"]
        if image["sha256"] is not None and image["sha256"] != info["sha256"]:
            fail("IMAGE_HASH_CHANGED:" + item["key"])
        if item["key"] in selected_keys:
            if require_review and (not image["reviewed"] or image["sha256"] is None):
                fail("IMAGE_REVIEW_REQUIRED:" + item["key"])
            result[item["key"]] = dict(info, file=image["file"], reviewed=image["reviewed"])
    return result


def validate_local_identity(identity, root=ROOT):
    if identity.get("port") != 3307 or identity.get("database") != "maimai" or identity.get("readOnly") != 0:
        fail("NOT_LOCAL_DEVELOPMENT_DATABASE")
    # A loopback tunnel to production still fails this independent server-side datadir check.
    expected = root / ".local/mysql-data"
    if expected.is_symlink() or expected.resolve() != expected.absolute():
        fail("DATABASE_DIRECTORY_LINK_FORBIDDEN")
    expected = expected.resolve()
    if Path(identity.get("datadir", "")).resolve() != expected:
        fail("DATABASE_DATADIR_OUTSIDE_PROJECT")


class LocalDatabase:
    def __init__(self, mysql=MYSQL, client=CLIENT):
        self.mysql, client = Path(mysql).resolve(), Path(client).resolve()
        if not self.mysql.is_file() or not client.is_file() or not client.is_relative_to((ROOT / ".local/private").resolve()):
            fail("LOCAL_MYSQL_OR_PRIVATE_CLIENT_NOT_FOUND")
        self.flags = ["--defaults-file=" + str(client.relative_to(ROOT)), "--host=127.0.0.1", "--port=3307", "--protocol=TCP", "--default-character-set=utf8mb4"]
        # Windows MySQL exposes the native filesystem path as binary system text.
        # Keep ordinary query data strictly UTF-8; decode only this known path separately.
        identities = self.rows("SELECT JSON_OBJECT('port',@@port,'database',DATABASE(),'datadirHex',HEX(@@datadir),'readOnly',@@read_only)")
        if len(identities) != 1:
            fail("DATABASE_IDENTITY_UNAVAILABLE")
        raw_path = bytes.fromhex(identities[0].pop("datadirHex"))
        self.datadir_hex = raw_path.hex().upper()
        try:
            identities[0]["datadir"] = raw_path.decode("utf-8")
        except UnicodeDecodeError:
            if os.name != "nt":
                fail("DATABASE_PATH_ENCODING_INVALID")
            identities[0]["datadir"] = raw_path.decode("mbcs")
        validate_local_identity(identities[0])

    def sql(self, query):
        result = subprocess.run([str(self.mysql), *self.flags, "--connect-timeout=5", "--skip-force", "--skip-reconnect", "--batch", "--raw", "--skip-column-names", "maimai"],
            input=("SET SESSION group_concat_max_len=1048576; SET SESSION innodb_lock_wait_timeout=10;\n" + query).encode(), capture_output=True, cwd=ROOT, timeout=120)
        if result.returncode:
            fail("LOCAL_SQL_FAILED_RAW_SQL_AND_PRIVATE_OUTPUT_WITHHELD")
        return result.stdout.decode("utf-8").strip()

    def rows(self, query):
        return [json.loads(line) for line in self.sql(query + ";").splitlines() if line.strip()]

    def backup(self, folder):
        dump = self.mysql.with_name("mysqldump.exe" if os.name == "nt" else "mysqldump")
        if not dump.is_file():
            fail("MYSQLDUMP_MISSING")
        target = folder / "before.sql"
        with target.open("xb") as handle:
            result = subprocess.run([str(dump), *self.flags, "--single-transaction", "--no-tablespaces", "--set-gtid-purged=OFF", "maimai"], stdout=handle, stderr=subprocess.PIPE, cwd=ROOT, timeout=120)
        if result.returncode or target.stat().st_size == 0:
            fail("BACKUP_FAILED_NO_DATABASE_CHANGE")
        return {"file": target.name, "bytes": target.stat().st_size, "sha256": digest(target.read_bytes())}


def marker(key):
    return BATCH + ":" + key


def anchor_query(anchor):
    image_names = [f"/uploads/products/demo043-{anchor['key']}-{v}.jpg" for v in ("front", "back", "side")]
    return """SELECT JSON_OBJECT('id',p.id,'seller',p.seller_id,'region',p.region,'version',p.version,
      'source',p.experience_source,'status',u.status,'role',EXISTS(SELECT 1 FROM user_roles r WHERE r.user_id=u.id AND r.role='SELLER'),
      'application',(SELECT a.id FROM seller_applications a WHERE a.user_id=u.id ORDER BY a.created_at DESC,a.id DESC LIMIT 1),
      'approval',(SELECT a.status FROM seller_applications a WHERE a.user_id=u.id ORDER BY a.created_at DESC,a.id DESC LIMIT 1),
      'channel',(SELECT a.channel_status FROM seller_applications a WHERE a.user_id=u.id ORDER BY a.created_at DESC,a.id DESC LIMIT 1),
      'images',(SELECT COUNT(*) FROM product_images i WHERE i.product_id=p.id),
      'matchedImages',(SELECT COUNT(DISTINCT i.path) FROM product_images i WHERE i.product_id=p.id AND i.path IN (""" + ",".join(map(literal, image_names)) + ")) ) FROM products p JOIN users u ON u.id=p.seller_id WHERE p.title=" + literal(anchor["title"]) + " AND p.experience_source=" + literal(ANCHOR_SOURCE)


def category_query(parent, child):
    return "SELECT JSON_OBJECT('parentId',p.id,'parentStatus',p.status,'id',c.id,'status',c.status,'children',(SELECT COUNT(*) FROM categories x WHERE x.parent_id=c.id)) FROM categories p LEFT JOIN categories c ON c.parent_id=p.id AND c.name=" + literal(child) + " WHERE p.parent_id IS NULL AND p.name=" + literal(parent)


def existing_map(db, keys):
    reasons = ",".join(literal(marker(k)) for k in keys)
    rows = db.rows("SELECT JSON_OBJECT('key',a.reason,'id',a.target_id,'audit',a.after_state,'present',p.id,'source',p.experience_source,'status',p.status) FROM admin_audit_logs a LEFT JOIN products p ON p.id=a.target_id WHERE a.action=" + literal(ACTION) + " AND a.reason IN(" + reasons + ")")
    result = {}
    for row in rows:
        key = row["key"].removeprefix(BATCH + ":")
        if key in result or row["present"] is None or row["source"] != BATCH:
            fail("EXISTING_ID_MAP_INCONSISTENT:" + key)
        result[key] = row
    return result


def plan_import(db, items, images, anchors, allow_new_categories=False):
    mapped = existing_map(db, [p["key"] for p in items])
    new, skipped = [], []
    for item in items:
        if item["key"] in mapped:
            skipped.append({"key": item["key"], "id": mapped[item["key"]]["id"], "reason": "ALREADY_IMPORTED_PRESERVE_ALL_CURRENT_FIELDS"})
            continue
        anchor = anchors.get(item["sellerAnchor"])
        if anchor is None:
            fail("UNKNOWN_SELLER_ANCHOR")
        query = anchor_query(anchor)
        matches = db.rows(query)
        if len(matches) != 1:
            fail("SELLER_ANCHOR_AMBIGUOUS:" + item["key"])
        owner = matches[0]
        if owner["source"] != ANCHOR_SOURCE or owner["status"] != "ACTIVE" or owner["role"] != 1 or owner["approval"] != "APPROVED" or owner["channel"] != "QUALIFIED" or owner["images"] != 3 or owner["matchedImages"] != 3:
            fail("SELLER_ANCHOR_NOT_ELIGIBLE:" + item["key"])
        region = text(owner["region"], 100)
        # Only inherit the seller's public region, never a private address or guessed location.
        if not re.fullmatch(r"[\u3400-\u9fffA-Za-z ·-]{2,100}", region) or any(s in region for s in ("号", "栋", "室", "单元")):
            fail("PUBLIC_REGION_REQUIRES_REVIEW:" + item["key"])
        category_sql = category_query(item["parent"], item["category"])
        categories = db.rows(category_sql)
        if len(categories) != 1 or categories[0]["parentStatus"] != "ACTIVE":
            fail("PARENT_CATEGORY_INVALID:" + item["key"])
        cat = categories[0]
        if cat["id"] is None:
            if not allow_new_categories:
                fail("CATEGORY_MISSING_EXPLICIT_FLAG_REQUIRED:" + item["key"])
        elif cat["status"] != "ACTIVE" or cat["children"] != 0:
            fail("ACTIVE_LEAF_CATEGORY_REQUIRED:" + item["key"])
        row = {"item": item, "image": images[item["key"]], "owner": owner, "category": cat, "region": region,
               "anchorQuery": query, "categoryQuery": category_sql}
        row["fingerprint"] = digest({"item": item, "image": row["image"]["sha256"], "seller": owner["seller"], "region": region})
        new.append(row)
    return {"new": new, "skipped": skipped}


def guard_equal(query, expected):
    # MySQL JSON comparison preserves number/null types and ignores object key order.
    check = "record = CAST(" + literal(packed(expected)) + " AS JSON)"
    aliased = query.replace(") FROM products p", ") record FROM products p", 1).replace(") FROM categories p", ") record FROM categories p", 1)
    return "INSERT INTO expansion_guard SELECT IF(COUNT(*)=1 AND MIN(" + check + ")=1,1,0) FROM (" + aliased + ") guarded"


def build_sql(plan, admin, approve=False, datadir_hex=None):
    integer(admin, 1, 2**63 - 1)
    if not plan["new"]:
        return ""
    statements = ["CREATE TEMPORARY TABLE expansion_guard(ok TINYINT NOT NULL CHECK(ok=1))", f"INSERT INTO expansion_guard VALUES(GET_LOCK({literal(BATCH)},0))", "START TRANSACTION",
        "SELECT lock_name FROM governance_locks WHERE lock_name IN ('ACCOUNT_ROLES','CATEGORIES') ORDER BY lock_name FOR UPDATE"]
    expected_dir = (ROOT / ".local/mysql-data").resolve().as_posix().lower()
    if datadir_hex is not None:
        if not re.fullmatch(r"[0-9A-F]+", datadir_hex):
            fail("DATABASE_PATH_ENCODING_INVALID")
        directory_guard = "HEX(@@datadir)=" + literal(datadir_hex)
    else:
        directory_guard = "LOWER(TRIM(TRAILING '/' FROM REPLACE(@@datadir,CHAR(92),'/')))=" + literal(expected_dir)
    statements.insert(1, "INSERT INTO expansion_guard VALUES(IF(@@port=3307 AND @@read_only=0 AND DATABASE()='maimai' AND " + directory_guard + ",1,0))")
    sellers = sorted({admin} | {r["owner"]["seller"] for r in plan["new"]})
    statements += ["SELECT id FROM users WHERE id IN(" + ",".join(map(str, sellers)) + ") ORDER BY id FOR UPDATE",
        f"INSERT INTO expansion_guard SELECT IF(COUNT(*)=1,1,0) FROM users u WHERE u.id={admin} AND u.status='ACTIVE' AND EXISTS(SELECT 1 FROM user_roles r WHERE r.user_id=u.id AND r.role='SUPER_ADMIN')",
        "SELECT id FROM seller_applications WHERE user_id IN(" + ",".join(map(str, sellers)) + ") ORDER BY id FOR UPDATE",
        "SELECT id FROM products WHERE id IN(" + ",".join(map(str, sorted({r["owner"]["id"] for r in plan["new"]}))) + ") ORDER BY id FOR UPDATE"]
    checked_anchors, checked_cats = set(), set()
    for row in plan["new"]:
        if row["owner"]["id"] not in checked_anchors:
            statements.append(guard_equal(row["anchorQuery"], row["owner"]))
            checked_anchors.add(row["owner"]["id"])
        cat_key = (row["item"]["parent"], row["item"]["category"])
        if cat_key not in checked_cats:
            statements.append(guard_equal(row["categoryQuery"], row["category"]))
            checked_cats.add(cat_key)
    created_categories = set()
    for row in plan["new"]:
        p, cat, owner, im = row["item"], row["category"], row["owner"], row["image"]
        key = p["key"]
        statements.append(f"INSERT INTO expansion_guard SELECT IF(COUNT(*)=0,1,0) FROM admin_audit_logs WHERE action={literal(ACTION)} AND reason={literal(marker(key))}")
        category_key = (p["parent"], p["category"])
        if cat["id"] is None and category_key not in created_categories:
            statements += [f"INSERT INTO categories(name,parent_id,sort,status) VALUES({literal(p['category'])},{cat['parentId']},100,'ACTIVE')",
                f"INSERT INTO admin_audit_logs(admin_id,action,target_type,target_id,reason) VALUES({admin},'CATALOG_EXPANSION_CATEGORY','CATEGORY',LAST_INSERT_ID(),{literal(BATCH)})"]
            created_categories.add(category_key)
        statements.append(f"SET @cat=(SELECT id FROM categories WHERE parent_id={cat['parentId']} AND name={literal(p['category'])} AND status='ACTIVE')")
        statements.append("INSERT INTO expansion_guard VALUES(IF(@cat IS NOT NULL,1,0))")
        values = [str(owner["seller"]), "@cat", literal(p["title"]), literal(p["description"]), literal(p["condition"]), literal(p["defects"]), str(p["priceCents"]), str(p["stock"]), literal(row["region"]), literal(",".join(p["deliveryMethods"])), str(p["freightCents"]), literal(p["returnPromise"]), literal(BATCH), literal(p["supplyNote"]), literal(packed(p["specifications"]))]
        statements += ["INSERT INTO products(seller_id,category_id,title,description,item_condition,defects,price_cents,stock_available,region,delivery_methods,freight_cents,return_promise,experience_source,supply_note,specifications) VALUES(" + ",".join(values) + ")", "SET @product=LAST_INSERT_ID()",
            f"INSERT INTO product_images(product_id,path,sort) VALUES(@product,{literal('/uploads/products/' + im['file'])},0)", "SET @image=LAST_INSERT_ID()",
            f"INSERT INTO stock_logs(product_id,delta_available,reason,ref_type,ref_id) VALUES(@product,{p['stock']},'EXPERIENCE_SUPPLY','ADMIN',{admin})"]
        content = {"title": p["title"], "categoryId": 0, "description": p["description"], "condition": p["condition"], "defects": p["defects"], "priceCents": p["priceCents"], "region": row["region"], "deliveryMethods": p["deliveryMethods"], "freightCents": p["freightCents"], "returnPromise": p["returnPromise"], "shippingProvinces": [], "latitude": None, "longitude": None, "images": [], "status": "DRAFT", "reviewReason": None, "experienceSource": BATCH, "supplyNote": p["supplyNote"], "specifications": p["specifications"]}
        statements += [f"SET @content=JSON_SET({literal(packed(content))},'$.categoryId',@cat,'$.images',JSON_ARRAY(JSON_OBJECT('id',@image,'path',{literal('/uploads/products/' + im['file'])},'sort',0)))",
            f"INSERT INTO product_revisions(product_id,version,action,actor_id,content) VALUES(@product,1,'CREATE',{owner['seller']},@content)",
            "UPDATE products SET status='PENDING_REVIEW',version=version+1 WHERE id=@product",
            "SET @content=JSON_SET(@content,'$.status','PENDING_REVIEW')",
            f"INSERT INTO product_revisions(product_id,version,action,actor_id,content) VALUES(@product,2,'SUBMIT_REVIEW',{owner['seller']},@content)"]
        if approve:
            reason = "已人工核对本批体验商品资料与生成图片，来源独立标识；不代表真实供货或检测。"
            statements += [f"UPDATE products SET status='ON_SALE',version=version+1,review_reason={literal(reason)} WHERE id=@product",
                f"SET @content=JSON_SET(@content,'$.status','ON_SALE','$.reviewReason',{literal(reason)})",
                f"INSERT INTO product_revisions(product_id,version,action,actor_id,content) VALUES(@product,3,'APPROVE',{admin},@content)",
                f"INSERT INTO product_review_logs(product_id,reviewer_id,action,reason,from_status,to_status) VALUES(@product,{admin},'APPROVE',{literal(reason)},'PENDING_REVIEW','ON_SALE')",
                f"INSERT INTO admin_audit_logs(admin_id,action,target_type,target_id,reason,before_state,after_state) VALUES({admin},'PRODUCT_REVIEW_APPROVE','product',@product,{literal(reason)},'PENDING_REVIEW','ON_SALE')",
                f"INSERT INTO notifications(user_id,type,title,content,target_path) VALUES({owner['seller']},'PRODUCT_REVIEW',{literal('商品审核通过')},{literal('商品《'+p['title']+'》已通过审核并上架。体验来源说明保留。')},CONCAT('/publish/',@product))"]
        audit = {"batch": BATCH, "key": key, "contentHash": row["fingerprint"], "imageSha256": im["sha256"]}
        statements += ["INSERT INTO ai_governance_signals(source_type,source_id) VALUES('PRODUCT',@product)",
            f"INSERT INTO admin_audit_logs(admin_id,action,target_type,target_id,reason,after_state) VALUES({admin},{literal(ACTION)},'PRODUCT',@product,{literal(marker(key))},{literal(packed(audit))})"]
    statements += ["COMMIT", f"SELECT RELEASE_LOCK({literal(BATCH)})"]
    return ";\n".join(statements) + ";\n"


def private_folder():
    folder = ROOT / ".local/private/catalog-expansion" / (datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ") + "-" + uuid.uuid4().hex[:8])
    folder.mkdir(parents=True, mode=0o700)
    if os.name == "nt":
        identity = subprocess.check_output(["whoami"], text=True).strip()
        result = subprocess.run(["icacls.exe", str(folder), "/inheritance:r", "/grant:r", identity + ":(OI)(CI)(F)", "SYSTEM:(OI)(CI)(F)"], capture_output=True)
        if result.returncode:
            fail("BACKUP_PERMISSION_FAILED")
    return folder


def save_json(path, value):
    with path.open("x", encoding="utf-8") as handle:
        handle.write(packed(value) + "\n")


def stage_images(plan, media=MEDIA, uploads=UPLOADS):
    if uploads.is_symlink() or uploads.resolve() != uploads.absolute():
        fail("UPLOAD_DIRECTORY_LINK_FORBIDDEN")
    uploads.mkdir(parents=True, exist_ok=True)
    files = []
    for row in plan["new"]:
        info = row["image"]
        raw = (media / info["file"]).read_bytes()
        if digest(raw) != info["sha256"]:
            fail("IMAGE_CHANGED_AFTER_PREVIEW")
        dest = uploads / info["file"]
        if dest.is_symlink() or (dest.exists() and digest(dest.read_bytes()) != info["sha256"]):
            fail("UPLOAD_DESTINATION_CONFLICT")
        created = False
        if not dest.exists():
            with dest.open("xb") as handle:
                handle.write(raw)
                handle.flush()
                os.fsync(handle.fileno())
            created = True
        files.append({"file": str(dest.relative_to(ROOT)), "sha256": info["sha256"], "created": created})
    return files


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--ids", help="Comma-separated exp021-001 IDs, not numeric database IDs")
    parser.add_argument("--limit", type=int)
    parser.add_argument("--image-audit", action="store_true", help="Read photos and emit hashes; no DB connection or manifest mutation")
    parser.add_argument("--apply", action="store_true")
    parser.add_argument("--approve", action="store_true", help="Explicitly record review approval for newly inserted rows only; otherwise pending review")
    parser.add_argument("--create-missing-categories", action="store_true", help="Only a missing direct child of a uniquely matched active existing parent")
    parser.add_argument("--admin-id", type=int)
    parser.add_argument("--mysql", type=Path, default=MYSQL)
    parser.add_argument("--client-file", type=Path, default=CLIENT)
    args = parser.parse_args(argv)
    if args.image_audit and args.apply:
        fail("IMAGE_AUDIT_IS_READ_ONLY")
    items = validate_manifest(json.loads(MANIFEST.read_text("utf-8")))
    selected = select_items(items, args.ids, args.limit)
    images = check_images(selected, items, require_review=args.apply)
    if args.image_audit:
        print(json.dumps({"status": "IMAGE_AUDIT", "images": images}, ensure_ascii=True))
        return
    database = LocalDatabase(args.mysql, args.client_file)
    anchors = {p["key"]: p for p in json.loads((OLD_MEDIA / "manifest.json").read_text("utf-8"))["items"]}
    plan = plan_import(database, selected, images, anchors, args.create_missing_categories)
    output = {"status": "PREVIEW_READY", "newItems": [p["item"]["key"] for p in plan["new"]], "skipped": plan["skipped"], "targetState": "ON_SALE" if args.approve else "PENDING_REVIEW", "changed": False}
    if args.apply and plan["new"]:
        admins = database.rows("SELECT JSON_OBJECT('id',u.id) FROM users u WHERE u.status='ACTIVE' AND EXISTS(SELECT 1 FROM user_roles r WHERE r.user_id=u.id AND r.role='SUPER_ADMIN') ORDER BY u.id")
        admin = args.admin_id if args.admin_id is not None else admins[0]["id"] if len(admins) == 1 else None
        if admin not in [a["id"] for a in admins]:
            fail("ACTIVE_SUPER_ADMIN_REQUIRED_USE_ADMIN_ID")
        sql = build_sql(plan, admin, args.approve, database.datadir_hex)
        folder = private_folder()
        backup = database.backup(folder)
        save_json(folder / "before-manifest.json", {"batch": BATCH, "items": selected, "plan": plan, "backup": backup})
        files = stage_images(plan)
        save_json(folder / "installed-images.json", files)
        # Any SQL error terminates the client; the active transaction rolls back on disconnect.
        database.sql(sql)
        mapping = existing_map(database, [p["key"] for p in selected])
        if set(mapping) != {p["key"] for p in selected}:
            fail("POST_APPLY_ID_MAP_MISMATCH_BACKUP_RETAINED")
        save_json(folder / "idmap.json", {"batch": BATCH, "items": mapping})
        save_json(folder / "rollback-plan.json", {"batch": BATCH, "backup": backup, "newIds": [mapping[p["item"]["key"]]["id"] for p in plan["new"]], "images": files,
            "strategy": "通过正常后台下架本批商品。保留订单、图片、修订及审计；不删除账户、不覆盖用户后改商品、不使用全库恢复覆盖新增交易。",
            "guards": ["重新核验每条audit批次与商品experience_source", "出现订单或用户编辑时只停止后续售卖并人工核查", "SQL事务失败时保留未引用图片及清单，禁止自动删除"]})
        output.update(status="APPLIED_LOCAL", changed=True, receipt=str(folder.relative_to(ROOT)), insertedIds=[mapping[p["item"]["key"]]["id"] for p in plan["new"]])
    elif not plan["new"]:
        output["status"] = "NO_CHANGES"
    print(json.dumps(output, ensure_ascii=True))


if __name__ == "__main__":
    try:
        main()
    except ImportErrorSafe as error:
        print(json.dumps({"error": str(error), "changed": "unconfirmed; inspect private receipt if apply started"}, ensure_ascii=True), file=sys.stderr)
        sys.exit(2)
