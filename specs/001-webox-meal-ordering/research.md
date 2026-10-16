# WeBox 技术研究与决策

**日期**：2026-10-16  
**范围**：全部 Tier 1–3；无开发或交付时限。输入见 [spec.md](spec.md)、[delivery-constraints.md](delivery-constraints.md)。

按规划技能分别派发后端与事务、前端与实时体验、真实 AI 集成三个研究任务，并核验官方资料。本文件中的方案是项目决策；性能数字仍需实施后的真实测量，不是文档研究得出的性能保证。

## R-01 后端与数据库版本

- **Decision**：Java 17、Spring Boot 4.1.1、Spring MVC、Spring Security、Spring JDBC、Spring Session JDBC、Flyway、MySQL 8.4 LTS / InnoDB。Maven Wrapper 使用 3.9 系列，Spring 依赖遵循 Boot BOM。首轮实施锁定其余依赖补丁与容器 digest，不使用动态 `latest`。
- **Rationale**：用户指定 Java 17 + Spring Boot + 独立 MySQL。官方资料确认 Boot 4.1.1 支持 Java 17。约 200 员工的业务适合单体内的明确事务边界，显式 SQL 便于核查库存锁序与统计。
- **Alternatives considered**：Boot 3.5 同样兼容，但没有旧代码迁移约束；JPA 可行但不减少关键 SQL 的复杂度；不引入微服务、分布式事务或嵌入式数据库。AI 直接适配 OpenRouter HTTP，不引入未经核验的 Spring AI 兼容组合。
- **Sources**：[Boot requirements](https://docs.spring.io/spring-boot/system-requirements.html)、[MySQL release model](https://dev.mysql.com/doc/refman/8.4/en/mysql-releases.html)、[Connector/J compatibility](https://dev.mysql.com/doc/connector-j/en/connector-j-versions.html)。

## R-02 前端与工具链

- **Decision**：Node 24 LTS、React 19.2.x、TypeScript 5.9.x、Vite 8.x、TanStack Query 5、React Router 7 Declarative、Recharts 3；使用 npm 与提交的 package-lock.json。采用 CSS Modules、语义化 HTML 和原生表单组件，避免额外全局状态框架。
- **Rationale**：SPA 与独立 Java 服务边界清晰；上述系列已有官方发布，Node 24 满足 Vite 的 Node 要求。Query 负责远端状态，reducer 负责最多 5 份的购物车，组件职责可独立验证。React、react-dom、react-is 锁定一致兼容版本。
- **Alternatives considered**：SSR 框架对本内部系统没有必要收益；Redux/Zustand 对当前本地状态规模非必需；图表附表格数据，避免仅依赖视觉读取。
- **Sources**：[React 19.2](https://react.dev/blog/2025/10/01/react-19-2)、[TypeScript 5.9](https://www.typescriptlang.org/docs/handbook/release-notes/typescript-5-9.html)、[Vite 8](https://vite.dev/blog/announcing-vite8.html)、[Node releases](https://nodejs.org/en/about/previous-releases)、[Query installation](https://tanstack.com/query/latest/docs/framework/react/installation)、[Router modes](https://reactrouter.com/start/modes)、[Recharts dependencies](https://github.com/recharts/recharts/blob/main/package.json)。

## R-03 身份、会话与输入

- **Decision**：同源 Cookie session，Spring Session JDBC 持久化到独立 MySQL；生产 Cookie 为 HttpOnly、Secure、SameSite=Lax。登录轮换会话，退出撤销。CSRF 保持启用，登录、注册、退出及其他写请求均带 token；登录/退出后重取 token。密码使用可升级标记的 Argon2id 和所需 BouncyCastle 依赖，参数以目标机压测确定并记录。
- **Rationale**：满足刷新登录、退出撤销、SSE 认证和跨用户隔离，不向浏览器存储长期身份令牌。规格允许 128 字符密码，不能直接套用 bcrypt 的 72 字节限制。邮箱按 trim + Locale.ROOT 小写规范化，并以二进制精确唯一值比较，避免数据库重音折叠扩大邮箱等价关系。
- **Alternatives considered**：JWT 会增加撤销与浏览器凭据存储负担；SameSite 不替代 CSRF；管理员入口隐藏不替代服务端权限校验。参数化 SQL、输出按文本渲染、服务端限长和角色/所有权检查共同执行。
- **Sources**：[Session JDBC](https://docs.spring.io/spring-session/reference/configuration/jdbc.html)、[Session management](https://docs.spring.io/spring-security/reference/servlet/authentication/session-management.html)、[SPA CSRF](https://docs.spring.io/spring-security/reference/servlet/exploits/csrf.html)、[Password storage](https://docs.spring.io/spring-security/reference/features/authentication/password-storage.html)。

## R-04 下单幂等与有效餐次唯一性

- **Decision**：订单分别建立 `UNIQUE(user_id,idempotency_key)` 和基于生成列 `active_slot` 的有效餐次唯一索引；`active_slot` 在 Pending/Confirmed 为 1，其他状态为 NULL。同 key 校验规范化请求 hash，重放优先于截止、报价过期、库存及有效订单检查。
- **Rationale**：幂等表示同一意图不能重复，有效订单约束表示不同意图也不能占同一餐次。MySQL 唯一索引允许多个 NULL，取消与完成释放有效名额但保留历史记录。旧 key 与订单同生命周期保留，取消后仍返回原订单。
- **Alternatives considered**：应用层先查后插不能处理竞态；按 status 建唯一索引会允许 Pending 与 Confirmed 同时存在；不另设需人工同步释放的订单占位表。用户行短锁序列化同人订单写入，并有数据库唯一约束兜底，无需独立幂等预留表。
- **Sources**：[MySQL unique indexes](https://dev.mysql.com/doc/refman/8.4/en/create-index.html)、[Locking reads](https://dev.mysql.com/doc/refman/8.4/en/innodb-locking-reads.html)。

## R-05 库存与状态事务

- **Decision**：每日菜品供应保留 total、allocated、version；remaining 为差值。按菜品汇总各配置需求，订单、条目快照、地址、库存及 outbox 在同一 MySQL 事务完成。统一采用 user → order/quote → dish（ID 排序）→ daily supply（日期/ID 排序）的适用锁序；任何地方不反向取锁。业务写事务使用 READ COMMITTED，权威字段锁定读取；报价读取和报表采用只读 REPEATABLE READ，避免当前根版本与旧子表快照混读。
- **Rationale**：数量不为负、整单原子提交和取消恢复必须共享权威数据；全部配置与午晚餐共用每日供应。取消与确认竞争同一用户/订单锁，只有合法转换成功。Completed 释放有效餐次名额但仍占供应。
- **Alternatives considered**：Redis 库存会增加双写和恢复复杂度；仅禁用按钮无法保证正确性。死锁/锁超时只做有限次完整事务重试，不能重放某条 SQL；失败事务先回滚，之后在新事务解析幂等/唯一冲突。
- **Sources**：[InnoDB locks](https://dev.mysql.com/doc/refman/8.4/en/innodb-locks-set.html)、[Deadlock handling](https://dev.mysql.com/doc/refman/8.4/en/innodb-deadlocks-handling.html)。

## R-06 报价、日期和金额

- **Decision**：持久化短期 checkout quote（5 分钟），固定菜品、配置版本、数量、日期餐次和整数分金额；不预留库存。提交使用 quoteId + 地址 + Idempotency-Key。价格/选项变更或越过截止返回可恢复冲突，重新确认后创建新 quote 和新 key。成功的同 key 重放不依赖 quote 仍存在。
- **Rationale**：把员工确认的内容与最终权威核验连接起来；不允许客户端直接声明成交价格。配送日用 LocalDate/DATE；业务时区 Asia/Shanghai；事件用 UTC instant。金额用 long/BIGINT 整数分，验证溢出和 JS 安全整数边界，不用二进制浮点累加。
- **Alternatives considered**：签名报价也可，但短期记录便于追踪和消耗控制；库存预占不在需求范围。BigDecimal/DECIMAL 正确但整数分更直接。过去日期拒绝，今天过期餐次切换；未来未截止的原选择保留。

## R-07 购物车与菜单性能

- **Decision**：购物车采用本地 reducer 和按用户隔离的 localStorage，退出保留，同账号重登恢复并重验；配置按 option ID 排序去重，不按名称/选择顺序区分。每次已接受动作都处理，不以防抖丢掉有效加购。菜单分页默认 24、最多 100，搜索延迟 200ms 并取消旧请求；分类 OR、关键词 AND。
- **Rationale**：无需跨设备购物车同步，300ms 本地反馈不必等待网络；所有数量、价格和可用性在下单再验证。菜单资料采用按 dishId/version 定位的 Caffeine 缓存，maximumSize=10000、expireAfterWrite=5m；日期成员、当前版本和库存查 MySQL，更新提交后失效旧键，旧事务回填不能覆盖新版本。库存、报价、权限、订单和看板不缓存；静态资源指纹缓存，Query 仅保存短期展示数据并由写入/事件失效。
- **Alternatives considered**：服务端购物车需要额外版本与并发协议；依据本次修订覆盖后端缓存考察点，明确采用 Caffeine，版本由 Boot BOM 管理；Redis 非必选。缓存不承担订单正确性，记录命中、失效、过期和加载证据。
- **Sources**：[Spring Boot caching](https://docs.spring.io/spring-boot/reference/io/caching.html)、[Caffeine eviction](https://github.com/ben-manes/caffeine/wiki/Eviction)、[React queued updates](https://react.dev/learn/queueing-a-series-of-state-updates)、[Query mutation scopes](https://github.com/TanStack/query/blob/main/docs/framework/react/guides/mutations.md)。

## R-08 实时库存协议

- **Decision**：库存采用同源 GET EventSource；事务 outbox 每 250ms 扫描未发布记录，提交后投递绝对数量与单调行版本。连接和重连总发权威 snapshot，先注册订阅缓冲更新，再读快照、发送、排空缓冲。菜单结构变化用 menu revision 触发重取。
- **Rationale**：单向事件足够；至少一次事件用版本去重，旧 HTTP 数据不能覆盖新 SSE 数据。不依赖 auto-increment ID 游标扫描“之后事件”，避免小 ID 晚提交造成漏消息。初期单应用实例，数据库持久化恢复与重连快照覆盖进程退出；扩大实例数前须重设计广播分发。
- **Alternatives considered**：WebSocket 增加不需要的双向协议；仅内存 after-commit 广播存在崩溃窗口；Last-Event-ID 自身不保证重放。每 15 秒心跳、有限连接寿命、背压关闭后重连，并验证会话有效性，保持代理不缓冲。
- **Sources**：[WHATWG SSE](https://html.spec.whatwg.org/multipage/server-sent-events.html)、[Spring transaction events](https://docs.spring.io/spring-framework/reference/data-access/transaction/event.html)。

## R-09 真实 LLM 与能力边界

- **Decision**：按用户 2026-10-16 的实施指令改用 OpenRouter `POST /api/v1/chat/completions`；默认 `openai/gpt-4.1-mini`，通过服务端环境变量可替换。密钥仅存本地忽略的 `.env` 或部署秘密配置，不进入源码、前端、报告或日志。
- **Rationale**：采用 `response_format.type=json_schema`、`strict:true` 和 `provider.require_parameters:true`，分别验证非流式意图提取与 SSE 推荐输出；格式限制之外仍须服务器独立校验业务。公开模型目录已核对默认模型支持相关参数；实际权限与生成能力由真实调用验证。
- **Evidence**：记录配置/返回的模型 ID、provider、generation ID、可用的 usage/system_fingerprint、客户端版本、暖机/并发与逐次时延。托管服务不提供可固定的模型 digest 或上游硬件信息时，明确记为不可得，不能编造。
- **Alternatives considered**：原本的本地 Ollama 方案被用户指定的 OpenRouter 替代；规则只用于候选过滤，不能替代真实 LLM。服务故障不得阻断普通订餐。
- **Sources**：[OpenRouter API](https://openrouter.ai/docs/api_reference/overview)、[Structured outputs](https://openrouter.ai/docs/guides/features/structured-outputs)、[Streaming](https://openrouter.ai/docs/api_reference/streaming)、[Models API](https://openrouter.ai/api/v1/models)。

## R-10 推荐过滤与流式卡片

- **Decision**：先短结构化意图提取，服务器解释时间范围、按菜品基础及所有当前选项标签并集应用本人过敏原硬排除，同时应用售罄/明确排重限制，再从最多 30 个匹配候选中让模型流式输出最多 3 个 `{dishId,reason}`。默认近 7 天为软偏好，明确排重是硬过滤；歧义返回英文澄清提示。发卡前重新核验当前菜单、库存、偏好和历史。
- **Rationale**：结构化输出只限制格式，不能替代业务校验。增量 JSON 解析器在完整 item 校验通过后才发 card；名称、图片、价格由服务器提供，不接受模型创建商品信息。最终合法 JSON 且 provider finish_reason:stop 和 [DONE] 才发 done；断流或错误发 error，已出卡片时标记 partial。
- **Alternatives considered**：原始 token 直接展示会暴露未验证商品；只在提示词约束过敏原不能验收。浏览器用带 CSRF 的 POST fetch 读取 SSE，不能使用仅支持 GET 的 EventSource 来发送诉求。
- **Operational limits**：总超时 20 秒包含排队、两阶段调用及校验，首张有效卡片目标 5 秒；正常性能样本预登记、完整分母与预期无卡/故障分组按[流契约3.4](contracts/streaming.md#34-时限取消与容量)执行。默认并行 1、有界等待 8，满时 AI_BUSY。没有凭据/模型、超时或设备慢不能以假推荐交付；需在目标硬件验证，不达标则调整算力/配置。模型不获得邮箱、地址、密码、其他用户记录或执行工具。
- **Sources**：[Chat API](https://docs.ollama.com/api/chat)、[Streaming](https://docs.ollama.com/api/streaming)、[Stream errors](https://docs.ollama.com/api/errors)。

## R-11 图片与经营统计

- **Decision**：图片接受 JPEG/PNG/WebP，真实解码、最多 5,242,880 字节和合理像素边界；TwelveMonkeys 补齐 WebP 读取，统一生成 JPEG/PNG 展示资源，随机存储名、受控媒体目录，不按上传文件名执行或暴露路径。原始附件已在 Downloads 的候选人交付包找到，压缩包完整性通过且包含 20 张图；实施时导入九道菜所需映射并记录来源。上传与转换产物上限统一为 5 MiB。
- **Decision**：看板直接在一致性只读事务内按配送日统计真实订单；查询date定义D，省略时取Asia/Shanghai运营今天，所有概览/排行/餐次/库存预警取D，趋势D-6至D补零。收入与销量只计 Completed，订单总数和餐次量包含四状态；库存取D供应行当前状态，不回放历史时点。前端只呈现聚合结果和英文口径，提供 Refresh 与 generatedAt。
- **Rationale**：金额口径与订单快照一致；当前规模无需汇总数据库和定时最终一致报表。
- **Alternatives considered**：对象存储可在需要多实例时采用；当前单实例持久卷足够，须纳入备份。静态看板样例不能替代真实查询。
- **Sources**：[TwelveMonkeys ImageIO](https://github.com/haraldk/TwelveMonkeys)。

## R-12 验证与交付

- **Decision**：JUnit 与 Spring 测试、真实 MySQL Testcontainers、Vitest 5/Testing Library、Playwright、Gatling HTTP/SSE。使用注入的测试 Clock，而非生产调时入口。版本通过各工程锁定；Testcontainers 运行独立 MySQL 进程，不用 H2 代替。
- **Rationale**：并发、状态机、精确金额需要有针对性的验证；浏览器体验和200连接容量是不同测量。真实 AI smoke 独立执行，mock 仅用于故障测试；10 名使用者的任务完成率和推荐相关性保留人工验收。
- **Alternatives considered**：仅端到端测试难以稳定构造竞争；仅接口测试不能证明 DOM 显示耗时与多屏可用性。原始 AI 编程记录必须原生导出，文档摘要不能代替。
- **Sources**：[Vitest](https://vitest.dev/guide/)、[Testing Library](https://testing-library.com/docs/react-testing-library/intro/)、[Playwright projects](https://playwright.dev/docs/test-projects)、[Gatling SSE](https://docs.gatling.io/reference/script/sse/)、[OpenAPI 3.1](https://spec.openapis.org/oas/v3.1.0.html)。

## 研究关闭状态

技术选型、认证、并发、实时同步、AI 服务与验收路径均已确定，无未决设计澄清。模型下载/算力、图片素材、原始对话导出和运行环境是实施与交付依赖，已有处理路径；不代表本轮已经安装、运行或满足性能目标。项目宪章是占位模板，没有可套用的已批准条款；用户原始要求及补充范围作为设计硬约束继续执行。
