# 复核底稿

逐张看图时记下的原始记录，留作凭证，日常不用动。

- `part-XXX.txt`：每行一张照片 —— `编号|分类|中文名|英文名|规格|价格|促销|备注`
  - `dup=pXXX` 这张和 pXXX 是同一个商品，重复拍了
  - `skip=原因` 这张不是商品照片或无法使用
  - `multi=…` 冷冻柜价签合照，一张里有多个商品，拆到 `frozen-labels.txt`
- `frozen-labels.txt`：冷冻柜价签逐条抄录（只有价签，没有商品图）

合并成主数据的脚本是 `../tools/merge_review.py`。合并之后以 `../products.csv` 为准。
