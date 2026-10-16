# US7 实时库存与恢复验证

日期：2026-10-16。T095–T103完成，AC-US07-01–04全部通过。使用真实隔离MySQL8.4.11、Java17与三个Playwright浏览器，不访问开发业务数据。

## 事务与连接

订单、取消和管理供应仍由原业务事务同时写供应版本及outbox。单实例每250ms扫描已提交、到期且未发布的记录，最多100条；不使用最大ID游标。事件进入订阅队列后标记发布，无订阅者也可发布。失败记录保留，250ms起指数退避至30s；不会清理未发布记录。历史清理属于T129。

每次连接先注册缓冲，再在REPEATABLE READ事务中读取完整可见日期快照，先发snapshot再发缓冲事件。快照包含全部成员，空日期合法；纯数量更新发送绝对值及单调版本。occurredAt取事务写outbox时的原时间，不宣称它是精确提交时间。公开帧不含用户、订单或地址。

Servlet非阻塞输出仅在isReady时写入、刷新，网络发送在独立有界执行器中进行；订单和outbox线程只入队。每连接最多256帧且1MiB、默认5s写入等待、15s心跳、30min寿命、每会话3/实例600连接。触及连接上限时，向该会话的有界已有连接发送探测注释，让已断开的空闲socket尽快被发现并释放；仍有效的连接保留，当前超限请求返回JSON429。慢连接溢出或超时关闭，通过新快照恢复。[Servlet输出契约](https://jakarta.ee/specifications/servlet/6.1/apidocs/jakarta.servlet/jakarta/servlet/servletoutputstream)说明非阻塞模式的isReady/WriteListener约定。

每次发帧前查询当前会话，不保存会话、不延长空闲期。失效时发送session.expired并关闭；前端停止重试、清除个人缓存并回到登录。内部ERROR分派仅允许容器进入/error处理，普通外部/error仍受拒绝规则保护，错误不包含内部消息或堆栈。该分派规则依据[Spring Security授权说明](https://docs.spring.io/spring-security/reference/servlet/authorization/authorize-http-requests.html)。

## 浏览器合并与恢复

当前菜单日期只保留一条EventSource；首快照后请求菜单，换日/离开/退出关闭旧连接。连接具有独立epoch，旧事件、旧日期及断线前发出的库存HTTP响应不能覆盖新连接。库存按dish版本合并，重复及乱序数量不回退；全量快照删除缺席成员，菜单revision变化、未知成员触发重新取快照及菜单。晚到的旧revision菜单响应会再次重取。

断线显示英文状态与旧数量提示，0.5s起退避至10s，重连前核验会话；页面重新可见、恢复联网时校准。普通菜单仍可查询，不支持EventSource时明确显示非实时状态。Low Stock用于1–3份，0显示Sold Out并禁用加购；购物车同菜不同配置共享库存。管理员资料变化关闭旧配置对话框并重核购物车。库存冲突保留整份草稿，显示具体菜名及可用数量，允许移除缺货项后重新报价提交。

## 已执行与证据

- 最初库存接口缺失及前端无实时状态的行为红灯：`.local/logs/us7-stream-red.log`、`us7-inventory-state-red.log`、`us7-inventory-ui-red.log`。缺货提示缺少菜名/数量的红灯：`us7-conflict-ui-red.log`。内部错误分派原返回401的红灯：`us7-error-dispatch-red.log`。中间编译/测试夹具错误不作为业务红灯证据。
- InventoryStreamIT验证六项真实HTTP流场景：完整快照与权限、下单/取消绝对更新、空菜单安排与重连、会话撤销及空闲时间不变、连接上限和缩短为3s的寿命、注册后读取前有订单提交。缩短时钟验证同一寿命逻辑，未等待生产30分钟。
- OutboxDispatcherIT四项真实DB测试：小UUID事务晚提交仍可发布；广播前失败保留并退避；广播后标记前失败重投相同payload；回滚不投递。前后故障由受控异常注入，不冒充真实进程崩溃。
- InventoryRestartIT另启两个实际打包JVM：第一进程暂停投递、打开快照后提交真实订单，强制结束进程；使用相同隔离DB及媒体目录重新启动，原会话取得remaining19/version1新快照，未发布事件转为已发布且记录仍唯一。MySQL未重启，完整持久化/备份演练仍属于T130。独立运行见`us7-restart.log`。
- InventoryBackpressureIT三项测试使用真实会话、MySQL和订单，受控Servlet非就绪传输验证不写非就绪流、慢连接溢出/写超时、全局容量及其他连接/下单可继续；传输替身用于确定性故障，并非200条真实TCP负载。
- 前端状态及生命周期八项单元覆盖缺席成员删除、版本去重、旧HTTP/日期/连接、未知成员重取、1–3/0标签、断线期间迟到HTTP不冒充live、重连先验会话、session.expired停止所有重试和离开关闭。
- 既有真实数据库订单并发测试继续覆盖20人竞争10份、相同key并发只扣一次、多条目不足整单回滚、20次重复取消只恢复一次；新增两人竞争一份并核对失败itemIssues中的dishId与availableQuantity0。

后端完整91项（6单元+85集成）通过：`.local/logs/us7-backend-final.log`。后续Firefox恢复修正后，鉴权/错误/实时流/连接释放24项集成及6单元再次通过：`us7-reconnect-backend.log`，新增连接探测测试使已运行的唯一后端测试总数增为92。前端43项单元、类型检查及构建通过：`us7-unit.log`、`us7-build.log`。

完整234项浏览器矩阵首先得到231通过、3失败：`us7-e2e-full.log`。失败均为Firefox断线恢复，已经定位为已断开的空闲socket暂占会话名额，且SSE Accept头使429错误无法按JSON序列化，最终变成500。真实诊断见`us7-firefox-reconnect-debug.log`，相同Accept头的后端红灯见`us7-stream-error-red.log`。修正公共错误响应显式JSON类型，并在容量拒绝时主动探测本会话旧连接；不更改连接上限、不关闭其他会话的健康连接。受影响的三种浏览器×三种尺寸×三条库存流程共27项重跑全部通过，见`us7-inventory-final.log`；首轮失败未删除或冒充一次全绿。

修复后另9个样本为26.93–303.57ms，见[修复后记录](us7-inventory-latency-after-fix.json)。首轮9个正常实时DOM样本为28.17–293.43ms，见[逐次记录](us7-inventory-latency.json)。从另一位买家的真实订单HTTP201返回至当前页面可见remaining19计时，单次上限1s；记录提交完成观测时间、DOM观测时间及订单数据库业务createdAt。后者来自固定夹具业务时钟，不用于延迟计算，不冒充精确提交时刻。

200会话持续负载与p95仍由T131/T132执行，不能用本阶段少量浏览器样本代替。
