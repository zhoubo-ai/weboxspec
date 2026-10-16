# 并发负载与用户可见性能

正式200会话负载复测已通过，T131/T132与SC-003的技术条件已实测达标。测试协议在首次正式运行前登记于[performance-protocol.json](performance-protocol.json)，固定每项20次、零重试、nearest-rank p95；失败和未执行样本对应正无穷，不从分母删除。

入口：先执行`./scripts/frontend.sh run build`，再执行`./scripts/backend.sh -Pload gatling:test`。每次自动创建独立MySQL、临时媒体、500道菜和200个员工会话；独立应用JVM监听随机loopback端口，没有开发业务库连接或测试HTTP后门。60秒爬升，每人持有一条库存SSE；全部200条连接就绪才开始600秒稳态计时，后端聚合活跃流每秒采样。员工以1–5秒思考时间浏览、搜索、筛选、查看详情，并完成合法报价、下单和取消。Gatling分别报告请求类型和业务冲突，要求200笔有效下单、零非预期失败。

同一稳态中启动Chromium1440×900，实际加载生产Vite构建，依次采集菜单、筛选、搜索、购物车、有效下单、库存与看板各20次。菜单和搜索等待新的HTTP响应及可用DOM；搜索包含200ms防抖。购物车从页面内点击到数量更新后两帧计时，避免自动化框架轮询时间掩盖0.3秒阈值。库存从发起真实下单到库存徽章更新计时，包括写事务和网络，比只计提交后广播更保守。每次测量前核对服务器仍至少200条活跃流。

机器为Apple M4 Pro、14逻辑CPU、48GiB内存、macOS15.7.3。Docker Engine28.1.1分配2CPU、6214086656字节内存；应用JDK17单独JVM，堆256MiB–1GiB，Hikari16连接。MySQL8.4.11镜像固定digest；发生器、应用与浏览器同机，数据库在Docker VM。所有结果只代表该环境，不能推断任意生产机器或跨地域网络。

限流保持开启。因200人来自同一loopback/NAT地址，认证桶配置突发240、每分钟300；普通写入120/300、AI10/30及实例认证600/1200保持默认。该参数同时记录在代码、README和结果，不通过关闭限流取得性能。

## 短跑与历史失败

`-Dwebox.load.smoke=true`仅用10人、5秒爬升和30秒稳态验证工具，不替代正式负载。首次短跑10笔合法订单和全部Gatling请求成功，浏览器因地址控件尚未渲染而跳过填写，订单计时失败；固定分母中的未执行指标计正无穷。修复后短跑六项计时各2次全部通过：菜单p95约86.8ms、搜索231.1ms、购物车23.4ms、下单85.2ms、库存296.3ms、看板106.2ms。随后补入规格要求的独立筛选计时，正式样本涵盖七项。

失败及复测原始结果分别见[performance-smoke-initial.json](performance-smoke-initial.json)和[performance-smoke-retest1.json](performance-smoke-retest1.json)。原始Gatling、应用、浏览器日志和trace已在clean构建前归档至`.local/validation-runs/release-initial/pre-clean-backend-target/load/`。

真实AI性能使用另一组预登记20次正常请求，不混入此200会话负载；已有功能24/24和浏览器20/20结果、全部失败历史与模型标识见[us8.md](us8.md)。真人相关性仍依赖10名参与者，不能由这些延迟数据推断。

Gatling脚本使用官方[场景DSL](https://docs.gatling.io/concepts/scenario/)及[SSE检查](https://docs.gatling.io/reference/script/sse/)，HTTP/SSE统计只作为传输侧证据，不替代浏览器DOM计时。

## 首轮正式负载失败

正式初轮已达到200会话，并成功完成200笔有效下单。集中订单和取消产生广播积压，后端活跃库存连接降至不足200；浏览器严格检查负载前提后判失败，缺失样本仍保留在各20次分母中。初轮在失去有效稳态后提前停止，不能作为10分钟通过证据。见[performance-full-initial.json](performance-full-initial.json)；停止后日志中的连接拒绝属于主动终止过程，同样保留。

排查发现逐帧会话查询和flush造成放大开销。修复将最多16条连续完整事件、通常不超过64KiB合为一次socket写入（单独初始快照仍受1MiB上限约束），每次写入前仍实时查询会话，不缓存有效性、不丢帧、不改变256条/1MiB队列与5秒写超时。新增关闭原因聚合指标和突发帧顺序/撤销测试，再执行200人短跑与完整600秒复测。

修复后的200人短跑已通过：200笔合法订单、零Gatling失败、整段30秒至少200条活跃库存连接，以及七项DOM指标各2次通过。证据见[performance-smoke200-after-batching.json](performance-smoke200-after-batching.json)。正式复测按相同阈值与20次分母重新登记于[performance-protocol-v2.json](performance-protocol-v2.json)，结果如下。

## 正式复测结果（2026-10-16 23:06 CST）

`full-after-batching`完成60秒爬升和600秒稳态，600个每秒样本全部至少200条活跃库存连接，200笔有效下单、0业务冲突。Gatling共41,308次请求，全部成功；传输侧p95为21ms，但验收使用下列独立DOM计时。队列溢出、写超时及执行器饱和关闭均为0；写入/会话异常关闭聚合值51保留在原始结果中，该指标也计入浏览器切页后的socket关闭，不能将其表述为全部连接从未关闭。负载要求的200条连接始终保持。

| 用户可见操作 | 样本数 | 失败数 | p95（ms） | 门槛（ms） |
| --- | ---: | ---: | ---: | ---: |
| 菜单 | 20 | 0 | 235.78 | 2000 |
| 筛选 | 20 | 0 | 75.00 | 2000 |
| 搜索（含防抖） | 20 | 0 | 225.98 | 2000 |
| 购物车 | 20 | 0 | 26.60 | 300 |
| 有效下单 | 20 | 0 | 104.70 | 3000 |
| 库存更新 | 20 | 0 | 296.94 | 2000 |
| 看板刷新 | 20 | 0 | 98.22 | 3000 |

Chromium156.0.8078.4、1440×900；浏览器140次尝试全部保留。原始[服务器采样](performance-full-after-batching.json)与[逐次DOM计时](performance-browser-after-batching.json)随文档提供；完整日志`.local/logs/load-full-after-batching.log`，Gatling报告`backend/target/gatling/mealorderingsimulation-20261016145429394/index.html`。初轮失败保留在上一节，不被本次通过覆盖。

## 会话竞态修复后的最终复核

随后复核发现：在空队列检查和待flush的socket写入之间，新事件恰好到达时可能漏掉本次会话查询。确定性背压探针修复前写出撤销后的数据，修复后只写session.expired；对应新单元测试已纳入20项单元+119项集成的完整回归，相关浏览器27项通过。最终负载重新按[performance-protocol-v3.json](performance-protocol-v3.json)登记，负载规模、阈值和固定分母均不改变。

[本轮逐次DOM计时](performance-browser-final.json)七项各20次、共140次已全部通过；600秒服务器稳态已通过，600个逐秒样本全部至少200条活跃库存连接，200笔有效下单、0业务冲突。队列溢出、写超时及执行器饱和关闭均为0；写入/会话异常聚合值52原样保留，同样包含浏览器切页的socket关闭。[最终服务器原始结果](performance-full-final.json)记录全部样本与关闭原因。

| 操作 | 样本数 | 失败数 | p95（ms） | 门槛（ms） |
| --- | ---: | ---: | ---: | ---: |
| 菜单 | 20 | 0 | 225.08 | 2000 |
| 筛选 | 20 | 0 | 56.95 | 2000 |
| 搜索（含防抖） | 20 | 0 | 232.24 | 2000 |
| 购物车 | 20 | 0 | 25.40 | 300 |
| 有效下单 | 20 | 0 | 91.80 | 3000 |
| 库存更新 | 20 | 0 | 295.23 | 2000 |
| 看板刷新 | 20 | 0 | 101.03 | 3000 |

最终Gatling共41,336次请求、0失败，200次合法下单断言通过，命令退出0。日志`.local/logs/release-final-load.log`；原始HTML为`backend/target/gatling/mealorderingsimulation-20261016152020623/index.html`。本轮替代先前复测作为最终实现版本的性能结论，先前成功与失败均保留。
