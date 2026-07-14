# Security Policy

## Supported Version

安全修复以 `main` 分支的最新代码为准。当前不维护历史版本分支，请先确认问题在最新提交中仍可复现。

## Reporting a Vulnerability

请通过仓库的 [GitHub Private Vulnerability Reporting](https://github.com/flyfish-dev/shop/security/advisories/new) 私下提交安全问题，不要在 Issue、Discussion、提交信息或公开聊天中披露可利用细节、真实凭据或生产数据。

报告建议包含：

- 受影响的模块、接口和提交版本
- 最小化复现步骤与必要的请求样例
- 影响范围、前置条件和已知缓解措施
- 不包含真实密钥、个人信息或生产数据库的证明材料

维护者确认后会评估影响、准备修复并协调披露时间。安全修复发布前，请勿公开漏洞细节。

## Deployment Responsibility

本仓库中的默认配置仅用于本地体验。公开部署前必须更换数据库密码和 `USER_JWT_SECRET`，使用 HTTPS，关闭未配置的外部通道，并将密钥存放在专用密钥管理服务中。
