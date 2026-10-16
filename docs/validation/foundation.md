# Phase 2 基础能力验证

日期：2026-10-16。环境沿用 [Phase 1](environment.md)。测试使用新建 MySQL 8.4.11 Testcontainers 容器、独立媒体目录和固定 Clock；不使用 Compose 业务库或 H2。

## 实现与实测

- Java 固定枚举映射、整数分乘加/版本溢出检查、Unicode 码点与无效代理字符校验、密码不trim、上海运营日期与10:00/15:00截止时间。前后端共享 `shared/fixtures/values.json`，最大安全整数显示为 `90071992547409.91`。
- 统一英文错误与 requestId、API no-store、未知/重复字段及标量类型转换拒绝、1 MiB JSON请求上限；错误日志仅记错误码、异常类型和requestId。已验证数字不能隐式转成文本。
- V001采用 Spring Session JDBC4.1.1官方MySQL schema；V002保存事务outbox。空库启动迁移成功，重复 migrate 返回0项，官方会话索引保留。事件必须在写事务内创建，回滚不留事件，提交后仍未发布。
- 默认拒绝、公开健康/CSRF入口、Admin边界、持久化WEBOXSESSION、HttpOnly/Lax、本地非Secure、8h空闲配置。会话有效性直接只读查询，验证前后LAST_ACCESS_TIME相同；过期不可用。
- 健康200只含status；关闭测试连接池后返回503安全错误（该负向测试验证实际连接池不可用，未声称停掉MySQL进程）。模型不参与普通健康判定。
- OpenAPI60个schema生成TypeScript类型；客户端同源Cookie/CSRF、AbortSignal、账号切换取消旧请求，内存中保存CSRF和Query状态。员工/Console分别提供英文导航与响应式基础布局；功能占位不冒充业务成功。
- 浏览器独立后端启动器创建临时MySQL与媒体目录、生成短期夹具密钥、等待真实health；测试夹具和控制端点只在test classpath，不打入生产jar。夹具只创建身份和数据库会话，业务用户实体在US1补齐。

## 验证入口与结果

| 入口 | 结果 |
| --- | --- |
| `./scripts/backend.sh verify` | 6个单元测试 + 10个真实集成测试，全部通过、0跳过 |
| `./scripts/frontend.sh run test:unit -- --run` | 7通过 |
| `./scripts/frontend.sh run build` | TypeScript与生产构建通过 |
| `./scripts/frontend.sh run test:e2e` | 18通过；三浏览器×三尺寸，包含真实API、持久会话、刷新和Console布局 |
| `./scripts/frontend.sh audit --omit=dev` | 生产依赖0告警；React Router更新至7.18.4并锁定 |
| OpenAPI/来源静态检查 | 60schema、32HTTP操作、81未执行AC、118原文引用完整；原始PRD摘要未变 |
| 凭据扫描 | backend/src、frontend/src、frontend/tests、specs、docs、scripts中无OpenRouter密钥值 |

API客户端和值规则先运行缺少行为的测试，观察失败后实现并通过。HTTP最初遇到Jackson3配置与测试JDBC URL拼接错误，已修复；这些环境/配置失败不计作业务测试红灯。之后实际捕获数字转文本的错误行为，修复后通过。MockMvc失败详情自动打印已关闭，避免失败时输出会话/CSRF夹具；本轮最终应用日志未出现密码、会话Cookie或私有测试内容。

本地详细日志在忽略目录 `.local/logs/foundation-*.log`，后端报告在 `backend/target/surefire-reports/` 与 `failsafe-reports/`，浏览器报告在 `frontend/playwright-report/`。这些记录只完成基础能力，不代表账号故事、订单、推荐、负载或真人验收。81项AC保持未执行。

依赖依据：[Spring CSRF](https://docs.spring.io/spring-security/reference/servlet/exploits/csrf.html)、[OpenAPI TypeScript CLI](https://openapi-ts.dev/cli)、[React Router7.18.4](https://github.com/remix-run/react-router/releases/tag/react-router%407.18.4)。
