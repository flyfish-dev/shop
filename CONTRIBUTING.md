# Contributing

感谢参与飞鱼小铺。提交改动前，请先说明要解决的问题，并确保实现符合现有模块边界和开源交付边界。

## Development Flow

1. 从最新 `main` 创建功能分支。
2. 先用测试或可重复命令确认问题，再进行最小范围修改。
3. 后端公共契约放入对应 `*-api` 模块，业务实现保留在 `*-app` 模块。
4. 认证、小铺和低代码实例不得互相引入具体实现；共享用户态通过认证 API 和客户端完成。
5. 前端复杂逻辑应放入 hooks、组件或 API 层，避免继续堆入页面视图。
6. 为行为变化补充与风险匹配的单元测试、接口测试或浏览器验证。

## Verification

```bash
./mvnw test

cd web
npm ci
npm run build
npm run test:architecture
npm run test:build-artifacts
npm run test:route-smoke
```

涉及页面交互时，请额外验证桌面端和移动端。涉及 Docker 配置时，请执行：

```bash
docker compose -f deploy/docker/docker-compose.native.yml config
```

## Security and Data

- 不得提交 `.env`、数据库、日志、证书、密钥、真实 Token、生产截图或用户数据。
- 测试夹具必须使用明显的示例域名、示例账号和不可用凭据。
- 商业授权算法、根密钥、专有签发协议和内部运维客户端不属于本仓库贡献范围。
- 安全漏洞请按 [SECURITY.md](SECURITY.md) 私下报告。

## Commit and Pull Request

提交信息应简洁说明意图，例如 `feat(shop): add sku pricing`、`fix(auth): preserve oauth redirect`。Pull Request 需要说明行为变化、验证命令、配置影响和截图，并保持提交中不包含无关格式化或生成产物。
