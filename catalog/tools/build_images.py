#!/usr/bin/env python3
"""
把原始照片做成小程序用的商品图。

    python3 catalog/tools/build_images.py ../原始照片-801张

- 读取 catalog/products.csv，只处理上架且有 source_photo 的商品
- 方向按 EXIF 摆正后写进像素（不再依赖客户端识别方向）
- uploads/products/<id>.jpg        长边 1200，详情页用
- uploads/products/thumb/<id>.jpg  长边 480，列表用
HEIC 原图需要 macOS 自带的 sips 转码。
"""
import csv, os, subprocess, sys, tempfile
from concurrent.futures import ThreadPoolExecutor
from PIL import Image, ImageOps

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
OUT = os.path.join(ROOT, "uploads", "products")

def load(path):
    if path.lower().endswith(".heic"):
        tmp = tempfile.NamedTemporaryFile(suffix=".jpg", delete=False).name
        subprocess.run(["sips", "-s", "format", "jpeg", "-s", "formatOptions", "92", path, "--out", tmp],
                       check=True, capture_output=True)
        im = Image.open(tmp); im.load(); os.unlink(tmp)
    else:
        im = Image.open(path); im.load()
    return ImageOps.exif_transpose(im).convert("RGB")

def build(job):
    pid, src = job
    im = load(src)
    big = im.copy(); big.thumbnail((1200, 1200), Image.LANCZOS)
    big.save(os.path.join(OUT, pid + ".jpg"), quality=82, optimize=True, progressive=True)
    small = im.copy(); small.thumbnail((480, 480), Image.LANCZOS)
    small.save(os.path.join(OUT, "thumb", pid + ".jpg"), quality=76, optimize=True)
    return pid

def main():
    src_dir = sys.argv[1]
    os.makedirs(os.path.join(OUT, "thumb"), exist_ok=True)
    jobs = []
    for r in csv.DictReader(open(os.path.join(ROOT, "catalog", "products.csv"), encoding="utf-8-sig")):
        if not r["image"]:
            continue
        src = os.path.join(src_dir, r["source_photo"])
        if not os.path.exists(src):
            print("缺原图：", r["id"], r["source_photo"]); continue
        jobs.append((r["id"], src))
    with ThreadPoolExecutor(8) as ex:
        done = list(ex.map(build, jobs))
    print("生成", len(done), "张商品图（含缩略图）")

if __name__ == "__main__":
    main()
