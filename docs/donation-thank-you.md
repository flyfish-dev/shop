# 打赏感谢通知

## 行为

- 自由打赏和 Git 仓库打赏开通商品在服务端确认实付金额大于 0 后，记录感谢任务；未付款、零元抵扣和普通商品不触发。
- 通知与付款确认在同一数据库事务内落库，提交成功后立即唤醒分发器。SMTP 和用户资料查询不进入支付事务，也不改变订单交付规则。
- 从认证服务取得打赏者当前资料，使用其中的昵称和邮箱。邮件包含实付金额、币种、项目名称、订单号与订单入口，提供 UTF-8 HTML 和纯文本两种正文。
- 使用下单时的 `paymentLocale` 选择中英文模板，旧订单未保存语言时默认中文。不扫描历史订单，不批量补发旧打赏。
- 邮箱为空或不合法时，创建一张系统感谢跟进工单。管理员未读数为 1，发送管理员工单通知；用户不会收到“你提交了工单”的通知。工单归属打赏者，便于后续回复与联系。
- 邮件或用户信息读取失败会指数退避重试；第 5 次仍失败时转人工跟进。资料读取故障不会被误判为用户没有邮箱。工单创建失败则继续保留任务重试。

## 持久化与资源边界

新表 `shop_donation_thank_you` 由 `schema/50-shop-donation-thanks.sql` 幂等初始化。无需迁移或修改旧订单。

- `order_no` 唯一约束防止重复支付回调生成多条任务。
- 每批最多 20 条、同时最多处理 2 条；使用状态和时间索引，不遍历订单或用户全表。
- 5 分钟处理租约及随机领取标识防止并发执行、旧任务覆盖新结果；服务重启后恢复到期任务。
- 工单编号固定为 `DT` 加订单数据库 ID，重试会复用已有工单。
- 每 5 秒扫描一次，作为进程重启或提交后唤醒信号丢失时的兜底；`shop.donation.thank-you.scan-delay` 可调整扫描间隔（毫秒）。
- 关闭服务时取消分发订阅。SMTP 连接、读写超时默认为 10 秒，可通过标准 Spring Mail 配置覆盖。
- 任务表不复制用户邮箱；日志和错误字段仅记录订单号、次数、异常类型，不输出 SMTP 错误正文或收件人。

重复回调与并发消费不会重复通知。与所有普通 SMTP 发送一样，若邮件服务器已经接收邮件、进程却在写入成功状态前崩溃，恢复时仍可能再次发送；SMTP 不提供端到端 exactly-once，不能承诺消除这个崩溃窗口。

## 配置与发布

复用小铺现有 `spring.mail.*` 配置。发件地址优先使用 `support.notification.mail.from`（对应 `SUPPORT_NOTIFICATION_MAIL_FROM` / `FROM`），为空时回退 `spring.mail.username`。邮件订单入口使用 `support.notification.portal-base-url`，生产环境必须显式配置为自己的 HTTPS 站点地址。

感谢邮件不受工单提醒开关控制；自动工单的外部管理员提醒仍遵循现有工单通知配置。SMTP 未配置不会被当作发送成功，而会重试并最终转工单。

本功能是 shop 后端变更，无需单独发布前端。部署前按项目流程备份数据库并构建 shop；不要混入工作区内其他尚未准备好的改动。部署后核对新表存在、三个服务健康，并使用受控测试账号完成一笔打赏，分别验证有邮箱与无邮箱路径。

任务检查（不要打印真实邮箱或 SMTP 凭据）：

```sql
SELECT order_no, state, attempts, next_attempt_time, ticket_no, completed_time, last_error
FROM shop_donation_thank_you ORDER BY id DESC LIMIT 20;
```

## 本地回归

```bash
./mvnw -pl flyfish-shop/flyfish-shop-app -am \
  -Dtest='Donation*Test,ShopOrderServiceImplTest,SupportTicket*Test' \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

覆盖真实 H2 SQL/事务、并发入队去重、领取租约、到期恢复、支付确认、无效签名、HTML 转义、邮件失败、无邮箱工单以及管理员提醒。测试仅使用模拟 SMTP，不向客户发送邮件。模板预览生成在 `flyfish-shop/flyfish-shop-app/target/donation-mail-preview/`。
