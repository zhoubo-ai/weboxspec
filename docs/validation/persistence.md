# 重启与隔离备份恢复验证

2026-10-16执行`./scripts/backend.sh -Ppersistence verify`，最终运行`.local/logs/persistence-sixth.log`通过：19项单元测试、1项完整恢复集成场景，场景内4个阶段×3浏览器×2项检查，共24项浏览器检查全部通过。结构化证据见[persistence-initial.json](persistence-initial.json)。运行使用Temurin17.0.20.1、MySQL8.4.11固定镜像和真实打包应用，未连接开发/业务数据库。

阶段分别为基线、正常应用进程退出再启动、实际MySQL容器重启后启动应用、完整DB与媒体备份恢复到另一个带专用标签的空容器。每阶段都核对保存账号可登录、未过期会话可用、注销会话401、偏好/预算/Unicode地址保留、订单配置和5900分历史金额不随现价变化、库存18份、图片可实际解码以及原幂等key仍重放同一订单。

seed再次运行不重置管理员密码哈希、不增加重复菜品、不覆盖已改为3211分的种子菜品或18份库存。restore脚本拒绝再次覆盖已有数据的目标。备份包含MySQL和媒体，SHA-256校验通过；备份原件及含私有会话的测试产物保留在忽略的backend/target/persistence，不放入公开报告。

此前失败均保留：第一次夹具使用不支持的Italian分类；第二次npm exec参数分隔导致误运行常规套件，已停止；第三次发现Docker Desktop重启随机映射端口变化；第四次捕获重启瞬间端口尚未发布；第五次为新增负载代码的Gatling hook编译错误。修复后第六次完整场景通过，未把部分运行算作全量成功。测试读取Docker实际新端口，不依赖Testcontainers缓存地址；真实持久性内容没有重建。

另运行MaintenanceJobsIT与OperationalMetricsIT，`.local/logs/maintenance-first.log`的2项集成及19项单元通过：未消费过期报价、旧已发布outbox与无引用旧媒体清理；消费过的报价、订单/成功key、未发布事件、近期文件及已注册图片保留。具体保留策略、每日备份建议和隔离恢复命令见[运行文档](../operations.md)。

这里只验证正常重启及隔离恢复演练；没有测量或宣称断电、磁盘损坏、地域灾难的RPO/RTO或SLA。端口重启后的旧测试连接池日志不影响已恢复应用；后续测试已关闭该旧连接池和后台session清理，以避免无关告警。

最终干净构建复核：`.local/logs/release-persistence-retest2.log`退出0，19单元+1完整演练及其24项浏览器检查全部通过。最新证据见[persistence-release.json](persistence-release.json)。四个真实应用进程日志均未再出现断连转换异常；旧测试连接池已在MySQL重启前关闭。
