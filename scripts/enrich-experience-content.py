"""Maintain explicitly marked historical experience content, never real transactions.

Preview is the default. --apply backs up the local database before a guarded atomic
batch. Only 127.0.0.1:3307/maimai is supported. No service configuration, credentials,
identity, stock, price, payment status, or historical order snapshots are changed.
This is direct-SQL maintenance of imported experience records; it does not replay
registration or trade APIs, send mail, enqueue AI work, or contact external services.
"""

import argparse
from collections import Counter
from copy import deepcopy
from datetime import datetime, timezone
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import uuid


ROOT = Path(__file__).resolve().parent.parent
SOURCE = "maimai-experience-045"
BATCH = "maimai-experience-content-063"
CONTENT_PATH = ROOT / "scripts/data/experience-content.json"
BASELINE_PATH = ROOT / "deploy/catalog-experience-v070.json"
DEFAULT_MYSQL = Path("C:/Program Files/MySQL/MySQL Server 8.0/bin/mysql.exe")
DEFAULT_CLIENT = ROOT / ".local/private/mysql-client.ini"


class MaintenanceError(RuntimeError):
    """Messages contain only fixed diagnostic codes, never SQL or record text."""


def json_text(value):
    return json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":"))


def digest(value):
    return hashlib.sha256(json_text(value).encode("utf-8")).hexdigest()


def literal(value):
    if value is None:
        return "NULL"
    return "CONVERT(0x" + str(value).encode("utf-8").hex() + " USING utf8mb4)" if str(value) else "''"


def validate_package(package, baseline):
    if package.get("schemaVersion") != 1 or package.get("source") != SOURCE or baseline.get("source") != SOURCE:
        raise MaintenanceError("CONTENT_SOURCE_INVALID")
    items, original = package.get("items", []), baseline.get("items", [])
    if len(items) != 65 or len(original) != 65:
        raise MaintenanceError("CONTENT_COUNT_INVALID")
    if len({p.get("key") for p in items}) != 65:
        raise MaintenanceError("CONTENT_KEYS_NOT_UNIQUE")
    for number, (item, old) in enumerate(zip(items, original), 1):
        if (item.get("key"), item.get("title"), item.get("source"), item.get("rating")) != (
                f"item-{number:03d}", old["title"], SOURCE, old["rating"]):
            raise MaintenanceError("CONTENT_IDENTITY_OR_RATING_CHANGED")
        if item["key"] != old["key"] or item["rating"] not in range(1, 6):
            raise MaintenanceError("CONTENT_BASELINE_INVALID")
        for name, limit in {"description": 10000, "supplyNote": 500, "question": 1000, "answer": 1000, "review": 500}.items():
            value = item.get(name)
            if not isinstance(value, str) or not value.strip() or len(value) > limit or "\0" in value:
                raise MaintenanceError("CONTENT_TEXT_INVALID")
    return items


def expected_images(key):
    return {f"/uploads/products/demo043-{key}-{view}.jpg" for view in ("front", "back", "side")}


def plan_content(items, originals, snapshot):
    """Pure planning: protected conflicts are skipped; broken trade links reject the batch."""
    originals = {p["key"]: p for p in originals}
    changes, guards, outcomes = [], {}, []
    for item in items:
        key, old = item["key"], originals[item["key"]]
        outcome = {"key": key, "status": "SKIPPED", "changes": [], "skipped": []}
        outcomes.append(outcome)
        candidates = [p for p in snapshot.get("PRODUCT", []) if p["title"] == item["title"]]
        if len(candidates) != 1:
            outcome["skipped"].append("product-missing-or-ambiguous")
            continue
        product = candidates[0]
        if product["source"] != SOURCE:
            outcome["skipped"].append("not-historical-experience")
            continue
        images = product["content"]["images"]
        if len(images) != 3 or {image["path"] for image in images} != expected_images(key):
            outcome["skipped"].append("product-media-changed-or-ambiguous")
            continue
        if product["status"] != "ON_SALE":
            outcome["skipped"].append("product-not-on-sale")
            continue
        if not product["sellerActive"] or not product["sellerRole"] or product["sellerApproval"] != "APPROVED":
            raise MaintenanceError("SELLER_RELATION_INVALID")
        order_no = "MX045" + key[-3:]
        orders = [o for o in snapshot.get("ORDER", []) if o["orderNo"] == order_no]
        if not orders:
            outcome["skipped"].append("historical-order-missing")
            continue
        if len(orders) != 1:
            raise MaintenanceError("HISTORICAL_ORDER_AMBIGUOUS")
        order = orders[0]
        if (order["source"] != SOURCE or order["seller"] != product["seller"]
                or order["buyer"] == order["seller"] or not order["buyerActive"] or not order["sellerActive"]
                or order["fulfillment"] != "COMPLETED" or order["payStatus"] != "PAID"
                or order["refundStatus"] != "NONE" or not order["completedAt"]
                or len(order["items"]) != 1 or order["items"][0]["product"] != product["id"]
                or order["items"][0]["seller"] != product["seller"] or order["items"][0]["quantity"] != 1):
            raise MaintenanceError("HISTORICAL_ORDER_RELATION_INVALID")
        receipts = [p for p in order["receipts"] if p["status"] == "PAID"]
        if len(receipts) != 1 or not receipts[0]["simulated"] or receipts[0]["amount"] != order["total"]:
            raise MaintenanceError("HISTORICAL_PAYMENT_RELATION_INVALID")
        linked = [o for o in snapshot.get("ORDER", []) if o["source"] == SOURCE
                  and any(line["product"] == product["id"] for line in o["items"])]
        if len(linked) != 1:
            raise MaintenanceError("PRODUCT_HISTORY_AMBIGUOUS")
        rating_rows = [r for r in snapshot.get("RATING", []) if r["order"] == order["id"]]
        parties = {(order["buyer"], order["seller"]), (order["seller"], order["buyer"])}
        if any((r["rater"], r["ratee"]) not in parties for r in rating_rows):
            raise MaintenanceError("RATING_PARTICIPANT_INVALID")

        product_current = (product["description"], product["supplyNote"])
        product_target = (item["description"], item["supplyNote"])
        product_seed = (product["seedDescription"], product["seedSupplyNote"])
        if product_current != product_target and (
                not product["seedDescription"] or product["seedSupplyNote"] != old["supplyNote"]
                or product_current != product_seed):
            outcome["skipped"].append("product-content-edited-after-import")
            continue

        item_changes, item_guards = [], [("PRODUCT", product), ("ORDER", order)]

        def change(kind, row, after):
            item_changes.append({"kind": kind, "id": row["id"], "key": key, "before": row, "after": after})
            item_guards.append((kind, row))

        if product_current != product_target:
            change("PRODUCT", product, {"description": item["description"], "supplyNote": item["supplyNote"]})
        target_ratings = [r for r in rating_rows if r["rater"] == order["buyer"] and r["ratee"] == order["seller"]]
        if len(target_ratings) != 1:
            outcome["skipped"].append("rating-missing-or-ambiguous")
        else:
            rating = target_ratings[0]
            if (rating["hidden"] or rating["reports"] or rating["rating"] != old["rating"]
                    or rating.get("followup") is not None or rating.get("images", 0)
                    or any(rating.get(field) is not None for field in ("descriptionRating", "communicationRating", "fulfillmentRating"))
                    or rating["comment"] not in (old["review"], item["review"])):
                outcome["skipped"].append("rating-edited-hidden-or-reported")
            elif rating["comment"] != item["review"]:
                change("RATING", rating, {"comment": item["review"]})
        questions = [c for c in snapshot.get("COMMENT", []) if c["product"] == product["id"]
                     and c["author"] == order["buyer"] and c["replyTo"] is None
                     and c["content"] in (old["question"], item["question"])]
        if len(questions) != 1:
            outcome["skipped"].append("question-edited-missing-or-ambiguous")
        else:
            question = questions[0]
            answers = [c for c in snapshot.get("COMMENT", []) if c["product"] == product["id"]
                       and c["author"] == order["seller"] and c["replyTo"] == question["id"]
                       and c["content"] in (old["answer"], item["answer"])]
            if len(answers) != 1:
                outcome["skipped"].append("answer-edited-missing-or-ambiguous")
            elif any(c["status"] != "PUBLISHED" or c["reports"] for c in (question, answers[0])):
                outcome["skipped"].append("discussion-hidden-or-reported")
            else:
                for comment, text in ((question, item["question"]), (answers[0], item["answer"])):
                    item_guards.append(("COMMENT", comment))
                    if comment["content"] != text:
                        change("COMMENT", comment, {"content": text})
        if item_changes:
            changes.extend(item_changes)
            for kind, row in item_guards:
                guards[(kind, row["id"])] = row
            outcome["changes"] = [row["kind"] for row in item_changes]
            outcome["status"] = "PARTIAL" if outcome["skipped"] else "READY"
        elif not outcome["skipped"]:
            outcome["status"] = "ALREADY_CURRENT"
    return {"changes": changes, "guards": guards, "outcomes": outcomes}


def aggregate_json(expression, table, condition, order):
    return ("JSON_EXTRACT(CONCAT('[',(SELECT COALESCE(GROUP_CONCAT(" + expression
            + " ORDER BY " + order + " SEPARATOR ','),'') FROM " + table + " WHERE " + condition + "),']'),'$')")


IMAGES = aggregate_json("JSON_OBJECT('id',i.id,'path',i.path,'sort',i.sort)", "product_images i", "i.product_id=p.id", "i.sort,i.id")
ORDER_ITEMS = aggregate_json("JSON_OBJECT('product',i.product_id,'seller',p.seller_id,'quantity',i.quantity)",
                            "order_items i LEFT JOIN products p ON p.id=i.product_id", "i.order_id=o.id", "i.id")
PAYMENTS = aggregate_json("JSON_OBJECT('id',pr.id,'status',pr.status,'amount',pr.amount_cents,'simulated',pr.simulated)",
                         "payment_requests pr", "pr.order_id=o.id", "pr.id")


def csv_json(field):
    return f"IF({field} IS NULL OR {field}='',JSON_ARRAY(),JSON_EXTRACT(CONCAT('[',REPLACE(JSON_QUOTE({field}),',','\",\"'),']'),'$'))"


PRODUCT_CONTENT = f"""JSON_OBJECT('title',p.title,'categoryId',p.category_id,'description',p.description,
 'condition',p.item_condition,'defects',p.defects,'priceCents',p.price_cents,'region',p.region,
 'deliveryMethods',{csv_json('p.delivery_methods')},'freightCents',p.freight_cents,'returnPromise',p.return_promise,
 'shippingProvinces',{csv_json('p.shipping_provinces')},'latitude',p.latitude,'longitude',p.longitude,
 'images',{IMAGES},'status',p.status,'reviewReason',p.review_reason,'experienceSource',p.experience_source,
 'supplyNote',p.supply_note,'specifications',JSON_EXTRACT(COALESCE(NULLIF(p.specifications,''),'{{}}'),'$'))"""

QUERIES = {
    "PRODUCT": ("products", "p", f"""SELECT JSON_OBJECT('id',p.id,'seller',p.seller_id,'title',p.title,
 'source',p.experience_source,'description',p.description,'supplyNote',p.supply_note,'status',p.status,'version',p.version,
 'content',{PRODUCT_CONTENT},'revisionVersion',(SELECT COALESCE(MAX(v.version),0) FROM product_revisions v WHERE v.product_id=p.id),
 'seedDescription',(SELECT JSON_UNQUOTE(JSON_EXTRACT(v.content,'$.description')) FROM product_revisions v WHERE v.product_id=p.id AND v.action='EXPERIENCE_SUPPLY' ORDER BY v.version DESC LIMIT 1),
 'seedSupplyNote',(SELECT JSON_UNQUOTE(JSON_EXTRACT(v.content,'$.supplyNote')) FROM product_revisions v WHERE v.product_id=p.id AND v.action='EXPERIENCE_SUPPLY' ORDER BY v.version DESC LIMIT 1),
 'sellerActive',EXISTS(SELECT 1 FROM users u WHERE u.id=p.seller_id AND u.status='ACTIVE'),
 'sellerRole',EXISTS(SELECT 1 FROM user_roles r WHERE r.user_id=p.seller_id AND r.role='SELLER'),
 'sellerApproval',(SELECT a.status FROM seller_applications a WHERE a.user_id=p.seller_id ORDER BY a.created_at DESC,a.id DESC LIMIT 1)) record FROM products p"""),
    "ORDER": ("orders", "o", f"""SELECT JSON_OBJECT('id',o.id,'orderNo',o.order_no,'source',o.experience_source,
 'buyer',o.buyer_id,'seller',o.seller_id,'fulfillment',o.fulfillment_status,'payStatus',o.pay_status,'refundStatus',o.refund_status,
 'completedAt',o.completed_at,'total',o.total_cents,'items',{ORDER_ITEMS},'receipts',{PAYMENTS},
 'buyerActive',EXISTS(SELECT 1 FROM users u WHERE u.id=o.buyer_id AND u.status='ACTIVE'),
 'sellerActive',EXISTS(SELECT 1 FROM users u WHERE u.id=o.seller_id AND u.status='ACTIVE')) record FROM orders o"""),
    "RATING": ("community_order_ratings", "r", """SELECT JSON_OBJECT('id',r.id,'order',r.order_id,
 'rater',r.rater_id,'ratee',r.ratee_id,'rating',r.rating,'comment',r.comment,'hidden',r.is_hidden,
 'followup',r.followup,'descriptionRating',r.description_rating,'communicationRating',r.communication_rating,
 'fulfillmentRating',r.fulfillment_rating,'images',(SELECT COUNT(*) FROM rating_images i WHERE i.rating_id=r.id),
 'reports',(SELECT COUNT(*) FROM community_reports cr WHERE cr.resource_type='ORDER_REVIEW' AND cr.resource_id=r.id)) record FROM community_order_ratings r"""),
    "COMMENT": ("product_comments", "c", """SELECT JSON_OBJECT('id',c.id,'product',c.product_id,'author',c.author_id,
 'replyTo',c.reply_to_id,'content',c.content,'status',c.status,'updatedAt',c.updated_at,
 'reports',(SELECT COUNT(*) FROM community_reports cr WHERE cr.resource_type='PRODUCT_COMMENT' AND cr.resource_id=c.id)) record FROM product_comments c"""),
}


class LocalDatabase:
    def __init__(self, mysql, client_file):
        self.mysql = Path(mysql).resolve()
        self.client_file = Path(client_file).resolve()
        if not self.mysql.is_file() or self.mysql.name.lower() not in ("mysql.exe", "mysql"):
            raise MaintenanceError("MYSQL_EXECUTABLE_NOT_FOUND")
        if not self.client_file.is_file():
            raise MaintenanceError("MYSQL_CLIENT_FILE_NOT_FOUND")
        # MySQL's Windows defaults-file reader cannot reliably open an absolute
        # Unicode workspace path. cwd is ROOT; keep project paths relative.
        client_argument = self.client_file.relative_to(ROOT) if self.client_file.is_relative_to(ROOT) else self.client_file
        self.flags = ["--defaults-file=" + str(client_argument), "--host=127.0.0.1", "--port=3307", "--protocol=TCP",
                      "--default-character-set=utf8mb4"]

    def sql(self, query):
        result = subprocess.run([str(self.mysql), *self.flags, "--connect-timeout=5", "--skip-force", "--skip-reconnect", "--batch", "--raw", "--skip-column-names", "maimai"],
                                input=("SET SESSION group_concat_max_len=1048576;\n" + query).encode("utf-8"), capture_output=True, cwd=ROOT)
        if result.returncode:
            raise MaintenanceError("LOCAL_DATABASE_OPERATION_FAILED; SQL and private output withheld")
        return result.stdout.decode("utf-8").strip()

    def snapshot(self, titles):
        title_sql = ",".join(literal(title) for title in titles)
        filters = {
            "PRODUCT": "p.title IN (" + title_sql + ")",
            "ORDER": f"o.experience_source={literal(SOURCE)} OR o.order_no REGEXP '^MX045[0-9]{{3}}$'",
            "RATING": f"r.order_id IN(SELECT id FROM orders WHERE experience_source={literal(SOURCE)})",
            "COMMENT": f"c.product_id IN(SELECT id FROM products WHERE experience_source={literal(SOURCE)})",
        }
        snapshot = {}
        for kind, (_, _, query) in QUERIES.items():
            rows = self.sql(f"SELECT JSON_OBJECT('value',record,'fingerprint',SHA2(record,256)) FROM ({query} WHERE {filters[kind]}) snapshot;")
            values = []
            for line in rows.splitlines():
                parsed = json.loads(line)
                value = parsed["value"]
                if isinstance(value, str):
                    value = json.loads(value)
                value["_fingerprint"] = parsed["fingerprint"]
                values.append(value)
            snapshot[kind] = values
        return snapshot

    def backup(self):
        dump = self.mysql.with_name("mysqldump.exe" if self.mysql.suffix.lower() == ".exe" else "mysqldump")
        if not dump.is_file():
            raise MaintenanceError("MYSQLDUMP_NOT_FOUND")
        # This is a new private backup directory, never an existing user directory.
        stamp = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ") + "-" + uuid.uuid4().hex[:8]
        folder = ROOT / ".local/private/experience-content-backups" / stamp
        folder.mkdir(parents=True, mode=0o700)
        if os.name == "nt":
            identity = subprocess.check_output(["whoami"], text=True).strip()
            result = subprocess.run(["icacls.exe", str(folder), "/inheritance:r", "/grant:r", identity + ":(OI)(CI)(F)", "SYSTEM:(OI)(CI)(F)"], capture_output=True)
            if result.returncode:
                raise MaintenanceError("BACKUP_PERMISSIONS_FAILED")
        else:
            folder.chmod(0o700)
        target = folder / "before.sql"
        descriptor = os.open(target, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
        with os.fdopen(descriptor, "wb") as handle:
            result = subprocess.run([str(dump), *self.flags, "--single-transaction", "--no-tablespaces", "--set-gtid-purged=OFF", "maimai"],
                                    stdout=handle, stderr=subprocess.PIPE, cwd=ROOT, timeout=120)
        if result.returncode or target.stat().st_size == 0:
            raise MaintenanceError("BACKUP_FAILED; no content changes applied")
        return target


def build_apply_sql(plan, admin):
    """Generate only the reviewed whitelist mutations; caller must back up first."""
    if not isinstance(admin, int) or admin < 1:
        raise MaintenanceError("ADMIN_ID_INVALID")
    if not plan["changes"]:
        return ""
    statements = ["CREATE TEMPORARY TABLE content_guard(ok TINYINT NOT NULL CHECK(ok=1))",
                  f"INSERT INTO content_guard VALUES(GET_LOCK({literal(BATCH)},0))", "START TRANSACTION",
                  "SELECT lock_name FROM governance_locks WHERE lock_name IN ('ACCOUNT_ROLES','CATEGORIES') ORDER BY lock_name FOR UPDATE"]
    user_ids = {admin}
    for (kind, _), row in plan["guards"].items():
        if kind == "ORDER":
            user_ids.update((row["buyer"], row["seller"]))
    statements.append("SELECT id FROM users WHERE id IN(" + ",".join(map(str, sorted(user_ids))) + ") ORDER BY id FOR UPDATE")
    statements.append(f"INSERT INTO content_guard SELECT IF(COUNT(*)=1,1,0) FROM users u WHERE u.id={admin} AND u.status='ACTIVE' AND EXISTS(SELECT 1 FROM user_roles r WHERE r.user_id=u.id AND r.role='SUPER_ADMIN')")
    for kind in ("PRODUCT", "ORDER", "RATING", "COMMENT"):
        ids = sorted(identifier for current, identifier in plan["guards"] if current == kind)
        if not ids:
            continue
        table = QUERIES[kind][0]
        statements.append(f"SELECT id FROM {table} WHERE id IN({','.join(map(str, ids))}) ORDER BY id FOR UPDATE")
    for (kind, identifier), row in sorted(plan["guards"].items()):
        if not isinstance(identifier, int) or identifier < 1 or kind not in QUERIES:
            raise MaintenanceError("SNAPSHOT_ID_INVALID")
        fingerprint = row.get("_fingerprint", "")
        if not re.fullmatch(r"[0-9a-f]{64}", fingerprint):
            raise MaintenanceError("SNAPSHOT_FINGERPRINT_MISSING")
        _, alias, query = QUERIES[kind]
        statements.append(f"INSERT INTO content_guard SELECT IF(COUNT(*)=1 AND MIN(SHA2(record,256))={literal(fingerprint)},1,0) FROM ({query} WHERE {alias}.id={identifier}) guarded_snapshot")
    for change in plan["changes"]:
        kind, identifier, before, after = change["kind"], change["id"], change["before"], change["after"]
        if (kind, identifier) not in plan["guards"] or before != plan["guards"][(kind, identifier)]:
            raise MaintenanceError("CHANGE_WITHOUT_MATCHING_GUARD")
        if kind == "PRODUCT":
            if set(after) != {"description", "supplyNote"}:
                raise MaintenanceError("PRODUCT_FIELDS_NOT_ALLOWED")
            statements.append(f"UPDATE products SET description={literal(after['description'])},supply_note={literal(after['supplyNote'])},version=version+1,updated_at=UTC_TIMESTAMP(6) WHERE id={identifier}")
            statements.append("INSERT INTO content_guard VALUES(IF(ROW_COUNT()=1,1,0))")
            content = deepcopy(before["content"])
            content.update(after)
            statements.append(f"INSERT INTO product_revisions(product_id,version,action,actor_id,content) VALUES({identifier},{before['revisionVersion']+1},'EXPERIENCE_CONTENT',{admin},{literal(json_text(content))})")
        elif kind == "RATING":
            if set(after) != {"comment"}:
                raise MaintenanceError("RATING_FIELDS_NOT_ALLOWED")
            statements.append(f"UPDATE community_order_ratings SET comment={literal(after['comment'])} WHERE id={identifier}")
            statements.append("INSERT INTO content_guard VALUES(IF(ROW_COUNT()=1,1,0))")
        elif kind == "COMMENT":
            if set(after) != {"content"}:
                raise MaintenanceError("COMMENT_FIELDS_NOT_ALLOWED")
            statements.append(f"UPDATE product_comments SET content={literal(after['content'])},updated_at=UTC_TIMESTAMP(6) WHERE id={identifier}")
            statements.append("INSERT INTO content_guard VALUES(IF(ROW_COUNT()=1,1,0))")
        else:
            raise MaintenanceError("CHANGE_KIND_NOT_ALLOWED")
        fields_before = {name: before[name] for name in after}
        before_audit = {"key": change["key"], "source": SOURCE, "hash": digest(fields_before)}
        after_audit = {"key": change["key"], "source": SOURCE, "hash": digest(after)}
        statements.append("INSERT INTO admin_audit_logs(admin_id,action,target_type,target_id,reason,before_state,after_state) VALUES("
                          + f"{admin},'EXPERIENCE_CONTENT',{literal(kind)},{identifier},{literal(BATCH)},{literal(json_text(before_audit))},{literal(json_text(after_audit))})")
    statements += ["COMMIT", f"SELECT RELEASE_LOCK({literal(BATCH)})"]
    return ";\n".join(statements) + ";\n"


def summary(plan):
    return {"mode": "HISTORICAL_EXPERIENCE_MAINTENANCE", "source": SOURCE,
            "changes": dict(Counter(row["kind"] for row in plan["changes"])),
            "items": dict(Counter(row["status"] for row in plan["outcomes"])), "outcomes": plan["outcomes"]}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--mysql", type=Path, default=DEFAULT_MYSQL, help="Local mysql executable; mysqldump must be its sibling")
    parser.add_argument("--client-file", type=Path, default=DEFAULT_CLIENT, help="Existing private client defaults file; contents are never printed")
    parser.add_argument("--admin-id", type=int, help="Existing active super administrator; required if selection is ambiguous")
    parser.add_argument("--apply", action="store_true", help="Back up, lock, recheck and apply the previewed historical-content changes")
    args = parser.parse_args()
    package = json.loads(CONTENT_PATH.read_text(encoding="utf-8"))
    baseline = json.loads(BASELINE_PATH.read_text(encoding="utf-8"))
    items = validate_package(package, baseline)
    database = LocalDatabase(args.mysql, args.client_file)
    plan = plan_content(items, baseline["items"], database.snapshot([p["title"] for p in items]))
    output = summary(plan)
    output["status"] = "PREVIEW_READY" if plan["changes"] else "NO_CHANGES"
    if args.apply and plan["changes"]:
        admins = [int(value) for value in database.sql("SELECT u.id FROM users u JOIN user_roles r ON r.user_id=u.id WHERE u.status='ACTIVE' AND r.role='SUPER_ADMIN' ORDER BY u.id;").splitlines()]
        admin = args.admin_id if args.admin_id is not None else (admins[0] if len(admins) == 1 else None)
        if admin not in admins:
            raise MaintenanceError("ACTIVE_SUPER_ADMIN_REQUIRED; select an existing account with --admin-id")
        sql = build_apply_sql(plan, admin)
        backup = database.backup()
        database.sql(sql)
        output["status"] = "APPLIED_LOCAL"
        output["backupWritten"] = backup.is_file()
        checked = plan_content(items, baseline["items"], database.snapshot([p["title"] for p in items]))
        output["remainingChanges"] = len(checked["changes"])
        if checked["changes"]:
            raise MaintenanceError("POST_APPLY_REVIEW_REQUIRED; backup retained")
    print(json.dumps(output, ensure_ascii=True))


if __name__ == "__main__":
    try:
        main()
    except MaintenanceError as error:
        print(str(error), file=sys.stderr)
        sys.exit(1)
    except Exception:
        print("CONTENT_MAINTENANCE_FAILED; private output withheld", file=sys.stderr)
        sys.exit(1)
