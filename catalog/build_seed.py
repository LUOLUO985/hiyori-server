#!/usr/bin/env python3
"""
商品主数据 → 数据库种子脚本。

    python3 catalog/build_seed.py

读取  catalog/products.csv、categories.csv、home.json
生成  src/main/resources/db/migration/V2__seed_data.sql

【重要】数据库第一次上线之前，可以随便改 CSV 再重跑本脚本。
上线以后 Flyway 会校验 V2 的内容，不能再改它 —— 那时请用后台接口改商品，
或者新增 V3__xxx.sql。
"""
import csv, json, os, sys
from decimal import Decimal

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
OUT = os.path.join(ROOT, "src", "main", "resources", "db", "migration", "V2__seed_data.sql")


def q(v):
    return "'" + str(v).replace("\\", "\\\\").replace("'", "''") + "'"


def dec(v):
    return str(Decimal(v or "0").quantize(Decimal("0.01")))


def values(rows):
    return ",\n".join("  (" + ", ".join(r) + ")" for r in rows) + ";\n"


def main():
    cats = list(csv.DictReader(open(os.path.join(HERE, "categories.csv"), encoding="utf-8-sig")))
    products = [r for r in csv.DictReader(open(os.path.join(HERE, "products.csv"), encoding="utf-8-sig"))]
    home = json.load(open(os.path.join(HERE, "home.json"), encoding="utf-8"))

    cat_ids = {c["id"] for c in cats}
    ids = set()
    for p in products:
        if p["category"] not in cat_ids or p["category"] == "deals":
            sys.exit(f"商品 {p['id']} 的分类不存在：{p['category']}")
        if p["id"] in ids:
            sys.exit(f"商品编号重复：{p['id']}")
        ids.add(p["id"])
        if len(p["name_zh"]) > 128 or len(p["name_en"]) > 160:
            sys.exit(f"商品 {p['id']} 名称太长")
    for s in home["sections"]:
        for pid in s["items"]:
            if pid not in ids:
                sys.exit(f"首页区块 {s['id']} 里的商品不存在：{pid}")

    sql = []
    sql.append("-- 本文件由 catalog/build_seed.py 生成，请不要手改。")
    sql.append("-- 数据来源：catalog/products.csv（801 张实拍照片逐张人工复核）。\n")

    sql.append("INSERT INTO category (id, name, name_en, emoji, image, description, description_en, sort) VALUES")
    sql.append(values([
        [q(c["id"]), q(c["name_zh"]), q(c["name_en"]), q(c["emoji"]), "''", q(c["desc_zh"]), q(c["desc_en"]), str(i)]
        for i, c in enumerate(cats)
    ]))

    sql.append("INSERT INTO product (id, category_id, name, name_en, spec, spec_en, price, sale_price, promo, promo_en,"
               " remark, remark_en, barcode, badge, badge_en, image, thumb, status, sort) VALUES")
    rows = []
    for i, p in enumerate(products):
        image = "/files/" + p["image"] if p["image"] else ""
        thumb = image.replace("/files/products/", "/files/products/thumb/") if image else ""
        rows.append([
            q(p["id"]), q(p["category"]), q(p["name_zh"]), q(p["name_en"]), q(p["spec"]), q(p["spec_en"]),
            dec(p["price"]), dec(p["sale_price"]), q(p["promo"]), q(p["promo_en"]),
            q(p["remark"]), q(p["remark_en"]), q(p["barcode"]), "''", "''",
            q(image), q(thumb), p["status"] or "1", str(i),
        ])
    sql.append(values(rows))

    sql.append("INSERT INTO banner (title, title_en, sub, sub_en, image, link_category_id, sort) VALUES")
    sql.append(values([
        [q(b["title"]), q(b["title_en"]), q(b["sub"]), q(b["sub_en"]), q(b.get("image", "")), q(b.get("link", "")), str(i)]
        for i, b in enumerate(home["banners"])
    ]))

    sql.append("INSERT INTO hot_keyword (keyword, keyword_en, sort) VALUES")
    sql.append(values([[q(zh), q(en), str(i)] for i, (zh, en) in enumerate(home["hot_keywords"])]))

    sql.append("INSERT INTO home_section (id, title, title_en, sub, sub_en, link_category_id, sort) VALUES")
    sql.append(values([
        [q(s["id"]), q(s["title"]), q(s["title_en"]), q(s["sub"]), q(s["sub_en"]), q(s["link"]), str(i)]
        for i, s in enumerate(home["sections"])
    ]))

    sql.append("INSERT INTO home_section_item (section_id, product_id, sort) VALUES")
    sql.append(values([
        [q(s["id"]), q(pid), str(i)] for s in home["sections"] for i, pid in enumerate(s["items"])
    ]))

    open(OUT, "w", encoding="utf-8").write("\n".join(sql))
    print(f"已生成 {os.path.relpath(OUT, ROOT)}：{len(cats)} 个分类，{len(products)} 个商品，{len(home['sections'])} 个首页区块")


if __name__ == "__main__":
    main()
