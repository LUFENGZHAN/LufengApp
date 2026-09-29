# database 模块

数据库层独立成模块，只提供「容器编排 + 初始化脚本」，后端通过 JDBC 访问，前端/App 永不直连。

## 启动

```bash
docker compose up -d      # 首次启动自动执行 init/*.sql（按文件名字典序）
docker compose ps         # 等待 mysql / redis 状态为 healthy
docker compose down       # 停止（保留数据卷）
docker compose down -v    # 停止并清空数据（下次启动会重新初始化）
```

> **端口冲突**：宿主机 3306 常常已经被本机 MySQL 服务或其他容器占用。这时 `lufeng-mysql` 会停在 `Created`
> 状态、或启动后没有任何端口映射（`docker ps` 里 Ports 列只有 `3306/tcp` 而不是 `0.0.0.0:3307->3306/tcp`），
> 于是后端会连到「别人的库」上，业务接口全部返回 `code=10007 服务异常`。
> 本模块默认映射到宿主机 **3307**，正是为了避开这个坑。确实想用 3306：
> `MYSQL_HOST_PORT=3306 docker compose up -d`（需要先停掉占用 3306 的进程/容器）。

## 连接信息

| 服务 | 地址 | 账号 |
|---|---|---|
| MySQL 8 | `127.0.0.1:3307` / 库名 `lufeng_chat` | `lufeng / lufeng123456`（业务）、`root / root123456` |
| Redis 7 | `127.0.0.1:6379` | 无密码（AOF 已开启） |
| Adminer | http://localhost:8080 | server 填 `mysql` |

连接自检：

```bash
docker exec lufeng-mysql mysql -uroot -proot123456 -N -e "select id,user_no,account,left(password_hash,7) from lufeng_chat.user_account;"
# 期望：1 U00000001 zhangsan $2a$10$ ...
```

后端 JDBC（与 `backend/src/main/resources/application.yml` 默认值一致）：

```
jdbc:mysql://127.0.0.1:3307/lufeng_chat?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai&rewriteBatchedStatements=true
```

> **踩坑**：JDBC 的 `characterEncoding` 只能写 Java 字符集名（`UTF-8`），写 MySQL 的 `utf8mb4` 会抛
> `java.sql.SQLException: Unsupported character encoding 'utf8mb4'`，表现为所有查库接口返回
> `code=10007 服务异常`（而 `/api/v1/health` 正常，因为它不碰数据库）。

## 脚本说明

| 文件 | 内容 |
|---|---|
| `init/01-schema.sql` | 12 张核心表：`user_account`、`user_profile`、`user_device_session`、`friend`、`friend_request`、`conversation`、`conversation_member`、`message`、`message_receipt`、`idempotency_record`、`seq_allocator`、`chat_group`（二期） |
| `init/02-seed.sql` | 演示数据：`zhangsan / lisi / wangwu`，密码均为 `123456`（BCrypt 哈希）。**生产部署请删除本文件** |

## 端到端冒烟

`scripts/ws-smoke.mjs` 验证「登录 → WS 握手 → 发消息 → 对端实时收到 → 幂等重发 → 历史消息 → 心跳」全链路，
依赖 Node ≥ 22（内置 fetch + WebSocket，无需 `npm install`）：

```bash
SMOKE_BASE=http://127.0.0.1:3001 node scripts/ws-smoke.mjs
```

## 设计要点

- 消息查询主链路走 `(conversation_id, seq)` 联合索引，历史消息用 **游标分页**（`seq < cursor`），深度翻页性能恒定。
- 离线消息不单独建表：用 `conversation_member.last_ack_seq` 与 `message.seq` 计算，避免双写不一致。
- 幂等靠唯一索引兜底：`uk(conversation_id, client_msg_id)`、`uk(conversation_id, seq)`。
- 演示账号密码如需更换，用 BCrypt(cost=10) 重新生成 `password_hash` 即可，登录校验走 `BCryptPasswordEncoder.matches`。
