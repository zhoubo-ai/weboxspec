# 运行与维护

当前目标为单实例Spring Boot + 独立MySQL + 同源Nginx HTTPS，生产配置不代表已经对外部署。生产域名、真实证书和主机由部署方提供。完整验证状态见`docs/validation/`及验收台账。

## 构建与部署

1. 使用JDK17、Node24/npm11，执行`./scripts/frontend.sh ci`、`./scripts/frontend.sh run build`、`./scripts/backend.sh verify`。发布构建产物为`frontend/dist/`和`backend/target/webox-api-0.1.0-SNAPSHOT.jar`。真实模型另按README的live入口验证。
2. 创建操作系统专用用户webox，将静态文件安装到`/srv/webox/frontend/`、JAR安装到`/srv/webox/webox-api.jar`，创建该用户拥有的`/srv/webox/media/`与`/srv/webox/runtime/`。媒体必须持久化，不能随发布目录删除。
3. MySQL8.4使用独立进程和持久卷。创建生产专用schema和非root账号，授权仅限该schema。应用需SELECT/INSERT/UPDATE/DELETE；启动时Flyway还需CREATE/ALTER/INDEX/REFERENCES/DROP等迁移权限，禁止全局授权。开发、测试、恢复目标各自独立，不能共用生产schema。
4. 以0600权限保存`/etc/webox/application.env`，配置DB_URL（含UTC连接参数）、DB_USERNAME、DB_PASSWORD、MEDIA_ROOT、OPENROUTER_API_KEY、OPENROUTER_MODEL。不要把凭据写入命令行、前端、版本库、日志或报告。该环境文件采用systemd EnvironmentFile格式；不要直接把含任意shell语法的文件当脚本执行。
5. 安装`infra/webox.service`，确认`/usr/bin/java`为JDK17，运行prod profile。默认仅监听127.0.0.1:8080，Cookie强制Secure/HttpOnly/SameSite=Lax。容器化时只能在私有网络监听0.0.0.0，并把TRUSTED_PROXY_REGEX限定为实际代理地址；不公开应用和MySQL端口。
6. Nginx验证版本为1.30.6，容器镜像固定`nginx:1.30.6-alpine@sha256:970364228b1e4f932abc22d321932816782cca8a20b85a77552a7cfe9acfb3b5`。安装`infra/nginx.conf`为conf.d配置，把server_name改为真实域名，提供`/etc/nginx/tls/fullchain.pem`和`privkey.pem`，执行`nginx -t`后启动或reload。HTTP重定向HTTPS；静态SPA回退、带hash资源一年缓存、index重新验证、API不缓存；TLS1.2/1.3、HSTS、CSP和点击劫持保护已配置。
7. `/api/`关闭响应缓冲和压缩，读超时65秒大于15秒心跳，保留同源Cookie和CSRF；代理不自动重放写请求。覆盖客户端X-Forwarded-For，只有可信代理可决定真实地址。依据[Nginx代理文档](https://nginx.org/en/docs/http/ngx_http_proxy_module.html)及[HTTPS文档](https://nginx.org/en/docs/http/configuring_https_servers.html)。

采用单实例是当前库存SSE广播和内存限流的明确边界。业务库存、报价、订单幂等、会话和偏好均在MySQL；Caffeine只缓存菜品版本化静态资料。扩展多个应用节点前必须引入跨节点事件广播和共享限流，不能直接增加副本。

## 健康、指标与故障

公开`GET /api/v1/health`仅给UP状态；生产`/actuator/health`及`/actuator/metrics/**`由Admin会话保护，未公开env/configprops等端点。完整接口仍要求登录、角色及写请求CSRF。根据[Spring Boot端点文档](https://docs.spring.io/spring-boot/reference/actuator/endpoints.html)显式控制暴露和权限。

聚合指标包括`webox.orders`的created/replayed、`webox.conflicts`固定错误码、`webox.order.lock.retries`、`webox.outbox.pending`、`webox.inventory.active`、按固定原因分类的`webox.inventory.closed`、`webox.ai.active`、`webox.ai.first_card`和`webox.ai.finished`；Caffeine的cache.*命中/未命中已接入，Spring提供HTTP/JVM/连接池指标。没有用户ID、session、地址、提示词或generation ID标签。outbox积压读取失败时为-1，不能当0处理。请求返回X-Request-Id；业务日志仅记录请求ID、错误码、异常类型。Nginx关闭包含URL查询的access log。

建议监控5xx、连接池等待、内存、磁盘、持续outbox积压、库存/AI活跃连接和延迟，并结合本部署基线设置告警。AI不可用时返回明确错误，普通菜单/报价/订单不依赖AI。不得以清理未发布事件或订单来消除积压。锁冲突最多三次尝试，客户端网络不确定时继续使用原幂等key。

## 有界限流

限流在认证与CSRF校验之后执行，采用单调时钟令牌桶，最多10000个身份桶，空闲30分钟清除。限流返回429/RATE_LIMITED、Retry-After及X-Request-Id。服务器不直接信任客户端X-Forwarded-For。

| Spring属性前缀webox.rate-limit | 默认突发容量 | 每分钟补充 | 范围 |
| --- | ---: | ---: | --- |
| auth-capacity / auth-per-minute | 60 | 120 | 同一真实IP的CSRF获取、注册、登录 |
| global-auth-capacity / global-auth-per-minute | 600 | 1200 | 实例认证总入口，保护新身份桶大量创建 |
| write-capacity / write-per-minute | 120 | 300 | 同一账号的普通写入 |
| ai-capacity / ai-per-minute | 10 | 30 | 同一账号的推荐请求；另有并发1/等待8/总20秒限制 |

注销不受频率桶阻止，仍需合法会话和CSRF。读菜单/库存快照不占写入桶，SSE另有每session3/实例600连接、队列和写超时约束。可通过同名Spring环境变量（如WEBOX_RATE_LIMIT_AUTH_CAPACITY）调整。普通隔离集成测试profile关闭限流以测试业务并发；RateLimitIT显式启用，真实浏览器E2E服务和生产启用。最终200人负载也必须启用，按实测校准并记录。

## 清理

MaintenanceJobs单线程，每小时运行一批，启动默认延迟60秒；每批默认100行（上限1000），数据库事务5秒超时、READ COMMITTED，过期报价父行先锁定并跳过正在锁定的行。

- 只删除已过期超过24小时且**未消费**的报价和其明细；已消费报价、订单、订单明细及成功幂等key永久保留。
- 只删除发布超过7天的outbox记录；未发布记录永不清理，无论重试次数或年龄。
- 媒体按有界1000文件窗口轮转；只删除超过24小时、文件名符合应用生成规则且无media_assets记录的临时/崩溃残留。保留全部注册资产（包括已替换图片）、新文件、符号链接与无关文件。

属性在`webox.maintenance`下：enabled、initial-delay-ms、interval-ms、batch-size、quote-retention-hours、outbox-retention-days、media-retention-hours。测试单独验证这些保留边界；不要把业务日期直接当清理保留期。

## 备份与隔离恢复

备份包含密码哈希、会话和私人业务数据，目录创建为0700、文件0600，放在`.local/backups/`或专门受控磁盘；不得上传到公开仓库。按业务要求至少每日一次并保留滚动副本，将加密副本存到独立存储，定期验证恢复。脚本不擅自删除历史备份或声明未验证RPO/RTO。

`infra/backup.sh`需要显式指定拥有MYSQL_USER/MYSQL_PASSWORD/MYSQL_DATABASE环境的MySQL容器，凭据仅在容器内使用。使用非root专用schema账号；`mysqldump --single-transaction`一致性导出，不进行DDL变更期间执行备份。先导出DB，再归档不可变媒体；维护不删除注册媒体，因此导出时引用的资产仍可拷贝。额外新媒体可以存在于归档，不改变数据库快照。

```bash
./infra/backup.sh --container YOUR_MYSQL_CONTAINER --database webox_prod \
  --media-root /srv/webox/media --output /secure/backups/webox-YYYYMMDD-HHMM
```

成功目录包括database.sql.gz、media.tar.gz、manifest.json（SHA-256、字节数、时间、文件数）。没有完整manifest或校验失败的目录不能恢复。备份脚本绝不把SQL写到终端。

恢复脚本仅接受容器标签`webox.restore-target=true`、名称以`webox_restore_`开头且匹配该容器MYSQL_DATABASE的**空schema**、非root账号及**空媒体目录**。恢复前验证SHA-256，拒绝目录穿越、符号链接、特殊文件、单文件超过5MiB和总媒体超过10GiB的归档。超大部署须另行评估并调整明确上限。不会DROP或覆盖已有schema/媒体。

```bash
./infra/restore.sh --container YOUR_LABELLED_RESTORE_CONTAINER \
  --database webox_restore_drill --media-root /isolated/webox-media \
  --from-backup /secure/backups/webox-YYYYMMDD-HHMM
```

恢复失败保留目标隔离；不要把部分恢复目标切换到生产。成功后用独立端口启动同版本应用，核对账号、偏好、地址、订单/状态/金额、库存、图片和未过期会话；确认注销会话仍无效。正常应用/MySQL重启与备份恢复是两项不同验证，证据由T130单独保存。

## 本地生产代理验证

先构建前端，再运行`./scripts/backend.sh -Pproxy verify`。该测试只使用隔离MySQL、临时测试证书及固定Nginx镜像，检查真实构建SPA、TLS、安全Cookie、API/指标权限、库存SSE即时快照和推荐POST SSE。测试证书不用于外部部署。
