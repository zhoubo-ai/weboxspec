# WeBox 部署、启动与使用指南

更新日期：2026-10-17。适用于当前项目的员工订餐、管理员 Console、实时库存、OpenRouter 推荐和经营看板。

本文面向部署人员和使用者。说明采用中文，页面名称及按钮保留系统实际的英文名称。

## 1. 先选择使用方式

| 场景 | 操作入口 |
| --- | --- |
| 使用这台开发机上已经启动的系统 | 打开 <http://127.0.0.1:15678>；后端端口为 `18888` |
| 从源码在自己电脑上启动 | 按第 2 节操作，默认前端 `5173`、后端 `8080` |
| 部署到 Linux 服务器供员工访问 | 按第 3 节配置 MySQL、systemd 和 Nginx HTTPS |
| 员工注册、选餐、结算和查看订单 | 见第 4 节 |
| 管理菜品、每日菜单、订单和看板 | 见第 5 节 |
| 停机、重启、备份或排查问题 | 见第 6、7 节 |

`127.0.0.1` 只表示访问者自己的电脑。手机或其他员工不能通过这个地址访问开发机；多人使用应访问第 3 节部署好的 HTTPS 域名。

当前开发机项目目录为 `/Users/zhoubo/weboxspec`。换一台电脑时，请改用实际项目目录。当前预览服务的进程信息在 `.local/pids/preview.json`，日志在 `.local/logs/preview-*.log`；进程是否仍在运行，以实际健康检查为准。

## 2. 本地从零启动

### 2.1 准备环境

| 组件 | 要求 | 用途 |
| --- | --- | --- |
| JDK | 17 | 编译和运行 Spring Boot 后端 |
| Node.js / npm | Node 24、npm 11 | 安装依赖、启动和构建前端 |
| Docker / Compose | Docker 服务已启动，Compose v2 可用 | 运行独立 MySQL、执行隔离集成测试 |
| OpenRouter | 可用的 API Key，后端可访问其 HTTPS API | 真实 AI 推荐 |
| Python | Python 3，使用备份脚本时建议 3.10 或以上 | 数据库与图片备份、隔离恢复 |

首次构建还需要能够访问 Maven、npm 和容器镜像源。项目自带 Maven Wrapper，不需要另外安装全局 Maven；Redis 不是本项目的运行依赖。

进入项目根目录，检查工具：

```bash
cd /Users/zhoubo/weboxspec
./scripts/backend.sh --version
./scripts/frontend.sh --version
docker version
docker compose version
```

后端脚本优先使用 `JAVA_HOME` 或项目已有的 `.local/toolchains/java17`；前端脚本优先使用 `WEBOX_NODE_BIN` 或项目已有的 `.local/toolchains/node24`。新机器不自带这些本地工具链，需要先安装对应版本。`WEBOX_NODE_BIN` 应指向 `node` 可执行文件，`JAVA_HOME` 应指向 JDK 安装目录。

### 2.2 配置本地环境文件

在项目根目录执行：

```bash
test -e .env || cp .env.example .env
chmod 600 .env
```

用编辑器打开 `.env`。已有文件保留原值；首次启动至少填写下列配置：

| 配置 | 填写说明 |
| --- | --- |
| `MYSQL_ROOT_PASSWORD` | MySQL 管理员密码，设置非空强密码 |
| `MYSQL_DATABASE` / `MYSQL_USER` | 默认均为 `webox` |
| `MYSQL_PASSWORD` | 应用数据库用户的非空强密码 |
| `MYSQL_PORT` | 默认 `3306`；本机已占用时可调整 |
| `DB_URL` | 默认连接本机 `3306/webox`；端口、库名与上面一致，保留 UTC 连接参数 |
| `DB_USERNAME` / `DB_PASSWORD` | 分别与 `MYSQL_USER` / `MYSQL_PASSWORD` 一致 |
| `ADMIN_EMAIL` | 初始管理员邮箱，模板为 `admin@example.com`，可自行修改 |
| `ADMIN_INITIAL_PASSWORD` | 初始管理员密码；8–128 个字符，包含字母和数字 |
| `MEDIA_ROOT` | 图片持久目录；模板为项目下 `.local/media` |
| `OPENROUTER_API_KEY` | 填入自己的真实密钥，仅由后端读取 |
| `OPENROUTER_MODEL` | 当前配置为 `openai/gpt-4.1-mini` |

数据库连接格式示例：

```dotenv
DB_URL='jdbc:mysql://127.0.0.1:3306/webox?connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true'
```

`.env` 使用 shell 赋值语法；密码正确加引号，不在等号两侧加空格。密钥不要使用 `VITE_` 前缀，也不要填进前端代码。本文不提供固定通用密码；管理员密码以你实际配置并初始化时使用的值为准。

### 2.3 启动 MySQL

在准备运行后端的终端 A 中，从项目根目录加载配置：

```bash
set -a
source .env
set +a
mkdir -p "$MEDIA_ROOT"
docker compose --env-file .env -f infra/compose.yaml up -d mysql
docker compose --env-file .env -f infra/compose.yaml ps
```

等待 `mysql` 显示 `healthy`。必要时查看数据库启动日志：

```bash
docker compose --env-file .env -f infra/compose.yaml logs --tail=80 mysql
```

Compose **只启动 MySQL**，不会自动启动前端或后端。数据保存在 Docker 命名卷，普通重启不会丢失。已有卷中的数据库密码不会因为修改 `.env` 而自动改变。

### 2.4 初始化管理员、菜品与菜单

继续在已加载 `.env` 的终端 A 中执行：

```bash
./scripts/backend.sh spring-boot:run -Dspring-boot.run.profiles=seed
```

这是一次性初始化，完成后正常退出，不会持续监听 HTTP 端口。它会运行数据库迁移，并初始化：

- `.env` 指定的管理员账号。
- 9 道英文样例菜品、图片及自定义选项。
- 按 `Asia/Shanghai` 计算的今天和明天菜单，每道初始供应 20 份。

重复执行只补缺失数据，不重置已有管理员密码，不覆盖运营人员已编辑的菜品或已存在的日期菜单，也不会抹掉订单和库存占用。种子图片已经随源码提供，不需要原始图片压缩包或开发者的 Downloads 目录。

**初始化不是自动排餐任务。** 后续日期的菜单需要管理员在 `Daily menus` 中安排。晚上 15:00 后当天餐次已经关闭，结算会转向次日午餐；这时应确认次日菜单已安排。

### 2.5 启动后端

继续在终端 A 中执行：

```bash
./scripts/backend.sh spring-boot:run
```

该进程保持运行。默认监听 `8080`。另开终端检查：

```bash
curl --fail --silent --show-error http://127.0.0.1:8080/api/v1/health
```

预期返回 `{"status":"UP"}`。这用于检查应用及数据库入口，不能代替真实 AI 推荐验证。

每次新开后端终端，都要先进入项目根目录，再执行 `set -a`、`source .env`、`set +a`。脚本不会自动加载根目录的 `.env`。

### 2.6 启动前端

在终端 B 中进入项目根目录：

```bash
cd /Users/zhoubo/weboxspec
./scripts/frontend.sh ci
./scripts/frontend.sh run dev
```

打开 <http://127.0.0.1:5173>。前端将 `/api` 和 `/media` 转发到本机后端 `8080`，浏览器统一通过前端地址访问。前端终端不需要加载含数据库密码和模型密钥的 `.env`。

后续普通重启可直接执行 `run dev`；依赖锁文件变化或首次安装时再执行 `ci`。

### 2.7 使用其他端口

如果 `8080` 或 `5173` 已被其他项目使用，在完成数据库和 seed 步骤后，分别在两个终端运行下面的启动命令。

终端 A 先加载 `.env`，再覆盖后端端口：

```bash
PORT=18888 ./scripts/backend.sh spring-boot:run
```

终端 B 启动匹配的前端代理：

```bash
WEBOX_API_ORIGIN=http://127.0.0.1:18888 ./scripts/frontend.sh run dev -- --port 15678 --strictPort
```

此时访问 <http://127.0.0.1:15678>，健康检查为：

```bash
curl --fail --silent --show-error http://127.0.0.1:15678/api/v1/health
```

这也是当前开发机预览使用的端口组合。已有实例运行时直接访问，不要重复启动占用相同端口的进程。更改后端端口时，必须同步修改 `WEBOX_API_ORIGIN`；不要通过停止其他项目来腾出端口。

## 3. Linux 服务器生产部署

### 3.1 部署结构与准备事项

本文给出单台 Linux 主机、systemd、独立 MySQL 容器和 Nginx 的部署步骤。服务器已安装 JDK 17、Docker Compose v2、Nginx；在服务器构建时还需 Node 24/npm 11。Nginx 需要支持项目配置中的 `http2 on` 指令。

```text
员工浏览器 → HTTPS :443 / Nginx
                    ├─ 前端静态文件 /srv/webox/frontend
                    └─ /api、/media → Spring Boot 127.0.0.1:8080
                                            ├─ MySQL 127.0.0.1:3306
                                            ├─ 图片 /srv/webox/media
                                            └─ OpenRouter HTTPS API
```

准备好域名、DNS 和有效 TLS 证书。网络入口只开放所需的管理端口及 HTTP/HTTPS；后端和 MySQL 保持本机访问。当前库存广播、缓存和限流按单应用实例设计，增加应用副本前需要补充跨实例事件广播和共享限流。

以下示例假定源码已放在服务器 `/opt/weboxspec`，在有 sudo 权限的部署账号下执行。不要将开发机 `.env`、`.local`、`ai-conversations` 或测试凭据作为公开静态文件发布。

先确认 Docker 守护进程已启动并随系统启动：

```bash
sudo systemctl enable --now docker
sudo docker version
```

### 3.2 构建发布产物

在源码目录、未加载生产环境配置的终端中执行：

```bash
cd /opt/weboxspec
./scripts/frontend.sh ci
./scripts/frontend.sh run test:unit -- --run
./scripts/frontend.sh run build
./scripts/backend.sh verify
```

后端验证需要可用的 Docker，并启动隔离测试数据库；不要让部署账号为执行这些命令而连接生产业务库。构建产物为：

- `backend/target/webox-api-0.1.0-SNAPSHOT.jar`
- `frontend/dist/`

也可以在构建机完成这些步骤，再将产物及 `infra/` 文件传到服务器。仅运行已经构建好的产物时，服务器不需要 Node 或 Maven。

### 3.3 创建运行用户和持久目录

```bash
getent passwd webox >/dev/null || sudo useradd --system --user-group --home-dir /srv/webox --shell /usr/sbin/nologin webox
sudo install -d -o root -g root -m 0755 /srv/webox /srv/webox/frontend
sudo install -d -o webox -g webox -m 0700 /srv/webox/media /srv/webox/runtime
sudo install -d -o root -g root -m 0700 /etc/webox
sudo install -m 0644 backend/target/webox-api-0.1.0-SNAPSHOT.jar /srv/webox/webox-api.jar
sudo cp -R frontend/dist/. /srv/webox/frontend/
sudo chmod -R a+rX /srv/webox/frontend
sudo install -m 0644 infra/compose.yaml /etc/webox/compose.yaml
```

后续升级只替换 JAR 和前端产物。`/srv/webox/media` 与 MySQL 数据卷需要长期保留。

### 3.4 配置并启动生产 MySQL

用 `sudoedit /etc/webox/mysql.env` 新建配置，将下列占位密码替换为不同的非空强密码：

```dotenv
MYSQL_DATABASE=webox_prod
MYSQL_USER=webox_app
MYSQL_PASSWORD='REPLACE_WITH_DATABASE_PASSWORD'
MYSQL_ROOT_PASSWORD='REPLACE_WITH_DIFFERENT_ROOT_PASSWORD'
MYSQL_PORT=3306
MYSQL_IMAGE=mysql:8.4.11@sha256:24989179f58856db763f418010c1169fecc944e3ec1ea7e921572d83eb070f70
```

```bash
sudo chmod 600 /etc/webox/mysql.env
sudo docker compose --project-name webox-prod --env-file /etc/webox/mysql.env -f /etc/webox/compose.yaml up -d mysql
sudo docker compose --project-name webox-prod --env-file /etc/webox/mysql.env -f /etc/webox/compose.yaml ps
```

等待健康状态通过。每次管理此部署都保留 `--project-name webox-prod` 和上述文件路径，以使用同一个数据卷。使用现有独立 MySQL 时，可跳过容器创建，但需先创建专用库和非 root 应用账号，并保证应用连接参数匹配。

应用启动会执行 Flyway 迁移，账号除日常读写外还需本 schema 的建表、修改表、索引等迁移权限。Compose 首次初始化创建的用户只获其指定数据库的权限。

### 3.5 配置后端服务环境

用 `sudoedit /etc/webox/application.env` 创建如下文件，替换邮箱、密码和 API Key：

```dotenv
DB_URL="jdbc:mysql://127.0.0.1:3306/webox_prod?connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true"
DB_USERNAME=webox_app
DB_PASSWORD="REPLACE_WITH_DATABASE_PASSWORD"
ADMIN_EMAIL=admin@example.com
ADMIN_INITIAL_PASSWORD="REPLACE_WITH_ADMIN_PASSWORD"
MEDIA_ROOT=/srv/webox/media
PORT=8080
SERVER_ADDRESS=127.0.0.1
SESSION_TIMEOUT=8h
OPENROUTER_BASE_URL=https://openrouter.ai/api/v1
OPENROUTER_MODEL=openai/gpt-4.1-mini
OPENROUTER_API_KEY="REPLACE_WITH_OPENROUTER_KEY"
WEBOX_RATE_LIMIT_AUTH_CAPACITY=240
WEBOX_RATE_LIMIT_AUTH_PER_MINUTE=300
WEBOX_RATE_LIMIT_GLOBAL_AUTH_CAPACITY=600
WEBOX_RATE_LIMIT_GLOBAL_AUTH_PER_MINUTE=1200
```

```bash
sudo chmod 600 /etc/webox/application.env
```

这是 **systemd EnvironmentFile 格式**，不要写 `export`，不要依赖 `${PWD}`、`$VAR` 或命令替换；所有目录使用绝对路径。数据库密码与 `mysql.env` 中的应用账号密码一致，管理员密码符合 8–128 位且包含字母和数字的规则。

**共享出口 IP 的限流配置需要上线前验证。** 源码默认认证容量为 60、每分钟补充 120，CSRF、注册和登录共用同一 IP 的额度。上一轮复核已发现，200 次 CSRF 请求在约 60 秒内可出现 21 次 429。上面的 240/300 与已有订餐压测配置一致，但该压测使用预先建立的登录会话，不能证明 200 人同时注册或登录也全部通过。应按公司的实际出口和登录规模验证完整流程，再确定限额；不要把关闭限流作为解决方案。说明见[运行维护](operations.md)及[性能验证](validation/performance.md)。

### 3.6 初始化生产数据并启动后端

先执行 `/usr/bin/java -version`，确认输出为 JDK 17。实际 Java 路径不同时，同时调整下方命令和 `infra/webox.service` 的 `ExecStart`。MySQL 就绪后，用一次性 systemd 任务读取生产环境文件，运行 `prod,seed`：

```bash
sudo systemd-run --unit=webox-seed --wait --collect \
  --property=User=webox \
  --property=Group=webox \
  --property=WorkingDirectory=/srv/webox \
  --property=EnvironmentFile=/etc/webox/application.env \
  /usr/bin/java -Duser.timezone=UTC -jar /srv/webox/webox-api.jar --spring.profiles.active=prod,seed
sudo journalctl -u webox-seed -n 80 --no-pager
```

确认初始化成功退出，再安装常驻服务。

```bash
sudo install -m 0644 infra/webox.service /etc/systemd/system/webox.service
sudo systemctl daemon-reload
sudo systemctl enable --now webox
sudo systemctl status webox --no-pager
curl --fail --silent --show-error http://127.0.0.1:8080/api/v1/health
```

常驻服务使用 `prod` profile，默认只监听本机，登录 Cookie 强制 Secure。初始化不会在以后每次服务重启时自动执行。上述 seed 会安排示例餐单，正式运营前由管理员核对菜品、过敏原、价格和供应数量。

### 3.7 配置 Nginx 和 HTTPS

将有效证书安装到以下位置，私钥只允许相应管理进程读取：

- `/etc/nginx/tls/fullchain.pem`
- `/etc/nginx/tls/privkey.pem`

安装项目配置：

```bash
sudo install -m 0644 infra/nginx.conf /etc/nginx/conf.d/webox.conf
sudoedit /etc/nginx/conf.d/webox.conf
```

将两个 `server_name _;` 都改成实际域名，例如 `server_name meals.example.com;`。核对静态目录 `/srv/webox/frontend` 和后端 `127.0.0.1:8080`；检查已有站点，避免重复定义相同域名。然后执行：

```bash
sudo nginx -t
sudo systemctl enable --now nginx
sudo systemctl reload nginx
```

通过实际域名检查，下面示例域名需要替换：

```bash
curl --fail --silent --show-error https://meals.example.com/api/v1/health
```

使用 HTTPS 域名登录，不要让员工直接访问后端端口。保留项目 `/api/` 的关闭缓冲、关闭压缩和 65 秒读取超时配置，以保证库存和 AI 流式响应可用；不要开启代理层的自动写请求重试。

### 3.8 部署后的使用检查

依次完成以下检查；下单会产生真实业务记录，请使用指定的测试账号和测试供应：

1. 员工可注册、登录；刷新后仍保持登录。
2. 管理员可进入 Console；普通员工不能访问 Console。
3. 选一个尚未截单且已安排菜单的日期/餐次，确认图片、选项和价格正常。
4. 完成 `Review order` → `Place Order`，核对订单日期、地址、配置和金额。
5. 第二个浏览器看到库存变化；取消 Pending 测试订单后库存恢复。
6. `Help me choose` 能返回真实推荐卡片，并可进入选项配置和加购。
7. 如需验证经营统计，另用明确允许完成的测试订单走完确认、完成流程，再刷新看板；Completed 订单不能通过普通取消撤回，记录不会自动清理。

## 4. 员工使用流程

### 4.1 注册和登录

打开网站，点击 `Create an account`。填写有效邮箱及密码，邮箱不限定公司域名，不能重复注册；密码为 8–128 位且包含字母和数字。注册成功后跳转到 `Sign in`，需要再登录一次。

管理员使用初始化时配置的账号登录，默认进入 Console。自行注册的账号都是 Employee，不会因为填写了某个邮箱而自动成为管理员。

### 4.2 设置饮食偏好

进入 `Preferences`：

- `Allergen reminders`：标记过敏原。普通菜单仍展示相关菜品，加购命中时弹出确认提醒。
- `Favorite cuisines`：选择喜欢的菜系。
- `Spice level`、`Flavor intensity`：选择辣度和口味浓淡。
- `Minimum budget (CNY)`、`Maximum budget (CNY)`：填写预算上下限，或者两项都留空。金额以元填写，例如 `15.00`、`30.00`。

点击 `Save preferences` 保存。点击 `Clear preferences` 只清空表单，仍需保存才会生效。超预算会提示，但不阻止合法下单。

### 4.3 选菜和购物车

1. 进入 `Menu`，确认 `Delivery date`。
2. 使用菜系多选或 `Search dishes` 搜索名称、描述；关键词最多 50 个字符。
3. 点击 `View & customize` 查看详情，完成必选配置，再选择可选加料。
4. 查看实时单价，点击 `Add to cart`。重复点击同一配置会增加数量，不同配置单独显示。
5. 进入 `Cart` 调整数量、修改配置或移除条目，然后进入结算。

每单合计最多 **5 份**，不是最多 5 个品种。达到上限或库存不足时不能继续增加。`Sold Out` 表示售罄；1–3 份会显示低库存提示。`Live inventory` 表示实时库存连接正常。

菜单上的 `Recommended for Me` 开关会调整排序并高亮匹配项。购物车草稿按当前账号保存在同一浏览器，不在不同设备之间同步；重新进入时会检查价格和可售状态，变化项需要确认。

### 4.4 Checkout、Review 和提交订单

1. 在 `Checkout` 选择配送日期、`Lunch` 或 `Dinner`。
2. 选择历史地址，或填写 `New address`；地址最多 200 个字符。
3. 点击 `Review order`，获得服务端报价，核对菜品、配置、份数、单价、小计和总价。
4. 阅读过敏原、预算等提示，确认配送信息后点击 `Place Order`。
5. 提交成功进入 `Order details`，查看订单编号、配送信息、状态和金额。

报价有效期为 5 分钟，过期或内容变化后需要重新 Review。**Review 不会创建订单或占用库存**；点击 Place Order 才会提交餐单。当前系统没有在线付款步骤。

如果显示 `Retry same order request`，说明上一次请求结果尚未确认，使用该按钮重试原请求，避免另起一笔订单。如果库存变化导致失败，按提示回到购物车调整后重新 Review。

### 4.5 截单和重复订单规则

所有业务截止时间以服务器 `Asia/Shanghai` 时区为准：

| 场景 | 行为 |
| --- | --- |
| 当天午餐 | 必须在 10:00 前提交，10:00 起关闭 |
| 当天晚餐 | 必须在 15:00 前提交，15:00 起关闭 |
| 已选午餐关闭，但当天晚餐仍开放 | 切换为当天 Dinner |
| 当天两餐均已关闭 | 切换为次日 Lunch |
| 日期或餐次自动改变 | 点击 `Confirm delivery selection`，再重新 Review |
| 同一天同餐次已有 Pending 或 Confirmed 订单 | 显示 `View Existing Order`，进入已有订单 |

例如，10:30 结算当天午餐，会切到当天晚餐；16:00 则切到次日午餐。切换日期后可能没有菜单，需要管理员安排对应日期的供应。

### 4.6 查看和取消订单

进入 `Orders`，可按状态筛选，点击 `View order` 查看详情。仅 `Pending` 订单提供 `Cancel order`；取消成功后恢复原配送日库存。`Confirmed`、`Completed` 不能通过员工页面取消。

| 状态 | 含义 |
| --- | --- |
| Pending | 已提交，等待管理员处理 |
| Confirmed | 管理员已确认 |
| Completed | 管理员标记完成 |
| Cancelled | 已取消，相关库存已恢复 |

### 4.7 AI 选餐助手

在 `Menu` 点击 `Help me choose`，确认餐次，输入英文诉求，再点击 `Find recommendations`。例如：

```text
I want something light today.
Something mild under 40 yuan.
Nothing I ate yesterday.
```

推荐结合偏好、所选日期的可售库存和近期订单，以卡片逐步出现。选择卡片后完成自定义选项，再加入购物车；数量和下单规则与普通菜单相同。AI 推荐会排除标记的过敏原冲突菜品，这与普通菜单的“提醒后可继续”不同。

没有符合条件的菜品时会显示无匹配结果；模型服务不可用时会显示错误，仍可使用普通菜单订餐。`Stop recommendations` 可停止当前推荐。

## 5. 管理员使用流程

### 5.1 进入 Console

管理员登录后默认进入 Console。导航包含 `Dishes`、`Daily menus`、`Orders`、`Dashboard`。点击 `Employee menu` 可切回员工选餐页面，之后也可从员工页面进入 Console。

### 5.2 新增、编辑和上下架菜品

进入 `Dishes`，点击 `Create dish`，填写英文名称、描述、分类、蛋白质来源、辣度、基础价格和过敏原。按实际配方设置必选/可选配置、单选/多选、加料价格及选项过敏原。

上传 JPEG、PNG 或 WebP 图片，输入图片最大 5 MiB；图片会经过解码校验和转换。完成保存后，在列表点击 `Publish` 上架；已有菜品可通过 `Edit` 修改，或 `Unpublish` 下架。

**保存菜品、Publish 和安排每日菜单是不同操作。** 新菜品需要上架并被安排到员工选择的日期，才能出现在该日期菜单。下架会使菜品在员工菜单中不可见，不会改写历史订单。

### 5.3 安排每日供应

1. 进入 `Daily menus`，默认日期是次日；也可选择当天或其他日期。
2. 在 `Find a dish to schedule` 搜索菜品，点击 `Schedule`。
3. 勾选需要供应的菜品，在 `Total supply` 填写该日总供应数量。
4. 点击 `Save daily menu`。

`Total` 是总供应，`Allocated` 是订单已占用数量，`Remaining` 是剩余数量。**总供应不能低于已占用数量**，午餐和晚餐共享同一天的供应。想增加剩余库存时，应增加总供应，而不是把总供应直接改成想要的剩余数。

多人同时修改时若出现冲突，使用 `Reload latest menu` 加载最新数据，核对后再保存。请持续提前安排后续日期；系统不会自动复制昨天菜单。

### 5.4 处理订单

进入 Console 的 `Orders`，筛选并打开订单，按实际处理进度操作：

| 当前状态 | 可执行操作 | 结果 |
| --- | --- | --- |
| Pending | `Confirm order` | 变为 Confirmed |
| Pending | `Cancel order` | 变为 Cancelled，恢复库存 |
| Confirmed | `Complete order` | 变为 Completed |
| Completed / Cancelled | 查看 | 不提供回退操作 |

管理员可以处理员工订单。修改冲突时先刷新确认当前状态，不要反复点击旧状态下的按钮。

### 5.5 查看经营看板

进入 `Dashboard`，默认统计运营今天；可选择 `Report date`，通过 `Use today` 回到今天，点击 `Refresh dashboard` 刷新。

看板包括订单概览、菜品销量 Top 10、午晚餐分布、最近 7 天趋势和库存预警。收入按 **Completed 订单金额** 统计，按配送日期归属；Pending、Confirmed、Cancelled 不计收入。排行也按 Completed 订单份数统计。因此仅创建或确认订单后收入仍为 0，属于当前已采用的统计规则。

## 6. 停止、重启、更新和备份

### 6.1 本地停止与重启

对按第 2 节前台启动的进程，在后端、前端各自终端按 `Ctrl+C`。需要停止数据库时执行：

```bash
docker compose --env-file .env -f infra/compose.yaml stop mysql
```

再次使用时，先 `up -d mysql`，再加载后端环境并启动前后端。已有数据通常无需再次 seed。`docker compose down -v` 会删除数据库卷，不用于日常停止或故障排查。

当前开发机已有后台预览进程时，先核对 `.local/pids/preview.json` 和实际进程命令，再管理本项目进程；不要批量终止所有 Java 或 Node 进程。

### 6.2 生产服务管理

```bash
sudo systemctl status webox --no-pager
sudo journalctl -u webox -n 100 --no-pager
sudo systemctl restart webox
```

`systemctl stop webox` 用于停止，`systemctl start webox` 用于再次启动。修改 `/etc/webox/application.env` 后需要重启应用；修改服务文件后还要先 `systemctl daemon-reload`。Nginx 配置修改后，先 `nginx -t`，通过后再 reload。

更新版本时先备份并保留上个版本产物，在维护窗口停止应用，替换 JAR 和前端文件后重新启动并检查。数据库迁移随启动执行；回退旧 JAR 前需确认其兼容当前 schema，不能把代码回退等同于数据库回退。

### 6.3 数据和图片备份

在保留 `infra/` 脚本的源码目录执行。下例对应第 3 节部署方式，输出目录必须是尚不存在的新目录：

```bash
webox_mysql_container=$(sudo docker compose --project-name webox-prod --env-file /etc/webox/mysql.env -f /etc/webox/compose.yaml ps -q mysql)
webox_backup_dir="/secure/backups/webox-$(date -u +%Y%m%dT%H%M%SZ)"
sudo ./infra/backup.sh --container "$webox_mysql_container" --database webox_prod \
  --media-root /srv/webox/media --output "$webox_backup_dir"
```

备份包含数据库、媒体文件和带 SHA-256 的 `manifest.json`，也包含账号等私人业务数据。定期备份到受控存储，避免在数据库迁移期间执行。

恢复工具只支持带 `webox.restore-target=true` 标签的隔离容器、名称以 `webox_restore_` 开头的空数据库及空媒体目录。它不会覆盖正在使用的生产数据库。完整恢复条件与演练步骤见[运行维护文档](operations.md#备份与隔离恢复)。

## 7. 常见问题

| 现象 | 检查和处理 |
| --- | --- |
| 前端无法启动，提示端口已占用 | 检查是否已有实例；需要独立实例时按第 2.7 节同时调整前后端端口 |
| 页面能打开，但 API 报 502 或连接失败 | 检查后端健康接口、`WEBOX_API_ORIGIN` 或 Nginx upstream 是否指向实际端口 |
| MySQL 启动失败 | 检查 Docker、端口、非空密码及 Compose 日志；已有卷不会同步新 `.env` 密码 |
| 管理员登录失败 | 确认对当前数据库运行过 seed，使用当时的初始账号密码；重跑 seed 不会重置密码，当前无自助找回入口 |
| 菜单为空或新菜不出现 | 检查配送日期、Publish 状态、该日期是否 Schedule 并保存；后续日期需持续排餐 |
| 图片不显示或上传失败 | 检查 `MEDIA_ROOT`、文件权限、`/media` 代理、格式与 5 MiB 限制；生产运行用户为 webox |
| 加购按钮不可用 | 检查必选配置、总份数是否达到 5、剩余库存及购物车变化确认提示 |
| Review 按钮不可用 | 等待配送时间检查完成；确认切换后的日期/餐次，修正购物车中变化或不可售条目 |
| 结算只显示 View Existing Order | 该账号当天同餐次已有 Pending 或 Confirmed 订单；进入已有订单查看 |
| Quote expired / 报价过期 | 重新 Review，核对最新价格和库存后提交 |
| 提交后网络中断 | 使用 Retry same order request，并查看 Orders，避免创建新的提交意图 |
| AI 无推荐或提示不可用 | 先区分无匹配菜品与模型故障；检查后端 Key、模型配置、网络和账户可用额度，修改配置后重启 |
| 库存一直显示连接中或中断 | 检查 `/api/` SSE 是否被代理缓冲、压缩或超时；重连后应获取新快照 |
| Dashboard 有订单但收入为 0 | 检查报表配送日期及订单是否 Completed；仅确认订单还不计收入 |
| 多人登录时出现 429 / RATE_LIMITED | 根据 Retry-After 稍后重试，检查共享 IP 认证限额，按第 3.5 节校准并验证 |
| 生产登录成功后仍像未登录 | 使用 HTTPS；检查同源代理和 Secure Cookie，避免混用 HTTP、域名与直接后端地址 |

## 8. 验证与参考资料

需要重新运行自动化时，在开发或隔离构建环境使用：

```bash
./scripts/frontend.sh run typecheck
./scripts/frontend.sh run test:unit -- --run
./scripts/frontend.sh run build
./scripts/backend.sh verify
./scripts/frontend.sh exec -- playwright install
./scripts/frontend.sh run test:e2e
```

浏览器测试会使用独立的 `15173/18081` 端口和测试数据库。后端构建、E2E 和负载测试共享 `backend/target`，按顺序执行。真实 LLM 测试另见 README，需要加载服务端 OpenRouter 配置，并会实际调用模型。

- [README：项目介绍与全部验证入口](../README.md)
- [运行维护：部署边界、限流、监控、清理、备份恢复](operations.md)
- [架构说明](architecture.md)
- [OpenAPI 接口文档](../specs/001-webox-meal-ordering/contracts/openapi.yaml)
- [测试证据索引](validation/README.md)
- [Checkout / Review 主流程复测](validation/checkout-review.md)
- [真人验收任务](validation/user-acceptance.md)

已有自动化测试不替代生产域名、真实网络及多人登录的部署验收；真人使用效率、提醒理解和推荐相关性仍需实际使用者完成验收。
