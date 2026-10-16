# 干净构建与全量交付回归

全部工程实现与自动化交付回归通过。完整后端、324项浏览器、真实OpenRouter以及库存竞态修复后的最终负载均有实际证据；真人验收仍未执行。

环境：macOS15.7.3/Apple M4 Pro/48GiB，Temurin17.0.20.1、Node24.22.0/npm11.11、Docker28.1.1，独立MySQL8.4.11。源代码没有.git，因此使用命令记录、固定依赖、测试产物与SHA-256标识，不虚构commit。原始PRD保留不变。

| 命令（项目根目录） | 当前结果 | 证据 |
| --- | --- | --- |
| `./scripts/frontend.sh ci` | 退出0，按锁文件重装 | `.local/logs/release-frontend-initial.log` |
| `./scripts/frontend.sh run typecheck` | 退出0 | 同上 |
| `./scripts/frontend.sh run test:unit -- --run` | 55/55，14个文件 | 同上 |
| `./scripts/frontend.sh run build` | 退出0，生产构建 | 同上 |
| `./scripts/backend.sh clean verify` 后修复负载再 `verify` | 20单元+119集成全部通过，无跳过 | `.local/logs/release-final-backend.log`；clean基线见`release-backend-retest2.log` |
| `./scripts/backend.sh -Pproxy verify` | 19单元+1实际Nginx/TLS集成通过 | `.local/logs/release-proxy-retest2.log` |
| `./scripts/backend.sh -Ppersistence verify` | 退出0，19单元+1演练，含24项三浏览器检查通过 | [persistence.md](persistence.md) |
| `./scripts/backend.sh -Pload gatling:test` | 最终200会话/600秒、200笔有效订单、41,336请求零失败，140次DOM计时全部通过 | [performance.md](performance.md) |
| `./scripts/frontend.sh run test:e2e` | 324/324通过，三浏览器×375/768/1440，6.0分钟 | `.local/logs/release-post-load-initial-e2e.log` |
| `./scripts/backend.sh -Pllm-live verify` | 本轮24/24真实功能通过 | [us8.md](us8.md) |
| `./scripts/frontend.sh run test:e2e:live` | 本轮20/20通过；首卡/完成p95 3.182/3.529秒 | [us8.md](us8.md) |

命令开始/结束时间和退出码保存在`.local/validation-runs/release-initial/`与`release-retest2/commands.json`；原始日志均保留。首次clean前的backend/target完整归档至私有`release-initial/pre-clean-backend-target/`，其中包含之前失败的浏览器trace、短负载和恢复演练产物。恢复演练启动真实打包JAR、执行Flyway和幂等seed，使用新的独立MySQL和临时媒体完成从零启动，测试不复用开发库。

## 发现与整改

首次完整后端运行暴露两处跨场景测试隔离问题：HealthContractIT把整库已发布事件计入未发布事件预期；MaintenanceJobsIT故意保留的近期临时文件留给了后续媒体测试。改为按本场景aggregate_key核对总量/未发布量，并在维护测试finally中清理它自己创建的临时文件。第3次clean verify完整通过；前两次失败日志仍保留，不把选择性通过替代完整结果。

实际浏览器切页取消图片或SSE响应时，容器原本会再次尝试写JSON错误，引发无意义的转换异常。已识别不可用响应、已提交响应，以及Tomcat内部ERROR/INCLUDE的错误路由；普通请求、其他include和Admin权限不放宽。新增内部错误转发边界与32操作权限矩阵复测通过，最新真实代理和恢复场景继续核验断连行为。

文档静态验证已确认60个schema、32个HTTP操作、15个schema示例、7个类型化SSE示例、13个合法及20个非法边界、51个用户场景、118条来源需求、10项修订决策及原文118处引用一致；原始PRD未改。静态结果独立于运行结果，最终AC状态在[acceptance.md](../../specs/001-webox-meal-ordering/acceptance.md)逐项回填。

10名首次使用者及管理员真人任务尚未执行，见[user-acceptance.md](user-acceptance.md)。原始对话将由`scripts/finalize-conversation-export.py`等待本轮原生task_complete后完整复制，校验摘要后才更新T136/AC-DC-010；导出状态见[原生manifest](../../ai-conversations/manifest.json)。真人判断不由自动测试代替。

修复后正式负载与最终回归使用的源码指纹见[release-source.json](release-source.json)，用于没有Git仓库时定位实际实现版本。

正式负载初轮发现逐帧会话查询和flush放大开销，导致库存流队列溢出。保持逐次写入的实时会话校验，改为最多16帧/64KiB有界批量写入后，突发/撤销回归、200人短跑及正式10分钟负载全部通过；具体失败与修复证据见[performance.md](performance.md)。默认集成测试关闭后台outbox派发，仅实时流测试显式开启，避免缓存的多个Spring上下文竞争同一测试库的事件。

代码复核又发现空队列到写入之间的新事件竞态：原生socket背压探针在检查间隙撤销会话并加入事件，修复前确认写出未授权数据。现强制新到达事件重新进入会话检查；同一探针修复后只发session.expired。新增InventoryAuthorizationTest，红/绿记录分别为`.local/logs/inventory-empty-queue-race-red.log`及`inventory-empty-queue-race-green.log`。完整后端、相关浏览器及正式负载按[protocol-v3](performance-protocol-v3.json)再次核验，不用此前通过掩盖这项修复。

库存竞态修复后的三浏览器×三尺寸定向回归27/27通过，日志`.local/logs/release-final-inventory-browser.log`。此前完整324项套件与本次27项复核分别计数。真实模型复核使用修复后的代码。服务端私有.env权限0600，交付源码、文档及前端生产构建未检出实际OpenRouter key。

## 最终结果与本机运行

最终负载`full-final-authorization`退出0，41,336次请求全部成功，600个逐秒样本全部至少200条活跃库存流。结果、固定20次分母和原始p95见[performance.md](performance.md)。完整命令起止时间与退出码汇总于[release-results.json](release-results.json)；历史失败依旧独立保留。

本机正式JAR按正常配置执行幂等seed后启动于18888，Vite开发入口为[http://127.0.0.1:15678](http://127.0.0.1:15678)。页面、API及前端代理的健康检查均为200/UP，使用`weboxspec-mysql-1`独立MySQL及已有私有配置。默认8080/5173另有本机服务，因此此处使用已核验的独立端口。日志`.local/logs/preview-seed.log`、`preview-backend.log`、`preview-frontend.log`；PID位于`.local/pids/preview.json`。

2026-10-16后续按用户要求复测Checkout与Review，修复确认后旧提示未清除的问题。三浏览器相关99项、55项前端单元与正常服务实测通过；这一轮的实现指纹、截图与证据独立记录于[checkout-review.md](checkout-review.md)。
