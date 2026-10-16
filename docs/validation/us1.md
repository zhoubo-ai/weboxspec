# US1 账号与会话验证

日期：2026-10-16。T020–T028 完成。使用 JDK17 / Spring Boot4.1.1 / MySQL8.4.11 临时容器、Node24 / React19.2 / Router7.18.4，以及 Chromium156、Firefox157、WebKit27.2。功能账号为每次生成的 UUID 邮箱，覆盖 example.com 与 example.org，不限制企业域名；日期固定2026-10-16（账号功能不依赖配送日期）。自动化执行者：Codex。

## 通过的场景

| AC | 实际输入与结果 | 证据 |
| --- | --- | --- |
| AC-US01-01 | 唯一邮箱注册201、角色Employee，注册后me仍401；显式登录200，刷新后仍返回相同User | AuthContractIT；auth.spec.ts × 9组合 |
| AC-US01-02 | 大小写重复邮箱409 EMAIL_ALREADY_REGISTERED；错误密码401 INVALID_CREDENTIALS；浏览器保留邮箱并显示英文原因 | 同上 |
| AC-US01-03 | 登录更换session ID，旧匿名ID失效，旧CSRF被403拒绝；取新CSRF后退出204，旧会话me401；数据库8h空闲期越界后拒绝访问 | AuthContractIT；auth.spec.ts；auth.test.tsx |
| AC-US01-04（部分） | 匿名被401拒绝，Employee访问Console API403/UI Access denied，Admin成功登录并进入独立Console导航 | AuthContractIT、HealthContractIT、auth.spec.ts；跨用户订单/地址/偏好尚无实体，完整AC保持未执行 |
| AC-US01-05 | 无效/超长邮箱、7字符或无字母/数字及129码点密码400；含emoji的128码点密码可注册登录，截短版本不可登录；SQL只保存Argon2id哈希 | AuthContractIT；浏览器非法密码输入检查 |

退出同时取消旧API请求、清个人Query缓存、调用流清理回调；身份与密码不存localStorage。刷新通过服务器恢复身份，Admin/Employee分别默认到Console/Menu。单元测试检查个人缓存被移除与清理回调执行。

V003提供UUID账号、规范化邮箱二进制唯一约束、角色CHECK、版本和UTC时间。Argon2id参数为19 MiB、2 iterations、parallelism1、16-byte盐、32-byte哈希；未知账号也执行一次dummy哈希匹配，用户反馈固定。

seed profile使用同一主应用，以非Web方式运行管理员初始化并关闭context；真实数据库连续运行两次，第二次不同初始密码未覆盖已存哈希。将已有Employee配置为管理员会失败，不隐式提权；普通Web启动不执行seed。

## 本轮命令

- `./scripts/backend.sh verify`：6单元 + 16真实集成测试通过，0失败/0跳过；其中AuthContractIT5项、SeedApplicationIT1项。
- `./scripts/frontend.sh run test:unit -- --run`：11通过。
- `./scripts/frontend.sh run build`：类型检查与生产构建通过。
- `./scripts/frontend.sh run test:e2e`：36通过（4流程 × 3浏览器 × 3尺寸），31.2秒；包括18项账号流程与18项基础回归。

测试先执行：账号端点未实现时明确返回404而非预期201/400，3个目标行为测试失败；前端3个账号交互测试失败。实现后通过。早先测试控制器意外参与其他测试扫描的问题已通过TestComponent隔离修复，不作为业务红灯证据。

详细本地日志：`.local/logs/us1-auth-red.log`、`us1-auth.log`、`us1-auth-ui-red.log`、`us1-auth-ui.log`、`us1-e2e.log`。生成报告在后端target和前端playwright-report（忽略源码交付）。本报告不宣称跨实体owner隔离、全系统权限或真人验收已完成。

US6阶段补齐Console、订单/地址/偏好所有权的完整验证后，AC-US01-04已通过，见[US6记录](us6.md)。本报告上文为US1当时的阶段结果。
