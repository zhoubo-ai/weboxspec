# 生产配置与运行保护

2026-10-16，执行者Codex。T127、T128已完成。T129的定时清理已验证，备份/恢复脚本的实机演练随T130继续。

- `.local/logs/operations-red.log`保留实施前行为失败：prod Cookie不Secure、认证/写入/AI请求未限流。
- `.local/logs/operations-first.log`：生产Cookie与指标权限、两组限流测试及订单并发/推荐流/非Web种子回归，共20项集成和19项单元通过。缺CSRF先拒绝；超出桶返回429和Retry-After；客户端伪造X-Forwarded-For不换桶；账号写入与AI分别限流。
- `.local/logs/maintenance-red.log`：实施前过期未消费报价未清理，真实失败；同次OperationalMetricsIT通过。
- `.local/logs/maintenance-first.log`：维护与指标两项集成通过。已提交订单与幂等重放分开计数，固定冲突码、outbox积压、连接与Caffeine指标可读，业务指标标签不包含私人标识。清理保留边界覆盖已消费报价、近期过期/有效报价、未发布/近期发布事件、注册媒体、新临时文件和符号链接，已成功key仍能重放。
- `.local/logs/production-proxy-first.log`：`-Pproxy verify`通过，真实Nginx1.30.6、临时自签TLS、真实生产静态包和隔离MySQL。验证TLS/CSP/HSTS/Secure Cookie、API及指标鉴权、SPA回退、缺失资源404、库存GET SSE与推荐POST SSE。库存首个快照35ms；结果见`production-proxy.json`。测试CA仅在客户端信任存储中加载，没有关闭证书或主机名验证。
- `.local/logs/security-accessibility-e2e-second.log`的36项浏览器检查使用开启限流的实际E2E服务，业务流程与注销恢复通过。

生产部署步骤、参数、单实例边界、日志和备份说明见[operations.md](../operations.md)。此记录不表示已经公开部署，也不替代最终负载和恢复演练。
