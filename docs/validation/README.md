# 验证证据索引

各文档区分实际执行、历史失败、复测及外部依赖。生成测试文件或静态检查通过不等于应用验收通过。最终统一状态以[验收台账](../../specs/001-webox-meal-ordering/acceptance.md)和[tasks.md](../../specs/001-webox-meal-ordering/tasks.md)为准；最终回归、正式负载与真实模型复核均已通过；真人任务仍待参与者。

| 范围 | 实际记录 |
| --- | --- |
| 工具链、独立MySQL和基础契约 | [environment.md](environment.md)、[foundation.md](foundation.md) |
| Checkout / Review正常服务复测 | [checkout-review.md](checkout-review.md) |
| 身份/菜单/购物车/订餐与规则 | [us1.md](us1.md)、[us2.md](us2.md)、[us3.md](us3.md)、[us4.md](us4.md) |
| 偏好、运营、图片与九菜seed | [us5.md](us5.md)、[us6.md](us6.md) |
| 事务outbox与库存SSE恢复 | [us7.md](us7.md) |
| 真实OpenRouter功能和独立AI性能 | [us8.md](us8.md)、[model-environment.md](model-environment.md) |
| 手算报表、事务快照、图表 | [us9.md](us9.md) |
| 全32操作权限矩阵和浏览器安全 | [security.md](security.md) |
| 三浏览器/三尺寸与键盘和英文 | [accessibility-language.md](accessibility-language.md) |
| 生产HTTPS代理、Cookie、限流、指标 | [operations.md](operations.md) |
| 实际应用/MySQL重启和隔离备份恢复 | [persistence.md](persistence.md) |
| 200会话负载与用户可见计时 | [performance.md](performance.md) |
| 全量来源、要求、任务与AC映射 | [traceability.md](traceability.md) |
| 干净构建与最终完整回归 | [release.md](release.md) |
| 真人首次使用和管理员任务 | [user-acceptance.md](user-acceptance.md)，尚未执行 |

主入口为`./scripts/backend.sh verify`及`./scripts/frontend.sh run test:e2e`；特殊入口为`-Pproxy verify`、`-Ppersistence verify`、`-Pllm-live verify`、`-Pload gatling:test`、`run test:e2e:live`。先构建frontend/dist再运行proxy和load。命令从仓库根执行，完整启动见[quickstart.md](../../specs/001-webox-meal-ordering/quickstart.md)。

原始命令日志在忽略的`.local/logs/`，JUnit/Gatling/浏览器产物在backend/target或frontend/test-results中。公开JSON报告只保存无凭据的结果与计时；含session、数据库备份和原始对话的资料留在本地私有目录。失败产物不被成功复测覆盖。
