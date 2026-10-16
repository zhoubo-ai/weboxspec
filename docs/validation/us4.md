# US4 数量、截单与有效订单验证

日期：2026-10-16。执行者：Codex。T063–T069完成；真实MySQL8.4.11隔离容器，沿用[环境记录](environment.md)。

## 实现及验证

- AC-US04-01：菜单、详情和购物车共用加购规则，5份、售罄、失效配置和跨配置库存均有英文说明。减量后恢复加购；绕过页面直接提交总6份返回422，0/单行6/非整数返回400，不生成订单。
- AC-US04-02：使用服务器时间及单调经过时间判断Asia/Shanghai截止；跨10:00切Dinner，跨15:00切次日Lunch。重新聚焦刷新时间。下单瞬间收到SLOT_EXPIRED时采用服务器建议，必须明确确认再获取新报价。09:59:59、10:00:00、14:59:59、15:00:00、23:59:59逐一验证Lunch/Dinner结果。
- AC-US04-03：未来日期及Dinner保留，过去日期拒绝且不静默改为今天。
- AC-US04-04：查询本人Pending/Confirmed分页，匹配日期餐次后显示View Existing Order。另一并行新提交仍由user锁及唯一约束拒绝；复用US3真实数据库竞争测试。
- AC-US04-05：浏览器取消后明确新结算可成功。OrderTransactionIT验证Completed释放餐次但保留库存，旧key在取消/完成、quote清理、过期和截止后仍返回原单。
- AC-US04-06：切次日后该菜未安排，保留条目并阻止继续，不跳更晚日期。变价、配置变更、下架恢复验证复用menu-cart和ordering浏览器流程。
- AC-SC-005：以上边界加OrderTransactionIT中20次同key、不同quote同餐次竞争、真实锁等待跨截止/报价到期、跨菜品缺货原子回滚全部通过。金额与库存证据承接[US3](us3.md)。

报价读取真实菜品期间跨截止点的新增测试先得到201（预期409），定位到只在读取开始判断时间的问题；写报价事务内补充时间检查后返回409且无quote写入。浏览器首次测试发现原查询库只监听visibilitychange，不处理window focus；补上焦点校准后通过。红灯证据为`.local/logs/us4-rules-read-red.log`、`us4-e2e-first.log`；单元初始规则红灯在`us4-selection-red.log`。

测试专用时钟请求头仅存在于test源码E2eBackend，验证随机夹具密钥，并以ThreadLocal限定单次请求、finally清理；不同浏览器用例互不改变服务器全局时间。生产包不包含此入口。浏览器时钟仅推动页面经过时间，实际API、MySQL、库存、订单均真实执行。

## 验证结果

- `./scripts/backend.sh verify`：6单元+44集成=50通过，无失败/跳过；`.local/logs/us4-backend.log`。
- `./scripts/frontend.sh run test:unit -- --run`：29通过，含焦点校准后确认与冻结请求不自动改餐次；`.local/logs/us4-unit.log`。
- `./scripts/frontend.sh run build`：类型检查与构建通过；`.local/logs/us4-build.log`。
- `./scripts/frontend.sh run test:e2e`：135通过，15流程×Chromium/Firefox/WebKit×375/768/1440，2.1分钟；其中36项US4，99项既有流程回归；`.local/logs/us4-e2e.log`。

US4完成，偏好、管理员、实时通知、AI推荐、看板及最终交付继续按后续阶段实施。
