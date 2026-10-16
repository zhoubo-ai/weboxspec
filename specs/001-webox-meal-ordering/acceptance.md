# WeBox 验收场景与执行台账

**日期**：2026-10-16。当前 **77项通过、4项未执行**；文档静态检查另记，不代替应用、MySQL、LLM、负载或真人验证。

共 **81 项**：51 个用户场景、11 项质量目标、10 项交付检查、9 组差异夹具；77项通过、4项未执行。

依据：[spec.md](spec.md)、[修订决策](decisions.md)、[原文追溯](requirements-source.md)。参考版180个场景只作为来源索引；本表按本项目已采用规则重新编号，不混用参考版收入、会话或其他未采用判据。

## 执行规则

- 每次执行记录时间、环境/版本、角色与账号、业务日期、夹具、输入、实际结果、证据路径和执行者；状态可为未执行/通过/失败/阻塞。空证据或缺运行环境不得标通过。
- 隔离夹具，控制截止时钟，避免唯一餐次、库存或旧数据掩盖被测规则。涉及权限、库存、份数和状态的场景同时核验直接请求及数据库结果。
- 每个AC编号保持稳定。修改判据记录对应决策，补充场景使用新编号；通过结果须注明适用版本，不能沿用旧口径测试结论。
- 运行方法与固定夹具见 [quickstart.md](quickstart.md)，文档质量清单只评审需求质量。

## 用户场景

| AC | 依据 | 前置条件 | 操作 | 预期 | 状态 | 证据 |
| --- | --- | --- | --- | --- | --- | --- |
| <a id="ac-us01-01"></a>AC-US01-01 | [FR-005](spec.md#fr-005)、[FR-006](spec.md#fr-006) | 邮箱尚未注册 | 员工提交格式正确的邮箱和符合密码要求的密码 | 账号创建成功且可登录，不要求邮箱属于特定公司域名。 | 通过 | [2026-10-16 US1实测](../../docs/validation/us1.md) |
| <a id="ac-us01-02"></a>AC-US01-02 | [FR-005](spec.md#fr-005)、[FR-006](spec.md#fr-006)、[FR-007](spec.md#fr-007) | 已有注册邮箱 | 再次注册或输入错误密码登录 | 用户看到明确英文反馈，且不会创建重复账号或获得登录状态。 | 通过 | [2026-10-16 US1实测](../../docs/validation/us1.md) |
| <a id="ac-us01-03"></a>AC-US01-03 | [FR-007](spec.md#fr-007) | 已登录员工 | 刷新页面或退出后再次访问个人页面 | 刷新保持登录，退出后受限页面要求重新登录。 | 通过 | [2026-10-16 US1实测](../../docs/validation/us1.md) |
| <a id="ac-us01-04"></a>AC-US01-04 | [FR-008](spec.md#fr-008)、[FR-031](spec.md#fr-031) | 员工或未登录访客 | 直接访问 Console 或尝试操作他人的订单、地址和偏好 | 操作被拒绝，且不泄露他人数据；管理员可进入独立导航的 Console。 | 通过 | [US6验证](../../docs/validation/us6.md) |
| <a id="ac-us01-05"></a>AC-US01-05 | [FR-004](spec.md#fr-004)、[FR-006](spec.md#fr-006) | 无效邮箱、7 位密码、纯字母密码或超长输入 | 提交表单 | 显示对应的英文校验信息，未产生账号；密码不会以原文保存在可查阅的账号资料中。 | 通过 | [2026-10-16 US1实测](../../docs/validation/us1.md) |
| <a id="ac-us02-01"></a>AC-US02-01 | [FR-010](spec.md#fr-010)、[FR-011](spec.md#fr-011) | 当日有已上架菜品 | 打开菜单或详情 | 卡片显示图片、英文名称、两位小数的人民币价格和菜系；详情展示描述、蛋白质来源、过敏原、辣度与配置。 | 通过 | [US2验证](../../docs/validation/us2.md) |
| <a id="ac-us02-02"></a>AC-US02-02 | [FR-012](spec.md#fr-012) | 菜单包含多个菜系 | 同时选择 `Chinese`、`Japanese` 并输入关键词 | 结果属于所选任一菜系且名称或描述匹配关键词；无结果时显示可恢复的英文空状态。 | 通过 | [US2验证](../../docs/validation/us2.md) |
| <a id="ac-us02-03"></a>AC-US02-03 | [FR-013](spec.md#fr-013) | `Classic Beef Burger` 有必选面胚与酱料 | 员工未完成必选项就尝试加入 | 被要求补全；选择加料后立即展示包含加价的单价。 | 通过 | [US2验证](../../docs/validation/us2.md) |
| <a id="ac-us02-04"></a>AC-US02-04 | [FR-014](spec.md#fr-014) | 同一汉堡存在不同配置 | 连续添加同配置两份、另一配置一份 | 同配置合为数量 2 的条目，不同配置单独列出，配置与金额均准确。 | 通过 | [US2验证](../../docs/validation/us2.md) |
| <a id="ac-us02-05"></a>AC-US02-05 | [FR-015](spec.md#fr-015)、[FR-022](spec.md#fr-022) | 购物车内有多条菜品 | 调整数量、移除条目或快速多次添加 | 每次成功添加增加一份、总份数和总价一致，达到 5 份时停止继续增加，减量后可继续添加。 | 通过 | [US2验证](../../docs/validation/us2.md) |
| <a id="ac-us02-06"></a>AC-US02-06 | [FR-003](spec.md#fr-003)、[FR-015](spec.md#fr-015) | `Tomato Pasta` 为 ¥26.50、芝士加价 ¥3.00 | 选择面型并加入两份含芝士意面 | 单价为 ¥29.50、小计及无其他条目时的总价为 ¥59.00。 | 通过 | [US2验证](../../docs/validation/us2.md) |
| <a id="ac-us02-07"></a>AC-US02-07 | [FR-014](spec.md#fr-014)、[FR-026](spec.md#fr-026) | 员工甲有购物车草稿 | 刷新、关闭重开浏览器或退出后重新登录甲账号 | 恢复原配置及份数并重新校验当前价格和可售性；登录乙账号看不到甲草稿，失效条目须处理后才能结算。 | 通过 | [US2验证](../../docs/validation/us2.md) |
| <a id="ac-us03-01"></a>AC-US03-01 | [FR-016](spec.md#fr-016) | 有效购物车且所选餐次可订 | 员工检查默认配送日期、选择 `Lunch` 或 `Dinner` 并选用历史地址或输入新地址 | 结算展示每项配置、数量、含加料的小计与总价。 | 通过 | [US3验证](../../docs/validation/us3.md) |
| <a id="ac-us03-02"></a>AC-US03-02 | [FR-019](spec.md#fr-019) | 订单提交成功 | 显示结果 | 员工看到唯一订单编号、配送日期、餐次、地址及 `Pending` 状态，新地址成为本人可再次选择的历史地址，已订购的购物车条目清空。 | 通过 | [US3验证](../../docs/validation/us3.md) |
| <a id="ac-us03-03"></a>AC-US03-03 | [FR-018](spec.md#fr-018) | 同一次结算提交遇到网络延迟或结果丢失 | 员工连续点击或重试 | 最终仅有一个订单，重试获得该订单结果，不产生多次订单或库存的重复扣减。 | 通过 | [US3验证](../../docs/validation/us3.md) |
| <a id="ac-us03-04"></a>AC-US03-04 | [FR-020](spec.md#fr-020) | 员工已有历史订单且菜品后来被改名、改价或下架 | 查看订单列表与详情 | 仅看到本人全部订单，历史名称、配置、单价、数量、小计、总价和配送信息保持下单时内容。 | 通过 | [US3验证](../../docs/validation/us3.md) |
| <a id="ac-us03-05"></a>AC-US03-05 | [FR-021](spec.md#fr-021) | 本人的订单为 `Pending` | 取消或重复取消 | 最终为 `Cancelled` 且仅产生一次取消效果；`Confirmed`、`Completed` 和他人订单均不可由该员工取消。 | 通过 | [US3验证](../../docs/validation/us3.md) |
| <a id="ac-us03-06"></a>AC-US03-06 | [FR-004](spec.md#fr-004)、[FR-017](spec.md#fr-017) | 空购物车、空白地址、超过 200 字符的地址或过去的配送日期 | 尝试提交 | 显示英文原因且不会产生订单。 | 通过 | [US3验证](../../docs/validation/us3.md) |
| <a id="ac-us04-01"></a>AC-US04-01 | [FR-022](spec.md#fr-022) | 购物车已有 5 份 | 查看菜单添加按钮或购物车加号 | 均不可增加且解释上限；即使绕过页面限制尝试提交 6 份，也不能生成订单。 | 通过 | [US4验证](../../docs/validation/us4.md) |
| <a id="ac-us04-02"></a>AC-US04-02 | [FR-023](spec.md#fr-023)、[FR-024](spec.md#fr-024) | 当日午餐截止前尚可订 | 运营时间到达 10:00 或员工在 10:30 提交当日午餐 | 所选餐次切换到当日晚餐并显示英文通知；当日 15:00 及之后则切换到次日午餐。 | 通过 | [US4验证](../../docs/validation/us4.md) |
| <a id="ac-us04-03"></a>AC-US04-03 | [FR-023](spec.md#fr-023)、[FR-026](spec.md#fr-026) | 配送日为未来且所选餐次尚未截止 | 进入或提交结算 | 保留原选择；已过期日期不会被悄悄当作当天订单处理。 | 通过 | [US4验证](../../docs/validation/us4.md) |
| <a id="ac-us04-04"></a>AC-US04-04 | [FR-025](spec.md#fr-025) | 本人已在所选日期和餐次有 `Pending` 或 `Confirmed` 订单 | 进入结算或切换到该餐次 | `Place Order` 变为 `View Existing Order`，点击打开已有订单；同时从不同页面提交也不会出现两个有效订单。 | 通过 | [US4验证](../../docs/validation/us4.md) |
| <a id="ac-us04-05"></a>AC-US04-05 | [FR-018](spec.md#fr-018)、[FR-025](spec.md#fr-025) | 同餐次订单已 `Cancelled` 或 `Completed` 且仍未截单 | 员工明确发起一笔新的结算 | 可重新订餐；旧提交的重试仍返回旧订单。 | 通过 | [US4验证](../../docs/validation/us4.md) |
| <a id="ac-us04-06"></a>AC-US04-06 | [FR-017](spec.md#fr-017)、[FR-026](spec.md#fr-026)、[FR-033](spec.md#fr-033) | 自动切换后目标日期没有所选菜品，或购物车内菜品已下架、价格或配置改变 | 提交 | 明确列出需调整内容并保留购物车，不静默替换菜品或按未确认的新价格下单。 | 通过 | [US4验证](../../docs/validation/us4.md) |
| <a id="ac-us05-01"></a>AC-US05-01 | [FR-027](spec.md#fr-027) | 员工保存偏好 | 刷新或重新登录 | 设置仍存在；非法预算如负数、下限大于上限不能保存并有英文反馈。 | 通过 | [US5验证](../../docs/validation/us5.md) |
| <a id="ac-us05-02"></a>AC-US05-02 | [FR-028](spec.md#fr-028) | 员工标记 `Egg` 过敏且菜品包含 `Egg` | 尝试加入购物车 | 弹窗列出命中的过敏原；拒绝不改变购物车，选择继续才添加，菜品仍可在普通菜单中看到。 | 通过 | [US5验证](../../docs/validation/us5.md) |
| <a id="ac-us05-03"></a>AC-US05-03 | [FR-029](spec.md#fr-029) | 员工偏好 `Japanese` 和 `None` 辣度 | 开启 `Recommended for Me` | 优先展示偏好菜系、在同一优先组中优先匹配辣度并高亮命中项；关闭时恢复默认顺序，不改变搜索和筛选条件。 | 通过 | [US5验证](../../docs/validation/us5.md) |
| <a id="ac-us05-04"></a>AC-US05-04 | [FR-030](spec.md#fr-030) | 单餐预算上限为 ¥30.00 | 结算金额为 ¥30.01 | 出现英文超预算提示但仍可提交；金额等于上限时不提示超出。 | 通过 | [US5验证](../../docs/validation/us5.md) |
| <a id="ac-us05-05"></a>AC-US05-05 | [FR-028](spec.md#fr-028) | 基础菜品无 Egg、可选加料标记 Egg，且员工标记 Egg | 不选该加料、再选择该加料或修改已有配置 | 仅实际配置命中时提醒；新增匹配重新确认，拒绝保留原购物车。 | 通过 | [US5验证](../../docs/validation/us5.md) |
| <a id="ac-us06-01"></a>AC-US06-01 | [FR-031](spec.md#fr-031)、[FR-032](spec.md#fr-032) | 管理员已登录 | 进入 Console 菜品表格、搜索名称或按分类筛选 | 可定位已有菜品，并可新增或编辑完整菜单资料及图片。 | 通过 | [US6验证](../../docs/validation/us6.md) |
| <a id="ac-us06-02"></a>AC-US06-02 | [FR-032](spec.md#fr-032) | 管理员编辑价格、过敏原或必选/付费选项 | 保存有效内容 | 员工随后打开详情可见更新；负价格、未知分类、缺失必填项或无效图片不能保存，显示英文原因。 | 通过 | [US6验证](../../docs/validation/us6.md) |
| <a id="ac-us06-03"></a>AC-US06-03 | [FR-033](spec.md#fr-033) | 已上架菜品出现在每日菜单 | 管理员下架 | 员工重新获取菜单后不再看到该菜品，购物车中的旧条目不能继续下单，历史订单不受影响。 | 通过 | [US6验证](../../docs/validation/us6.md) |
| <a id="ac-us06-04"></a>AC-US06-04 | [FR-034](spec.md#fr-034) | 管理员进入每日菜单设置 | 保持默认次日或切换到当日、选择菜品并设置非负整数供应量 | 指定日期安排被保存，只有已上架且被安排的菜品出现在该日菜单。 | 通过 | [US6验证](../../docs/validation/us6.md) |
| <a id="ac-us06-05"></a>AC-US06-05 | [FR-035](spec.md#fr-035) | 项目按启动说明完成初始化 | 在当日尚未截单时打开员工端 | 当日已有可用于完整订餐的菜单及供应数据，包含 PRD 示例中的菜品、选项与英文内容；截止后按订餐规则提示最近可订餐次。 | 通过 | [US6验证](../../docs/validation/us6.md) |
| <a id="ac-us06-06"></a>AC-US06-06 | [FR-036](spec.md#fr-036) | 订单状态为 `Pending` | 管理员依次确认、完成 | 状态依次为 `Confirmed`、`Completed` 且员工可见；跳过确认、回退状态或确认/完成已取消订单均不允许。 | 通过 | [US6验证](../../docs/validation/us6.md) |
| <a id="ac-us06-07"></a>AC-US06-07 | [FR-021](spec.md#fr-021)、[FR-036](spec.md#fr-036)、[FR-040](spec.md#fr-040) | 他人的 Pending 订单 | 管理员取消并重试 | 返回同一 Cancelled 订单，原配送日库存只恢复一次；普通员工无权取消他人订单，管理员亦不能取消 Confirmed/Completed。 | 通过 | [US6验证](../../docs/validation/us6.md) |
| <a id="ac-us06-08"></a>AC-US06-08 | [FR-035](spec.md#fr-035) | 干净数据与运营日期 D | 初始化、修改菜品和供应、再执行初始化 | 首次 D 与 D+1 各有九道菜、每道 20 份，重跑不覆盖修改、不重置占用或重复记录。 | 通过 | [US6验证](../../docs/validation/us6.md) |
| <a id="ac-us06-09"></a>AC-US06-09 | [FR-032](spec.md#fr-032) | 可解码且像素合规的图片 | 上传 5,242,880 字节与 5,242,881 字节的文件 | 前者通过大小校验，后者拒绝；替换失败保留旧图。 | 通过 | [US6验证](../../docs/validation/us6.md) |
| <a id="ac-us07-01"></a>AC-US07-01 | [FR-037](spec.md#fr-037) | 剩余量分别为 4、3、1、0 | 员工浏览菜单 | 均展示数量，1–3 有低库存提示，0 显示售罄并不可添加或下单。 | 通过 | [US7验证](../../docs/validation/us7.md) |
| <a id="ac-us07-02"></a>AC-US07-02 | [FR-038](spec.md#fr-038) | 仅剩 1 份且两名员工同时各买 1 份 | 提交 | 最多一人成功，库存不为负；失败者知道具体不足菜品及可用数量，能调整后重新提交。 | 通过 | [US7验证](../../docs/validation/us7.md) |
| <a id="ac-us07-03"></a>AC-US07-03 | [FR-038](spec.md#fr-038) | 多条目订单中有一个菜品库存不足 | 提交 | 整单不创建，不扣除其他菜品库存，购物车保留。 | 通过 | [US7验证](../../docs/validation/us7.md) |
| <a id="ac-us07-04"></a>AC-US07-04 | [FR-039](spec.md#fr-039)、[FR-040](spec.md#fr-040) | 两名员工打开同一天菜单 | 一人下单或取消待处理订单 | 另一人在无需手动刷新的情况下看到剩余量变化；重复提交或重复取消不重复扣减或恢复。 | 通过 | [US7验证](../../docs/validation/us7.md) |
| <a id="ac-us08-01"></a>AC-US08-01 | [FR-041](spec.md#fr-041)、[FR-042](spec.md#fr-042) | 员工标记 `Dairy` 过敏且存在售罄菜品 | 请求推荐 | 结果仅包含目标日期可售、未售罄且不含所标记过敏原的菜品，并结合菜系、辣度、口味和预算偏好解释理由。 | 通过 | [US8验证](../../docs/validation/us8.md) |
| <a id="ac-us08-02"></a>AC-US08-02 | [FR-041](spec.md#fr-041)、[FR-042](spec.md#fr-042) | 员工近 7 天曾点过部分菜品 | 有其他匹配候选 | 优先推荐未点过的菜品；明确要求不重复时剔除指定期间已点菜品，候选不足时说明原因而不放宽过敏原限制。 | 通过 | [US8验证](../../docs/validation/us8.md) |
| <a id="ac-us08-03"></a>AC-US08-03 | [FR-043](spec.md#fr-043) | 正常可用的选餐助手 | 员工提交英文描述 | 推荐内容逐步呈现，每项为带英文理由的菜品卡片，可进入正常配置和加购流程。 | 通过 | [US8验证](../../docs/validation/us8.md) |
| <a id="ac-us08-04"></a>AC-US08-04 | [FR-044](spec.md#fr-044) | 推荐服务不可用、超时或无匹配菜品 | 发起请求 | 得到英文说明，可重试或返回普通菜单继续订餐，不出现虚构菜品或成功提示。 | 通过 | [US8验证](../../docs/validation/us8.md) |
| <a id="ac-us08-05"></a>AC-US08-05 | [FR-042](spec.md#fr-042) | 员工标记 Egg，某菜基础标签无 Egg 但任一可选配置有 Egg | 请求推荐 | 整道菜被排除，模型或用户提示词不能放宽；普通菜单仍遵循实际所选配置提醒。 | 通过 | [US8验证](../../docs/validation/us8.md) |
| <a id="ac-us09-01"></a>AC-US09-01 | [FR-045](spec.md#fr-045)、[FR-047](spec.md#fr-047) | 今日有不同状态订单 | 查看概览 | 显示今日订单总数、收入、已完成数、待处理数、已取消数，统计口径可从看板上的英文说明理解。 | 通过 | [US9验证](../../docs/validation/us9.md) |
| <a id="ac-us09-02"></a>AC-US09-02 | [FR-045](spec.md#fr-045)、[FR-047](spec.md#fr-047) | 今日菜品有不同销量 | 查看排行和时段分布 | 柱状图展示销量最高的至多 10 个菜品，餐次对比区分 `Lunch` 与 `Dinner`；七日折线图包含无订单日期的零值。 | 通过 | [US9验证](../../docs/validation/us9.md) |
| <a id="ac-us09-03"></a>AC-US09-03 | [FR-046](spec.md#fr-046) | 有菜品剩余量为 0–3 | 查看库存预警 | 列出这些菜品并区分售罄与低库存；新订单或取消发生后，通过自动更新或手动刷新得到最新真实统计。 | 通过 | [US9验证](../../docs/validation/us9.md) |
| <a id="ac-us09-04"></a>AC-US09-04 | [FR-047](spec.md#fr-047) | D 配送的 Pending ¥10、Confirmed ¥20、Completed ¥30、Cancelled ¥40 四笔订单，另有 D 创建而 D+1 配送订单 | 查看 D 看板 | 总数 4、待处理 2、完成 1、取消 1、收入 ¥30，排除 D+1 订单；只有完成订单计入销量。 | 通过 | [US9验证](../../docs/validation/us9.md) |

## 质量目标

以规范中完整测试条件执行，不仅检查数值写在文档中。

| AC | 依据 | 验证目标与条件 | 状态 | 证据 |
| --- | --- | --- | --- | --- |
| <a id="ac-sc-001"></a>AC-SC-001 | [SC-001](spec.md#sc-001) | 在 10 名首次使用者的验收任务中，至少 9 人无需协助即可在手机或桌面完成注册、选餐和下单，单人耗时不超过 5 分钟，并能准确指出配送日期、餐次和地址。 | 未执行 | [真人任务已准备，等待参与者](../../docs/validation/user-acceptance.md) |
| <a id="ac-sc-002"></a>AC-SC-002 | [SC-002](spec.md#sc-002) | 在 375、768、1440 像素宽度下，全部员工流程（含 AI 选餐与库存查看）均可完成；逐页检查的系统提供文案及预置菜单内容 100% 为英文，用户自由地址保留原文，无遮挡关键操作或页面级横向滚动。 | 通过 | [三浏览器/三尺寸/英文/键盘专项](../../docs/validation/accessibility-language.md) |
| <a id="ac-sc-003"></a>AC-SC-003 | [SC-003](spec.md#sc-003) | 在 200 名同时活跃员工、500 个菜单菜品的验收场景中，95% 的菜单打开/筛选/搜索在 2 秒内显示可用结果，95% 的购物车操作在 0.3 秒内显示结果，95% 的有效下单在 3 秒内显示明确结果；不以省略业务校验换取速度。 | 通过 | [200会话600秒及140次DOM计时](../../docs/validation/performance.md) |
| <a id="ac-sc-004"></a>AC-SC-004 | [SC-004](spec.md#sc-004) | 覆盖普通菜品、单选、多选、付费加料及混合数量的至少 20 个验收订单，配置保留正确率与金额精确到分的一致率均为 100%。 | 通过 | [US3验证](../../docs/validation/us3.md) |
| <a id="ac-sc-005"></a>AC-SC-005 | [SC-005](spec.md#sc-005) | 数量为 0/1/5/6、各截单边界、跨日、无菜单、价格变化及同餐次有效订单场景全部得到预期结果；同一次提交并行重试 20 次只产生一个订单，两个独立提交竞争同餐次时最多产生一个有效订单。 | 通过 | [US4验证](../../docs/validation/us4.md) |
| <a id="ac-sc-006"></a>AC-SC-006 | [SC-006](spec.md#sc-006) | 账号、偏好、地址、菜单、每日库存和订单在正常重启后全部可恢复；未授权账号访问个人记录或 Console 的验收尝试 100% 被拒绝，刷新保持登录、退出后受限访问失败。 | 通过 | [应用/MySQL重启与隔离恢复](../../docs/validation/persistence.md)、[权限矩阵](../../docs/validation/security.md) |
| <a id="ac-sc-007"></a>AC-SC-007 | [SC-007](spec.md#sc-007) | 管理员无需修改程序即可在 5 分钟内新增菜品、上传图片、安排次日供应并完成上下架；员工重新打开菜单后 2 秒内看到已生效结果；合法订单状态变化与员工所见一致。 | 未执行 | [真人任务已准备，等待参与者](../../docs/validation/user-acceptance.md) |
| <a id="ac-sc-008"></a>AC-SC-008 | [SC-008](spec.md#sc-008) | 至少 9/10 名验收使用者能正确解释过敏原警告与预算提示的含义，以及是否可以继续；全部命中过敏原的加购场景得到提醒，超预算但其他条件有效的订单可以成功提交。 | 未执行 | [真人任务已准备，等待参与者](../../docs/validation/user-acceptance.md) |
| <a id="ac-sc-009"></a>AC-SC-009 | [SC-009](spec.md#sc-009) | 初始 10 份供应由 20 名员工同时各购买 1 份时最多成功 10 份、剩余量不为负；取消后准确恢复；95% 的已连接菜单页面在库存变更后 2 秒内显示新数量。 | 通过 | [20人争10份及取消](../../docs/validation/us7.md)、[200会话下库存计时](../../docs/validation/performance.md) |
| <a id="ac-sc-010"></a>AC-SC-010 | [SC-010](spec.md#sc-010) | 至少 20 组包含过敏原、售罄、预算和近期订单的功能诉求中，推荐 100% 指向真实可售且无过敏原冲突菜品。性能另以预先登记的至少 20 次有有效候选、预期 COMPLETE 的正常请求为固定样本，首张有效卡片呈现 p95 ≤5 秒、完整完成 p95 ≤20 秒；全部尝试计入分母，缺首卡或未 COMPLETE 的对应耗时计为正无穷，不得事后剔除失败。预期 NO_MATCH、澄清与故障用例独立核验，不纳入首卡性能样本；分组和计时遵循流契约 3.4。至少 8/10 名使用者认为理由与个人诉求相关。 | 未执行 | [技术指标通过，真人相关性待验收](../../docs/validation/us8.md) |
| <a id="ac-sc-011"></a>AC-SC-011 | [SC-011](spec.md#sc-011) | 对至少覆盖连续 7 天、四种订单状态和不同配置的已知订单样本，分别验证省略 date 的运营今天及显式 date 为非今天的 D；看板所有指标统一取各自查询日，趋势覆盖该日及前六日，每个计数、金额、销量和库存预警与独立核算 100% 一致；刷新后 3 秒内反映已完成的订单变化。 | 通过 | [US9验证](../../docs/validation/us9.md) |

## 交付检查

| AC | 依据 | 检查内容 | 预期证据 | 状态 | 证据 |
| --- | --- | --- | --- | --- | --- |
| <a id="ac-dc-001"></a>AC-DC-001 | [DC-001](delivery-constraints.md#dc-001) | 必须交付覆盖 Tier 1、Tier 2、Tier 3 全部功能且可完整运行的前后端；目标架构为前端 SPA → 后端服务 → 独立 MySQL | 不能以纯前端模拟、纯后端或不可运行的任一端代替完整交付 | 通过 | [完整回归、真实模型与正式负载](../../docs/validation/release.md) |
| <a id="ac-dc-002"></a>AC-DC-002 | [DC-002](delivery-constraints.md#dc-002) | 后端必须使用 Java（JDK 17）和 Spring Boot；前端技术栈不限，需说明选型理由 | 后续计划明确遵循指定后端，说明前端选择及理由 | 通过 | [JDK17与选型、实际构建](../../docs/validation/release.md)、[架构](../../docs/architecture.md) |
| <a id="ac-dc-003"></a>AC-DC-003 | [DC-003](delivery-constraints.md#dc-003) | 持久化必须使用独立部署、作为独立进程真实运行的 MySQL | 不接受 H2、SQLite、HSQLDB、Derby、嵌入式 MySQL 等嵌入式或内存数据库作为替代；README 给出连接方法 | 通过 | [独立MySQL重启及备份恢复](../../docs/validation/persistence.md) |
| <a id="ac-dc-004"></a>AC-DC-004 | [DC-004](delivery-constraints.md#dc-004) | 缓存方案不限，Redis 可选，可使用进程内缓存等其他方案 | 本项目纳入附录 A.2 菜单热点缓存及更新/失效验收；采用 Caffeine 进程内缓存，不强制 Redis，不以缓存承担库存和成交正确性 | 通过 | [缓存命中/过期/容量](../../docs/validation/us2.md)、[版本失效/运营更新](../../docs/validation/us6.md) |
| <a id="ac-dc-005"></a>AC-DC-005 | [DC-005](delivery-constraints.md#dc-005) | 所有面向用户的内容以及入库的菜单文案均使用英文 | 包括系统界面、菜单、分类、过敏原、选项、错误、校验、推荐理由及看板；人民币仍使用 `¥`。用户自填邮箱/地址保留原文并安全处理，不强制翻译 | 通过 | [界面英文与原文地址](../../docs/validation/accessibility-language.md)、[真实英文推荐](../../docs/validation/us8.md) |
| <a id="ac-dc-006"></a>AC-DC-006 | [DC-006](delivery-constraints.md#dc-006) | 项目应可长期运行，满足生产运行质量定位 | 规划覆盖可维护性、可靠性、权限边界、输入限制、金额精度和核心业务正确性；不得把仅能展示的静态演示当完整交付 | 通过 | [生产HTTPS/限流/指标/维护](../../docs/validation/operations.md)、[安全](../../docs/validation/security.md)、[备份恢复](../../docs/validation/persistence.md) |
| <a id="ac-dc-007"></a>AC-DC-007 | [DC-007](delivery-constraints.md#dc-007) | 交付可运行代码与 README | README 包括项目介绍、技术栈说明和从零开始的 JDK、后端、前端、独立 MySQL 启动步骤；选用其他服务时补齐 | 通过 | [从零启动README](../../README.md)、[隔离从零启动演练](../../docs/validation/persistence.md) |
| <a id="ac-dc-008"></a>AC-DC-008 | [DC-008](delivery-constraints.md#dc-008) | 交付 API 文档与架构说明 | 接口清单、请求/响应格式；架构图或文字，包含核心模块、数据流和适用的缓存及并发决策 | 通过 | [API与流文档](contracts/README.md)、[实现架构](../../docs/architecture.md)、[静态核验](../../docs/validation/release.md) |
| <a id="ac-dc-009"></a>AC-DC-009 | [DC-009](delivery-constraints.md#dc-009) | 交付可运行并通过的核心业务测试 | 覆盖下单、幂等、金额、规则、权限、实时库存与防超卖、真实 AI 推荐及经营看板的全部交付范围；规格中的验收标准不等于测试已经通过 | 通过 | [完整回归、真实模型与正式负载](../../docs/validation/release.md) |
| <a id="ac-dc-010"></a>AC-DC-010 | [DC-010](delivery-constraints.md#dc-010) | 提交项目开始至结束的完整 AI 编程原始对话导出 | 使用工具原生导出文件，不接受截图、节选、片段或事后摘要；混用多个工具时全部提交，默认存于项目根目录 `ai-conversations/`，或在 README 明确实际路径 | 通过 | [原生完整会话及最终回复，SHA-256已核验](../../ai-conversations/manifest.json) |

## 差异固定夹具

| AC | 决策 | 前置与动作 | 预期 | 状态 | 证据 |
| --- | --- | --- | --- | --- | --- |
| <a id="ac-rev-01"></a>AC-REV-01 | [DEC-R01](decisions.md#dec-r01) | 完成收入：D 配送 Pending ¥10、Confirmed ¥20、Completed ¥30、Cancelled ¥40；另有 D 创建但 D+1 配送订单 | D 总数4、待处理2、完成1、取消1、收入¥30；仅 Completed 贡献销量 | 通过 | [US9验证](../../docs/validation/us9.md) |
| <a id="ac-rev-02"></a>AC-REV-02 | [DEC-R03](decisions.md#dec-r03) | 购物车恢复：甲配置两份餐，刷新/重开/退出重登；换乙；期间管理员改价或下架 | 甲草稿恢复，乙不见甲内容；变化重新确认、失效条目阻止提交，不自动下单 | 通过 | [US2验证](../../docs/validation/us2.md) |
| <a id="ac-rev-03"></a>AC-REV-03 | [DEC-R05](decisions.md#dec-r05) | 配置过敏原：标记 Egg；基础无 Egg，选项显式 Egg；不选/选中/修改已有配置 | 实际配置命中才提醒；新匹配再次确认，拒绝保留原条目；AI 整菜排除 | 通过 | [US8验证](../../docs/validation/us8.md) |
| <a id="ac-rev-04"></a>AC-REV-04 | [DEC-R02](decisions.md#dec-r02) | 取消竞争：同一 Pending 并发管理员确认与本人/管理员取消，之后重试取消 | 仅一个合法转换；成功取消仅回原日期库存一次，重复返回当前结果；Confirmed/Completed 不可取消 | 通过 | [US6验证](../../docs/validation/us6.md) |
| <a id="ac-rev-05"></a>AC-REV-05 | [DEC-R04](decisions.md#dec-r04) | 图片边界：合法内容、像素合规，大小分别 5,242,880 与 5,242,881 字节 | 前者过大小校验、后者 IMAGE_TOO_LARGE；假图片仍拒绝，替换失败旧图保留 | 通过 | [US6验证](../../docs/validation/us6.md) |
| <a id="ac-rev-06"></a>AC-REV-06 | [DEC-R07](decisions.md#dec-r07) | 缓存一致性：预热命中后改价/配置标签/上下架/每日安排/供应；模拟旧事务延后回填 | 新请求选择新版本、最新可售范围和库存；旧报价不能成交，旧键不能覆盖新版本；保留命中及失效证据 | 通过 | [US6验证](../../docs/validation/us6.md) |
| <a id="ac-rev-07"></a>AC-REV-07 | [DEC-R07](decisions.md#dec-r07) | 缓存清理：控制测试时钟跨5分钟、写入超过10,000项并触发维护、重启进程 | 过期/容量逐出可观察，按库重新加载；不因缓存清空改变业务事实 | 通过 | [US2验证](../../docs/validation/us2.md) |
| <a id="ac-rev-08"></a>AC-REV-08 | [DEC-R08](decisions.md#dec-r08) | 初始化：D与D+1各九道20份，修改菜品、供应并真实下单，再重跑 | 不覆盖修改、不重复记录、不重置占用；图片从项目内资源加载 | 通过 | [US6验证](../../docs/validation/us6.md) |
| <a id="ac-rev-09"></a>AC-REV-09 | [DEC-R06](decisions.md#dec-r06) | 用户地址：保存“上海市 A 楼 3 层”等原始地址并查看订单 | 保留原值且安全渲染；系统文案及错误仍为英文 | 通过 | [US3验证](../../docs/validation/us3.md) |

<a id="fr-coverage"></a>

## FR 反向覆盖索引

以下链接用于定位场景；是否通过取决于相应执行记录及关联SC/DC，不凭映射存在而判通过。

| 功能要求 | 用户场景/质量目标 | 状态 |
| --- | --- | --- |
| [FR-001](spec.md#fr-001) | [AC-SC-002](acceptance.md#ac-sc-002) | 通过 |
| [FR-002](spec.md#fr-002) | [AC-SC-002](acceptance.md#ac-sc-002) | 通过 |
| [FR-003](spec.md#fr-003) | [AC-US02-06](acceptance.md#ac-us02-06) | 通过 |
| [FR-004](spec.md#fr-004) | [AC-US01-05](acceptance.md#ac-us01-05)、[AC-US03-06](acceptance.md#ac-us03-06) | 通过 |
| [FR-005](spec.md#fr-005) | [AC-US01-01](acceptance.md#ac-us01-01)、[AC-US01-02](acceptance.md#ac-us01-02) | 通过 |
| [FR-006](spec.md#fr-006) | [AC-US01-01](acceptance.md#ac-us01-01)、[AC-US01-02](acceptance.md#ac-us01-02)、[AC-US01-05](acceptance.md#ac-us01-05) | 通过 |
| [FR-007](spec.md#fr-007) | [AC-US01-02](acceptance.md#ac-us01-02)、[AC-US01-03](acceptance.md#ac-us01-03) | 通过 |
| [FR-008](spec.md#fr-008) | [AC-US01-04](acceptance.md#ac-us01-04) | 通过 |
| [FR-009](spec.md#fr-009) | [AC-SC-006](acceptance.md#ac-sc-006) | 通过 |
| [FR-010](spec.md#fr-010) | [AC-US02-01](acceptance.md#ac-us02-01) | 通过 |
| [FR-011](spec.md#fr-011) | [AC-US02-01](acceptance.md#ac-us02-01) | 通过 |
| [FR-012](spec.md#fr-012) | [AC-US02-02](acceptance.md#ac-us02-02) | 通过 |
| [FR-013](spec.md#fr-013) | [AC-US02-03](acceptance.md#ac-us02-03) | 通过 |
| [FR-014](spec.md#fr-014) | [AC-US02-04](acceptance.md#ac-us02-04)、[AC-US02-07](acceptance.md#ac-us02-07) | 通过 |
| [FR-015](spec.md#fr-015) | [AC-US02-05](acceptance.md#ac-us02-05)、[AC-US02-06](acceptance.md#ac-us02-06) | 通过 |
| [FR-016](spec.md#fr-016) | [AC-US03-01](acceptance.md#ac-us03-01) | 通过 |
| [FR-017](spec.md#fr-017) | [AC-US03-06](acceptance.md#ac-us03-06)、[AC-US04-06](acceptance.md#ac-us04-06) | 通过 |
| [FR-018](spec.md#fr-018) | [AC-US03-03](acceptance.md#ac-us03-03)、[AC-US04-05](acceptance.md#ac-us04-05) | 通过 |
| [FR-019](spec.md#fr-019) | [AC-US03-02](acceptance.md#ac-us03-02) | 通过 |
| [FR-020](spec.md#fr-020) | [AC-US03-04](acceptance.md#ac-us03-04) | 通过 |
| [FR-021](spec.md#fr-021) | [AC-US03-05](acceptance.md#ac-us03-05)、[AC-US06-07](acceptance.md#ac-us06-07) | 通过 |
| [FR-022](spec.md#fr-022) | [AC-US02-05](acceptance.md#ac-us02-05)、[AC-US04-01](acceptance.md#ac-us04-01) | 通过 |
| [FR-023](spec.md#fr-023) | [AC-US04-02](acceptance.md#ac-us04-02)、[AC-US04-03](acceptance.md#ac-us04-03) | 通过 |
| [FR-024](spec.md#fr-024) | [AC-US04-02](acceptance.md#ac-us04-02) | 通过 |
| [FR-025](spec.md#fr-025) | [AC-US04-04](acceptance.md#ac-us04-04)、[AC-US04-05](acceptance.md#ac-us04-05) | 通过 |
| [FR-026](spec.md#fr-026) | [AC-US02-07](acceptance.md#ac-us02-07)、[AC-US04-03](acceptance.md#ac-us04-03)、[AC-US04-06](acceptance.md#ac-us04-06) | 通过 |
| [FR-027](spec.md#fr-027) | [AC-US05-01](acceptance.md#ac-us05-01) | 通过 |
| [FR-028](spec.md#fr-028) | [AC-US05-02](acceptance.md#ac-us05-02)、[AC-US05-05](acceptance.md#ac-us05-05) | 通过 |
| [FR-029](spec.md#fr-029) | [AC-US05-03](acceptance.md#ac-us05-03) | 通过 |
| [FR-030](spec.md#fr-030) | [AC-US05-04](acceptance.md#ac-us05-04) | 通过 |
| [FR-031](spec.md#fr-031) | [AC-US01-04](acceptance.md#ac-us01-04)、[AC-US06-01](acceptance.md#ac-us06-01) | 通过 |
| [FR-032](spec.md#fr-032) | [AC-US06-01](acceptance.md#ac-us06-01)、[AC-US06-02](acceptance.md#ac-us06-02)、[AC-US06-09](acceptance.md#ac-us06-09) | 通过 |
| [FR-033](spec.md#fr-033) | [AC-US04-06](acceptance.md#ac-us04-06)、[AC-US06-03](acceptance.md#ac-us06-03) | 通过 |
| [FR-034](spec.md#fr-034) | [AC-US06-04](acceptance.md#ac-us06-04) | 通过 |
| [FR-035](spec.md#fr-035) | [AC-US06-05](acceptance.md#ac-us06-05)、[AC-US06-08](acceptance.md#ac-us06-08) | 通过 |
| [FR-036](spec.md#fr-036) | [AC-US06-06](acceptance.md#ac-us06-06)、[AC-US06-07](acceptance.md#ac-us06-07) | 通过 |
| [FR-037](spec.md#fr-037) | [AC-US07-01](acceptance.md#ac-us07-01) | 通过 |
| [FR-038](spec.md#fr-038) | [AC-US07-02](acceptance.md#ac-us07-02)、[AC-US07-03](acceptance.md#ac-us07-03) | 通过 |
| [FR-039](spec.md#fr-039) | [AC-US07-04](acceptance.md#ac-us07-04) | 通过 |
| [FR-040](spec.md#fr-040) | [AC-US06-07](acceptance.md#ac-us06-07)、[AC-US07-04](acceptance.md#ac-us07-04) | 通过 |
| [FR-041](spec.md#fr-041) | [AC-US08-01](acceptance.md#ac-us08-01)、[AC-US08-02](acceptance.md#ac-us08-02) | 通过 |
| [FR-042](spec.md#fr-042) | [AC-US08-01](acceptance.md#ac-us08-01)、[AC-US08-02](acceptance.md#ac-us08-02)、[AC-US08-05](acceptance.md#ac-us08-05) | 通过 |
| [FR-043](spec.md#fr-043) | [AC-US08-03](acceptance.md#ac-us08-03) | 通过 |
| [FR-044](spec.md#fr-044) | [AC-US08-04](acceptance.md#ac-us08-04) | 通过 |
| [FR-045](spec.md#fr-045) | [AC-US09-01](acceptance.md#ac-us09-01)、[AC-US09-02](acceptance.md#ac-us09-02) | 通过 |
| [FR-046](spec.md#fr-046) | [AC-US09-03](acceptance.md#ac-us09-03) | 通过 |
| [FR-047](spec.md#fr-047) | [AC-US09-01](acceptance.md#ac-us09-01)、[AC-US09-02](acceptance.md#ac-us09-02)、[AC-US09-04](acceptance.md#ac-us09-04) | 通过 |
