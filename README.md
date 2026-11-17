# Hiyori 亚超 · 后端（hiyori-server）

**这个文件夹就是要传到服务器上的全部东西**：后端代码、商品图、商品数据、部署配置。

| 路径 | 内容 |
| --- | --- |
| `src/` `pom.xml` | Spring Boot 后端（Java 21） |
| `Dockerfile` `docker-compose.yml` | 服务器上的运行方式（按 2G 内存调过） |
| `uploads/products/` | 商品图 698 张（长边 1200）＋ `thumb/` 缩略图（长边 480） |
| `catalog/商品对照表.xlsx` | 老板整理的商品名称 / 英文名 / 分类（以它为准） |
| `catalog/products.csv` | **商品主数据**：802 个商品的中英文名、分类、规格、价格、特价、条码 |
| `catalog/对照表调整说明.md` | 按对照表调整时的说明：哪几行和照片对不上、哪些合并 / 拆开 |
| `catalog/categories.csv` `home.json` | 分类、首页轮播 / 区块 / 热搜词 |
| `catalog/build_seed.py` | 把上面的数据生成数据库种子 `V2__seed_data.sql` |
| `catalog/复核报告.md` | 801 张照片的复核结果：改了什么、哪些还要到店确认 |
| `deploy/` | 上传脚本、服务器一键初始化、自动备份、Nginx 配置 |

## 本地跑起来

```bash
cd hiyori-server
export JAVA_HOME=/opt/homebrew/opt/openjdk@21
mvn spring-boot:run          # http://localhost:8080，本地用 H2 内存库，不用装 MySQL
mvn test                     # 10 个集成测试：接口、分页、搜索、鉴权、上传
```

图片目录默认就是 `./uploads`，不用再设环境变量。
小程序 `miniprogram/utils/config.js` 里的 `API.baseUrl` 指向这里。

## 改商品（上线之前）

**改名称 / 分类**：改 `catalog/商品对照表.xlsx`，然后

```bash
python3 catalog/tools/apply_sheet.py catalog/商品对照表.xlsx   # 合并进 products.csv（价格取价签复核记录）
python3 catalog/build_seed.py                                 # 生成数据库种子
```

对照表的「商品类别」只能填这 9 个：肉类海鲜、速冻面点、米面粮油、酱料调味、方便速食、零食饼干、饮料冲调、日用百货、其他。

**改价格 / 特价**：直接改 `catalog/products.csv`，再跑 `build_seed.py`。
（注意：之后再跑 `apply_sheet.py` 会按价签记录重算价格，手改的价格要重新改一次。）

改完重启后端：

```bash
pkill -f hiyori-mall-server; cd hiyori-server && mvn -q package -DskipTests && nohup java -jar target/hiyori-mall-server-0.1.0.jar > logs/server.log 2>&1 &
```

| 列 | 说明 |
| --- | --- |
| `price` | 价签原价；填 0 = 小程序显示「到店询价」 |
| `sale_price` | 特价；留空 = 没有特价 |
| `promo` / `promo_en` | 多件优惠，如 `2件3€` / `2 for €3` |
| `remark` / `remark_en` | 给顾客看的补充说明，如整箱价 |
| `image` | 留空 = 暂无图片（冷冻柜只拍到价签的那 96 个商品） |
| `status` | 1 上架，0 下架 |
| `review_note` | 复核时留的内部备注，不会显示给顾客 |

重新生成全部商品图（需要 `../原始照片-801张`）：`python3 catalog/tools/build_images.py ../原始照片-801张`

补拍了照片：放进 `uploads/products/` 和 `uploads/products/thumb/`，文件名用商品编号（如 `f001.jpg`），
再把 `products.csv` 里那一行的 `image` 填成 `products/f001.jpg`。

> **上线之后不要再改 `V2__seed_data.sql`**（Flyway 会校验它，改了后端起不来）。
> 上线后改商品请用管理员接口，或者新增 `V3__xxx.sql`。

## 接口一览

统一返回 `{ code, msg, data }`，`code = 0` 为成功。中英文两套文案一起返回（`name` / `nameEn`）。

| 方法 | 路径 | 鉴权 | 说明 |
| --- | --- | --- | --- |
| GET | `/api/home` | – | 首页：轮播、分类、商品区块、热搜（有缓存） |
| GET | `/api/categories` | – | 分类列表，带商品数；第一个 `deals` 是「特价优惠」 |
| GET | `/api/products?cat=sauce&page=1&size=30` | – | 某分类下的商品，分页；`cat=deals` = 所有特价 / 促销商品 |
| GET | `/api/products/search?keyword=&page=&size=` | – | 搜索：中文名 / 英文名 / 条码 / 分类名 |
| GET | `/api/products/{id}` | – | 商品详情 + 6 件同类推荐（下架返回 404） |
| GET | `/api/hot-keywords` | – | 热搜词（中英文） |
| POST | `/api/auth/login` `{code}` | – | `wx.login` 的 code → token |
| GET | `/api/me` | 登录 | 当前用户 |
| GET | `/api/admin/products` | 管理员 | 后台商品分页（含已下架） |
| POST | `/api/admin/products` | 管理员 | 新建商品 |
| PUT | `/api/admin/products/{id}` | 管理员 | 编辑商品 |
| PATCH | `/api/admin/products/{id}/status` `{status}` | 管理员 | 上架 1 / 下架 0 |
| DELETE | `/api/admin/products/{id}` | 管理员 | 删除（逻辑删除） |
| POST | `/api/admin/upload` (multipart `file`) | 管理员 | 上传商品图，返回 `url` |

## 部署到服务器（腾讯云轻量 2核2G，上海）

**第一次（约 15 分钟）**

```bash
# 1. 你的电脑：生成登录密钥，并装到服务器上（只需输入一次服务器密码）
ssh-keygen -t ed25519 -N "" -f ~/.ssh/id_ed25519
ssh-copy-id ubuntu@服务器公网IP

# 2. 你的电脑：打包并上传代码 + 商品图
cd ~/WeChatProjects/hiyori-server
./deploy/upload.sh ubuntu@服务器公网IP

# 3. 服务器：初始化（装 Docker / Nginx、加虚拟内存、生成随机密码、每天自动备份、启动）
ssh ubuntu@服务器公网IP
cd /srv/hiyori && sudo bash deploy/server-setup.sh
```

浏览器打开 `http://服务器公网IP/api/categories` 能看到分类就成功了。
小程序 `utils/config.js` 里 `BASE_URLS.test` 填这个地址，`ENV` 改成 `"test"` 即可在开发者工具里测试。

**微信登录**：服务器上 `nano /srv/hiyori/.env` 填 `WX_SECRET`（小程序后台 → 开发管理 → 开发设置），
再 `cd /srv/hiyori && docker compose up -d server`。没填之前浏览商品不受影响，只是不能登录。

**以后每次更新**：在电脑上再跑一次 `./deploy/upload.sh ubuntu@服务器公网IP`。

**备案通过后**（换成域名 + HTTPS）：

1. 域名解析：`api.你的域名` 的 A 记录指向服务器 IP；
2. 服务器：`sudo apt install -y certbot python3-certbot-nginx`，把 `deploy/nginx.conf.example` 改好域名放到
   `/etc/nginx/sites-available/hiyori`（`alias` 改成 `/srv/hiyori/uploads/`），`sudo certbot --nginx -d api.你的域名`；
3. `.env` 里 `APP_PUBLIC_BASE_URL` 改成 `https://api.你的域名/files`，重启 server；
4. 小程序后台 → 服务器域名：request 和 downloadFile 合法域名都填 `https://api.你的域名`；
5. `utils/config.js` 的 `prod` 填域名，`ENV` 改成 `"prod"`，上传、提交审核。

**备份**：每天 03:30 自动导出数据库到 `/srv/hiyori/backups/`（保留 14 天）。手动备份：`bash deploy/backup.sh`。
建议偶尔把 `backups/` 下载到自己电脑：`scp ubuntu@服务器公网IP:/srv/hiyori/backups/*.gz ~/Downloads/`。

**排查**：`docker compose ps` 看状态，`docker compose logs --tail 80 server` 看后端日志。

> 服务器在上海，**域名必须先 ICP 备案**才能用（腾讯云控制台「网站备案」），小程序本身也要在公众平台做「小程序备案」。
> 备案前只能用 IP 在开发者工具 / 手机调试模式里测试，不能提交审核。

## 设计要点

- **登录**：`wx.login` → 后端调微信 `code2Session` 换 openid → 自动注册 → 签发 JWT；
  token 里只放用户 id，角色每次从库里读，撤销管理员立即生效。
- **双语**：中英文存在同一行（`name` / `name_en`），接口一起返回，小程序切换语言不用重新请求。
- **特价**：`deals` 是按 `sale_price` / `promo` 自动汇总的虚拟分类，不用手工维护。
- **首页一条 SQL**：多个区块的商品用一次 JOIN 查出再分组，避免 N+1；`/api/home` 有缓存，后台改商品时清掉。
- **图片**：数据库只存路径，大图和缩略图分开；线上由 Nginx 直接从磁盘读。
- **表结构版本化**：Flyway 迁移脚本，本地 H2 和线上 MySQL 跑同一份。
- **上传安全**：按文件头魔数判断类型、服务端生成文件名（防路径穿越）、5MB 限制。
