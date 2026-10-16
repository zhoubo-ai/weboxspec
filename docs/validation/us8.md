# US8：真实模型推荐

2026-10-16，执行者：Codex。已接通 OpenRouter 两阶段调用、POST SSE、推荐面板和既有配置/购物车。T104–T117完成；真实模型功能24/24、独立浏览器性能20/20、真实下单与最新版本全量回归均通过。本阶段AC-US08-01–05与AC-REV-03已更新为通过；SC-010仍保留真人相关性待验收。

## 已验证的实现

- 严格请求结构、Unicode 码点上限、日期/餐次、认证和 CSRF；流前返回 JSON 错误，流后只返回有界英文错误。
- 从真实 MySQL 取目标日期已安排、上架且有库存的菜品。已保存及明确新增的过敏原按基础标签与全部选项标签的并集排除；明确菜系、辣度、预算、蛋白质或菜名进一步过滤。
- 默认近七天非取消订单仅影响排序；明确排重日期由 Asia/Shanghai 运营日期归一化，查询完整范围。不能用模型截取的短证据把三十天缩成七天；含混区间要求澄清。
- 发出每张卡之前使用新的一致性快照，重读偏好版本、菜单成员、资料版本、库存和所需历史。菜品资料来自数据库，理由必须通过 ID、重复、英文纯文本、长度及事实校验。
- 外层 SSE 与内层 JSON 分别解析；完整 item 才可能发卡。必须同时具备完整 JSON、正常 stop 与 `[DONE]` 才能完成；中途失败保留明确 partial 状态。
- 默认并发 1、队列 8，总 deadline 20 秒包含排队和两阶段；取消关闭上游。下游使用非阻塞有界队列，15 秒心跳与 1 秒连接探测，避免短请求等到下一次心跳才发现浏览器已断开。探测不计有效内容、不延长会话空闲期限。
- 浏览器逐卡渲染，换诉求/日期/餐次/离开/退出取消旧请求；推荐卡复用 DishDialog 和购物车的配置、过敏原提醒及五份上限。推荐餐次带入结算。
- 只在有界内存诊断中保留请求 UUID、模型/provider/generation ID、可用 usage/fingerprint 和阶段耗时；不存提示词、用户 ID、邮箱、地址、会话或密钥。原始模型结果的额外诊断仅存在于 llm-live 测试类，输入都是登记的合成夹具。

## 自动化证据

- `.local/logs/us8-contract-red.log`：接口实施前的真实 404 行为失败。
- `.local/logs/us8-facts-green.log`：17 项真实 MySQL/HTTP 集成测试通过，覆盖完整与 partial、缺配置、库存/偏好/历史变化、满队列、总时限、浏览器取消和会话撤销；同次单元检查也通过。
- `.local/logs/us8-front-stream-red.log` → `.local/logs/us8-front-cutoff-fixed.log`：POST 流和取消接通后，52 项前端单元测试通过。测试还发现旧结算组件在自动切换餐次时存在短暂 ready 状态；现同时检查当前餐次是否合法，切换前立即撤销 ready。
- `.local/logs/us8-e2e-first.log`：Chromium/Firefox/WebKit × 375/768/1440，推荐故障恢复、订餐和规则回归共 **90 项通过**。其中 27 项为推荐场景；partial 明确标为故障注入，不作为真实推荐质量证据。
- `.local/logs/us8-front-minimum-build.log`：类型检查与生产构建通过。

## 固定真实模型用例及历史失败

登记文件：`backend/src/test/resources/recommendation/live-cases.json`，SHA-256 `7e17a65d10f3fe36e5d16cde74caa579b74b9bf056b739b0ecc3332146a193ec`。包含 24 组功能输入和独立的 20 次正常性能请求；固定并发 1、一次暖机、默认 `openai/gpt-4.1-mini`、temperature 0、max_tokens 512。业务今天为 2026-10-16，配送日期为隔离夹具中的合法未来日期；全部尝试保留，重跑使用新文件名。

此前轮次均保留在 `backend/target/failsafe-live-reports/recommendation-functional-*.json`，已复制到本目录：

- `initial`：15/24 通过。普通诉求误澄清、相对日期计算不稳定、理由包含未证实健康/过敏原断言等失败均保留。
- `retest1`：17/24 通过。保存了结构化意图和原始理由，定位到“healthy”“gluten-free”等违规断言，以及明确要意面却返回其他菜的情况。
- `retest2`：20/24 通过。继续定位“light meal”被误认成菜系、真实菜系比较语句被误拒绝，以及一项无依据的 dairy-free 断言。
- `retest3`：对理由增加负向正则约束的兼容性实验失败；上游输出空内容并以 length/max_output_tokens 终止。该限制已移除，不能用增加输出上限或接受截断输出绕过校验。能力探测见 `us8-schema-capability-raw.txt`；Python 客户端连接失败和 curl 探测也分别保留。
- `retest4`：22/24 通过，发现模型虚构过敏原及重述 without dairy 的断言。
- `retest5`：**24/24 通过**。只允许提取诉求实际提及的过敏原；第二阶段只接收规范化意图和过滤后的候选，不再传原始诉求或过敏原列表。每个有候选的成功结果均有两阶段真实 generation ID。

全部轮次已复制到本目录的 `us8-recommendation-functional-*.json`。此前失败不能从分母删除，也不由新的成功样本替换。

[OpenRouter 结构化输出](https://openrouter.ai/docs/guides/features/structured-outputs)与[上游结构化输出文档](https://developers.openai.com/api/docs/guides/structured-outputs)列出 schema 支持范围；本项目仍以实际 provider 行为和服务端验证为准。取消与流内错误语义参考 [OpenRouter Streaming](https://openrouter.ai/docs/api_reference/streaming)。

## 最终检查

`.local/logs/us8-backend-full.log`：19项单元＋103项集成，**122项全通过**；包含非Web seed启动、库存流重启/恢复、订单并发和所有推荐测试。`.local/logs/us8-front-final.log`：**52项单元全通过**。独立性能与真实卡片下单结果见下文。首卡缺失或未合法 COMPLETE 的对应指标记为 Infinity，p95 使用固定分母第 ceil(0.95×N) 个值。SC-010 的真人相关性和跨故事的负载验收仍属于最终阶段。

浏览器性能首轮 `us8-browser-performance-initial.json` 保留全部 20 次尝试，2 次证据完整、18 次测试客户端取流失败；首卡 p95 为 3249.90 ms，完成 p95 为 Infinity。页面能呈现卡片，但 Playwright 对已消费流调用 response.text() 不稳定。测试取证现使用浏览器内 Response.clone() 读取同一次真实响应；不修改应用读流或 DOM 计时，复测另存文件。


## 独立浏览器性能复测

`us8-browser-performance-retest1.json`：固定分母 **20**，有效首卡 **20/20**、合法 COMPLETE **20/20**，无 Infinity 项。首卡 p95 **3075 ms**（范围 2372.30–3125.30 ms）；完成 p95 **3666.10 ms**（范围 2504.50–3688.90 ms）。按升序第 19 个样本计算 p95。所有逐次卡片、理由、两阶段 generation ID、实际 model/provider、usage 和服务端耗时均保留。

客户端为 Apple M4 Pro、48 GiB RAM、macOS Darwin 24.6.0 arm64、Node 24.22.0、Chromium 156.0.8078.4、1440×900；并发 1、一次暖机。托管模型上游硬件未知，不声称模型运行于本机。计时从浏览器表单提交到真实卡片/完成状态的 DOM 变化，包含 CSRF、网络、排队、推理、数据库验证和前端更新；meta 与心跳不计首卡。

同次浏览器测试最后从真实推荐卡进入既有配置、购物车、报价和下单流程，确认真实订单为 Pending。命令 `./scripts/frontend.sh run test:e2e:live`，日志 `.local/logs/us8-browser-live-retest1.log`；1 项完整测试通过。加载服务端 OpenRouter 环境后运行，缺少密钥会失败，不跳过。每次复测使用不同 `WEBOX_LIVE_RUN` 标签，已有结果禁止覆盖。

此前浏览器首轮的取证失败和所有真实模型失败轮次均保留，不属于本次新的 20 次样本，也不以本次结果替换。SC-010 的真人 8/10 相关性要求尚未执行。

## 交付回归复核

2026-10-16 23:14–23:17 CST再次执行同一冻结用例，未修改分组、样本或阈值。真实功能[本轮结果](us8-recommendation-functional-release.json)24/24通过；独立浏览器[本轮20次结果](us8-browser-performance-release.json)20/20均有有效首卡及合法COMPLETE，首卡p95为3182.10ms，完整完成p95为3528.70ms，最后由真实推荐卡完成配置、报价和Pending订单。model为`openai/gpt-4.1-mini`，实际provider与generation标识随逐次结果保留。

日志`.local/logs/release-post-load-initial-live-functional.log`、`release-post-load-initial-live-browser.log`及命令时间/退出码`.local/validation-runs/release-post-load-initial.json`均保留。全部早期失败和旧复测仍可查，当前通过不抹除历史；真人相关性仍未执行。
