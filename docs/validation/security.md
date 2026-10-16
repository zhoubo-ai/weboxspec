# 全接口权限与输入边界

2026-10-16，执行者Codex。T125已完成。后端矩阵及相关29项集成测试通过；三浏览器×三尺寸18项新增安全测试通过。

`AuthorizationMatrixIT`从OpenAPI枚举全部32个操作，对未登录、Employee、Admin执行96次请求。私有接口未登录拒绝，员工访问Admin接口拒绝；全部写入（包括登录/注册/图片上传）缺CSRF均拒绝。已允许角色使用无效业务输入检查是否进入处理器；这部分明确只证明权限边界，正常成功业务由各故事的真实MySQL集成与浏览器流程覆盖。结果保存于`security-authorization-matrix.json`，没有会话或凭据。

后端同时运行AuthContractIT、PreferencesContractIT、OrderContractIT、RecommendationContractIT、InventoryStreamIT，共29项集成检查及19项单元检查通过（`.local/logs/security-backend-second.log`）。涵盖128码点密码原样验证、Argon2、登录轮换session和CSRF、8小时空闲过期、跨账号quote/address/order和偏好隔离、未知字段/超长Unicode、流不续期/注销断流、推荐CSRF与过期会话。SQL式搜索按文字查找；userId查询参数不切换本人数据。编译期的测试辅助方法同名冲突已修正，首轮失败日志保留。

该阶段不替代后续部署、限流、维护、备份恢复或负载验证。

`.local/logs/security-accessibility-e2e-second.log`共36项通过，其中18项安全与18项键盘/响应式检查。此轮真实E2E服务启用了认证/写入/AI限流；没有绕过业务鉴权。浏览器安全检查涵盖跨账号订单/报价/地址、跨会话CSRF、Unicode/HTML/SQL式地址安全显示、HttpOnly/SameSite、禁止跨站读取、两标签注销传播，以及旧cookie和库存流访问被拒绝。
