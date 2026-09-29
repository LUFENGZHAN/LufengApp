/**
 * 端到端冒烟：验证「登录 → WebSocket 握手 → 发消息 → 对端实时收到 → 幂等重发 → 历史消息 → 心跳」全链路。
 *
 * 前置：docker compose up -d && ./gradlew bootRun
 * 运行：SMOKE_BASE=http://127.0.0.1:3002 node database/scripts/ws-smoke.mjs
 * 依赖：Node >= 22（内置 fetch + WebSocket，无需 npm install）
 */
const BASE = process.env.SMOKE_BASE || 'http://127.0.0.1:3001'
const API = BASE + '/api/v1'
const PASSWORD = process.env.SMOKE_PASSWORD || '123456'

async function api(path, init = {}) {
  const res = await fetch(API + path, init)
  return res.json()
}

function headers(token, deviceId) {
  return {
    Authorization: 'Bearer ' + token,
    'Content-Type': 'application/json',
    'X-Device-Id': deviceId,
    'X-Client-Type': 'web',
  }
}

async function login(account, deviceId) {
  const res = await api('/auth/login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ account, password: PASSWORD, deviceId, deviceType: 'web' }),
  })
  if (res.code !== 0) throw new Error(`登录失败(${account})：${res.message}`)
  return res.data
}

/** 建连并按 type 分发下行帧 */
function connectWs(token, deviceId) {
  const url = `${BASE.replace(/^http/, 'ws')}/ws?token=${encodeURIComponent(token)}&deviceId=${encodeURIComponent(deviceId)}`
  const ws = new WebSocket(url)
  const waiters = new Map()

  ws.addEventListener('message', (event) => {
    let env
    try {
      env = JSON.parse(event.data)
    } catch {
      return
    }
    const list = waiters.get(env.type) ?? []
    waiters.set(env.type, [])
    list.forEach((fn) => fn(env))
  })

  return {
    ws,
    wait(type, timeout = 8000) {
      return new Promise((resolve, reject) => {
        const timer = setTimeout(() => reject(new Error(`等待 ${type} 超时`)), timeout)
        waiters.set(type, [...(waiters.get(type) ?? []), (env) => {
          clearTimeout(timer)
          resolve(env)
        }])
      })
    },
    opened() {
      return new Promise((resolve, reject) => {
        if (ws.readyState === WebSocket.OPEN) return resolve(true)
        ws.addEventListener('open', () => resolve(true), { once: true })
        ws.addEventListener('error', () => reject(new Error('WebSocket 连接失败')), { once: true })
      })
    },
  }
}

const sleep = (ms) => new Promise((r) => setTimeout(r, ms))

async function main() {
  const receiver = await login('lisi', 'smoke-lisi-1')
  const sender = await login('zhangsan', 'smoke-zs-1')
  console.log(`1. 登录：${sender.user.nickname} → ${receiver.user.nickname}`)

  const client = connectWs(receiver.accessToken, 'smoke-lisi-1')
  const connectedWaiter = client.wait('connected')
  await client.opened()
  const connected = await connectedWaiter
  console.log(`2. WS 握手成功：userId=${connected.data.userId} deviceId=${connected.data.deviceId}`)

  // 2.5 准备会话：删除好友会解散单聊会话，脚本必须能重复执行
  const peerId = receiver.user.userId
  const senderId = sender.user.userId
  const convs = await api('/conversations', { headers: headers(sender.accessToken, 'smoke-zs-1') })
  let conversationId = convs.data.find((c) => c.peer?.userId === peerId)?.conversationId ?? null

  if (!conversationId) {
    const friends = await api('/friends', { headers: headers(sender.accessToken, 'smoke-zs-1') })
    if (!friends.data.some((f) => f.userId === peerId)) {
      const incoming = await api('/friends/requests?direction=in&status=0', {
        headers: headers(receiver.accessToken, 'smoke-lisi-1'),
      })
      let requestId = incoming.data.find((r) => r.userId === senderId)?.requestId ?? null
      if (!requestId) {
        const apply = await api('/friends/requests', {
          method: 'POST',
          headers: headers(sender.accessToken, 'smoke-zs-1'),
          body: JSON.stringify({ toUserId: peerId }),
        })
        if (apply.code !== 0) throw new Error(`建立好友关系失败：${apply.message}`)
        requestId = apply.data.requestId
      }
      const accept = await api(`/friends/requests/${requestId}`, {
        method: 'PUT',
        headers: headers(receiver.accessToken, 'smoke-lisi-1'),
        body: JSON.stringify({ action: 'accept' }),
      })
      if (accept.code !== 0) throw new Error(`同意好友申请失败：${accept.message}`)
    }
    const conv = await api('/conversations/private', {
      method: 'POST',
      headers: headers(sender.accessToken, 'smoke-zs-1'),
      body: JSON.stringify({ targetUserId: peerId }),
    })
    if (conv.code !== 0) throw new Error(`创建会话失败：${conv.message}`)
    conversationId = conv.data.conversationId
    console.log(`2.5 准备会话：conversationId=${conversationId}`)
  }

  const pushed = client.wait('chat')
  const content = 'WS 实时冒烟 ' + new Date().toLocaleTimeString('zh-CN')
  const clientMsgId = 'smoke-' + Date.now()
  const sent = await api('/messages', {
    method: 'POST',
    headers: headers(sender.accessToken, 'smoke-zs-1'),
    body: JSON.stringify({ clientMsgId, conversationId, msgType: 1, content }),
  })
  console.log(`3. HTTP 发送：code=${sent.code} seq=${sent.data.seq}`)

  const frame = await pushed
  console.log(`4. WS 实时推送：seq=${frame.seq} from=${frame.data.senderNickname} content=${frame.data.content}`)

  const dup = await api('/messages', {
    method: 'POST',
    headers: headers(sender.accessToken, 'smoke-zs-1'),
    body: JSON.stringify({ clientMsgId, conversationId, msgType: 1, content }),
  })
  console.log(`5. 幂等重发：duplicated=${dup.data.duplicated} msgNo一致=${dup.data.msgNo === sent.data.msgNo}`)

  const history = await api(`/conversations/${conversationId}/messages?size=3`, {
    headers: headers(receiver.accessToken, 'smoke-lisi-1'),
  })
  console.log('6. 历史消息：' + history.data.list.map((m) => m.content).join(' / '))

  const pongWaiter = client.wait('pong', 4000).catch(() => null)
  client.ws.send(JSON.stringify({ type: 'ping', data: { ts: Date.now() } }))
  console.log('7. 心跳 pong：' + ((await pongWaiter) ? 'OK' : '未收到'))

  client.ws.close()
  await sleep(300)
  console.log('冒烟完成 ✅')
}

main().catch((e) => {
  console.error('冒烟失败 ❌', e.message)
  process.exit(1)
})
