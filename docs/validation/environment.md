# Phase 1 环境与工程初始化记录

日期：2026-10-16。状态：**Phase 1 T001–T007 完成**。原有沙箱与 DNS 阻塞已解除，下一阶段为基础能力。81 项业务验收仍未执行。

## 已验证的环境

| 项目 | 结果与证据 |
| --- | --- |
| 需求清单 | requirements.md 16/16 PASS，未修改标记 |
| JDK | Eclipse Temurin 17.0.20.1+1，macOS arm64，安装在项目忽略的 `.local/toolchains/`；实际 `java -version` 通过 |
| JDK 校验 | Adoptium 官方 API 返回的 SHA-256：`196d13ba5f10414bef7f6a05a9b3f00edacb18ebacef2b99485db9e2ee18f0e8`，下载文件逐字节核验一致 |
| Node | 官方 Node 24.22.0，SHA-256 `6a9724d9b50a5f22b4816cded441b5a8d9ddae546191a5e5b7efa5a5e2ee26d5`，npm 11.11.1；通过 scripts/frontend.sh 选择，不修改全局安装 |
| Maven Wrapper | Apache Wrapper 3.3.4 only-script、Maven 3.9.9；Maven archive SHA-512 核验通过并固定 SHA-256；来源见 Wrapper NOTICE |
| Docker | Docker Desktop 4.41.2、Engine 28.1.1、arm64；本轮 `docker version` 服务端成功返回 |
| MySQL | 官方 MySQL 8.4.11，镜像固定 `sha256:24989179f58856db763f418010c1169fecc944e3ec1ea7e921572d83eb070f70` |
| MySQL 隔离 | Compose 项目 `weboxspec`、容器 `weboxspec-mysql-1`、卷 `weboxspec_mysql-data`；未修改现有 webox-rehearsal 容器和卷 |
| MySQL 实测 | `up -d --wait` 退出 0；专用 webox 用户查询返回 8.4.11、global/session timezone 均 +00:00、utf8mb4 |
| 前端依赖 | npm install 成功，已生成 package-lock.json v3；项目固定官方 registry |
| 前端构建 | `./scripts/frontend.sh run build` 退出 0，TypeScript 类型检查及 Vite 8.3.4 生产构建通过 |
| 真实凭据 | 首次生成项目本地 .env，权限 0600，忽略版本控制；密码未输出到报告或命令参数 |
| 图片源 | 原始图片 zip 可读，20 个文件且完整性检查通过；T089 尚未导入 |

JDK 来源：[Adoptium API](https://api.adoptium.net/v3/assets/latest/17/hotspot?architecture=aarch64&image_type=jdk&os=mac&vendor=eclipse)。Node 来源：[官方发行校验清单](https://nodejs.org/dist/v24.22.0/SHASUMS256.txt)。Wrapper 来源：[NOTICE](../../backend/.mvn/wrapper/NOTICE.md)。

## 本轮修复

- 旧会话 Docker socket 访问及依赖 DNS 失败已随权限环境变更消除；未再申请提权。
- 应用内置 Node 24 的 macOS 签名不允许加载 Rolldown 原生模块，已替换为官方 Node 24 发行包，真实构建通过。
- 用官方 Apache Wrapper 替换临时 Takari 0.5.5 模板，加入 Maven 分发校验值。
- MySQL 从浮动 8.4 标签锁定实际版本及 digest，使用独立项目卷。
- 原有 Java 8 / Node 25 全局安装保持不变；scripts/backend.sh 与 scripts/frontend.sh 使用项目工具链或显式环境变量。

## 阶段检查结果

| 命令 / 检查 | 结果 |
| --- | --- |
| `./scripts/backend.sh verify` | Java17 / Boot4.1.1 构建通过；真实 MySQL EnvironmentIT 1通过、0失败、0跳过，检查事务回滚、utf8mb4、UTC、Clock与临时媒体隔离 |
| `./scripts/frontend.sh run test:unit -- --run` | 1通过；React 可访问入口渲染 |
| `./scripts/frontend.sh run build` | 类型检查与生产构建通过 |
| `./scripts/frontend.sh run test:e2e` | 9通过，Chromium/Firefox/WebKit × 375/768/1440；检查标题/语言/渲染/无横向溢出和浏览器异常 |
| Playwright 后端隔离负向配置 | 指向开发8080被明确拒绝，退出1 |
| `./scripts/backend.sh -Pllm-live verify`（加载本地环境） | 1测试通过，真实 OpenRouter 两次结构化生成；详情见[模型记录](model-environment.md) |
| 同命令移除 OPENROUTER_API_KEY | 预期退出1，凭据缺失明确失败，未跳过 |

Testcontainers 2.0.5 的 MySQL 镜像兼容检查要求 canonical `mysql@sha256:…`，已修正 tag+digest 的不兼容写法，真实测试通过。Playwright Firefox 157 默认使用本机系统代理，本地 HTTP 请求超时；对隔离的 loopback 测试浏览器配置 `network.proxy.type=0` 后9组全部通过，不修改用户系统代理。Chromium156、Firefox157、WebKit27.2 由锁定的 Playwright1.64.1 下载。

## 验证边界

日志在本地忽略的 `.local/logs/`：backend-setup.log、backend-test-setup.log、frontend-e2e-setup.log、openrouter-live-probe.log、openrouter-missing-key.log。正式生成证据见上述模型记录。此前 Java21 dependency:go-offline 已主动终止，不作为 JDK17 验证证据。旧 Ollama 缺失探针已由用户指定的 OpenRouter 验证替代。

此阶段的浏览器测试只覆盖公共工程入口，未连接业务 API；业务后端隔离启动与身份夹具在 T018 建立。两次模型探针不代表完整推荐/性能验收。没有更改原始 PRD、宪章占位模板或81项验收状态。
