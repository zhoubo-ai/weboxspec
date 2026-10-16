# WeBox 接口与交互契约

**日期**：2026-10-16。全部 Tier 1–3 必须交付；本目录定义实施边界，不表示服务已经运行。

- [openapi.yaml](openapi.yaml)：OpenAPI 3.1.0，32 个 HTTP 操作、请求/响应字段、权限和校验。
- [streaming.md](streaming.md)：库存 SSE、AI POST SSE、事件顺序、重连与模型错误。
- [data-model.md](../data-model.md)：约束、版本、状态机、事务锁序与统计口径。
- [quickstart.md](../quickstart.md)：后续实现完成后的启动与验收入口。

## 1. 公共约定

API 基址 `/api/v1`，与 SPA 同源。唯一例外是公开菜品图片 `/media/{mediaId}`，该操作在 OpenAPI 内显式覆盖 server 为 `/`。请求和响应使用 UTF-8 JSON，上传为 multipart，流为 text/event-stream；系统提供的展示文案和错误 message 是英文；用户地址等自由文本原样安全保留，不强制翻译。

业务 ID 是 UUID 字符串。日期是 `YYYY-MM-DD`，时区为 Asia/Shanghai；时间戳为 UTC、带 `Z`。金额为非负整数分，所有计算和传输不得超过 9,007,199,254,740,991；单位字段名以 Cents 结尾。版本也是 JSON 安全整数，不绕回、不重置。供应数量受存储整数上限约束。

全部必填文本 trim 后非空，密码不 trim；长度按 Unicode 码点统一校验。未声明请求字段拒绝，不能通过添加 role、userId、status、price 等字段改变业务权限或成交内容。文本作为文本而非 HTML/SQL/指令处理，参数化查询；LIKE 关键词中的通配符按普通字符处理。公共字符串枚举见 OpenAPI，不在代码中另建中文枚举。

列表采用 page 从 0 开始、size 默认 24/最多 100；菜单按英文名称、ID 稳定排序，订单按 createdAt 降序、ID 稳定排序。类别数组使用重复参数，例如 `?categories=Chinese&categories=Japanese`。空列表返回 200 与空数组。金额和历史展示字段不得从前端输入直接信任。

## 2. 身份与安全

1. SPA 先 `GET /auth/csrf` 获取 `{token,headerName}`，保存浏览器接受的匿名 session Cookie；headerName 固定 `X-XSRF-TOKEN`。
2. 注册和登录同样带 CSRF header；注册只创建 Employee，不自动登录。登录成功设置/轮换 HttpOnly `WEBOXSESSION`。
3. 除 health、csrf、注册/登录和公开媒体外，接口要求登录；所有 `/admin/**` 另要求 Admin。用户身份从服务端会话取得，个人查询按 owner 限制。
4. 登录/退出后重新读取 CSRF。写请求缺失/错误 token 返回 403；不能用 SameSite 代替 CSRF。退出使服务器会话失效，关闭流、清除个人请求缓存与页面购物车状态，保留按账号隔离的 localStorage 草稿；验证登录身份后才恢复本人草稿并重新校验，不存凭据或地址。
5. 生产 Cookie 使用 Secure、HttpOnly、SameSite=Lax。仅本地 localhost HTTP 配置允许非 Secure，开发通过代理维持同源。会话默认空闲有效期 8 小时，可配置；SSE 心跳本身不延长空闲期，显式用户操作才续期。
6. health 仅公开 UP 或失败；媒体仅公开已验证的菜品图片。身份、地址、偏好、订单、报价与流使用 no-store。无权限的个人实体读取返回 404，避免暴露其存在；Console 角色不足返回 403。

## 3. 接口分组

| 用途 | 入口 | 关键行为 |
| --- | --- | --- |
| 账号 | `/auth/csrf`、`/auth/register`、`/auth/login`、`/auth/logout`、`/auth/me` | 唯一邮箱、角色、session、CSRF |
| 个人设置 | `/me/preferences`、`/me/addresses` | 偏好全量替换带 expectedVersion；地址在订单成功时保存 |
| 员工菜单 | `/menus/{date}`、`/menus/{date}/dishes/{dishId}` | 仅已上架且安排菜品，售罄仍展示；搜索与偏好排序组合 |
| 实时供应 | `/menus/{date}/inventory`、`/menus/{date}/events` | 当前快照与流式绝对量，按版本合并 |
| 结算 | `/checkout/quotes`、`/orders` | 权威报价、不预留库存、一次消费、订单幂等 |
| 个人订单 | `/orders`、`/orders/{orderId}`、`/orders/{orderId}/cancel` | 本人历史与快照、仅 Pending 可取消 |
| AI | `/recommendations/stream` | 真实 LLM、硬过滤、逐卡验证、部分失败明确 |
| 菜品后台 | `/admin/dishes`、`/admin/dishes/{dishId}`、`/admin/dishes/{dishId}/publication` | 版本检查、配置维护、上/下架 |
| 菜单后台 | `/admin/menus/{date}` | 日期默认由 UI 选次日；允许当日；总量不低于已占用 |
| 订单后台 | `/admin/orders`、`/admin/orders/{orderId}`、`/admin/orders/{orderId}/status` | Pending→Confirmed→Completed 或 Pending→Cancelled；重复取消返回当前状态 |
| 看板 | `/admin/dashboard` | 配送日口径、一致性快照、七日趋势与真实库存 |
| 图片 | `/admin/media`、`/media/{mediaId}` | 管理员上传，安全转换后以不可变URL展示 |

配置、分页等具体字段以 OpenAPI 为准。新建菜品默认 unpublished，显式发布并安排日期后进入员工菜单。配置组至少一个选项；Single 最多一项、Multiple 每项最多一次，required 至少选择一项。保存现有 groupId/optionId 时必须属于该菜品；省略 ID 由服务端生成，旧 ID 的历史快照保留。

## 4. 结算协议与恢复顺序

购物车没有服务器增减接口。每项提交 `dishId, dishVersion, optionIds, quantity`，总份数 1–5；相同菜品和排序后的相同选项集合归并。客户端金额只用于展示。

草稿使用 `webox.cart.v1:<userId>` 的 localStorage，退出保留，恢复时重取当前菜单，变化须确认、不可用项须调整；不会自动提交。菜品 allergens 仅代表基础标签，所有选项读写和快照携带 allergens。报价的 FLAGGED_ALLERGEN 按每条实际配置计算，以 itemIndex 指向最终 Quote.items 的零基序号；AI 使用基础及全部当前选项标签的并集保守排除。

1. 调用 `POST /checkout/quotes`，服务器检查目标日期、餐次、当前菜单与版本、配置、份数、库存和本人有效订单。成功返回 201 Quote，五分钟有效、不预留库存。总价超预算仅产生 warning。
2. SLOT_EXPIRED 返回建议日期/餐次，UI 自动调整选择并提示，再请求报价；不能静默替员工创建另一餐次订单。过去日期返回 INVALID_DELIVERY_DATE，不重写成今日。
3. DISH_CHANGED/DISH_UNAVAILABLE 返回 itemIssues；保留购物车并展示变化，用户调整/确认后才用新的版本请求新报价。ACTIVE_ORDER_EXISTS 将按钮变为 `View Existing Order` 并打开 existingOrderId。
4. 员工确认新报价和地址后冻结这次提交快照，生成 UUID Idempotency-Key。请求仅含 quoteId 与 addressId/addressText 二选一；地址 text trim 后保存，历史 ID 必须属于本人。
5. `POST /orders` 首次成功返回 201；同 key、同 canonical payload 重放返回 200 原订单和其当前状态。重放判断先于 quote 是否还存在、到期、被消费、截止时间、当前价格、当前地址或库存核验。
6. 请求 hash 基于协议版本、规范化 quoteId、地址选择方式与规范化 ID/trim 文本；不直接哈希原始 JSON，也不把可变地址内容重新读入旧 hash。相同 key 改任何有效请求内容都返回 IDEMPOTENCY_KEY_REUSED。
7. 一个 quote 最多创建一单。同 key 优先重放；不同 key 使用已消费 quote 返回 QUOTE_CONSUMED。新订单意图必须使用新 quote 和新 key；取消/完成不使旧 quote 可复用。
8. 写事务锁后再核验时间及当前数据，原子更新订单、快照、历史地址、供应、报价消费和 outbox。失败无部分成功。瞬间截单返回 SLOT_EXPIRED，显示新安排并让用户确认后重新报价。
9. 网络结果未知时保留 quote/key/请求，重试解析结果；不能因超时生成新 key。成功后仅移除这次实际提交的购物车数量；期间新增内容保留。明确拒绝且用户修正内容后，开启新报价/新 key。

最小订单请求示例（示例 UUID 必须替换为实际报价）：

```http
POST /api/v1/orders
Content-Type: application/json
Idempotency-Key: 3e6a517e-f9b1-42c2-bc16-3c3df1995506
X-XSRF-TOKEN: <token from the current session>

{"quoteId":"c3c7bac8-15da-4a2c-8b6f-2105bb594a30","addressText":"Building A, Floor 3"}
```

## 5. 错误与交互恢复

错误共通为 `{code,message,requestId,retryable,...context}`。fieldErrors 解释输入错误，itemIssues 逐菜品解释不足/变化；只有当前用户可访问的已有订单才能返回 existingOrderId。以下是必需业务码，不能仅返回笼统 HTTP 500。

| HTTP / code | 触发与恢复 |
| --- | --- |
| 400 VALIDATION_ERROR | 字段、长度、枚举、数量格式或必选配置无效；保持已填内容并就地提示 |
| 401 INVALID_CREDENTIALS / UNAUTHENTICATED | 登录错误或会话失效；前者明确英文反馈，后者进入登录 |
| 403 FORBIDDEN / CSRF_INVALID | 权限不足或 token 不匹配；不可通过改客户端身份恢复权限 |
| 404 NOT_FOUND | 不存在或无权查看的个人资源，不泄露 owner |
| 409 EMAIL_ALREADY_REGISTERED | 规范化邮箱已存在；改邮箱或登录 |
| 422 INVALID_DELIVERY_DATE / ORDER_LIMIT_EXCEEDED | 过去日期或总份数超限；调整选择 |
| 409 SLOT_EXPIRED | 附 suggestedDeliveryDate/suggestedMeal；明确切换并重新确认 |
| 409 ACTIVE_ORDER_EXISTS | 附 existingOrderId；打开已有有效订单，不跳到更晚餐次绕过 |
| 409 DISH_CHANGED / DISH_UNAVAILABLE | 附 itemIssues；展示变化并重新确认，不静默改价/替换 |
| 409 INSUFFICIENT_STOCK | 附具体 dishId 与 availableQuantity；整单失败、保留购物车 |
| 409 QUOTE_EXPIRED / QUOTE_CONSUMED | 重新报价；已成功的同 key 必须仍重放，不返回该错误 |
| 409 IDEMPOTENCY_KEY_REUSED | 同成功 key 不同内容；不自动覆盖或返回另一笔新单 |
| 409 VERSION_CONFLICT | 运营/偏好资料被并发修改；重读最新内容再确认保存 |
| 409 ORDER_NOT_CANCELLABLE / INVALID_ORDER_TRANSITION | 状态不允许，展示最新状态；不回退、不恢复不该恢复的库存 |
| 409 SUPPLY_BELOW_ALLOCATED | 新总量低于现有占用；保留当前总量并提示限制 |
| 413 IMAGE_TOO_LARGE / 415 IMAGE_TYPE_UNSUPPORTED / 422 IMAGE_INVALID | 文件大小、类型或真实解码失败；允许重新选图 |
| 429 RATE_LIMITED / 503 TEMPORARY_UNAVAILABLE | 有界限流或锁等待/依赖暂不可用；遵循 Retry-After，重试写订单复用原 key |
| 503 AI_BUSY / AI_UNAVAILABLE | 流建立前模型队列满或不可用；普通菜单继续可用 |

流建立后的 AI 错误以 [streaming.md](streaming.md) 的 error 事件结束，不将 HTTP 200 误认为完成；INTENT_CLARIFICATION_REQUIRED 是产品内对用户诉求的反馈，不是本计划仍有未决需求。

## 6. 前端页面契约

| 路由 | 布局/访问 | 必需状态和操作 |
| --- | --- | --- |
| `/register`、`/login` | 访客 | 英文校验、错误、成功引导，无角色选择 |
| `/menu` | 员工布局/登录 | 日期菜单、搜索、多分类、推荐开关、配置对话框、库存/重连、AI入口 |
| `/cart`、`/checkout` | 员工布局/登录 | 配置、总份数、金额、预算提醒、日期餐次地址、报价变化、现有订单入口 |
| `/orders`、`/orders/:orderId` | 员工布局/登录 | 分页历史、成功订单配送信息、不可变明细、状态与合法取消 |
| `/settings` | 员工布局/登录 | 偏好读取/清除/保存、并发冲突、地址选用关联 |
| `/console/dishes`、`/console/menus` | Console/Admin | 表格、编辑、图片、发布、指定日期供应和冲突反馈 |
| `/console/orders` | Console/Admin | 筛选、订单详情、确认、完成、Pending 取消 |
| `/console/dashboard` | Console/Admin | 真实概览、Top10、餐次、七日、预警、Refresh、generatedAt 与英文口径 |

注册/登录后的默认入口：Employee 到 `/menu`，Admin 到 `/console/dishes`。管理员可保留员工功能入口，但 Console 操作只能由服务端验证 Admin。过敏原警告与预算警告都不硬过滤普通菜单；AI 则必须过滤已标记过敏原。关键操作需要键盘可用、焦点明确和可读错误，375/768/1440 宽度无页面级横向滚动。

菜单与购物车的加号在总份数达到5或菜品售罄时不可增加，并解释原因；同一菜品不同配置消耗同一剩余量。AI 结果走同一配置和加购入口。新增/编辑/上传失败保留可安全保留的输入；图片加载失败显示替代说明。所有图表附可核对的表格或文字，不仅依靠颜色。

## 7. 修订后的业务边界

- 管理员沿用 `PATCH /admin/orders/{orderId}/status`，body 为 `{status,expectedVersion}`，status 允许 Confirmed、Completed、Cancelled。身份/CSRF 与锁定后，当前/目标均为 Cancelled 则先于版本比较返回 200 当前 AdminOrder；不写版本、时间或事件。其余请求先验版本，合法转换仅 Pending→Confirmed、Confirmed→Completed、Pending→Cancelled；版本过时为 VERSION_CONFLICT，非法转换为 INVALID_ORDER_TRANSITION。员工取消接口及本人归属限制不变。
- 看板可选查询参数 `date` 定义 D，省略时取 Asia/Shanghai 运营今天；概览、Top 10、餐次和库存预警统一取 D，趋势为 D-6 至 D 并补零，响应 `date` 回显 D，`generatedAt` 为本次生成的 UTC 时间。仅 Completed 订单贡献收入和销量，收入含加料且显示 Completed Order Amount；按配送日统计，Top 10 按份数降序、dishId 升序。所有状态计订单总数/餐次，Pending+Confirmed 计待处理；预警读取 D 的已安排上架供应行当前剩余量，不回放历史库存。
- 图片上传与转换产物均最多 5 MiB（5,242,880 字节），大小合规仍须真实解码及像素校验；失败不替换旧图。Media.byteLength 是实际转换产物大小。
- 用户自由地址允许中文等 Unicode，保留去首尾空白后的原值；所有系统错误和菜单资料仍为英文。
- 菜单资料缓存只用于版本匹配的读取加速；当前可售范围、库存、报价和下单以数据库为准，见数据模型缓存章节。规格来源与执行状态见 [修订决策](../decisions.md) 和 [验收台账](../acceptance.md)。
