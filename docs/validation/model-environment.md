# OpenRouter 环境验证

日期：2026-10-16。按用户指定改用 OpenRouter，默认模型 `openai/gpt-4.1-mini`。

已执行 `set -a; source .env; set +a` 后运行 `./scripts/backend.sh -Pllm-live verify`：退出 0，1 个测试通过、0 跳过。测试发出两次真实请求，均使用严格 JSON Schema 和 require_parameters：非流式 1799 ms、SSE 1505 ms；返回 provider 为 OpenAI，生成内容均为 `{"status":"ready"}`。逐次模型和 generation ID 见 [probe evidence](openrouter-probe.json)。流式调用同时收到完整 JSON、正常 stop 与 [DONE]。

显式去掉 OPENROUTER_API_KEY 后再次运行：退出 1，提示凭据缺失，未跳过或发送请求。命令日志保存在本地忽略的 `.local/logs/openrouter-live-probe.log` 与 `openrouter-missing-key.log`。缺凭据检查会覆盖 Failsafe 的本轮 XML，因此成功探针另留上述证据。

凭据只保存在权限 0600 的忽略文件 `.env`。前端无 VITE 密钥配置。模型服务为远程托管，上游硬件和权重 digest 不可得；探针未进行暖机，顺序执行、并发1。客户端为 macOS arm64 / Temurin 17.0.20.1。此处的两次时延不是完整推荐、浏览器首卡、20样本p95或业务验收结果。T108–T117 仍待实现。

协议依据：[OpenRouter 结构化输出](https://openrouter.ai/docs/guides/features/structured-outputs)、[流式与错误语义](https://openrouter.ai/docs/api_reference/streaming)。
