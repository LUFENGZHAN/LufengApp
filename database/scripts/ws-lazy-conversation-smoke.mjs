/**
 * 冒烟：验证「成为好友不建会话 + 首条消息经 WebSocket 懒创建会话」。
 *
 * 覆盖：清理关系 → 加好友（此时会话列表不应出现）→ A 端 WS 上行只带 peerId
 *      → ACK 带回真实 conversationId → B 端实时收到 → 双方会话列表均出现同一会话
 *
 * 用法：SMOKE_BASE=http://127.0.0.1:3002 node database/scripts/ws-lazy-conversation-smoke.mjs
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
    send(type, data) {
      ws.send(JSON.stringify({ type, seq: null, ts: Date.now(), data }))
    },
    wait(type, timeout = 8000) {
      return new Promise((resolve, reject) => {
        const timer = setTimeout(() => reject(new Error(`等待 ${type} 超时`)), timeout)
        waiters.set(type, [
          ...(waiters.get(type) ?? []),
          (env) => {
            clearTimeout(timer)
            resolve(env)
          },
        ])
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

async function main() {
  const a = await login('zhangsan', 'lazy-a')
  const b = await login('lisi', 'lazy-b')
  const senderId = a.user.userId
  const peerId = b.user.userId
  console.log(`1. 登录：${a.user.nickname}(#${senderId}) → ${b.user.nickname}(#${peerId})`)

  // 2. 复位：删到非好友（删除会顺带解散会话），再重新加回 —— 保证脚本可重复执行
  await api(`/friends/${peerId}`, { method: 'DELETE', headers: headers(a.accessToken, 'lazy-a') })
  const apply = await api('/friends/requests', {
    method: 'POST',
    headers: headers(a.accessToken, 'lazy-a'),
    body: JSON.stringify({ toUserId: peerId, applyMsg: '懒创建冒烟' }),
  })
  if (apply.code !== 0) throw new Error(`申请好友失败：${apply.code} ${apply.message}`)
  const accept = await api(`/friends/requests/${apply.data.requestId}`, {
    method: 'PUT',
    headers: headers(b.accessToken, 'lazy-b'),
    body: JSON.stringify({ action: 'accept' }),
  })
  if (accept.code !== 0) throw new Error(`同意好友失败：${accept.code} ${accept.message}`)
  console.log('2. 重新成为好友：OK')

  // 3. 关键断言：成为好友后会话列表里仍没有对方
  const convsAfterAccept = await api('/conversations', { headers: headers(a.accessToken, 'lazy-a') })
  const stillAbsent = !convsAfterAccept.data.some((c) => c.peer?.userId === peerId)
  if (!stillAbsent) throw new Error('同意好友后仍自动创建了会话')
  console.log(`3. 同意后会话列表：${convsAfterAccept.data.map((c) => c.conversationNo).join(',') || '(空)'}（不含对方）`)

  const clientA = connectWs(a.accessToken, 'lazy-a')
  const clientB = connectWs(b.accessToken, 'lazy-b')
  await clientA.opened()
  await clientB.opened()
  const ackWaiter = clientA.wait('ack')
  const chatWaiter = clientB.wait('chat')
  console.log('4. 双端 WS 握手成功')

  // 5. WS 上行只带 peerId：服务端校验好友关系后懒创建会话
  const clientMsgId = 'lazy-' + Date.now()
  clientA.send('chat', {
    clientMsgId,
    peerId,
    msgType: 1,
    content: '第一条消息（WS 懒创建）',
  })
  const ack = await ackWaiter
  const conversationId = ack.data?.conversationId
  if (!conversationId) throw new Error('ACK 未返回 conversationId，前端无法切到真实会话')
  console.log(`5. WS 发送（仅 peerId）：ack seq=${ack.data.seq} conversationId=${conversationId}`)

  const pushed = await chatWaiter
  if (pushed.data.conversationId !== conversationId) {
    throw new Error(`对端收到的会话号不一致：${pushed.data.conversationId} != ${conversationId}`)
  }
  console.log(`6. 对端实时收到：content="${pushed.data.content}" conversationId=${pushed.data.conversationId}`)

  // 7. 双方会话列表都应出现同一个会话，且不会重复
  const ca = await api('/conversations', { headers: headers(a.accessToken, 'lazy-a') })
  const cb = await api('/conversations', { headers: headers(b.accessToken, 'lazy-b') })
  const convA = ca.data.filter((c) => c.peer?.userId === peerId)
  const convB = cb.data.filter((c) => c.peer?.userId === senderId)
  if (convA.length !== 1 || convB.length !== 1) {
    throw new Error(`会话数量异常：A=${convA.length} B=${convB.length}（应各 1 个）`)
  }
  if (convA[0].conversationId !== conversationId || convB[0].conversationId !== conversationId) {
    throw new Error('双方会话号不一致，产生了重复会话')
  }
  console.log(`7. 双方会话列表：A=${convA[0].conversationId} B=${convB[0].conversationId}（同一会话）`)

  clientA.ws.close()
  clientB.ws.close()
  console.log('\n全部通过 ✅')
}

main()
  .then(() => process.exit(0))
  .catch((e) => {
    console.error('冒烟失败：', e.message)
    process.exit(1)
  })
