# LufengApp Web 前端

在线聊天系统的 Web 端，Vue 3 + Vite + TypeScript。与 Android 端复用后端同一套 REST / WebSocket 接口、统一响应体与错误码。

## 技术栈

| 能力 | 选型 | 说明 |
|---|---|---|
| 框架 | Vue 3（`<script setup>` + Composition API） | 全 TS，无 Options API |
| 构建 | Vite 6 | dev 走代理，prod 走 Nginx |
| 状态 | Pinia | auth / conversation / message / friend / realtime / ui |
| 路由 | Vue Router 4 | 路由守卫做登录拦截 |
| HTTP | Axios | 统一解包 + 静默刷新 + 重放 |
| 长连接 | 原生 WebSocket | 自封装心跳、退避重连、离线队列 |
| 工具 | dayjs | 会话/消息时间格式化 |

依赖刻意保持在 5 个运行时包，UI 全部手写 CSS，不引入 UI 组件库。

## 启动

```bash
cd web-app
npm install
npm run dev        # http://127.0.0.1:8888
npm run build      # 产物 dist/
npm run typecheck  # vue-tsc 类型检查
```

前置条件：后端已启动（默认 `http://127.0.0.1:3001`），数据库已由 `database/docker-compose.yml` 拉起。

演示账号：`zhangsan` / `lisi`，密码 `123456`。

**注意**：本机若存在 `SERVER_PORT` 环境变量，会覆盖后端的 3001 端口；此时修改 `.env.development` 里的 `VITE_PROXY_TARGET` 保持一致。

> 故障排查：若 `npm run build` 报 `Cannot find module '@rollup/rollup-win32-x64-msvc'` 或
> `@esbuild/win32-x64 could not be found`，说明 npm 跳过了这两个平台二进制可选包，按本地版本补装即可：
> `npm i @rollup/rollup-win32-x64-msvc@<rollup版本> @esbuild/win32-x64@<esbuild版本>`（其他平台替换对应的平台后缀）。

## 桌面端（Electron）

同一份前端代码可直接打包为 Windows 桌面程序（exe），无需改动任何业务代码。

### 运行与打包

```bash
cd web-app
npm install

npm run electron:dev    # 本地调试：先 build 再以 Electron 打开
npm run electron:pack   # 只生成免解包目录 release-app/win-unpacked/
npm run electron:build  # 生成安装包与免安装 exe
```

产物（`web-app/release-app/`）：

| 文件 | 说明 |
| --- | --- |
| `lufeng-chat-setup-1.0.0.exe` | NSIS 安装包（可选安装目录、建桌面/开始菜单快捷方式） |
| `lufeng-chat-1.0.0-portable.exe` | 免安装单文件 exe，双击即用 |
| `win-unpacked/麓风聊天.exe` | 解包目录版，适合二次分发或调试 |

> 打包默认输出到固定目录 `release-app/`，重复执行 `npm run electron:build` 会**覆盖**上一次产物，不需要换目录。
> 若清理旧文件时报 `EBUSY: resource busy or locked, unlink ...app.asar`（Windows Defender 实时防护正扫描占用），
> **不要改成 release-app2/3…**，把项目目录加入 Defender 排除项即可彻底解决：以管理员 PowerShell 执行
> `Add-MpPreference -ExclusionPath 'E:\GitHub\LufengApp\web-app'`。

### 工作原理

```
┌───────────────── Electron 主进程 (electron/main.cjs) ─────────────────┐
│  启动本地服务 (electron/server.cjs)，监听 127.0.0.1 随机端口           │
│    ├─ 静态托管 dist/ 前端产物                                          │
│    ├─ /api、/static  ──HTTP 反代──►  后端地址（可配置，见下）          │
│    └─ /ws           ──WS 升级转发──►  后端地址（可配置，见下）          │
│                                                       │               │
│  BrowserWindow ── 加载 http://127.0.0.1:<随机端口> ◄──┘               │
└───────────────────────────────────────────────────────────────────────┘
```

渲染进程与后端始终「同源」（都在本地随机端口），因此前端的相对路径 `/api`、
`ws://<host>/ws`、`/static` 全部无需改动，也不用开 CORS、不怕 WS 握手被拦。

### 后端地址配置

后端地址**不写死在代码里**，按以下优先级解析（先命中先用）：

| 优先级 | 来源 | 说明 |
| --- | --- | --- |
| 1 | 环境变量 `LUFENG_BACKEND` | 开发/临时覆盖，如 `LUFENG_BACKEND=http://127.0.0.1:3002 npm run electron:dev` |
| 2 | **exe 同目录 `lufeng.config.json`** | 换域名就改这里，改完重启程序生效 |
| 3 | `<userData>/lufeng.config.json` | 安装目录不可写时的兜底位置 |
| 4 | 内置默认值 `http://127.0.0.1:3001` | 什么都没配时使用 |

打包版**首次启动**会在 exe 同目录自动生成一份带注释的配置模板，直接改 `backend` 即可：

```json
{
  "backend": "https://chat.example.com"
}
```

- 支持 `http` / `https`；填 `https://` 域名时，HTTP 走 TLS、`/ws` 自动升级为 `wss`（含 SNI）。
- 也支持带路径前缀的网关地址，如 `https://example.com/chat`，前缀会被原样拼回 `/api`、`/ws` 前。
- 便携版(portable)的配置放在**便携 exe 所在目录**（不是临时解包目录）。

### 其他说明

- **打包配置**：见 `package.json` 的 `build` 字段；图标资源在 `build/`（`icon.png` / `icon.ico`，
  可用 `python build/gen-icon.py` 重新生成）。
- **桌面端标记**：判定逻辑集中在 `src/utils/desktop.ts` —— 优先取预加载脚本注入的
  `window.lufengDesktop.isDesktop`，兜底判断 `navigator.userAgent` 是否含 `Electron`（预加载失效也能生效）。
  `main.ts` 据此给 `<html>` 加 `is-desktop` 类，`ChatLayout` 根节点同时挂 `is-desktop` 类。
  桌面端会去掉网页版「悬浮卡片」的 20px 外边距、圆角与边框，让界面**铺满整个窗口**。
  > ⚠️ 该覆盖样式必须写成普通 scoped 选择器 `.shell.is-desktop { ... }`。
  > 曾经写成 `:global(html.is-desktop) .shell`，编译后**后代选择器被整段丢掉**（只剩 `html.is-desktop`），
  > 规则落到 `<html>` 上，桌面端看起来「改了但完全没生效」——这类组合写法不要用。

> 桌面端依赖后端进程：使用 exe 前请先确保后端已启动（`database/` 目录 `docker compose up -d --build backend`）。

## 目录结构

```
web-app/
├─ src/
│  ├─ api/            # 接口层，TS 类型与后端 DTO 严格 1:1
│  │  ├─ types.ts     # 契约总表：ApiResult / UserVO / ConversationVO / MessageVO / ResultCode
│  │  ├─ http.ts      # Axios 封装：统一响应、10003 静默刷新、重放、ApiError
│  │  ├─ auth.ts user.ts friend.ts conversation.ts message.ts
│  ├─ ws/             # 长连接
│  │  ├─ protocol.ts  # 信封类型 + 事件总线
│  │  └─ client.ts    # 心跳 25s / pong 看门狗 / 指数退避重连 / 离线队列
│  ├─ stores/
│  │  ├─ auth.ts          # 凭证、deviceId、登录/登出/失效处理
│  │  ├─ conversation.ts  # 会话列表、未读总数、摘要乐观更新
│  │  ├─ message.ts       # 消息束、发送幂等、已读、离线补拉
│  │  ├─ friend.ts        # 好友与申请
│  │  ├─ realtime.ts      # WS 事件编排、typing、重连补拉
│  │  └─ ui.ts            # 轻提示
│  ├─ components/
│  │  ├─ common/      # UserAvatar / AppIcon / ConnectionBadge / ToastHost
│  │  ├─ chat/        # ConversationList / MessageListView / MessageComposer
│  │  └─ contact/     # ContactList（左侧列表栏）/ AddFriendDialog
│  ├─ views/          # Login / Register / ChatLayout / ChatView / ContactDetailView / Profile
│  ├─ router/index.ts # 登录守卫
│  ├─ utils/          # storage / id / time / display / navigator
│  └─ styles/
└─ vite.config.ts     # /api、/static、/ws 三路代理
```

## 界面结构（三栏）

```
┌──────┬────────────────┬──────────────────────┐
│ 侧栏 │   列表栏        │      内容区           │
├──────┼────────────────┼──────────────────────┤
│ 消息 │ ConversationList│ ChatView            │
│ 联系人│ ContactList    │ ContactDetailView   │
│ 我的 │ （隐藏）        │ ProfileView         │
└──────┴────────────────┴──────────────────────┘
```

一级 Tab 由**路由推导**（`route.name`），不用本地状态，避免刷新/前进后退时侧栏高亮与内容错位。列表栏内容随 Tab 切换：`activeTab !== 'profile'` 才渲染列表栏，「我的」是整页表单。

切换 Tab 会回到上次的选中项：点「消息」回到 `conversationStore.currentId` 对应的会话，点「联系人」回到上次查看的好友，而不是重置成空态。

### 图标与头像

- `AppIcon.vue`：内联 SVG 图标（`currentColor` 跟随文字色）。**不要用 CSS 边框/圆角去拼图标** —— 几个图标会长得完全一样且无法辨识。
- `UserAvatar.vue`：`src` 存在则渲染 `<img>`，**`@error` 时自动退回「昵称首/末字 + 哈希底色」的文字头像**，所以后端缺图、URL 失效、网络失败都不会出现破图。切换 `src` 时会重置失败标记。
- 头像地址约定为后端相对路径（如 `/static/images/default-avatar.png`），开发态由 Vite 的 `/static` 代理转发到后端；生产态由 Nginx 转发。**若新增后端静态资源前缀，记得同步 `vite.config.ts` 的 proxy 与 Nginx 配置。**

## 认证与凭证

- 登录成功 → `/api/v1/auth/login` 返回 `accessToken（2h）` + `refreshToken（30d）`，**随即建立 WebSocket 连接**。
- 请求头统一携带 `Authorization: Bearer <token>`、`X-Client-Type: web`、`X-Device-Id: <浏览器级ID>`。
- 业务接口返回 `code=10003` → 拦截层**静默刷新**：并发请求共享同一个刷新 Promise（避免 refresh token 被并发轮换），成功后自动重放原请求；刷新失败才清除本地状态并跳登录页。
- 收到 `code=20005`（被踢）或 WS `kick` 帧 → 立即清态并跳登录页，提示「账号已在其他设备登录」。
- `X-Device-Id` 持久化到 localStorage，用于服务端多端会话管理与单端登出。
- 多标签页：`storage` 事件监听，任一标签登出后其余标签同步退出。

## 长连接

握手地址：`ws(s)://<host>/ws?token=<accessToken>&deviceId=<deviceId>`（浏览器无法自定义 WS 请求头，走 query）。

| 机制 | 策略 |
|---|---|
| 心跳 | 每 25s 发送 `{type:"ping"}`，服务端回 `{type:"pong"}` |
| 死链判定 | 发出 ping 后 10s 未收到 pong → 主动关闭连接触发快速重连 |
| 重连 | 指数退避 1→2→4→…→30s，叠加 0~1s 随机抖动防止多端同时重连 |
| 重连后补拉 | 先刷新会话摘要，再按本地 `max seq` 逐会话 `/api/v1/messages/sync` |
| 离线队列 | 未连接时入队（上限 50 条），恢复后按序补发；`ping`/`typing` 不入队 |
| 帧类型 | connected / chat / ack / read / typing / notify / ping / pong / kick / error |

## 消息收发（幂等与时序）

1. 本地生成 `clientMsgId` → 先乐观插入气泡（半透明 `sending` 态，排在末尾）。
2. **WS 优先**发送 `{type:"chat"}`，等待本端 `ack`：
   - 5s 内未收到 ACK、或连接不可用 → **降级 HTTP** `POST /api/v1/messages`，复用同一个 `clientMsgId`；
   - 服务端唯一索引 `(conversation_id, client_msg_id)` 保证两条通道都成功也只落一条。
3. ACK/HTTP 返回 seq 后，气泡状态收敛为已发送；失败标记 `failed` 并提供「重试」，重试复用原 `clientMsgId`。
4. 收到的下行 `chat` 帧 → 合并到本地消息束（按 `seq` 排序，`clientMsgId` 去重，本地临时消息被服务端权威数据替换）。
5. 历史消息用游标分页（`hasMore` + `nextCursor`），上滑加载后保持滚动锚点；超过 5 分钟插入时间分隔线。
6. 进入会话或收到当前会话新消息 → `markRead` 上报 `lastReadSeq`（服务端 `GREATEST` 防回退），同时 WS `read` 帧同步给对方。
7. 发送/接收都会更新会话摘要：仅在 `seq` 更大时覆盖，防止旧消息倒灌覆盖最新预览。

## 已知边界

- 在线状态（好友列表小绿点）目前随会话/好友列表刷新更新；后端暂未推送上下线事件，如需实时需扩展 presence 广播。
- 仅支持文本消息渲染，图片/文件等 `msgType` 会退化为文本展示。
- 未做多人群聊 UI（后端已预留群会话表与成员模型）。
