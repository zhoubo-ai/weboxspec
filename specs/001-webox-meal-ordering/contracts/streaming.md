# WeBox 流式接口契约

**日期**：2026-10-16  
**状态**：Phase 1 设计；接口与示例是实施目标，不代表服务已运行。  
**依据**：[规格](../spec.md) FR-037–044、[研究](../research.md) R-08–10。公共 HTTP 数据类型见 [OpenAPI](openapi.yaml)，本文补充连接、事件顺序和失败语义。

## 1. 共通协议

- 公开接口前缀为 `/api/v1`。采用同源 Cookie session；服务器不得接受客户端提供的 userId 作为身份。
- SSE 响应为 `Content-Type: text/event-stream; charset=utf-8`，禁止缓存与代理缓冲；部署代理须透传流并允许连接持续存活。每个事件使用 `event:`、单行 JSON `data:` 和空行分隔，UTF-8 编码。
- UUID 字段使用标准 UUID 字符串。配送日期为 `YYYY-MM-DD`，按 `Asia/Shanghai` 解释；事件时间为带 `Z` 的 UTC RFC 3339 时间。
- `version`、`menuRevision` 为非负 `integer/int64`，公开值限制在 `0..9007199254740991`，确保浏览器可精确比较；到达上限不得环绕或重置。库存 `remaining` 为非负整数，金额沿用公共 HTTP 契约的整数分。
- 每 15 秒发送 SSE 注释心跳，例如 `: keep-alive`，注释不是业务事件，也不能算有效推荐内容。
- 返回头发出前，身份失效返回 HTTP 401、权限/CSRF 失败返回 HTTP 403、无效请求返回 HTTP 400，沿用公共错误响应。流开始后无法改变 HTTP 状态；客户端必须读取终止业务事件，不能将 HTTP 200 当作业务完成。
- 连接使用有界缓冲与写入超时。慢客户端或资源超限时关闭连接，不能无限增长队列、阻塞订单事务或静默丢事件后继续声称连接正常。

## 2. 库存订阅

### 2.1 请求与生命周期

`GET /api/v1/menus/{date}/events`

- 使用浏览器同源 `EventSource`，携带现有 session Cookie；这是只读接口，不需要 CSRF token。
- `{date}` 为合法配送日期。某日没有菜单仍可订阅，初始快照返回空 `items`，后续管理员安排菜单后通知变更。
- 每个浏览器页面只为当前选中日期保持一条连接；切换日期、退出或离开菜单时主动关闭。建议配置每会话最多 3 条、每应用实例最多 600 条库存连接，连接最长 30 分钟后正常关闭并重连；容量可依据 200 活跃员工场景压测调整，不能无界。
- 建连时检查 session；每次心跳及事件发送前重新检查会话有效性。过期/撤销后发送 `session.expired` 并关闭，客户端调用 `EventSource.close()`、清除本人敏感页面状态并进入登录流程，不继续自动重连。
- 网络错误、有限寿命到期或慢客户端关闭后，客户端先刷新会话状态，仍有效时以有上限的退避重新连接。隐藏页恢复可立即重连取得快照；不得让失效 session 持续刷请求。
- 每次连接和重连均提供完整权威快照。此接口不承诺历史 replay；不依赖 `Last-Event-ID` 恢复，不要求业务事件带 `id:`。

### 2.2 事件

| 事件 | 数据 | 语义 |
| --- | --- | --- |
| `inventory.snapshot` | `{deliveryDate, menuRevision, items:[{dishId, remaining, version}], serverTime}` | 目标日期当前员工可见菜单的完整库存快照；`items:[]` 合法。 |
| `inventory.updated` | `{deliveryDate, menuRevision, dishId, remaining, version, occurredAt}` | 已提交的某条每日供应的绝对剩余量；不是加减量。 |
| `menu.changed` | `{deliveryDate, menuRevision}` | 菜单成员、上下架或影响展示/配置的资料变化，要求重新获取该日期菜单。 |
| `session.expired` | `{message}` | 英文说明会话已失效；终止连接，不携带本人数据。 |

`MenuDish` 展示资料不通过库存事件补齐；收到 `menu.changed` 或未知 dishId 时，客户端重新取得普通菜单响应。菜单分页不限制快照范围：快照包含该日期完整可见菜单的供应信息，供已缓存页面与之后的分页结果合并。

示例（生产 IDs 必须为真实记录）：

```text
event: inventory.snapshot
data: {"deliveryDate":"2026-10-17","menuRevision":12,"items":[{"dishId":"7c3d454a-62ad-4e1e-83b0-d15dcf0a1b99","remaining":4,"version":6}],"serverTime":"2026-10-16T08:00:00Z"}

event: inventory.updated
data: {"deliveryDate":"2026-10-17","menuRevision":12,"dishId":"7c3d454a-62ad-4e1e-83b0-d15dcf0a1b99","remaining":3,"version":7,"occurredAt":"2026-10-16T08:00:01Z"}

event: menu.changed
data: {"deliveryDate":"2026-10-17","menuRevision":13}

: keep-alive

event: session.expired
data: {"message":"Your session has expired. Please sign in again."}

```

### 2.3 快照、版本与顺序

服务器必须按下列顺序建立连接，以避免读取快照和开始监听之间漏掉更新：

1. 先为该日期注册订阅并缓冲后续已提交事件。
2. 在一个一致性只读事务中读取目标日期菜单 revision 与全部当前供应版本/剩余量。
3. 发出 `inventory.snapshot`。
4. 排空注册以来的缓冲事件，再进入正常实时发送；快照已覆盖的重复更新可保留，由版本规则幂等处理。

供应版本以 `(deliveryDate, dishId)` 为单调递增范围；同日移出菜单又重新加入不得复用旧版本。菜单 revision 以配送日期为范围单调递增，结构或可展示资料变更时提升；仅库存数量变化不要求提升菜单 revision。跨多个日期生效的菜品变更须通知每个受影响日期。

客户端将快照作为新连接的基准，按 dishId 对照移除快照中不再存在的缓存成员。随后仅应用比已知行版本大的 `inventory.updated`，忽略相同/较小版本；`menu.changed` 仅在 revision 更新时使菜单查询失效。比当前已知菜单 revision 更旧的库存事件不得把已下架成员重新加入页面；新的 revision 或未知成员先触发菜单重取。菜单 HTTP 响应也必须按版本合并，不能用晚到的旧 HTTP 响应覆盖更新的 SSE 数量。

客户端切换日期或发起新连接后，来自旧连接的事件必须丢弃。`serverTime` 是快照读取时间，`occurredAt` 来自业务事务内 outbox 的 created_at；它不是数据库精确提交时间，不用来排序或替代版本。提交到显示的性能验收另以提交完成观测及浏览器采样计时。

### 2.4 持久化投递与恢复

- 订单创建、取消或供应调整，在同一 MySQL 事务内更新库存版本并写入 outbox；回滚事务不得发出业务事件。
- 首期单应用实例每 250ms 扫描未发布 outbox 记录，向已注册订阅者广播后标记已发布。扫描依据未发布状态，不使用“主键大于上次扫描 ID”的方式跳过可能晚提交的小 ID 记录。
- 进程在广播后、标记前退出可能产生重复事件，因此按至少一次设计；客户端版本去重。至少一次不表示每个断线客户端能收到每个历史事件，重连快照才是恢复权威状态的保证。
- 没有订阅者时也可完成发布标记，未来连接通过快照获得最新状态。慢客户端连接关闭后同样通过重连恢复，不能让其拖延全局 outbox 扫描。
- 目前不承诺多应用实例广播。横向扩容前必须增加跨实例分发设计与相应验收，不能仅多启动一个实例并沿用单实例内存订阅表。

## 3. AI 推荐流

### 3.1 请求

`POST /api/v1/recommendations/stream`

- 浏览器使用同源 `fetch`、Cookie session 和当前 CSRF token 读取 SSE；不使用只支持 GET 的 `EventSource` 发送诉求。
- Header：`Accept: text/event-stream`、`Content-Type: application/json` 以及公共认证契约规定的 CSRF header。
- 请求体：

```json
{
  "deliveryDate": "2026-10-17",
  "meal": "Lunch",
  "prompt": "I would like a mild Japanese meal, with nothing I ate yesterday."
}
```

`deliveryDate` 为目标配送日，`meal` 为 `Lunch` / `Dinner`；`prompt` trim 后非空，最多 1000 个字符，不截断后悄悄继续。服务器按结算同源规则校验日期/餐次，不把目标餐次自动换成另一餐次后继续推荐。请求不得包含 userId、provider URL、模型名或系统提示词。

为当前请求生成 UUID `requestId`；浏览器发起新诉求时取消前一个请求并忽略旧 requestId 的后续事件。推荐无写订单、无库存预留、无幂等重放义务；重试是读取最新菜单的新推荐。

### 3.2 事件和终止状态

| 事件 | 数据 | 语义 |
| --- | --- | --- |
| `meta` | `{requestId, deliveryDate}` | 流已建立；不表示有有效推荐，不满足首内容时延目标。 |
| `card` | `{requestId, index, dish:MenuDish, reason}` | 一张已校验、发出前仍可售的卡片；index 从 0 连续递增。`MenuDish` 沿用 OpenAPI 公共 schema。 |
| `done` | `{requestId, count, outcome:"COMPLETE"\|"NO_MATCH"}` | 正常终止；count 等于已经发出的卡片数。 |
| `error` | `{requestId, code, message, retryable, partial}` | 失败终止；message 必须为英文，partial 表示此前是否已发出至少一张卡片。 |

- 顺序为一个 `meta`、零至三个 `card`、恰好一个 `done` 或 `error`，然后关闭。请求未通过 HTTP 校验时不会建立该事件序列。
- `COMPLETE` 的 count 为 1–3；`NO_MATCH` 的 count 为 0，并由界面显示英文说明，例如 “No dishes match your preferences and restrictions for this meal.”。不足三张是合法结果，不为凑数放宽限制。
- 空候选可由确定性业务筛选直接给出 `NO_MATCH`，此时不需要空耗模型调用。存在候选却收到无效/无意义空输出，不能把 provider 失败冒充 `NO_MATCH`。
- `reason` 为 1–240 个字符的英文纯文本，由真实模型生成并经服务端校验；前端按文本渲染，不将其解释为 HTML、Markdown 链接或执行指令。
- `dish` 的名称、图片、价格、配置和库存资料由服务器从真实菜单取得，不采用模型自行生成的商品字段。
- 已开始流后鉴权失效使用 `error` / `SESSION_EXPIRED`，不扩展此接口的事件类型。
- 无终止事件的 EOF/断网视为失败。已有卡片可保留并标注结果未完成，普通加购再次核验可用性；不能显示完整成功提示。

正常成功示例中的 `dish` 结构由公共 `MenuDish` schema 定义；以下示例只展示不依赖该结构的控制事件：

```text
event: meta
data: {"requestId":"48d690da-47b5-4c6b-b7ad-5d6cf6f1d610","deliveryDate":"2026-10-17"}

event: done
data: {"requestId":"48d690da-47b5-4c6b-b7ad-5d6cf6f1d610","count":0,"outcome":"NO_MATCH"}

```

中途已输出卡片、随后超时的终止事件：

```text
event: error
data: {"requestId":"e7085990-4fde-43b0-aa8b-a62239f91879","code":"AI_TIMEOUT","message":"Recommendations took too long. Please retry or return to the menu.","retryable":true,"partial":true}

```

| code | retryable | 用户恢复动作 |
| --- | --- | --- |
| `AI_BUSY` | true | 稍后重试或返回菜单。 |
| `AI_UNAVAILABLE` | true | 服务恢复后重试；不暴露内部地址和原始 provider 错误。 |
| `AI_TIMEOUT` | true | 重试或返回菜单。 |
| `AI_INVALID_RESPONSE` | true | 拒绝非法/截断/不满足校验的模型内容，可重试。 |
| `INTENT_CLARIFICATION_REQUIRED` | false | 英文说明含混条件并提示改写诉求，再发新请求。 |
| `SESSION_EXPIRED` | false | 重新登录，取得新 CSRF token 后重新请求。 |

队列已满可在开始响应前使用 HTTP 503 与 `AI_BUSY`；上游服务明确不可用也可使用 HTTP 503。其他已开始响应的失败均按 `error` 终止处理。

### 3.3 真实推荐管线

1. 读取当前用户已保存偏好、目标日期菜单及近 7 天非取消订单。近 7 天以运营时区的今天及前 6 个自然日配送日期计算。
2. 用短结构化意图提取识别英文诉求，包括明确排重与时间范围。服务器解析 `today`、`yesterday`、天数或明确日期；无期间的 “no repeats” 默认近 7 天。有歧义时返回澄清错误，不悄悄忽略硬限制。明确更长期间时查询该完整期间本人历史，不能仅按已装载的 7 天排重。
3. 服务器硬过滤未安排、未上架、售罄和命中本人过敏原的菜品；过敏原资料优先于用户提示词或模型意见。硬过滤取菜品基础标签与所有当前选项标签的并集；任一命中即排除整道菜，不能只检查用户可能选择的配置。普通加购则按实际配置并集提醒，空标签不构成安全承诺。明确不重复时按解析期间排除；默认最近 7 天仅降优先级。
4. 结合用户意图、菜系/辣度/口味/预算偏好及近期历史，从通过硬限制的候选中筛选最多 30 个，交给模型生成最多三项。没有候选时返回 `NO_MATCH`，不能扩大过敏原或售罄条件。
5. 增量解析模型输出，每个 item 完整后验证 schema、候选 UUID、重复 ID、英文理由和长度。发出卡片前再次读取最新菜单、库存、本人偏好及所需历史，重做硬过滤；已失效候选丢弃，不发送。
6. 无法通过校验的模型内容不得直接展示。可售条件改变导致所有候选被剔除时可返回 `NO_MATCH`；模型结构错误、虚构 ID、截断或 provider 错误必须返回失败，不能伪装无匹配。
7. 理由只能基于已提供的菜单及偏好事实。不能作无资料支持的营养含量、治疗效果、健康适宜性或“绝对不会过敏”等医疗/安全断言。预算说明使用真实基础价/最低必选配置价并说明最终配置会影响价格；不由模型编造营养数据或金额。
8. 仅在 provider 正常结束且完整最终 JSON 校验成功后发送 `done`。流式解析应处理转义引号、Unicode、跨块字符串和数组边界，不能用正则寻找大括号代替 JSON 解析。

### 3.4 时限、取消与容量

- 总 deadline 为 20 秒，从应用接收有效请求起算，覆盖排队、两阶段调用、数据库读取及逐卡校验；超时取消上游并发送 `AI_TIMEOUT`。不能在每个阶段重置成新的 20 秒。
- 正常性能组须在运行前冻结至少 20 次请求及预期结果：目标日期/餐次合法，稳定夹具中每次均有至少一个有效候选，预期为 `COMPLETE`；使用预热后的真实模型并预先固定客户端硬件、版本、模型/provider标识、并发数和请求顺序，不混入有意修改库存/偏好等竞争场景。可以复用功能用例中符合条件的输入，但须达到独立登记的性能样本量。
- 性能计时从浏览器发起请求开始，到首张带有效理由的真实卡片呈现、以及收到合法 `done(COMPLETE)` 并显示完成为止；包括网络、排队、两阶段调用、数据库校验与渲染。首卡 p95 ≤5 秒、完整完成 p95 ≤20 秒；`meta`、loading、心跳以及未验证 token 不计首卡。服务端 20 秒 deadline 仍从接收有效请求起算，后端耗时单列，不能替代浏览器耗时。
- 固定分母 N 包含该组全部请求尝试，不能按实际结果筛选成功样本；缺少有效首卡时首卡耗时记为正无穷，未得到合法 `COMPLETE` 时完成耗时记为正无穷。非预期 `NO_MATCH`、错误、超时、断流或取消保留在原组；已有合法卡片后失败保留首卡实测值，但完成计为正无穷。每项耗时升序排列后取第 `ceil(0.95 × N)` 个值作为 p95；报告有限耗时、失败原因和无穷项数量，不用 0 或超时上限替代缺失值。非预期结果同时使对应功能用例失败，即使 p95 达标也不能宣称全项通过；重试单独记录，不替换原样本或从原组删去失败。
- 运行前登记为预期 `NO_MATCH`、诉求澄清或故障注入的用例单独核验终止结果、恢复行为和实际终止耗时，不进入正常性能组，也不把无卡结果计为零秒首卡。确定性空候选允许不调用模型；有候选的正常性能请求必须真实推理，stub 仅用于故障注入。
- 默认单个 provider 并行数 1、有界等待 8；排队也消耗 deadline，满时 `AI_BUSY`。不得通过放宽时限、无限排队或假推荐宣称达标。
- 关闭页面、新诉求覆盖或用户取消时，浏览器中止 fetch；服务器取消两阶段上游请求、清理缓冲和占用槽位。
- 目标客户端硬件、版本、模型/provider标识、暖机状态、并发数和首卡/完成 p95 都必须记录并实测。无法达标时调整部署算力/容量；本契约不声称当前硬件已经满足性能目标。

## 4. 内部 OpenRouter provider 契约

本节是服务端适配器边界，不是公开 API。只有运维配置能决定服务地址和模型；浏览器不能直接访问 provider、改变 base URL 或指定任意模型。

| 项目 | 默认值/约束 |
| --- | --- |
| Transport | OpenRouter HTTPS `POST /api/v1/chat/completions`。 |
| Base URL | `https://openrouter.ai/api/v1`，只能由服务端运维配置。 |
| Authentication | 后端 `Authorization: Bearer`，密钥从 `OPENROUTER_API_KEY` 读取；不得向浏览器、日志或测试报告暴露。 |
| Model | 默认 `openai/gpt-4.1-mini`，使用 `OPENROUTER_MODEL` 显式配置；记录实际返回模型/provider/generation ID，不伪造托管模型 digest。 |
| 第一阶段 | `stream:false` + 意图 `response_format: {type:"json_schema", json_schema:{name,strict:true,schema}}`；只提取诉求。 |
| 第二阶段 | `stream:true` + 推荐 JSON Schema，最多三项 `{dishId,reason}`。 |
| Generation | 初始默认 `temperature:0`、`max_tokens:512`、`provider:{require_parameters:true}`；第一阶段可使用更短输出预算，调优保留业务与性能约束。 |
| 权限 | 模型无数据库/订单写入/执行工具，无任意网络工具。 |
| 数据最小化 | 仅传诉求、必要菜单事实、偏好及本人所需历史摘要；不传邮箱、配送地址、密码、会话/CSRF token 或他人记录。 |

意图输出 schema 至少表达 `repeatPolicy`、`repeatFrom`、`repeatThrough`、`repeatEvidence`、`needsClarification` 和可识别偏好；显式范围用含首尾日期表达，再由服务器规范化为查询边界。模型返回日期和证据也须校验，不可直接拼入查询。业务日期由服务器提供，不能信任模型自己的当前日期。

第二阶段要求下列逻辑输出结构，`dishId` 可用本次候选 ID 构造 schema enum；schema 不替代服务器候选及实时业务校验：

```json
{
  "recommendations": [
    {
      "dishId": "7c3d454a-62ad-4e1e-83b0-d15dcf0a1b99",
      "reason": "This mild Japanese dish matches your preferences and adds variety to your recent meals."
    }
  ]
}
```

`/chat/completions` 的外层使用 SSE。适配器正确处理 UTF-8 分片、注释、多行 `data:` 和事件边界，再从 `choices[].delta.content` 增量解析内层 JSON；不把半个 item 当作卡片。任何外层 `error`、非成功 HTTP 响应、拒绝、异常 EOF、输出预算耗尽/截断均算失败。HTTP 200 不代表模型成功。只有完整合法 JSON、`finish_reason:"stop"` 以及 `[DONE]` 都已收到，才可发送业务 `done`。最终 usage 帧可携带空 content 并重复 stop，应作为计量帧处理，不重复终止。

更换模型必须证明结构化约束、真实生成、流式校验、取消和总 deadline 语义一致。断开连接用于中止上游并释放本地资源，但不能保证所有托管 provider 立即停止计费。规则排序只用于过滤/准备候选，不能代替真实 AI 推荐。

## 5. 后续验收场景

| 场景 | 必须观察到的结果 |
| --- | --- |
| 注册订阅与快照读取之间恰有订单提交 | 快照或缓冲事件覆盖该变更；最终剩余量与数据库一致。 |
| 重复、乱序投递与晚到 HTTP 响应 | 数量不回退到旧版本；已删除成员不会因旧事件重新出现。 |
| 广播前后进程退出、断网后重连 | 未发布事件可恢复；新连接快照恢复最新状态，不要求历史 replay。 |
| 200 活跃员工订阅、20 人竞争 10 份 | 无超卖；95% 已连接菜单在提交后 2 秒内更新；慢连接不阻塞其他订阅者。 |
| 会话退出/撤销、日期切换 | 旧连接关闭或失效；旧日期事件不污染新日期；他人身份不能注入请求。 |
| 过敏原、售罄、下架、未安排与明确排重 | 所有已发卡片均来自真实且发出时通过硬限制的候选；空候选不会放宽限制。 |
| 同时更新偏好、取消/创建历史订单或耗尽库存 | 发卡前复查采用最新权威数据；加购仍走普通配置/数量/过敏原及可用性验证。 |
| 分块 JSON、转义内容、非法 UUID/虚构 ID、HTML 理由 | 不显示未完成或非法 item；失败为 error，非固定替代推荐。 |
| provider 中途 error、EOF、超时、浏览器取消 | HTTP 200 不被误判成功；正确 partial 标识、取消与资源释放；核心下单仍可使用。 |
| 20 组真实模型诉求及使用者相关性验收 | 真实模型、英文理由、100% 无过敏原冲突且真实可售；记录 5 秒/20 秒 p95 和用户评价。 |

## 6. 核验来源

- [WHATWG Server-sent events](https://html.spec.whatwg.org/multipage/server-sent-events.html)：EventSource、事件格式和重连机制。
- [OpenRouter API](https://openrouter.ai/docs/api_reference/overview)：请求、认证和响应。
- [OpenRouter Streaming](https://openrouter.ai/docs/api_reference/streaming)：SSE、usage、流内错误和取消边界。
- [OpenRouter Structured Outputs](https://openrouter.ai/docs/guides/features/structured-outputs)：JSON Schema 与 provider 参数支持。
- [OpenRouter Models API](https://openrouter.ai/api/v1/models)：可用模型与参数目录；实际生成仍须真实验证。
