#!/usr/bin/env python3
"""
把老板整理好的《商品对照表》(xlsx) 合并进商品主数据 catalog/products.csv。

    python3 catalog/tools/apply_sheet.py catalog/商品对照表.xlsx

对照表四列：图片名称 | 英文名称 | 商品名称 | 商品类别
  - 名称、英文名、分类：以对照表为准
  - 价格、特价、促销、条码：沿用逐张看价签的复核结果（review/ 底稿）
  - 冷冻柜商品（对照表 p862 起）：只有价签没有商品照，按名称和复核记录配对取价格

少数几行对照表和照片明显对不上（见 OVERRIDE），保留按照片核对的名称，只用对照表的分类，
并写进「对照表调整说明.md」，请老板确认。
"""
import argparse
import csv
import glob
import os
import re
import sys
from decimal import Decimal, ROUND_HALF_UP
from difflib import SequenceMatcher

import openpyxl

HERE = os.path.dirname(os.path.abspath(__file__))
CATALOG = os.path.dirname(HERE)
REVIEW = os.path.join(CATALOG, "review")
sys.path.insert(0, HERE)
from merge_review import FIELDS, parse_promo, split_note, spec_en, money  # noqa: E402

# 对照表的类别 → 分类编码（顺序就是小程序里的显示顺序）
CATEGORIES = [
    # id, 中文, English, emoji, 中文副标题, English subtitle
    ("meat", "肉类海鲜", "Meat & Seafood", "🥩", "冷冻肉类、海鲜、腊肠", "Frozen meat, seafood and sausages"),
    ("frozen", "速冻面点", "Frozen Dumplings & Buns", "🥟", "水饺、包子、丸子、汤圆", "Dumplings, buns, hot pot balls"),
    ("rice", "米面粮油", "Rice, Noodles & Oil", "🍚", "大米、面条、粉丝、食用油", "Rice, noodles, vermicelli and oil"),
    ("sauce", "酱料调味", "Sauces & Seasonings", "🫙", "酱油、辣酱、火锅底料、香料", "Soy sauce, chili, hot pot bases, spices"),
    ("instant", "方便速食", "Instant Food", "🍜", "泡面、拌面、罐头、即食", "Instant noodles, canned and ready-to-eat"),
    ("snack", "零食饼干", "Snacks & Biscuits", "🍪", "薯片、辣条、糖果、饼干", "Chips, spicy snacks, candy and biscuits"),
    ("drink", "饮料冲调", "Drinks & Tea", "🧋", "茶饮、汽水、奶茶、茶叶", "Soft drinks, milk tea and tea leaves"),
    ("daily", "日用百货", "Household & Party", "🧴", "厨房用品、气球、礼品袋", "Kitchenware, balloons and gift bags"),
    ("other", "其他", "Others", "📦", "豆腐、蔬果、干货等", "Tofu, produce, dried goods and more"),
]
CAT_BY_ZH = {c[1]: c[0] for c in CATEGORIES}

# 对照表和照片 / 价签明显不是同一件商品：保留按照片核对的名称（分类仍用对照表的）
# 2026-11-17 老板已确认按照片保留；catalog/商品对照表.xlsx 里这几行也已改成照片上的名称
OVERRIDE = {
    "p179": "照片是 Dorkbua 鸡腿饼干条（香辣味）110g，价签相符；表里写的是海鲜味脆片 60g",
    "p198": "照片和价签都是 Golden Chef 罗望子糖 200g；表里写的是龙眼干",
    "p217": "照片和价签都是好人家大红袍红汤火锅底料 150g；表里写的是大红袍茶叶",
    "p280": "照片和价签都是 Magic Creams 黄油奶油夹心饼干 280g；表里写的是巧克力威化卷",
    "p296": "照片和价签都是葵花牌山楂片 125g；表里写的是蒟蒻果冻",
    "p335": "照片和价签都是 Maeboonlam 发酵鱼露 400ml；表里写的是 Maepranom 辣蘸酱",
    "p465": "照片和价签都是自然食記爆爆珠（蓝莓）450g；表里写的是杯装粥/果冻",
    "p466": "照片和价签都是自然食記爆爆珠（樱桃）450g；表里写的是杯装粥/果冻",
    "p467": "照片和价签都是自然食記爆爆珠（芒果）450g；表里写的是杯装粥/果冻",
    "p675": "照片和价签都是六月香甜面酱 300g；表里写的是韩国辣椒酱",
    "p690": "照片和价签都是蓬盛香港橄榄菜 180g；表里写的是兰花软瓜",
    "p798": "照片和价签都是厨之选 Q弹玉米面条 1kg（无麸质）；表里写的是意大利面 500g",
    "p123": "照片和价签都是康师傅茉莉龙井 500ml；表里写的是三得利龙井茶",
}

# 保留照片名称时，表里的分类是按表里那个（错的）名称选的，这几件跟着改
OVERRIDE_CAT = {
    "p217": "sauce",  # 火锅底料（表里当成茶叶放进了饮料）
    "p465": "rice",   # 爆爆珠：和表里的珍珠粉圆放一起
    "p466": "rice",
    "p467": "rice",
}

# 名称和价签数量不一致，需要到店确认
NOTE = {
    "p843": "表里写 40 袋，价签写 120g×20 包 30.00€，请到店确认",
    "p538": "照片是樱桃泡泡糖味，表里和 p539 都写成西瓜味，已合并为一件；樱桃味需要的话请在表里改名",
}

# 我上次误判为「重复拍摄」、其实是另一件商品，价签单独读过
DUP_PRICE_FIX = {
    "p118": ("3.20", "sale=2.50"),  # 星巴克 铁观音乌龙拿铁 270ml，手写 2.5
    "p843": ("30.00", ""),          # 辛拉面整箱，价签：120g×20 包 30.00€
    "p844": ("28.80", ""),          # 营多捞面整箱，价签：40 包 28.80€
}


# 同一件商品表里写了两次、两种写法品牌不同：用和价签品牌一致的那一行
RENAME_FROM_DUP = {
    "p120": "p507",  # 价签：康师傅 炼乳奶茶（表里另一行写成香飘飘 Meco）
    "p121": "p508",  # 价签：康师傅 大红袍奶茶
    "p128": "p506",  # 价签：康师傅 白桃乌龙茶（表里另一行写成东方树叶）
}


def variants(name):
    """名称里的口味 / 颜色：「葡萄/草莓味」→ {葡萄, 草莓}；「紫色/多彩」→ {紫色, 多彩}"""
    out = set()
    for tok in re.split(r"[\s（）()，,]+", name or ""):
        if "味" in tok:
            out |= {x for x in tok.split("味")[0].split("/") if x}
        elif re.search(r"色", tok) or "多彩" in tok:
            out |= {x for x in tok.split("/") if x}
    return out


def same_variant(a, b):
    """两组口味有交集（或互相包含，如「辣」和「麻辣花生」）就算同一款；有一边没写口味也算同一款"""
    va, vb = variants(a), variants(b)
    if not va or not vb:
        return True
    return any(x in y or y in x for x in va for y in vb)

# 冷冻柜商品：对照表编号区间
def is_freezer_id(n):
    return 862 <= n <= 895 or 902 <= n <= 965

# 冷冻柜里真正拍到商品本身的两张照片：对照表编号 → 照片
FREEZER_PHOTOS = {}

UNIT = r"(?:kg|KG|Kg|g|G|ml|ML|mL|L|cm|CM|包|条|个|片|袋|杯|支|只|双|枚|罐|瓶|盒|张|连包|入|pcs|PCS|ct|Sticks|Sachets|Bags|Pairs|Pack)"
SPEC_RE = re.compile(
    r"(\d+(?:\.\d+)?\s*(?:[x×*]\s*\d+(?:\.\d+)?\s*)?" + UNIT + r"(?:\s*[x×*]\s*\d+\s*" + UNIT + r"?)?"
    r"|\d+\s*/\s*\d+(?:\s*PCS)?|/\s*kg|\+/-\s*1\s*kg|\d+-\d+\s*g|\d+\"|\d+寸)"
)


def norm(s):
    return re.sub(r"[\s（）()·,，/\\\-+:：'\"“”]", "", (s or "").lower())


def sim(a, b):
    return SequenceMatcher(None, norm(a), norm(b)).ratio()


def tokens(s):
    return set(w.lower() for w in re.findall(r"[A-Za-z]{3,}|\d+(?:/\d+)?", s or ""))


def extract_spec(*names):
    """名称里写了规格就以名称为准（避免「名称写 20包、规格写 7包」这种自相矛盾）"""
    for name in names:
        found = SPEC_RE.findall(name or "")
        if found:
            spec = found[-1].strip()
            spec = re.sub(r"^/\s*kg$", "按公斤", spec)
            spec = re.sub(r"^\+/-\s*1\s*kg$", "约1kg", spec)
            spec = re.sub(r"\s*\*\s*", "×", spec)
            spec = re.sub(r"(?<=\d)\s+(?=[A-Za-z包条个片袋杯支只双枚罐瓶盒张])", "", spec)
            return spec
    return ""


def load_sheet(path):
    ws = openpyxl.load_workbook(path, read_only=True, data_only=True).active
    rows = {}
    for r in ws.iter_rows(min_row=2, values_only=True):
        if not r or not r[0]:
            continue
        pid = re.sub(r"\.jpe?g$", "", str(r[0]).strip(), flags=re.I)
        en, zh, cat = [str(v or "").strip() for v in r[1:4]]
        if cat not in CAT_BY_ZH:
            sys.exit(f"{pid}：不认识的类别「{cat}」，请在对照表里改成：{'、'.join(CAT_BY_ZH)}")
        rows[pid] = {"en": en, "zh": zh, "cat": CAT_BY_ZH[cat], "cat_zh": cat}
    return rows


def load_review():
    """复核底稿：每张照片的价格记录，以及重复 / 跳过 / 合照"""
    photo, dup, skip = {}, {}, {}
    for path in sorted(glob.glob(os.path.join(REVIEW, "part-*.txt"))):
        for line in open(path, encoding="utf-8"):
            if not line.startswith("p"):
                continue
            parts = line.rstrip("\n").split("|")
            tail = parts[-1].strip()
            if tail.startswith("dup="):
                dup[parts[0]] = tail[4:]
            elif tail.startswith("skip="):
                skip[parts[0]] = tail[5:]
            elif tail.startswith("multi="):
                pass
            else:
                pid, cat, zh, en, spec, price, promo, note = parts
                photo[pid] = dict(zh=zh, en=en, spec=spec, price=price, promo=promo, note=note)
    frozen = {}
    for line in open(os.path.join(REVIEW, "frozen-labels.txt"), encoding="utf-8"):
        if not line.startswith("f"):
            continue
        fid, cat, zh, en, spec, price, promo, note, src = line.rstrip("\n").split("|")
        frozen[fid] = dict(zh=zh, en=en, spec=spec, price=price, promo=promo, note=note, src=src)
    # 冷冻柜里那两张单独拍到大虾的照片也算冷冻商品
    for pid in ("p869", "p870"):
        frozen[pid] = dict(photo[pid], src=pid)
        del photo[pid]
    return photo, dup, skip, frozen


def match_freezer(sheet_ids, sheet, frozen):
    """对照表的冷冻商品 ↔ 复核记录：英文名词重合度 + 中文相似度，贪心一对一"""
    cands = []
    for sid in sheet_ids:
        s = sheet[sid]
        for fid, f in frozen.items():
            ta, tb = tokens(s["en"]), tokens(f["en"])
            jac = len(ta & tb) / max(1, len(ta | tb))
            score = jac + 0.6 * sim(s["zh"], f["zh"])
            cands.append((score, sid, fid))
    cands.sort(reverse=True)
    pair, used = {}, set()
    for score, sid, fid in cands:
        if sid in pair or fid in used or score < 0.35:
            continue
        pair[sid] = (fid, score)
        used.add(fid)
    for score, sid, fid in cands:
        if sid in pair or fid in used or score < 0.2:
            continue
        pair[sid] = (fid, score)
        used.add(fid)
    return pair, [f for f in frozen if f not in used]


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("sheet")
    ap.add_argument("--old-csv", default=os.path.join(REVIEW, "原图对照.csv"),
                    help="商品编号 → 原图文件名（默认 review/原图对照.csv）")
    args = ap.parse_args()

    sheet = load_sheet(args.sheet)
    photo, dup, skip, frozen = load_review()
    old_src = {}
    if args.old_csv and os.path.exists(args.old_csv):
        for row in csv.DictReader(open(args.old_csv, encoding="utf-8-sig")):
            old_src[row["商品编号"]] = row["原图文件"]
    barcodes = {}
    prev_path = os.path.join(CATALOG, "products.csv")
    if os.path.exists(prev_path):
        for row in csv.DictReader(open(prev_path, encoding="utf-8-sig")):
            barcodes[row["id"]] = row["barcode"]
            if row["source_photo"] and row["id"].startswith("p"):
                old_src.setdefault(row["id"], row["source_photo"])

    def num(pid):
        return int(re.sub(r"\D", "", pid))

    ids = sorted(sheet, key=num)
    freezer_ids = [i for i in ids if is_freezer_id(num(i))]
    pair, unmatched = match_freezer(freezer_ids, sheet, frozen)

    prefer = {}  # 同一件拍了两次时，名字改用重复那张的写法（更符合价签）
    rows, report = [], {"override": [], "merged": [], "split": [], "freezer": [], "nomatch": [], "noprice": [], "renamed": []}

    def add(pid, s, rec, image_from, note=""):
        price = rec["price"] if rec else "0"
        promo = rec["promo"] if rec else ""
        if pid in DUP_PRICE_FIX:
            price, promo = DUP_PRICE_FIX[pid]
        sale, promo_zh, promo_en = parse_promo(price, promo)
        remark, remark_en, review_note = split_note(rec["note"] if rec else "")
        if pid in OVERRIDE:
            zh, en = rec["zh"], rec["en"]
            spec = rec["spec"] or extract_spec(zh, en)
            review_note = (review_note + "；" if review_note else "") + "已确认按照片保留名称：" + OVERRIDE[pid]
            report["override"].append((pid, s["zh"], zh, OVERRIDE[pid]))
        else:
            zh, en = s["zh"], s["en"]
            spec = extract_spec(zh, en) or (rec["spec"] if rec else "")
        note = "；".join(x for x in (note, NOTE.get(pid, "")) if x)
        if note:
            review_note = (review_note + "；" if review_note else "") + note
        if money(price) == 0:
            report["noprice"].append((pid, zh))
        rows.append({
            "id": pid, "category": OVERRIDE_CAT.get(pid, s["cat"]), "name_zh": zh, "name_en": en,
            "spec": spec, "spec_en": spec_en(spec),
            "price": str(money(price)), "sale_price": sale,
            "promo": promo_zh, "promo_en": promo_en,
            "remark": remark, "remark_en": remark_en,
            "barcode": barcodes.get(pid, "") if image_from == pid else "",
            "image": f"products/{image_from}.jpg" if image_from else "",
            "source_photo": old_src.get(image_from, "") if image_from else "",
            "status": "1", "review_note": review_note,
        })

    for pid in ids:
        s = sheet[pid]
        n = num(pid)
        if is_freezer_id(n):
            if pid not in pair:
                report["nomatch"].append((pid, s["zh"]))
                add(pid, s, None, "", "冷冻柜商品：复核记录里没找到对应价签，价格需到店确认")
                continue
            fid, score = pair[pid]
            f = frozen[fid]
            image_from = f["src"] if fid in ("p869", "p870") else ""
            add(pid, s, f, image_from, "" if image_from else "冷冻柜商品，只拍到价签，需补拍商品图")
            report["freezer"].append((pid, s["zh"], f["zh"], f["price"], score))
            continue
        if pid in photo:
            add(pid, s, photo[pid], pid)
        elif pid in dup:
            keep = dup[pid]
            ks = sheet.get(keep) or {"zh": ""}
            kept_zh = photo[keep]["zh"] if keep in OVERRIDE else ks["zh"]
            if " + " in s["zh"] or "+" in s["zh"].replace("+/-", ""):
                # 一张照片里拍了好几件商品，表里写成「A + B」：每件已经单独有了，不再新建
                report["merged"].append((pid, s["zh"], keep, "（一张照片多件商品，均已单独上架）", 0))
            elif pid in DUP_PRICE_FIX or not same_variant(s["zh"], kept_zh):
                add(pid, s, photo.get(keep), pid)
                report["split"].append((pid, s["zh"], keep, kept_zh, 0))
            else:
                # 同一件商品拍了两次：只留一件；名字取更符合价签的那个写法
                if RENAME_FROM_DUP.get(keep) == pid:
                    prefer[keep] = (s["zh"], s["en"], pid)
                report["merged"].append((pid, s["zh"], keep, kept_zh, 0))
        elif pid in skip:
            add(pid, s, None, "", "原照片模糊无法使用，价格需到店确认，建议重拍")
        else:
            sys.exit(f"{pid} 在复核底稿里找不到")

    for r in rows:
        if r["id"] in prefer:
            zh, en, src = prefer[r["id"]]
            report["renamed"].append((r["id"], r["name_zh"], zh, src))
            r["name_zh"], r["name_en"] = zh, en
            r["spec"] = extract_spec(zh, en) or r["spec"]
            r["spec_en"] = spec_en(r["spec"])

    # 合并后若完全同名同规格，只保留第一件（避免货架上出现两件一模一样的商品）
    seen, final = {}, []
    for r in rows:
        key = (norm(r["name_zh"]), r["spec"], r["price"])
        if key in seen:
            report["merged"].append((r["id"], r["name_zh"], seen[key], "（同名同价）", 1.0))
            continue
        seen[key] = r["id"]
        final.append(r)

    with open(prev_path, "w", encoding="utf-8-sig", newline="") as f:
        w = csv.DictWriter(f, fieldnames=FIELDS)
        w.writeheader()
        w.writerows(final)

    with open(os.path.join(CATALOG, "categories.csv"), "w", encoding="utf-8-sig", newline="") as f:
        w = csv.writer(f)
        w.writerow(["id", "name_zh", "name_en", "emoji", "desc_zh", "desc_en"])
        w.writerow(["deals", "特价优惠", "On Sale", "🏷️", "手写特价和多件优惠", "Marked-down prices and multi-buy offers"])
        for c in CATEGORIES:
            w.writerow(c)

    # ---------- 说明 ----------
    from collections import Counter
    cnt = Counter(r["category"] for r in final)
    md = ["# 对照表调整说明\n",
          f"按《商品对照表》更新了名称、英文名和分类，共 **{len(final)}** 个商品。",
          "价格、特价、促销仍以价签复核结果为准。\n",
          "## 分类\n", "| 分类 | 商品数 |", "| --- | --- |"]
    md += [f"| {c[1]} | {cnt.get(c[0], 0)} |" for c in CATEGORIES]
    md.append("\n另有自动汇总的「特价优惠」（有特价或多件优惠的商品），不需要在表里填。\n")
    md.append("## 表里的名称和照片对不上 → 已确认按照片保留（2026-11-17）\n")
    md.append("| 编号 | 表里写的 | 现在用的 | 依据 |\n| --- | --- | --- | --- |")
    md += [f"| {a} | {b} | {c} | {d} |" for a, b, c, d in report["override"]]
    md.append("\n项目里的 catalog/商品对照表.xlsx 已同步改成照片上的名称。\n")
    md.append("## 上次我合并成「重复拍摄」、表里分开写的 → 已拆成独立商品\n")
    md.append("| 编号 | 表里名称 | 原先合并到 |\n| --- | --- | --- |")
    md += [f"| {a} | {b} | {c} {d} |" for a, b, c, d, _ in report["split"]]
    md.append("\n口味不同的价格沿用同一张价签（照片里只有一张价签）；p118 星巴克拿铁单独读了价签；两个整箱用的是整箱价签（30.00€ / 28.80€）。\n")
    md.append("## 表里写了两次、其实是同一件 → 只保留一件\n")
    md.append("| 编号 | 名称 | 保留 |\n| --- | --- | --- |")
    md += [f"| {a} | {b} | {c} |" for a, b, c, *_ in report["merged"]]
    if report["renamed"]:
        md.append("\n表里同一件商品写了两种名字的，用了更符合价签的那个：\n")
        md.append("| 编号 | 原名称 | 改用 | 来自 |\n| --- | --- | --- | --- |")
        md += [f"| {a} | {b} | {c} | {d} |" for a, b, c, d in report["renamed"]]
    md.append("\n## 冷冻柜商品（只有价签照片）\n")
    md.append(f"对照表 p862 起的 {len(freezer_ids)} 个冷冻商品，按名称和价签记录逐一配对取价格。"
              "这些商品只拍到价签，小程序里先显示占位图（两款大虾除外，有实物照）。\n")
    if report["nomatch"]:
        md.append("没配上价签的：" + "、".join(f"{a} {b}" for a, b in report["nomatch"]) + "\n")
    if unmatched:
        md.append("价签记录里有、对照表里没有的：" + "、".join(f"{u} {frozen[u]['zh']}" for u in unmatched) + "\n")
    md.append("## 价格待确认（小程序显示「到店询价」）\n")
    md += [f"- {a} {b}" for a, b in report["noprice"]] or ["- 无"]
    open(os.path.join(CATALOG, "对照表调整说明.md"), "w", encoding="utf-8").write("\n".join(md) + "\n")

    print(f"products.csv：{len(final)} 个商品", dict(cnt))
    print(f"保留照片名称 {len(report['override'])}，拆开 {len(report['split'])}，合并 {len(report['merged'])}，"
          f"冷冻配对 {len(report['freezer'])}，未配对 {len(report['nomatch'])}，价签记录多出 {len(unmatched)}")
    return report, unmatched, frozen


if __name__ == "__main__":
    rep, unmatched, frozen = main()
    if "-v" in sys.argv or True:
        print("\n== 拆开");  [print(" ", x[0], x[1], "←", x[2], x[3], round(x[4], 2)) for x in rep["split"]]
        print("\n== 合并");  [print(" ", x[0], x[1], "→", x[2], x[3], round(x[4], 2)) for x in rep["merged"]]
        print("\n== 冷冻配对（分数低的在前）")
        for x in sorted(rep["freezer"], key=lambda x: x[4])[:30]:
            print(f"  {x[0]} {x[1]}  ⇐  {x[2]} €{x[3]}  ({x[4]:.2f})")
        print("\n== 改名"); [print(" ", *x) for x in rep["renamed"]]
        print("\n未配对：", rep["nomatch"], "\n多出：", [(u, frozen[u]["zh"]) for u in unmatched])
        print("\n价格为0：", rep["noprice"])
