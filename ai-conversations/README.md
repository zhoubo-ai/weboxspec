# AI 编程对话公开副本

本目录按项目所有者要求公开现有的两份 Markdown 对话导出。对话顺序、消息标题、工具记录和行数保留；检测到的 API Key、CSRF Token、会话 Cookie 及凭据形式的字面值替换为 `[REDACTED:类型]`。

| 文件 | 行数 | 脱敏情况 |
| --- | ---: | --- |
| [会话 01a142c2](codex-session-01a142c2-6f17-73b1-9dc9-4a17e788e669.md) | 5,119 | 未检测到需要替换的凭据，文件内容保持原样 |
| [会话 01a14323](codex-session-01a14323-2627-79c1-8e6a-3d7653636198.md) | 95,123 | 2 处 OpenRouter Key、3 处 CSRF Token、3 处会话 Cookie、7 处凭据字面值已隐藏 |

较大文件约 8.63 MiB；GitHub 在线预览受限时，可下载或克隆仓库后查看。大小、原件与公开副本的 SHA-256、脱敏数量见 [manifest.public.json](manifest.public.json)。

这两份文件是现有 Markdown 导出的公开副本，不是重新生成的对话摘要，也不宣称它们是字节不变的原生 JSONL 导出或包含上传之后的新对话。涉及 PRD 对“完整原始对话”的验收时，应单独核验未修改的原生导出，不能用脱敏副本替代。

修改前的两份文件已完整备份到本机 `.local/private-conversations/20261016T165054Z/`，该目录不会上传。请保留这份本地备份。

`.gitignore` 只允许本目录中已核验的两份对话、说明和公开清单进入版本控制。以后由 `scripts/export-conversations.py` 生成的原始 JSONL、私有 `manifest.json` 及其他新导出仍默认忽略，公开前需单独检查。
