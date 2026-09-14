#!/usr/bin/env python3
"""One-time offline initialization of an empty, migrated preview database.

Run as root while maimai.service is stopped. Never imports the local demo DB.
Passwords remain in a mode-0600 file; no credentials are printed.
Requires Ubuntu 24.04 Python 3.12 with system bcrypt support through crypt.
"""
import argparse
import crypt
import fcntl
import json
import os
from pathlib import Path
import pwd
import secrets
import subprocess
import zipfile

MYSQL = "/opt/maimai/runtime/mysql/usr/bin/mysql"
CLIENT = "/etc/maimai/mysql-root.cnf"


def sql(text):
    result = subprocess.run(
        [MYSQL, f"--defaults-file={CLIENT}", "--default-character-set=utf8mb4", "--batch", "--skip-column-names", "maimai"],
        input=text, text=True, capture_output=True, check=False,
    )
    if result.returncode:
        raise RuntimeError("Database initialization failed; no SQL or credentials printed")
    return result.stdout.strip()


def literal(value):
    return "CONVERT(0x" + value.encode("utf-8").hex() + " USING utf8mb4)"


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--jar", required=True)
    parser.add_argument("--credentials-out", default="/etc/maimai/initial-admin.json")
    args = parser.parse_args()
    if os.geteuid() != 0:
        raise RuntimeError("Root is required")
    lock = open("/etc/maimai/preview-bootstrap.lock", "a")
    fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
    if subprocess.run(["systemctl", "is-active", "--quiet", "maimai.service"]).returncode == 0:
        raise RuntimeError("Stop maimai.service before offline initialization")
    if sql("SELECT (SELECT COUNT(*) FROM users)+(SELECT COUNT(*) FROM products);") != "0":
        raise RuntimeError("Refusing to change a nonempty database")
    if crypt.METHOD_BLOWFISH not in crypt.methods:
        raise RuntimeError("System bcrypt unavailable")

    owner = pwd.getpwnam("maimai")
    products = [
        ("手机", "iPhone 12 128GB 蓝色", "LIKE_NEW", 219900, "iphone-blue.jpg"),
        ("影音家电", "索尼 WH-1000XM4 降噪耳机", "GOOD", 89900, "headphones-charcoal.jpg"),
        ("图书教材", "Java 核心技术 卷 I", "GOOD", 4500, "java-textbook.jpg"),
        ("服饰鞋包", "羊毛混纺大衣 M 码", "LIKE_NEW", 15900, "wool-coat.jpg"),
        ("运动户外", "羽毛球拍 天斧77", "GOOD", 26000, "badminton-racket.jpg"),
    ]
    # Empty-table auto_increment need not be 1. Match the actual next IDs.
    first_id = int(sql("SELECT AUTO_INCREMENT FROM information_schema.tables WHERE table_schema='maimai' AND table_name='products';"))
    image_dir = Path("/var/lib/maimai/uploads/products")
    image_dir.mkdir(mode=0o700, exist_ok=True)
    os.chown(image_dir, owner.pw_uid, owner.pw_gid)
    with zipfile.ZipFile(args.jar) as jar:
        payloads = [jar.read("BOOT-INF/classes/demo/products/" + p[4]) for p in products]
    for offset, data in enumerate(payloads):
        target = image_dir / f"seed-{first_id + offset}.jpg"
        with target.open("xb") as handle:
            handle.write(data)
        target.chmod(0o600)
        os.chown(target, owner.pw_uid, owner.pw_gid)

    password = secrets.token_urlsafe(24)
    password_hash = crypt.crypt(password, crypt.mksalt(crypt.METHOD_BLOWFISH, rounds=4096))
    disabled_hash = crypt.crypt(secrets.token_urlsafe(32), crypt.mksalt(crypt.METHOD_BLOWFISH, rounds=4096))
    credentials = {"url": "https://market.example.com", "email": "admin@maimai.invalid", "password": password,
                   "note": "Private bootstrap administrator; this address does not receive email. Keep this file private."}
    fd = os.open(args.credentials_out, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    with os.fdopen(fd, "w") as handle:
        json.dump(credentials, handle, ensure_ascii=False, indent=2)
    statements = ["START TRANSACTION;",
        f"INSERT INTO users(email,password_hash,nickname,status) VALUES('admin@maimai.invalid',{literal(password_hash)},{literal('平台管理员')},'ACTIVE');",
        "SET @admin_id=LAST_INSERT_ID();",
        "INSERT INTO user_roles(user_id,role) VALUES(@admin_id,'USER'),(@admin_id,'SUPER_ADMIN');",
        f"INSERT INTO users(email,password_hash,nickname,status) VALUES('showcase@maimai.invalid',{literal(disabled_hash)},{literal('麦麦示范商品')},'DISABLED');",
        "SET @seller_id=LAST_INSERT_ID();",
        "INSERT INTO user_roles(user_id,role) VALUES(@seller_id,'USER');",
    ]
    for offset, (category, title, condition, cents, _) in enumerate(products):
        statements.append("INSERT INTO products(id,seller_id,category_id,title,description,item_condition,defects,price_cents,stock_available,region,delivery_methods,freight_cents,return_promise,status,review_reason) VALUES("
            f"{first_id + offset},@seller_id,(SELECT id FROM categories WHERE name={literal(category)} LIMIT 1),"
            f"{literal(title)},{literal('仅用于课程项目界面展示。图片为 AI 生成示意图，不是卖家实拍；价格、品相和交付方式均为演示内容，不代表有真实物品出售。本商品不接受下单或议价。')},"
            f"'{condition}',{literal('展示数据，请勿据此判断真实物品成色。')},{cents},0,{literal('上海市')},'EXPRESS,MEETUP',0,"
            f"{literal('仅供浏览，无真实交付及售后承诺。')},'ON_SALE',{literal('部署初始化的不可交易示范内容，未授予任何支付资质。')});")
        statements.append(f"INSERT INTO product_images(product_id,path,sort) VALUES({first_id + offset},'/uploads/products/seed-{first_id + offset}.jpg',0);")
    statements += [
        "UPDATE official_articles SET summary=" + literal("了解麦麦二手的服务范围、交易方式与当前体验版状态。") + ",body=" + literal(
            "麦麦二手是面向普通用户的二手实物信息管理与交易课程项目。\n\n当前开放 HTTPS 页面体验，示范商品仅供浏览，图片为 AI 生成示意图，不接受真实购买。\n\n真实微信付款、退款和分账仍待资质、渠道开通及完整验证。邮件、物流与智能客服依赖服务配置；页面上线不等于已完成真实交易运营验收。") + " WHERE slug='about-maimai' AND created_by IS NULL AND updated_by IS NULL;",
        "UPDATE official_articles SET title=" + literal("在线体验版本说明") + ",summary=" + literal("示范商品仅供浏览；真实支付、注册邮件等外部服务仍待开通。") + ",body=" + literal(
            "本网站已部署为课程项目在线体验版，可浏览商品、分类、官方信息以及注册与登录界面。示范商品库存为零，不接受下单；展示账号不能登录。\n\n真实微信支付、退款与分账尚未接通。公网环境不开放模拟支付和开发邮件读取入口，请勿通过私人收款码向示范商品付款。\n\n注册、找回密码所需邮件服务尚未配置；物流查询及智能客服也未启用，不能把展示页面视为这些服务已可使用。用户协议与交易规则仍为草案。\n\n体验时请记录页面、操作步骤和错误提示；请勿上传与课程测试无关的敏感资料。") + " WHERE slug='local-development-notice' AND created_by IS NULL AND updated_by IS NULL;",
        "COMMIT;",
    ]
    sql("\n".join(statements))
    print("PREVIEW_BOOTSTRAP_OK: 1 private administrator, 1 disabled showcase account, 5 zero-stock products")


if __name__ == "__main__":
    main()
