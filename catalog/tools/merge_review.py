#!/usr/bin/env python3
"""
一次性脚本：把人工复核结果（review/part-*.txt + review/frozen-labels.txt）合并成
catalog/products.csv（之后的唯一商品主数据）和 catalog/复核报告.md。

    python3 catalog/tools/merge_review.py --ocr <ocr.jsonl> --old-csv <旧清单.csv>

--ocr      可选。系统 OCR 的原始结果，只用来给商品补条码。
--old-csv  可选。旧的 OCR 识别清单，用来回填「原图文件」并统计改了多少。

products.csv 生成以后，日常维护请直接改 products.csv，再跑 build_seed.py。
"""
import argparse
import csv
import glob
import json
import os
import re
from decimal import Decimal, ROUND_HALF_UP

HERE = os.path.dirname(os.path.abspath(__file__))
CATALOG = os.path.dirname(HERE)
REVIEW = os.path.join(CATALOG, "review")

# 复核时用的临时分类 → 正式分类
CAT_MAP = {"chilled": "fresh", "meat": None}
MEAT_OVERRIDE = {"p399": "dry", "p801": "instant", "p802": "instant", "p803": "instant", "p804": "instant"}

FIELDS = [
    "id", "category", "name_zh", "name_en", "spec", "spec_en", "price", "sale_price",
    "promo", "promo_en", "remark", "remark_en", "barcode", "image", "source_photo",
    "status", "review_note",
]


def money(v):
    return Decimal(str(v)).quantize(Decimal("0.01"), rounding=ROUND_HALF_UP)


def spec_en(spec):
    s = spec
    table = [
        ("按公斤", "per kg"), ("整条", "whole fish"), ("约1kg", "approx. 1kg"),
        ("（净750g）", " (750g net)"), ("（净800g）", " (800g net)"),
    ]
    for zh, en in table:
        s = s.replace(zh, en)
    s = re.sub(r"(\d+)个$", r"\1 pcs", s)
    s = re.sub(r"(\d+)枚$", r"\1 pcs", s)
    s = re.sub(r"(\d+)件$", r"\1 pcs", s)
    s = re.sub(r"(\d+)只$", r"\1 pcs", s)
    s = re.sub(r"(\d+)张$", r"\1 pcs", s)
    s = re.sub(r"(\d+)包$", r"\1 packs", s)
    s = re.sub(r"(\d+)双$", r"\1 pairs", s)
    s = re.sub(r"(\d+)寸$", r'\1"', s)
    return s


def parse_promo(price, promo):
    """返回 (sale_price, promo_zh, promo_en)"""
    promo = promo.strip()
    if not promo:
        return "", "", ""
    m = re.fullmatch(r"sale=([\d.]+)", promo)
    if m:
        return str(money(m.group(1))), "", ""
    m = re.fullmatch(r"-(\d+)%", promo)
    if m:
        pct = int(m.group(1))
        sale = money(Decimal(str(price)) * (100 - pct) / 100)
        return str(sale), f"-{pct}%", f"{pct}% off"
    m = re.fullmatch(r"(\d+)件(\d+(?:\.\d+)?)€", promo)
    if m:
        return "", f"{m.group(1)}件{m.group(2)}€", f"{m.group(1)} for €{m.group(2)}"
    m = re.fullmatch(r"(\d+)件-(\d+)%", promo)
    if m:
        return "", f"{m.group(1)}件起 -{m.group(2)}%", f"{m.group(2)}% off {m.group(1)}+"
    if promo == "买1送1":
        return "", "买1送1", "Buy 1 Get 1 Free"
    raise ValueError("不认识的促销写法：" + promo)


def split_note(note):
    """顾客看得懂、有用的补充（整箱价、单包价）进 remark，其余是内部复核备注。"""
    note = note.strip()
    if not note:
        return "", "", ""
    m = re.fullmatch(r"整箱(\d+)(罐|包) ([\d.]+)€", note)
    if m:
        unit = {"罐": "cans", "包": "packs"}[m.group(2)]
        return f"整箱 {m.group(1)}{m.group(2)}：€{m.group(3)}", f"Full case of {m.group(1)} {unit}: €{m.group(3)}", ""
    m = re.fullmatch(r"单(包|支) (\d+g) 另售 ([\d.]+)€", note)
    if m:
        unit = {"包": "pack", "支": "piece"}[m.group(1)]
        return f"单{m.group(1)}（{m.group(2)}）另售：€{m.group(3)}", f"Single {unit} ({m.group(2)}) also sold: €{m.group(3)}", ""
    if note == "罐身图案随机":
        return "罐身图案随机发货", "Can design varies", ""
    return "", "", note


def ean_ok(code):
    if not code.isdigit() or len(code) not in (8, 12, 13):
        return False
    digits = [int(c) for c in code]
    body, check = digits[:-1], digits[-1]
    total = sum(d * (3 if i % 2 == 0 else 1) for i, d in enumerate(reversed(body)))
    return (10 - total % 10) % 10 == check


def load_barcodes(ocr_path):
    """每张照片挑一个最可信的条码：价签上印的数字 ∩ 系统识别出的条码。"""
    out = {}
    if not ocr_path or not os.path.exists(ocr_path):
        return out
    for line in open(ocr_path, encoding="utf-8"):
        r = json.loads(line)
        pid = r["f"][:-4]
        decoded = [b["v"] for b in r["bars"] if b["v"].isdigit()]
        printed = [t["t"].strip() for t in r["texts"] if re.fullmatch(r"\d{8,14}", t["t"].strip())]
        # 价签在画面里最大，取字最高的那串数字
        printed_big = sorted(
            (t for t in r["texts"] if re.fullmatch(r"\d{8,14}", t["t"].strip())),
            key=lambda t: -t["h"],
        )
        pick = ""
        both = [p for p in printed if p in decoded or p.lstrip("0") in [d.lstrip("0") for d in decoded]]
        if both:
            pick = both[0]
        elif printed_big and ean_ok(printed_big[0]["t"].strip()):
            pick = printed_big[0]["t"].strip()
        elif len(set(decoded)) == 1 and ean_ok(decoded[0]):
            pick = decoded[0]
        out[pid] = pick
    return out


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--ocr", default="")
    ap.add_argument("--old-csv", default="")
    args = ap.parse_args()

    barcodes = load_barcodes(args.ocr)
    old = {}
    if args.old_csv and os.path.exists(args.old_csv):
        for row in csv.DictReader(open(args.old_csv, encoding="utf-8-sig")):
            old[row["商品编号"]] = row

    rows, dups, skips, multis = [], [], [], []
    for path in sorted(glob.glob(os.path.join(REVIEW, "part-*.txt"))):
        for line in open(path, encoding="utf-8"):
            line = line.rstrip("\n")
            if not line.startswith("p"):
                continue
            parts = line.split("|")
            pid = parts[0]
            tail = parts[-1].strip()
            if tail.startswith("dup="):
                dups.append((pid, tail[4:]))
                continue
            if tail.startswith("skip="):
                skips.append((pid, tail[5:]))
                continue
            if tail.startswith("multi="):
                multis.append(pid)
                continue
            pid, cat, zh, en, spec, price, promo, note = parts
            cat = MEAT_OVERRIDE.get(pid) or CAT_MAP.get(cat, cat)
            sale, promo_zh, promo_en = parse_promo(price, promo)
            remark, remark_en, review_note = split_note(note)
            rows.append({
                "id": pid, "category": cat, "name_zh": zh, "name_en": en,
                "spec": spec, "spec_en": spec_en(spec),
                "price": str(money(price)), "sale_price": sale,
                "promo": promo_zh, "promo_en": promo_en,
                "remark": remark, "remark_en": remark_en,
                "barcode": barcodes.get(pid, ""),
                "image": f"products/{pid}.jpg",
                "source_photo": old.get(pid, {}).get("原图文件", ""),
                "status": "1", "review_note": review_note,
            })

    for line in open(os.path.join(REVIEW, "frozen-labels.txt"), encoding="utf-8"):
        line = line.rstrip("\n")
        if not line.startswith("f"):
            continue
        fid, cat, zh, en, spec, price, promo, note, src = line.split("|")
        sale, promo_zh, promo_en = parse_promo(price, promo)
        note = (note + "；" if note else "") + "冷冻柜商品，只拍到价签，需补拍商品图"
        rows.append({
            "id": fid, "category": cat, "name_zh": zh, "name_en": en,
            "spec": spec, "spec_en": spec_en(spec),
            "price": str(money(price)), "sale_price": sale,
            "promo": promo_zh, "promo_en": promo_en,
            "remark": "", "remark_en": "", "barcode": "",
            "image": "", "source_photo": old.get(src, {}).get("原图文件", "") + f"（{src}）",
            "status": "1", "review_note": note,
        })

    seen = set()
    for r in rows:
        assert r["id"] not in seen, r["id"]
        seen.add(r["id"])
        assert r["name_zh"] and r["name_en"], r["id"]

    with open(os.path.join(CATALOG, "products.csv"), "w", encoding="utf-8-sig", newline="") as f:
        w = csv.DictWriter(f, fieldnames=FIELDS)
        w.writeheader()
        w.writerows(rows)

    # ---------- 复核报告 ----------
    changed_name = changed_price = 0
    price_changes = []
    for r in rows:
        o = old.get(r["id"])
        if not o:
            continue
        if o["识别名称"].strip() != r["name_zh"]:
            changed_name += 1
        try:
            op = money(o["价格(EUR)"] or 0)
        except Exception:
            op = money(0)
        if op != money(r["price"]):
            changed_price += 1
            price_changes.append((r["id"], r["name_zh"], op, r["price"]))
    need_check = [r for r in rows if r["review_note"] and r["id"].startswith("p")]
    on_sale = [r for r in rows if r["sale_price"] or r["promo"]]

    md = []
    md.append("# 商品图片复核报告\n")
    md.append("801 张实拍照片逐张人工复核（看包装 + 看价签），结果已写入 `products.csv`。\n")
    md.append("## 总览\n")
    md.append("| 项目 | 数量 |\n| --- | --- |")
    md.append(f"| 原始照片 | 801 |")
    md.append(f"| 上架商品（有图） | {sum(1 for r in rows if r['image'])} |")
    md.append(f"| 冷冻柜商品（只有价签，暂无图） | {sum(1 for r in rows if not r['image'])} |")
    md.append(f"| 重复拍摄，已合并 | {len(dups)} |")
    md.append(f"| 不是商品 / 无法使用的照片 | {len(skips)} |")
    md.append(f"| 冷冻柜价签合照（一张多个商品，已拆开） | {len(multis)} |")
    md.append(f"| 名称被修正 | {changed_name} |")
    md.append(f"| 价格被修正 | {changed_price} |")
    md.append(f"| 有特价 / 促销 | {len(on_sale)} |")
    md.append(f"| 仍需到店确认 | {len(need_check)} |\n")

    md.append("## 需要你到店确认或重拍的\n")
    md.append("| 编号 | 商品 | 问题 |\n| --- | --- | --- |")
    for r in need_check:
        md.append(f"| {r['id']} | {r['name_zh']} | {r['review_note']} |")
    md.append("")
    md.append("另外：冷冻柜的 " + str(sum(1 for r in rows if not r['image'])) +
              " 个商品（编号 f001 起）只拍到了价签，名称和价格是准的，但没有商品图，小程序里先显示占位图。\n")

    md.append("## 没有使用的照片\n")
    md.append("| 编号 | 原因 |\n| --- | --- |")
    for pid, why in skips:
        md.append(f"| {pid} | {why} |")
    md.append("")

    md.append("## 重复拍摄（保留前一张）\n")
    md.append("| 重复的照片 | 保留的商品 |\n| --- | --- |")
    for pid, keep in dups:
        md.append(f"| {pid} | {keep} |")
    md.append("")

    md.append("## 价格修正明细（旧识别 → 复核后）\n")
    md.append("| 编号 | 商品 | 旧价格 | 正确价格 |\n| --- | --- | --- | --- |")
    for pid, name, op, np_ in price_changes:
        md.append(f"| {pid} | {name} | {op} | {np_} |")
    md.append("")
    open(os.path.join(CATALOG, "复核报告.md"), "w", encoding="utf-8").write("\n".join(md))

    print(f"products.csv：{len(rows)} 个商品；重复 {len(dups)}，跳过 {len(skips)}，合照 {len(multis)}")
    print(f"名称修正 {changed_name}，价格修正 {changed_price}，促销 {len(on_sale)}，待确认 {len(need_check)}")
    print("有条码：", sum(1 for r in rows if r["barcode"]))
    from collections import Counter
    print(Counter(r["category"] for r in rows))


if __name__ == "__main__":
    main()
