# Checkout / Review 主流程复测

2026-10-16，执行者Codex。使用当前正常配置服务http://127.0.0.1:15678（API18888、独立MySQL）及另起的隔离浏览器套件。没有使用真实服务的测试后门或改变服务时钟。

## 结果

- Chromium / Firefox / WebKit × 375 / 768 / 1440，`ordering.spec.ts`、`order-rules.spec.ts`、`menu-cart.spec.ts`共99项，修复前及修复后分别99/99通过；两次为同组用例复测，不合计成198种场景。
- 正常服务中的独立员工账号完成12个流程检查，两轮各12/12：真实注册登录、选配置与加料、增减购物车、截止后自动切换并确认、Review真实报价、空地址拦截、Place Order、订单刷新、购物车清空、取消、历史地址复用和订单筛选。两轮共4笔测试订单全部Cancelled；测试账号及其已取消订单作为测试记录保留。
- Tomato Pasta + Cheese每份¥29.50，两份¥59.00，三份¥88.50；Review与真实订单金额一致。
- 管理员使用已有配置成功登录，从Console的Employee menu进入结算。服务器订单核对确认2026-10-17 Lunch已有有效订单，显示View Existing Order符合规则。切换Dinner后可Review，报价¥29.50并显示Place Order；此管理员检查未提交订单，也未修改或取消其已有订单。
- 员工正常服务探针没有未捕获的页面脚本异常或HTTP 5xx，管理员探针没有页面脚本异常。类型检查、55项前端单元测试与生产构建通过。

## 修复

截图发现配送自动切换已经确认后，旧提示仍要求确认。`DeliverySelection`确认成功时现同时清除提示。既有截单浏览器用例增加了确认后旧提示消失的断言；业务截止时间、报价、订单和权限规则没有改变。修复已由当前Vite页面加载，生产构建也已更新。

[修复前截图](checkout-review/review-before-fix.png)、[修复后Review](checkout-review/employee-review.png)、[真实订单](checkout-review/employee-order.png)、[管理员Dinner Review](checkout-review/admin-review.png)。[员工原始结果](checkout-review/employee-result.json)、[管理员原始结果](checkout-review/admin-result.json)、[当前源码指纹](checkout-review/source.json)。之前发布验证的源码指纹保留，不把旧性能数据冒充本轮重新测量。

## 命令与原始证据

```bash
./scripts/frontend.sh run test:e2e -- ordering.spec.ts order-rules.spec.ts menu-cart.spec.ts
./scripts/frontend.sh run typecheck
./scripts/frontend.sh run test:unit -- --run
./scripts/frontend.sh run build
```

修复后完整日志`.local/logs/checkout-review-regression-fixed.log`（99通过/2.1分钟）；修复前日志`checkout-review-regression.log`保留。前端命令日志`checkout-review-frontend-checks.log`及时间/退出码`.local/validation-runs/checkout-review-frontend-commands.json`均保留。正常服务探针脚本为`.local/checkout-preview.mjs`和`.local/checkout-admin-preview.mjs`；截图与逐项记录在`.local/validation-runs/checkout-preview-*`和`checkout-admin-preview-*`。

管理员探针首轮遇到已有订单时等待一个不存在的Review按钮而超时，第二轮又可能读取切换餐次前的旧链接；最终探针使用服务器真实订单与明确的界面状态核对。早期探针及日志均保留，这两轮不作为管理员最终通过证据。

此处属于软件自动化与截图检查，不计入10名首次真人使用者的验收。
