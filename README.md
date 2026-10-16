# WeBox

企业员工餐食订购平台，包含全部Tier 1–3：账号、菜单与配置、购物车、报价与订餐、偏好提醒、管理员运营、实时库存、真实AI推荐和经营看板。界面、菜单和系统反馈使用英文，用户地址支持原样Unicode。

首次部署或使用请阅读[部署、启动与使用指南](docs/deployment-startup-usage.md)，包含本地运行、Linux生产部署、员工下单、管理员操作及常见问题。

全部Tier 1–3工程实现与自动化验收已完成，包括真实OpenRouter、200会话持续10分钟负载、三浏览器回归、生产HTTPS、权限、正常重启及隔离备份恢复。真人验收等待参与者；原生对话已完整导出至本次交付回复并核验。实际状态见[任务清单](specs/001-webox-meal-ordering/tasks.md)、[验收台账](specs/001-webox-meal-ordering/acceptance.md)及[验证索引](docs/validation/README.md)。

本次本机运行地址为[http://127.0.0.1:15678](http://127.0.0.1:15678)，API为18888，已核验页面及同源代理健康检查。使用项目独立MySQL和私有配置；运行PID记录位于`.local/pids/preview.json`，日志为`.local/logs/preview-*.log`。常规从零启动默认端口仍见下文。

## 环境与选型

- 后端：JDK17、Spring Boot4.1.1、Spring Security/JDBC/Session、Flyway、Caffeine；独立MySQL8.4进程，不使用内存数据库替代。
- 前端：Node24/npm11、React19.2、TypeScript5.9、Vite8.3、React Query、Recharts。组件便于复用配置表单，类型对应HTTP契约，Query管理服务端数据失效，图表与可访问附表使用同一份数据。
- Docker与Compose v2可用；构建可访问Maven Central、npm registry和镜像源。真实推荐需要OpenRouter凭据及网络访问。

`./scripts/backend.sh`校验JDK17，优先使用JAVA_HOME或项目本地工具链，调用官方Maven Wrapper；无需全局Maven。`./scripts/frontend.sh`校验Node24/npm11，优先使用WEBOX_NODE_BIN、本地工具链或PATH。当前验证使用Temurin17.0.20.1和Node24.22.0；`.local/`中的开发机工具链不作为交付依赖。新机器先安装对应版本，必要时设置JAVA_HOME和WEBOX_NODE_BIN。

## 从零启动

从项目根目录执行。首次复制模板，填写非空MySQL密码、与MySQL用户一致的DB密码、管理员初始密码及OpenRouter key；保留已有`.env`，不要覆盖它。该文件采用shell赋值语法，密码须正确加引号。

```bash
test -e .env || cp .env.example .env
chmod 600 .env
# 编辑 .env 后加载；不要输出完整环境或凭据。
set -a
source .env
set +a
mkdir -p "$MEDIA_ROOT"
docker compose --env-file .env -f infra/compose.yaml up -d mysql
docker compose --env-file .env -f infra/compose.yaml ps
```

等待MySQL healthy，执行一次幂等初始化。Flyway会自动建表，seed创建配置的管理员、九道英文样例菜品和今明两天各20份供应。重跑只补缺失内容，不重置管理员密码或覆盖已运营菜单、价格与库存。已有MySQL命名卷的用户密码不会随`.env`修改自动改变。

```bash
./scripts/backend.sh spring-boot:run -Dspring-boot.run.profiles=seed
./scripts/backend.sh spring-boot:run
```

在另一个终端从根目录启动前端：

```bash
./scripts/frontend.sh ci
./scripts/frontend.sh run dev
```

浏览器访问http://127.0.0.1:5173。后端默认8080；前端同源代理`/api`与`/media`。员工可自行注册，管理员使用`.env`中的初始账号进入Console。后端必须加载私有配置；OpenRouter默认模型为`openai/gpt-4.1-mini`，key只由后端读取，不能使用VITE_前缀。真实模型不可用时订餐仍可工作，推荐会明确报错。

`MEDIA_ROOT`和MySQL命名卷持久保留，不随构建清空。种子图片与`backend/src/main/resources/seed/ATTRIBUTION.md`随源码提供，seed不依赖开发机Downloads目录。详细时区、截止时间、版本和接口约定见[quickstart.md](specs/001-webox-meal-ordering/quickstart.md)。

## 验证入口

以下命令从根目录执行。测试自动创建隔离MySQL和媒体，不连接开发业务库；Docker不可用时明确失败。先安装Playwright浏览器，Linux可按环境需要增加`--with-deps`。

```bash
./scripts/frontend.sh exec -- playwright install
./scripts/frontend.sh run typecheck
./scripts/frontend.sh run test:unit -- --run
./scripts/frontend.sh run build
./scripts/backend.sh verify
./scripts/frontend.sh run test:e2e
```

常规浏览器套件覆盖Chromium、Firefox、WebKit与375/768/1440宽度。它使用15173/18081专用端口，并自行启动测试后端；不要在E2E或负载运行时执行另一个Maven构建，因为它们共用backend/target。

```bash
# 需要已构建的 frontend/dist；实际TLS代理与隔离恢复
./scripts/backend.sh -Pproxy verify
./scripts/backend.sh -Ppersistence verify
# 独立500菜品、200会话；60秒爬升后持续600秒，并采集真实DOM计时
./scripts/backend.sh -Pload gatling:test
# 已加载真实OpenRouter配置时，分别运行功能与预登记浏览器性能样本
WEBOX_LIVE_RUN="$(date -u +%Y%m%dT%H%M%SZ)-functional" ./scripts/backend.sh -Pllm-live verify
WEBOX_LIVE_RUN="$(date -u +%Y%m%dT%H%M%SZ)-browser" ./scripts/frontend.sh run test:e2e:live
```

正式负载启用限流；共享IP认证突发容量240、每分钟300，其他写入/AI限制保持默认。每人一条SSE，200人全部就绪后才计满10分钟。负载服务与发生器分属不同JVM，独立数据库退出后清理。`-Dwebox.load.smoke=true`仅用于10人短跑检查，不能充当正式性能证据。真人任务和匿名记录表见[user-acceptance.md](docs/validation/user-acceptance.md)，自动化不替代真人结论。

## 架构、接口与运行

- [实现架构](docs/architecture.md)：模块、数据流、精确金额、库存事务、会话、缓存与推荐验证。
- [OpenAPI](specs/001-webox-meal-ordering/contracts/openapi.yaml)：60个schema、32个HTTP操作；前端类型生成命令为`./scripts/frontend.sh run generate:api`。
- [SSE契约](specs/001-webox-meal-ordering/contracts/streaming.md)、[数据模型](specs/001-webox-meal-ordering/data-model.md)、[修订决策](specs/001-webox-meal-ordering/decisions.md)。
- [运行与维护](docs/operations.md)：单实例部署、HTTPS、安全Cookie、生产专用数据库账号、限流指标、定期清理、每日备份及隔离恢复。

生产配置位于`infra/nginx.conf`、`infra/webox.service`与`application-prod.yml`。需要部署方提供主机、域名和真实TLS证书；本项目的本地生产代理验证不代表已对外部署。扩展多应用节点前须增加共享事件广播与共享限流。

公开仓库提供[AI编程对话的脱敏Markdown副本](ai-conversations/README.md)，保留现有导出的消息顺序和工具记录，隐藏检测到的密钥及会话凭据；文件摘要和脱敏数量见[公开清单](ai-conversations/manifest.public.json)。修改前原件完整保存在本机被忽略的`.local/private-conversations/`中，公开副本不代替“未修改原生导出”的验收。

`python3 scripts/export-conversations.py`仍可复制本项目全部可访问的Codex原生JSONL（包括代理会话）到`ai-conversations/`，生成私有`manifest.json`；这些新原始导出默认被Git忽略。`python3 scripts/finalize-conversation-export.py`等待本轮原生task_complete后复制包含最终回复的完整会话，核验摘要成功才更新T136和AC-DC-010。后续新对话、其他工具或不可访问历史须分别补充原生完整导出，并在公开前单独检查凭据。
