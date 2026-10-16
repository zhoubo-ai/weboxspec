# WeBox 数据模型与事务设计

**日期**：2026-10-16  
**依据**：[spec.md](spec.md)、[research.md](research.md)、[delivery-constraints.md](delivery-constraints.md)。全部 Tier 1–3 均在本模型范围内。本文定义实现约束，不表示迁移、代码或测试已经完成。

## 1. 通用约定

| 项目 | 约定 |
| --- | --- |
| 数据库 | 独立 MySQL 8.4 LTS，业务表使用 InnoDB，字符集 utf8mb4；迁移由 Flyway 管理 |
| 标识 | 业务实体 ID 为服务端生成的 UUID 字符串；持久化 `CHAR(36)`、ASCII 二进制比较；API 使用字符串，不暴露自增序号 |
| 金额 | 人民币整数分；Java `long` / SQL `BIGINT` / JSON 安全整数；合法范围为 0–9,007,199,254,740,991，计算必须检查乘加溢出与 JSON 上界；展示固定两位小数 |
| 配送日 | Java `LocalDate` / SQL `DATE`，接口 `YYYY-MM-DD`；它不是 UTC 时间戳 |
| 事件时间 | Java `Instant`，持久化 `DATETIME(6)` 且 JDBC、数据库会话统一 UTC；接口为带 `Z` 的 ISO 8601 |
| 运营时区 | `Asia/Shanghai`；服务端注入 `Clock`，以运营时间计算今天、截止时间、最近七个自然日 |
| 版本 | 可变业务记录使用非负整数 `version`；每次成功修改递增，API 保持 JSON 安全整数范围；日期菜单使用同义的 `revision` |
| 文本 | 必填文本 trim 后非空；密码不 trim、不截断；系统英文业务内容在服务端校验/初始化和界面验收中共同保证；用户自由地址保留原文，不翻译 |
| 字符长度 | 校验统一按 Unicode 码点计数；不得一端按 UTF-16 单元、一端按字符而形成不同边界 |
| 删除 | 不实现业务实体任意硬删除；菜品下架、供应取消安排使用状态；订单与快照不级联删除 |
| 空值 | 未设置的可选标量使用 NULL；未标记过敏原、无偏好菜系、无配置使用空集合，不创建 `None` 伪记录 |
| 权威来源 | MySQL 是权威来源；单实例使用版本化 Caffeine 菜单资料缓存，库存、报价、订单、权限、日期可售范围和经营汇总不缓存 |

`created_at`、`updated_at` 适用于可变根实体。未另行说明时，外键使用 `RESTRICT`；外键关系不能代替当前用户的所有权检查。API 字段采用 camelCase，本文列名采用 snake_case。

### 业务枚举

| 字段 | 允许值 |
| --- | --- |
| 用户角色 | `Employee`、`Admin`；公开注册固定为 Employee |
| Category | `Chinese`、`Western`、`Japanese`、`Light Meal`、`Korean`、`Southeast Asian` |
| Spice | `None`、`Mild`、`Medium`、`Hot` |
| Allergen | `Peanuts`、`Dairy`、`Egg`、`Gluten`、`Soy`、`Fish`、`Shellfish` |
| Flavor | `Light`、`Moderate`、`Strong` |
| Meal | `Lunch`、`Dinner` |
| Order status | `Pending`、`Confirmed`、`Completed`、`Cancelled` |
| 配置选择方式 | `Single`、`Multiple`；单选组最多一项，多选组每选项每份最多一次 |

存储采用受 CHECK 限制的字符串，而非允许任意文本的字段；前后端从同一契约生成/维护允许值。SQL CHECK 验证单行不变量，跨行约束由事务服务与有针对性的集成测试保证。

## 2. 账号、偏好与地址

### users

| 字段 | 类型与规则 |
| --- | --- |
| id | UUID 主键 |
| email | VARCHAR(200)，trim 后的展示邮箱 |
| normalized_email | VARCHAR(200)，trim + `Locale.ROOT` 小写；使用二进制排序规则精确唯一，避免重音折叠 |
| password_hash | VARCHAR(255)，包含算法标识、参数及盐的 Argon2id 编码；不保存原始密码 |
| role | Employee / Admin；管理员由受控初始化过程创建 |
| version | 非负整数 |
| created_at, updated_at | UTC 时间 |

唯一索引：`normalized_email`。注册与登录密码为 8–128 个字符且包含字母与数字；验证原输入，不采用 bcrypt 的 72 字节截断。密码哈希、CSRF token、会话内容不得进入业务日志、AI 提示或 API 用户响应。

用户行也是同一员工订单写操作的串行锁：创建订单、取消和管理员确认/完成/取消均先锁订单所属用户，而不是锁操作者管理员。管理员操作前可只读定位订单的不可变 owner，再按规定锁序重新读取与校验。

### dietary_preferences / preference_cuisines / preference_allergens

| 实体 | 字段与约束 |
| --- | --- |
| dietary_preferences | `user_id` 同时为主键及 users 外键；`spice`、`flavor` 可为 NULL；`budget_min_cents`、`budget_max_cents` 可为 NULL；`version`；UTC `updated_at` |
| preference_cuisines | 复合主键 `(user_id, category)`；user_id 关联偏好根记录；category 受枚举约束 |
| preference_allergens | 复合主键 `(user_id, allergen)`；user_id 关联偏好根记录；allergen 受枚举约束 |

预算上下限必须同时为 NULL 或同时为非负整数，设置时下限不大于上限。不存在偏好记录等价于全部未设置；第一次保存创建根记录。清除偏好保留根记录并清空集合/可选字段、递增 version，避免丢失并发版本。

更新按 user → preference 根记录加锁，以客户端 expected version 防止覆盖其他页面刚保存的内容；子集合替换与版本递增在同一事务中完成。过敏原是普通加购提醒的依据，也是 AI 候选的硬过滤条件，两种使用方式不能混淆。

### delivery_addresses

| 字段 | 类型与规则 |
| --- | --- |
| id | UUID 主键 |
| user_id | users 外键；每次按当前用户过滤读取 |
| address_text | VARCHAR(200)，trim 后非空，不擅自合并内部空格或改变大小写 |
| created_at, last_used_at | UTC 时间 |

索引：`(user_id,last_used_at,id)`；唯一约束 `(user_id,address_text)`，地址文本采用精确比较，仅复用完全相同的 trim 后地址。输入新地址只在下单成功事务中持久化；订单失败不能留下被宣称成功使用的地址。订单保存独立 `delivery_address_snapshot`，后续地址变化不影响历史。

## 3. 菜品、配置与媒体

### dishes / dish_allergens

| 字段 | 类型与规则 |
| --- | --- |
| dishes.id | UUID 主键 |
| name | VARCHAR(100)，非空英文名称 |
| description | VARCHAR(2000)，非空英文描述 |
| base_price_cents | BIGINT，非负且不超过安全整数上界 |
| category | Category 枚举 |
| protein_source | VARCHAR(200)，非空英文内容 |
| spice | Spice 枚举 |
| media_id | media_assets 外键；必须指向已验证、可展示的图片 |
| is_listed | BOOLEAN；只有上架且按目标日期安排的菜品进入员工菜单 |
| version | 整数；资料、图片、过敏原、配置或价格变化均递增 |
| created_at, updated_at | UTC 时间 |
| dish_allergens | `(dish_id,allergen)` 复合主键，dish_id 外键；空集合表示无已声明过敏原 |

索引：`(is_listed,category,name,id)`、`(name,id)`；名称不是唯一身份。关键词按名称/描述进行不区分大小写的包含查询，转义 LIKE 的 `%`、`_`、反斜线等字符，输入是文字，不是查询语法；500 菜品规模先测量现有索引和分页，无需引入搜索服务。

菜品基础标签存于 dish_allergens；选项标签存于 option_allergens，不能将所有加料混写为菜品基础标签。下架不删除菜品、历史条目或供应；再次上架也不会改变已成交快照。管理员编辑按 dish 根行锁串行化，并校验 expected version，子表修改与 dish version 递增原子完成。

### customization_groups / customization_options

| 实体 | 字段与约束 |
| --- | --- |
| customization_groups | `id` UUID；`dish_id` FK；`name` VARCHAR(100) 非空；`selection_mode` Single/Multiple；`required` BOOLEAN；`display_order` 非负整数 |
| customization_options | `id` UUID；`group_id` FK；`name` VARCHAR(100) 非空；`extra_price_cents` 非负 BIGINT；`display_order` 非负整数 |
| option_allergens | `(option_id,allergen)` 复合主键；option_id 外键，allergen 使用统一枚举；空集合表示未标记 |

索引：groups `(dish_id,display_order,id)`，options `(group_id,display_order,id)`。配置组和选项由所属 dish version 覆盖，不单独建立与菜品不一致的报价版本。

普通配置有效过敏原 = dish_allergens ∪ 已选 option_allergens；报价逐条计算告警，不混入同菜其他配置；FLAGGED_ALLERGEN 的 itemIndex 指向归并后 Quote.items 的零基序号，allergens 为该条目与个人标记的非空交集。加购和编辑配置出现新的个人匹配时重新提醒，拒绝则保留原条目。AI 过滤使用基础标签与所有当前选项标签的并集。选项标签变动递增 dish version、使旧报价需重新确认；不根据菜名猜测标签，空标签不代表安全承诺。

跨行校验：有配置组时每组至少一个选项；required 组必须选择；Single 组只能选一个；Multiple 组不得重复 option；选项必须属于所提交菜品的有效组。当前配置可在锁 dish 后整体维护；报价与订单保留 option/group ID 及名称/价格/选项过敏原快照，不对这些当前可变子记录建立会破坏历史的级联依赖。

### media_assets

| 字段 | 类型与规则 |
| --- | --- |
| id | UUID 主键 |
| uploaded_by | users 外键，创建者必须为管理员 |
| original_filename | VARCHAR(255)，仅审计文本，不参与路径拼接，不作为下载响应的可信文件名 |
| source_mime, source_bytes | 真实检测的 JPEG/PNG/WebP 类型；输入大小 1–5,242,880 字节 |
| content_sha256 | 固定长度摘要，便于内容核对；不作为业务权限标识 |
| width, height | 正整数；解码前/过程中执行像素和内存上限，实施默认每边不超过 8192、总像素不超过 16,777,216 |
| storage_key | 服务端随机生成的相对资源键，唯一；解析后的路径必须位于受控持久媒体目录内 |
| served_mime, served_bytes | 统一转换后的 image/jpeg 或 image/png 及字节数；转换产物同样不得超过 5,242,880 字节；若无法在上限内生成则拒绝且保留旧图 |
| source_attribution | 可选来源及授权说明，用于替代素材的交付记录 |
| created_at | UTC 时间 |

只有完整解码和转换成功后才写入可引用记录；不保存用户声称的 MIME 作为验证结论，不将原始 SVG/HTML 或可执行内容作为图片发布。文件写入使用临时文件和原子重命名，失败清理临时文件；数据库提交失败导致的无引用资源由受控清理处理。被菜品引用的资源不能删除，媒体目录与数据库一起备份。

## 4. 每日菜单和共享供应

### daily_menus

| 字段 | 类型与规则 |
| --- | --- |
| delivery_date | DATE 主键；这是日期自然键，非另一个业务 UUID |
| revision | 非负整数；供应安排、上下架关联展示变化等菜单结构更新递增 |
| updated_by | users 外键，最后一次运营修改者 |
| updated_at | UTC 时间 |

日期主键是每日聚合的自然身份。未创建的日期按空菜单响应；新增日期由管理员配置或受控演示初始化创建，不因员工切换日期而自动建立供应。单纯购买/取消产生供应 version 更新，不必导致整日菜单结构 revision 变化。

### daily_supply

| 字段 | 类型与规则 |
| --- | --- |
| delivery_date, dish_id | 复合主键；分别关联 daily_menus 和 dishes |
| scheduled | BOOLEAN；false 表示不在该日安排，但保留历史供应记录 |
| total_quantity | INT，0–2,147,483,647 |
| allocated_quantity | INT，0–total_quantity；所有未取消订单已占用份数，包含 Completed |
| version | 非负整数；供应设置、安排状态、购买扣减和取消恢复均递增 |
| updated_at | UTC 时间 |

剩余量为 `total_quantity - allocated_quantity`，不另存一个易漂移的 remaining 列。数据库 CHECK 保证非负、allocated 不超过 total；索引 `(dish_id,delivery_date)` 支持菜品变更定位日期。

同一菜品的不同配置与午晚餐共享当日这一行。购物车和报价都不增加 allocated。取消安排可设置 scheduled=false，但不抹掉占用量；不物理删除有历史或占用的记录，旧订单取消仍能找到原供应并恢复数量。管理员降低 total 时不能低于当前 allocated。

每次有效库存变化在同一事务产生 outbox 事件，事件携带绝对 remaining、supply version 和日期/菜品 ID；不能只发 `-1` 或 `+1` 增量。客户端只能接受更高版本，避免重复投递和旧 HTTP 结果覆盖新 SSE 数据。

## 5. 购物车与短期结算报价

### 浏览器购物车（不建服务器表）

购物车保存在 `webox.cart.v1:<userId>` 的 localStorage 草稿中；先验证当前登录身份再加载对应键。刷新、关闭后重开、退出后同账号重登均恢复；退出清除页面状态、请求缓存和流，但保留草稿。只保存 dishId、排序去重的 selectedOptionIds、quantity、非敏感展示快照和已见 dishVersion，不保存凭据、CSRF、地址、quote 或提交请求。恢复后重新取目标菜单，核验价格、配置与可售性；变化要求确认，不可用条目标记并阻止提交，不自动下单。坏数据或未知草稿版本明确提示并重建，不信任浏览器金额或数量。条目标识为菜品 ID 加规范化选项集合。

本地 reducer 保证每个被接受的加购动作恰好增加一份、总份数不超过 5；浏览器价格和库存只用于展示，不能作为成交依据。切换用户不得显示上一用户购物车。成功提交只移除本次提交的数量和配置；请求期间另行添加的条目不得被整个清空。未知提交结果须以原 quote 和原 key 重试/解析结果，不能直接换 key 再下单。

### checkout_quotes / checkout_quote_items

| 实体/字段 | 类型与规则 |
| --- | --- |
| checkout_quotes.id | UUID 主键，服务端生成 |
| user_id | users 外键；报价不可跨用户使用 |
| delivery_date, meal | 员工已确认的日期和餐次 |
| total_quantity | 整数 1–5 |
| total_cents | 由服务器计算的非负安全整数分 |
| created_at, expires_at | UTC；`expires_at = created_at + 5 分钟`，到期时刻本身即失效 |
| consumed_order_id | 可空订单 FK、唯一；只在成功创建订单的事务内设置，禁止改绑 |
| checkout_quote_items.id | UUID 主键 |
| quote_id | checkout_quotes FK |
| dish_id, dish_version | 报价时的真实菜品身份及版本 |
| configuration_key | 规范化的 option ID 集合标识；与 dish_id 一起确定条目身份 |
| dish_name_snapshot | VARCHAR(100) |
| options_snapshot | JSON 数组：每项含 groupId、groupName、optionId、optionName、extraCents、allergens；按 ID 稳定排序 |
| effective_allergens_snapshot | JSON 数组；菜品基础与已选选项标签并集，API 为 effectiveAllergens；报价与订单保持不可变 |
| base_price_cents, unit_price_cents | 整数分；unit = base + 所选 option 加价 |
| quantity, subtotal_cents | quantity 1–5；subtotal = unit × quantity |

索引：quotes `(user_id,created_at,id)`、`(expires_at,consumed_order_id)`；quote items `(quote_id,id)`。每个报价内相同 dish/config 归并后只有一条，配置 key 的算法固定，保存选项集合以便审计和验证，不只保存散列。

创建报价时，先在显式只读 REPEATABLE READ 事务中检查当前目录、配置、日期安排和数量并计算快照；结束读取后以短写事务持久化不可变报价。两阶段之间也可能发生菜单或库存变化，因此报价不保证之后仍有库存，订单提交必定再次校验。提交必须重新检查所属用户、未消费、未过期、截止规则、dishVersion、上架与安排状态及实际库存。价格/配置发生变更时要求重新报价和确认，不静默修正金额。

状态由字段推导：Available（未消费且未到期）、Expired（未消费且已到期）、Consumed（consumed_order_id 非空）。Consumed 不因取消订单而回到 Available。第二个 key 使用已消费报价必须返回 `QUOTE_CONSUMED`；同 key 成功重放必须先于此检查。

未消费过期报价可延迟清理；成功订单独立保留 quote UUID、请求 hash 和完整快照，其重放不读取报价内容。已消费报价默认随订单保留，若以后制定清理策略，不得破坏订单的 quote 唯一约束或历史数据。报价清理不参与订单反向加锁。

## 6. 订单、不可变条目与状态机

### orders

| 字段 | 类型与规则 |
| --- | --- |
| id | UUID 主键，API 的 orderId；对外 orderNumber 固定派生为 `WB-` 加大写 UUID，无需另存自增编号 |
| user_id | users 外键，创建后不可改变 |
| quote_id | 原报价 UUID，唯一；订单不依赖报价存在才能读取/重放，不配置删除订单的级联关系 |
| idempotency_key | UUID 字符串；与 user_id 组成唯一键，与订单同生命周期保留 |
| request_hash | SHA-256 固定长度摘要；规范化请求的稳定表示 |
| delivery_date, meal | 从已确认且有效的报价复制，创建后不可改变 |
| delivery_address_snapshot | VARCHAR(200)，trim 后非空，创建后不可改变 |
| status | Pending / Confirmed / Completed / Cancelled |
| active_slot | 生成列：Pending/Confirmed 为 1，其他状态为 NULL；不可由应用直接写入 |
| total_quantity, total_cents | 1–5 份和安全整数分；等于全部条目合计，创建后不可改变 |
| version | 非负整数，状态每次改变递增 |
| created_at | UTC |
| confirmed_at, completed_at, cancelled_at | 可空 UTC，分别在对应成功转换中设置；禁止互相矛盾的组合 |
| updated_at | UTC |

必须存在的唯一索引：

- `(user_id,idempotency_key)`：同一结算意图只创建一次。
- `quote_id`：同一个报价最多关联一个订单，不能换 key 绕过消费限制。
- `(user_id,delivery_date,meal,active_slot)`：仅 Pending/Confirmed 占有效餐次名额；MySQL 允许多个 NULL，因此保留任意多笔 Cancelled/Completed 历史。

查询索引：员工列表 `(user_id,created_at,id)`；管理员与看板 `(delivery_date,status,meal,id)`；近期推荐历史 `(user_id,delivery_date,status,id)`。避免将 status 放进所谓有效订单唯一键而允许 Pending 和 Confirmed 各存在一笔。

### order_items

字段：`id` UUID 主键、`order_id` FK、`dish_id`（保留真实菜品身份）、`dish_name_snapshot`、`dish_version_snapshot`（API 的 dishVersion）、`configuration_key`、`options_snapshot`、`effective_allergens_snapshot`、`base_price_cents`、`unit_price_cents`、`quantity`、`subtotal_cents`。内容结构与报价条目一致，写入后不可修改；索引 `(order_id,id)` 和 `(dish_id,order_id)`。

订单名称、选项名称、单价、金额和配送地址完全从快照展示，不能通过当前 dishes/options/addresses 的 JOIN 重新计算。dish_id 用于汇总销量与恢复供应；菜品只能下架，不能删除被历史引用的身份。order_items 的删除不向任何当前资源传播；订单不提供硬删除入口。

### 合法状态转换

| 当前状态 | 动作与角色 | 结果 | 有效餐次名额 | 库存占用 |
| --- | --- | --- | --- | --- |
| 无订单 | Employee 提交有效报价 | Pending | 新增占位 | 增加原订单总份数 |
| Pending | 本人或 Admin 取消 | Cancelled | 释放 | 减少原订单各菜品份数，恰好一次 |
| Pending | Admin 确认 | Confirmed | 保持 | 不变 |
| Confirmed | Admin 完成 | Completed | 释放 | 保持，不恢复 |
| Cancelled | 本人或 Admin 重复取消 | Cancelled，成功返回当前结果 | 无变化 | 无变化 |
| 其他组合 | 任意非法转换 | 冲突拒绝 | 无变化 | 无变化 |

Completed/Cancelled 都是该订单的终态。Completed 不阻止在截止前使用新报价、新 key 重新下单；库存仍受限制。管理员操作先验证身份并锁 owner/order。目标与当前均为 Cancelled 时先于版本比较返回 200 当前订单，不写时间、版本、库存或事件；除此之外先校验 expectedVersion，旧版本返回 VERSION_CONFLICT。其余相同状态返回 INVALID_ORDER_TRANSITION；不能把 Completed 上的确认请求当作允许回退，也不允许跳过 Confirmed。

### 请求规范化与幂等重放

创建请求只接受 `quoteId` 和 `addressId` / `addressText` 二选一，header 接受 UUID `Idempotency-Key`，不接受客户端声明的总价、用户 ID 或订单状态。

canonical hash 包含协议版本、quoteId、地址选择方式，以及规范化 UUID addressId 或 trim 后 addressText。地址选择方式不同视为不同请求；同地址文本和已保存 addressId 不人为认定为同一请求。JSON 字段顺序、无意义首尾空格和 UUID 表示差异在规范化后稳定；不把当前价格、当前时间或会改变的数据库地址查阅结果放入 hash。

同一 user/key 且 hash 相同返回同一订单及当前状态；hash 不同返回 `IDEMPOTENCY_KEY_REUSED`。重放不再扣库存、存地址、消耗报价或写业务变更事件，即使报价过期、菜单改变、订单已取消或配送截止也一样。失败事务不保存成功 key；修改请求或重新确认报价应使用新的结算 key。

## 7. 事务顺序与并发规则

### 统一锁规则

- 订单写路径：`user → quote（创建时）/order（变更时）→ dish（ID 升序）→ daily_supply（日期、dish ID 升序）`。创建持有 quote 后写入新 order；已有 order 的状态修改不锁其 quote，避免反向依赖。
- 管理员菜单路径：`dish（ID 升序）→ daily_menus（日期升序）→ daily_supply（日期、dish ID 升序）`。只锁实际需要的行，不从 supply 反向补锁 dish 或日期元数据。
- 菜品编辑：先锁 dish，子组/选项及过敏原随根记录修改；如需更新相关日期 revision，再按日期升序锁元数据。不得先锁 daily_menus 后再锁 dish。
- 用户偏好或地址维护需要 user 时也必须先取 user；订单写路径不调用另一个会反向取锁的资料维护事务。
- 稳定排序按存储中的规范化 UUID 比较。多行锁使用明确顺序的逐行/有序操作，不假定任意 IN 查询按输入顺序锁定。

订单与运营写事务显式采用 READ COMMITTED，关键根记录和供应仍使用锁定读取及条件更新、唯一约束；这样不会在早先幂等普通查询建立旧快照后，将当前 dish version 与过时 options 混读。报价生成读取与看板聚合独立采用只读 REPEATABLE READ 一致性快照，不能通过改动全局隔离级别混淆两类目标。

事务内不得调用 OpenRouter、写入上传大文件、等客户端确认或向慢 SSE 连接阻塞发送。死锁与锁超时有限次数重试整个事务；外层重试保留请求 key。任何业务失败、唯一冲突或持久化异常都回滚完整事务，不能留下部分占用。

### 创建订单

1. 验证身份、CSRF、请求结构和长度，规范化请求并计算 hash；快速查询当前 user/key。找到后按 hash 重放或报冲突，先于任何报价/时间验证。
2. 开启事务并锁当前 user 行；再次查询 user/key，处理两个请求在快速查询后并发进入的情况。
3. 锁当前用户所属 quote。不存在/他人报价不泄露内容；已消费而 key 不匹配返回 `QUOTE_CONSUMED`。校验到期时间、份数与报价完整性。
4. 查询当前 user/date/meal 的有效订单，存在则返回可打开该订单的冲突。锁所有相关 dish，锁对应 daily_supply；校验最新版本、选项、上架、scheduled 和报价快照。
5. 在锁获取后再次以服务端 Clock 判断报价到期与订餐截止，避免等待锁越过边界仍成交。过期餐次返回建议日期/餐次，保留购物车，要求确认后重新报价。
6. 按 dish 汇总不同配置的需求，检查所有供应行；条件更新 allocated，必须满足剩余量足够。任何一行不足则整单回滚，并返回具体菜品与可用数量供用户调整。
7. 以报价与当前有效地址生成订单/条目快照，保存或复用新地址，写入 `consumed_order_id`；唯一约束兜底防止重复 key、重复 quote 和重复有效餐次。
8. 同事务写 outbox，提交成功后返回订单。若提交结果不确定，客户端仍用原 key 解析结果；若唯一冲突，先回滚再用新事务优先查同 key，其次查消费报价/有效订单。

库存、订单、地址、报价消费和 outbox 共享一个事务边界，不将扣库存拆成独立提交。锁定读取使用当前已提交行，不能在需要实时校验处依赖更早普通 SELECT 的快照。

### 取消与管理员处理

先从不可变订单身份定位 owner，按 user → order 重新锁定。员工须为 owner；Console 须为 Admin。已取消重复取消先于版本比较返回当前结果；其他管理员变更先验 expectedVersion。取消仅允许 Pending：按原订单 dish 汇总份数，再锁所需 supply 并减少 allocated；订单状态、取消时间、supply version 和 outbox 同事务提交。重复取消直接返回，不重复恢复；如果管理员先确认，取消返回状态冲突。

管理员确认/完成同样先锁 owner 与 order。确认不能处理 Cancelled；完成只能由 Confirmed 进入。状态写入同时更新 version/timestamp；完成不修改 supply。Pending → Cancelled 和 Pending → Confirmed 因争用同一订单锁只能有一项成功。

### 管理员供应修改

以请求 expected revision 检查日期菜单，按统一锁序锁菜品、日期元数据与供应。检查菜品存在且可安排、数量为非负整数、new total 不小于 allocated。保存 scheduled/total、递增相关 supply version 和日期 revision、写 outbox 后提交；版本冲突要求重新读取，不能把旧表格覆盖并发订单已产生的 allocated。

设置初始供应只写 total，不把 total 当 remaining 返回；用户消费导致 allocated 变化后，管理员编辑只能改变 total/scheduled，不能由客户端设置 allocated。

## 8. 事务 outbox 与会话基础设施

### outbox_events

| 字段 | 类型与规则 |
| --- | --- |
| id | UUID 主键，事件身份 |
| event_type | 受内部协议限制的类型，具体名称与 SSE 契约一致 |
| aggregate_key | 日期/菜品或相关聚合身份，不能携带个人秘密 |
| payload | JSON：库存绝对量、行版本、菜单 revision 等必要公开信息；不包含订单地址、邮箱或密码 |
| created_at | UTC，原业务事务产生 |
| published_at | 可空 UTC；NULL 表示尚未完成投递 |
| attempt_count, next_attempt_at | 非负整数与 UTC，用于有限退避和故障观测；失败不删除事件 |
| last_error_code | 可空短错误码，不记录敏感 payload/异常密钥 |

索引 `(published_at,next_attempt_at,created_at,id)`。每 250ms 分批扫描已提交且未发布的记录；不维护“最大 ID 已处理到哪里”的游标，不按 UUID 或自增序号推断提交顺序。单实例发布者向当前订阅者入队后标记 published；崩溃窗口允许重复投递，接收方以每行 version 去重。没有在线订阅者的事件也可完成发布，之后连接由数据库快照恢复当前事实。

连接建立/重连先注册缓冲订阅，再读取并发送权威快照，随后排空缓冲；重复/旧事件按 version 丢弃。慢连接使用有界队列，超限断开后重连取快照。已发布历史可按运维保留周期清理，未发布事件不能清理；数据库积压与发布失败纳入健康观测。

### Spring Session JDBC 表

使用与 Spring Session 版本匹配的官方 MySQL schema，由 Flyway 管理 `SPRING_SESSION`、`SPRING_SESSION_ATTRIBUTES`，不自行发明一套会话表。保留框架要求的主键、SESSION_ID 唯一性、EXPIRY_TIME 与 PRINCIPAL_NAME 索引及属性关系；框架内部 ID/时间列遵循其官方定义，而非强行套用业务 UUID/时间格式。

会话保存最小身份与安全上下文，不保存购物车、商品快照或模型对话。会话过期和退出销毁可以级联清理框架属性，因为它不属于业务历史；不能级联清理账号、地址或订单。CSRF token 保存在会话安全机制中，刷新身份时按 Spring Security 规则重新取得。

## 9. 推荐与经营统计的派生模型

### Recommendation

不创建可购买的“AI 商品”表，也不持久化完整自然语言对话作为业务必需数据。一次请求由 request UUID、当前 user、目标日期、诉求、结构化意图、候选 ID 集合和流式卡片组成，生命周期限于请求。

候选从已上架且 scheduled 的实际菜品/供应读取，remaining 必须大于 0；基础标签或任一当前选项标签命中本人过敏原即排除整道菜。近期记录按运营日期今天及前六日的 delivery_date 读取所有非 Cancelled 订单，默认对已有 dish 降权，明确排重按解析的指定日期范围硬排除。发卡前重新核对当前偏好 version、菜单/菜品/库存和相关历史，无法再满足时不发卡；之后正常加购/下单仍复核。

模型只接收所需菜品、偏好和按菜品归并的近期记录，不接收 user email、地址、密码、session 或其他用户记录。可记录无个人诉求的耗时、模型/provider/generation ID、候选数和错误码用于运行观测，不能将完整提示默认写入日志。

### Operating Metrics

统计不设独立人工录入或预置收入表。服务端在显式 `READ ONLY + REPEATABLE READ` 事务的一致性快照中完成多条聚合查询，返回同一 `generatedAt`；不能在默认 READ COMMITTED 下把多个时点的结果拼成同一看板。

所有查询使用同一个日期参数 D：来自 `/admin/dashboard?date=YYYY-MM-DD`，省略时以 Asia/Shanghai 计算运营今天。响应 `date` 回显 D，`generatedAt` 为本次生成的 UTC 时间，两者不能混用。显式非今天的合法日期沿用相同口径；读取当前快照中的订单状态和 D 供应行，不提供历史时点回放。

| 指标 | 口径 |
| --- | --- |
| 查询日订单总数 | delivery_date = D，全部四种状态 |
| 查询日收入 | D 配送的 Completed 订单 orders.total_cents 合计，含加料；显示 Completed Order Amount，不表示收款 |
| 已完成 / 待处理 / 已取消 | Completed；Pending + Confirmed；Cancelled，均按配送日 D |
| Top 10 | D 配送的 Completed 订单 order_items.quantity 按 dish_id 合并，不同配置合并；按销量降序、菜品 ID 升序稳定排序 |
| 午晚餐量 | D 配送的全部状态订单按 meal 计订单数 |
| 七日趋势 | D-6 至 D（含首尾），按 delivery_date；每日总订单量和上述收入口径，应用层生成完整七日序列并补零 |
| 低库存 | D 的 scheduled 且上架菜品，当前快照 remaining 0–3；0 为 Sold Out，1–3 为 Low Stock |

销量的菜品身份和份数来自订单快照，不因当前下架而漏算；排行榜显示名称优先使用该统计日最近一笔计入销量订单的名称快照，同 created_at 时以订单 ID 稳定裁决，避免当前改名改写历史统计标签。累计收入使用安全整数溢出检查；若聚合可能越界，返回明确错误并告警，不截断或转浮点。

单次快照保证看板自身可核对，不保证事务结束后仍没有新订单。用户 Refresh 启动新快照，按 SC-011 验证 3 秒内看到已完成事务的变化。

## 10. 模型验收要点

详细可执行验收台账见 [acceptance.md](acceptance.md)，所有业务场景当前未执行。

- 在独立 MySQL 上验证生成列唯一键：同人同餐次 Pending/Confirmed 合计最多一笔；多笔 Cancelled/Completed 允许；完成不恢复库存。
- 同 key 并行 20 次、原订单取消后旧 key 重放、quote 过期后旧 key 重放均指向同一历史订单；同 quote 换 key 不创建第二单。
- 10 份库存供 20 个用户竞争、配置合并扣减、跨菜品任一不足整单回滚、重复取消与取消/确认竞争均满足份数守恒。
- 管理员供应修改与下单并发时 allocated 不被覆盖，total 不低于占用；价格/配置版本改变及下架使旧报价拒绝成交。
- 邮箱大小写唯一、128 字符有效密码、跨用户 quote/address/order/prefs 访问、Cookie/CSRF/角色边界和重启持久化均有验证场景。
- outbox 延迟提交/重投递/发布进程重启与 SSE 重连仍恢复最新绝对库存；旧 HTTP 响应不能覆盖更高版本。
- 看板四状态、连续七日零值、配置合并、历史改名、Completed 后重订与取消后收入口径可逐项对账。

这些条目是模型设计的验证目标；实际运行命令与端到端步骤见 [quickstart.md](quickstart.md)，接口字段和错误码以 [contracts/](contracts/) 为准。

## 11. 菜单资料缓存

单实例使用 Caffeine 的 `menuDishDetails` 缓存，key 为 `dishId:dishVersion`，值为不可变菜品资料（名称、说明、图片、基础价格、分类、辣度、配置、基础及选项过敏原）。不包含库存、日期安排、用户偏好或最终个性化菜单响应。默认 maximumSize=10000、expireAfterWrite=5m，版本由 Spring Boot BOM 管理。

菜单查询在显式只读 REPEATABLE READ 快照中取得当前日期可售成员、分页/搜索/排序结果、dishVersion、menuRevision 和库存；只有 ID/version 精确匹配才能复用缓存。未命中时在同一数据库快照加载完整资料，验证根与子表属于该版本后放入对应键。不得把最新内容写入旧版本键。偏好匹配和剩余量在每次请求中组合，不从缓存取。

菜品变更事务提交后使该菜品已有缓存键失效；回滚不触发失效。旧读事务稍后回填只能写自己的旧版本键，后续查询由数据库版本选择新键，因此不会将旧值当最新值。日期安排与供给不进入该缓存，读取实时数据库；更新继续递增现有 revision/version 并发送 outbox。TTL 和容量只清理资源，不承担业务正确性。权限、报价、下单、AI 发卡硬过滤和经营统计均直接读取权威数据库。进程重启为空缓存，可重新加载；记录命中、未命中、逐出和加载耗时，不添加 Redis 依赖。
