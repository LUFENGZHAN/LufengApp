# LufengApp · 在线聊天系统

前端 + 后端 + 数据库三层架构，Web（Vue 3 + Vite）与 App（Android 原生 Java + XML）复用同一套 REST + WebSocket 接口。

```
LufengApp/
├── backend/       Java 21 + Spring Boot 4.1（仅暴露 REST /api/v1/**、静态资源 /static/** 与 WS /ws）
├── web-app/       Vue 3 + Vite + TypeScript（已实施，dev 通过 Vite 代理转发 /api、/static 与 /ws）
├── android-app/   Android 原生 Java + XML（待实施）
├── database/      docker-compose + 初始化脚本（MySQL 8 + Redis 7）
└── docs/          架构改造方案
```

## 一、启动顺序

```bash
# 1. 数据库层（首次启动自动建表 + 灌演示数据；MySQL 映射宿主机 3307，避开常见的 3306 占用）
cd database && docker compose up -d
docker compose ps            # 等 mysql / redis 都 healthy

# 2. 后端（推荐 Docker；backend 服务定义在 database/docker-compose.yml，构建上下文 ../backend）
cd database && docker compose up -d --build backend
# 健康检查：http://127.0.0.1:3001/api/v1/health
# 日志：docker compose logs -f backend ｜ 改代码后重建：docker compose up -d --build backend
# 若想本地裸跑（不用容器）：cd backend && ./gradlew bootRun --args="--server.port=3001"

# 3. Web 前端
cd web-app && npm install && npm run dev     # http://127.0.0.1:8888
# 构建：npm run build（产物 dist/）；类型检查：npm run typecheck

# 4. 桌面端（Electron，可选）：打包/运行见下「十一、桌面端（Electron）」

# 5. Android：Android Studio 打开 android-app/
```

> 后端默认连接 `127.0.0.1:3306/lufeng_chat`（lufeng / lufeng123456）与 `127.0.0.1:6379`，
> 与 `database/docker-compose.yml` 中的配置一致。生产环境用环境变量覆盖，见下表。

## 二、环境变量

| 变量 | 默认值 | 说明 |
|---|---|---|
| `SPRING_DATASOURCE_URL` | `jdbc:mysql://127.0.0.1:3306/lufeng_chat?...` | 数据库地址 |
| `SPRING_DATASOURCE_USERNAME` / `_PASSWORD` | `lufeng` / `lufeng123456` | 数据库账号 |
| `SPRING_DATA_REDIS_HOST` / `_PORT` | `127.0.0.1` / `6379` | Redis 地址 |
| `JWT_SECRET` | 开发用占位串 | **生产必须覆盖**，长度 ≥ 32 字节 |
| `JWT_ACCESS_TTL` / `JWT_REFRESH_TTL` | `7200s` / `30d` | 凭证有效期 |
| `WS_CLUSTER_ENABLED` | `false` | 多实例部署时置 true，启用跨节点 WS 投递 |
| `SERVER_PORT` | `3001` | 服务端口 |
| `MYSQL_HOST_PORT` | `3307` | MySQL 容器映射到宿主机的端口；宿主机 3306 空闲时可改回 3306 |
| `SPRING_DATASOURCE_URL` | `jdbc:mysql://127.0.0.1:3307/lufeng_chat?...` | 直接覆盖数据源，优先级最高 |

## 三、接口速查

统一响应：`{ code, message, data, requestId, timestamp }`，`code = 0` 为成功。
鉴权头：`Authorization: Bearer <accessToken>`，另需 `X-Device-Id`、`X-Client-Type: web|android`。

### 认证 `/api/v1/auth`

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/register` | 注册（账号 4-64 位，密码 6-32 位） |
| POST | `/login` | 登录，返回 accessToken / refreshToken / user |
| POST | `/refresh` | 刷新令牌（轮换，旧 refresh 立即失效） |
| POST | `/logout` | 登出当前设备 |
| GET | `/me` | 身份校验 |
| POST | `/password` | 改密码（所有设备下线） |

### 用户 `/api/v1/users`、好友 `/api/v1/friends`

| 方法 | 路径 | 说明 |
|---|---|---|
| GET/PUT | `/users/me` | 查询 / 更新个人资料 |
| GET | `/users/search?keyword=` | 搜索用户 |
| POST | `/friends/requests` | 发送好友申请 |
| GET | `/friends/requests?direction=in\|out` | 申请列表 |
| PUT | `/friends/requests/{id}` | `{action: accept\|reject}` |
| GET / DELETE | `/friends`、`/friends/{friendId}` | 好友列表 / 删除 |

### 会话 `/api/v1/conversations`、消息 `/api/v1/messages`

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/conversations/private` | 创建单聊会话（幂等） |
| GET | `/conversations?page&size` | 会话列表（含未读与摘要） |
| POST | `/conversations/{id}/read` | 已读上报 |
| POST | `/messages` | 发送消息（clientMsgId 幂等） |
| GET | `/conversations/{id}/messages?cursor&size` | 历史消息游标分页 |
| POST | `/messages/sync` | 离线补拉（按 lastAckSeq） |
| POST | `/messages/{msgNo}/recall` | 撤回（2 分钟内） |

## 四、WebSocket

- 地址：`ws://{host}/ws?token=<accessToken>&deviceId=<deviceId>`（也支持 `Authorization` 头与子协议 `access_token.<token>`）
- 鉴权失败返回 401，客户端应先 refresh 再重连
- 信封：`{ type, seq, ts, data }`

| type | 方向 | 说明 |
|---|---|---|
| `connected` | 下行 | 握手成功 |
| `chat` | 双向 | 消息 |
| `ack` | 下行 | 上行消息落库确认（含 msgNo / seq） |
| `read` | 双向 | 已读回执 |
| `typing` | 双向 | 正在输入 |
| `notify` | 下行 | 好友申请、撤回、上下线 |
| `ping` / `pong` | 双向 | 心跳（客户端 30s，服务端 90s 超时踢除） |
| `kick` | 下行 | 强制下线 |

重连策略：指数退避 1s → 30s（带随机抖动），重连成功后调用 `POST /messages/sync` 补齐离线消息，
再 flush 本地待发队列（复用原 `clientMsgId`，服务端幂等）。

## 五、演示账号

`zhangsan` / `lisi` / `wangwu`，密码均为 `123456`（`database/init/02-seed.sql`，生产请删除）。

## 六、端到端验证

```bash
# 注册 + 登录
curl -X POST http://127.0.0.1:3001/api/v1/auth/login -H "Content-Type: application/json" \
  -d '{"account":"zhangsan","password":"123456","deviceId":"web-1","deviceType":"web"}'

# 用返回的 accessToken 建会话、发消息
curl -X POST http://127.0.0.1:3001/api/v1/conversations/private \
  -H "Authorization: Bearer <token>" -H "Content-Type: application/json" -d '{"targetUserId":2}'

curl -X POST http://127.0.0.1:3001/api/v1/messages \
  -H "Authorization: Bearer <token>" -H "Content-Type: application/json" \
  -d '{"clientMsgId":"11111111-1111-1111-1111-111111111111","conversationId":1,"msgType":1,"content":"你好"}'
```

## 七、关键设计

- **幂等**：`uk(conversation_id, client_msg_id)` 唯一索引兜底，重复提交回放原消息（`duplicated=true`）
- **序号**：会话内 `seq` 由 Redis INCR 分配，DB 唯一索引兜底，Redis 挂了降级到 `seq_allocator`
- **离线消息**：不单独建表，用 `conversation_member.last_ack_seq` 与 `message.seq` 计算
- **JWT 失效**：`user_account.token_version` + jti 黑名单，支持改密码/登出即时生效
- **多端在线**：Redis Hash `ws:online:{userId}`（心跳续期） + 本机 session 表，跨节点走 Redis Pub/Sub
- **客户端发送**：Web 端 WS 优先（5s 未 ACK 降级 HTTP）、App 端 HTTP 优先，两条通道共用 `clientMsgId`
- **前端不依赖 router**：store 通过 `utils/navigator` 反转跳转，避免打包器循环引用

## 八、Web 前端要点

详见 [`web-app/README.md`](web-app/README.md)：

- 登录成功即建连；心跳 25s + pong 看门狗 10s；重连指数退避 1→30s 带抖动，重连后 `messages/sync` 补拉
- 收到 `code=10003` 静默刷新（并发共享同一刷新 Promise）后自动重放原请求
- 消息三态：乐观 `sending` → ACK/HTTP 收敛 → `failed` 可重试（复用 `clientMsgId`）
- 会话摘要只在 `seq` 更大时覆盖；已读上报用 `GREATEST` 防位点回退

### 一键冒烟（REST + WebSocket 全链路，Node ≥ 22）

```bash
cd database && docker compose up -d          # 数据层
cd ../backend && ./gradlew bootRun           # 后端，默认 3001
SMOKE_BASE=http://127.0.0.1:3001 node database/scripts/ws-smoke.mjs
```

依次验证：登录 → WS 握手 → 发消息 → 对端实时收到 → 幂等重发（duplicated=true）→ 历史消息 → 心跳 pong。

## 九、常见故障排查

业务接口统一返回 `code=10007 服务异常`，而 `/api/v1/health` 正常时，说明异常出在查库链路上。

| 现象 | 根因 | 处理 |
|---|---|---|
| 所有查库接口 10007，`健康检查 200` | JDBC URL 写了 `characterEncoding=utf8mb4` → `Unsupported character encoding 'utf8mb4'` | JDBC 的该参数只能写 Java 字符集名：**`UTF-8`** |
| 同上，且连接的库是空的/别人的 | 宿主机 3306 被本机 MySQL 或其他容器占用，`lufeng-mysql` 没拿到端口映射，后端连到了别的实例 | `docker ps` 看 Ports 列是否有 `0.0.0.0:3307->3306/tcp`；确认连的是我们的库 |
| 中文显示成 `å¼ ä¸‰` | init 脚本执行时会话字符集是 latin1，中文被双重编码 | 每个 `.sql` 文件都要 `SET NAMES utf8mb4;`（各文件是独立会话）；compose 里已加 `--character-set-client-handshake=FALSE` 兜底 |
| 演示数据灌不进去 | 同上，或 init 只在**首次创建数据卷**时执行 | `docker compose down -v && docker compose up -d` 强制重灌 |

开发环境下 `GlobalExceptionHandler` 会把根因拼进 `message`（dev/local 才显示，生产仍是笼统文案），排查时不必翻日志。

## 十、下一步

- Android 端（Java + XML）：OkHttp + WebSocket、Room 本地消息库、TokenManager 自动刷新、前台保活 service
- 端到端验证：Docker 拉起 MySQL/Redis 后跑通「注册 → 加好友 → 建会话 → 双端收发」全链路

详见 [`docs/架构改造方案.md`](docs/架构改造方案.md)。

## 十一、桌面端（Electron）

同一份前端代码可直接打包为 Windows 桌面程序（`.exe`），业务代码零改动。详见 [`web-app/README.md`](web-app/README.md#桌面端electron)。

**原理**：Electron 主进程在本地起一个零依赖 HTTP/WS 代理服务（`electron/server.cjs`，监听 `127.0.0.1` 随机端口），静态托管 `dist/` 并把 `/api`、`/static`（HTTP）与 `/ws`（WebSocket 升级）反向代理到后端。渲染进程加载本地页面，因此与后端「同源」，彻底规避浏览器 CORS 与 WS 握手跨域问题。

**运行 / 打包**（在 `web-app/` 下）：

```bash
npm install
npm run electron:dev    # 本地调试：先 build 再以 Electron 打开
npm run electron:pack   # 只生成免解包目录 win-unpacked/
npm run electron:build  # 生成安装包与免安装 exe（输出 release-app/）
```

**产物**（`web-app/release-app/`）：

| 文件 | 说明 |
|---|---|
| `lufeng-chat-setup-1.0.0.exe` | NSIS 安装包（可选安装目录、建桌面/开始菜单快捷方式） |
| `lufeng-chat-1.0.0-portable.exe` | 免安装单文件 exe，双击即用 |
| `win-unpacked/麓风聊天.exe` | 解包目录版，适合二次分发或调试 |

> 桌面端依赖后端进程：使用 exe 前请先确保后端已启动（`database/` 目录 `docker compose up -d --build backend`）。
>
> **后端地址可配置**（不写死）：优先级为 环境变量 `LUFENG_BACKEND` → **exe 同目录 `lufeng.config.json`** → `<userData>/lufeng.config.json` → 默认 `http://127.0.0.1:3001`。
> 打包版首次启动会在 exe 同目录生成配置模板，换域名直接改里面的 `backend` 即可（支持 `https://域名`，`/ws` 会自动走 `wss`），也可带网关前缀如 `https://example.com/chat`。详见 [`web-app/README.md`](web-app/README.md#后端地址配置)。
