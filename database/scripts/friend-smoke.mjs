/**
 * 好友申请链路端到端冒烟（零依赖，Node >= 22）。
 *
 * 覆盖：登录 → 发申请（不传 applyMsg）→ 对方收到 → 同意 → 双向好友【不预建会话】
 *      → 首条消息按 peerId 懒创建会话 → 删除好友（会话双方解散、收发双向阻断）
 *      → 重新加回好友（仍不预建会话）→ 发消息复用原会话、历史可见
 * 用法：SMOKE_BASE=http://127.0.0.1:3002 node database/scripts/friend-smoke.mjs
 */
const base = (process.env.SMOKE_BASE || 'http://127.0.0.1:3001') + '/api/v1'

async function call(path, { method = 'GET', token, deviceId, body } = {}) {
  const headers = { 'Content-Type': 'application/json', 'X-Device-Id': deviceId }
  if (token) headers.Authorization = 'Bearer ' + token
  const res = await fetch(base + path, { method, headers, body: body ? JSON.stringify(body) : undefined })
  return res.json()
}

async function login(account, deviceId) {
  const r = await call('/auth/login', {
    method: 'POST',
    deviceId,
    body: { account, password: '123456', deviceId, deviceType: 'web' },
  })
  if (r.code !== 0) throw new Error(`登录失败 ${account}: ${r.code} ${r.message}`)
  return { token: r.data.accessToken, userId: r.data.user.userId, nickname: r.data.user.nickname }
}

const who = (u) => `${u.nickname}(#${u.userId})`

async function main() {
  // 清理上一轮可能残留的关系，保证脚本可重复执行
  const a = await login('zhangsan', 'friend-smoke-a')
  const b = await login('wangwu', 'friend-smoke-b')
  console.log(`1. 登录：${who(a)} → ${who(b)}`)

  // 清理上一轮残留：确保双方不是好友，并且两者之间的会话已解散。
  // 若本就不是好友（历史脏数据可能留下「非好友却仍活跃」的会话），
  // 先加回来再删一次，借 delete 的 dissolvePrivate 把旧会话归位，保证脚本可重复执行。
  const delFirst = await call(`/friends/${b.userId}`, {
    method: 'DELETE',
    token: a.token,
    deviceId: 'friend-smoke-a',
  })
  if (delFirst.code !== 0) {
    const tmp = await call('/friends/requests', {
      method: 'POST',
      token: a.token,
      deviceId: 'friend-smoke-a',
      body: { toUserId: b.userId },
    })
    if (tmp.code !== 0) throw new Error(`清理阶段申请失败：${tmp.code} ${tmp.message}`)
    const tmpAccept = await call(`/friends/requests/${tmp.data.requestId}`, {
      method: 'PUT',
      token: b.token,
      deviceId: 'friend-smoke-b',
      body: { action: 'accept' },
    })
    if (tmpAccept.code !== 0) throw new Error(`清理阶段同意失败：${tmpAccept.code} ${tmpAccept.message}`)
    const delAgain = await call(`/friends/${b.userId}`, {
      method: 'DELETE',
      token: a.token,
      deviceId: 'friend-smoke-a',
    })
    if (delAgain.code !== 0) throw new Error(`清理阶段删除失败：${delAgain.code} ${delAgain.message}`)
  }

  // 2. 发送申请：故意不传 applyMsg，验证 NOT NULL 列兜底
  const apply = await call('/friends/requests', {
    method: 'POST',
    token: a.token,
    deviceId: 'friend-smoke-a',
    body: { toUserId: b.userId },
  })
  if (apply.code !== 0) throw new Error(`发申请失败：${apply.code} ${apply.message}`)
  console.log(`2. 发送申请（未填留言）：requestId=${apply.data.requestId}`)

  // 3. 重复申请应被拦截
  const dup = await call('/friends/requests', {
    method: 'POST',
    token: a.token,
    deviceId: 'friend-smoke-a',
    body: { toUserId: b.userId },
  })
  console.log(`3. 重复申请拦截：code=${dup.code} message=${dup.message}`)

  // 4. 接收方看到申请，且留言为默认文案
  const incoming = await call('/friends/requests?direction=in&status=0', { token: b.token, deviceId: 'friend-smoke-b' })
  const req = incoming.data?.find((r) => r.requestId === apply.data.requestId)
  if (!req) throw new Error('接收方未查到该申请')
  console.log(`4. 接收方收到申请：from=${req.nickname} applyMsg="${req.applyMsg}" status=${req.status}`)

  // 5. 同意
  const accept = await call(`/friends/requests/${apply.data.requestId}`, {
    method: 'PUT',
    token: b.token,
    deviceId: 'friend-smoke-b',
    body: { action: 'accept' },
  })
  if (accept.code !== 0) throw new Error(`同意失败：${accept.code} ${accept.message}`)
  console.log('5. 同意申请：OK')

  // 6. 双向好友
  const fa = await call('/friends', { token: a.token, deviceId: 'friend-smoke-a' })
  const fb = await call('/friends', { token: b.token, deviceId: 'friend-smoke-b' })
  const convOf = (list, peerId) => list.find((c) => c.peer?.userId === peerId)
  console.log(
    `6. 好友列表：A=${fa.data.map((f) => f.nickname).join(',') || '(空)'} / B=${fb.data.map((f) => f.nickname).join(',') || '(空)'}`,
  )

  // 7. 关键断言：成为好友【不】预建会话，列表里不该出现与对方的单聊
  const ca = await call('/conversations', { token: a.token, deviceId: 'friend-smoke-a' })
  const auto = convOf(ca.data, b.userId)
  if (auto) throw new Error(`同意好友后仍自动创建了会话 ${auto.conversationId}`)
  console.log(
    `7. 同意后会话列表：${ca.data.map((c) => c.conversationNo).join(',') || '(空)'}（不应含对方）`,
  )

  // 8. 备注更新（空串=清空）
  await call(`/friends/${b.userId}/remark`, {
    method: 'PUT',
    token: a.token,
    deviceId: 'friend-smoke-a',
    body: { remark: '老王' },
  })
  let fa2 = await call('/friends', { token: a.token, deviceId: 'friend-smoke-a' })
  console.log(`8. 设置备注：${fa2.data.map((f) => f.remark).join(',') || '(空)'}`)

  await call(`/friends/${b.userId}/remark`, {
    method: 'PUT',
    token: a.token,
    deviceId: 'friend-smoke-a',
    body: { remark: '' },
  })
  fa2 = await call('/friends', { token: a.token, deviceId: 'friend-smoke-a' })
  console.log(`9. 清空备注：remark="${fa2.data.map((f) => f.remark).join(',')}"（应为空）`)

  // 10. 首条消息只带 peerId：服务端校验好友关系后懒创建会话
  const send1 = await call('/messages', {
    method: 'POST',
    token: a.token,
    deviceId: 'friend-smoke-a',
    body: {
      peerId: b.userId,
      clientMsgId: `smoke-${Date.now()}-1`,
      msgType: 1,
      content: '删除前的一条消息',
    },
  })
  if (send1.code !== 0) throw new Error(`按 peerId 发首条消息失败：${send1.code} ${send1.message}`)
  const conversationId = send1.data?.conversationId
  if (!conversationId) throw new Error('懒创建会话未返回 conversationId')
  const caSent = await call('/conversations', { token: a.token, deviceId: 'friend-smoke-a' })
  const cbSent = await call('/conversations', { token: b.token, deviceId: 'friend-smoke-b' })
  const seenByA = convOf(caSent.data, b.userId)?.conversationId
  const seenByB = convOf(cbSent.data, a.userId)?.conversationId
  if (seenByA !== conversationId || seenByB !== conversationId) {
    throw new Error(`懒创建的会话未对双方可见：A=${seenByA} B=${seenByB} 期望=${conversationId}`)
  }
  console.log(`10. 首条消息懒建会话：id=${conversationId} seq=${send1.data.seq}（A/B 均可见）`)

  // 11. 删除好友：关系解除 + 会话对双方都消失
  const del = await call(`/friends/${b.userId}`, { method: 'DELETE', token: a.token, deviceId: 'friend-smoke-a' })
  if (del.code !== 0) throw new Error(`删除好友失败：${del.code} ${del.message}`)
  const caAfter = await call('/conversations', { token: a.token, deviceId: 'friend-smoke-a' })
  const cbAfter = await call('/conversations', { token: b.token, deviceId: 'friend-smoke-b' })
  const goneFromA = !convOf(caAfter.data, b.userId)
  const goneFromB = !convOf(cbAfter.data, a.userId)
  console.log(`11. 删除好友：A 列表已移除=${goneFromA} / B 列表已移除=${goneFromB}`)
  if (!goneFromA || !goneFromB) throw new Error('删除好友后会话未对双方解散')

  // 12. 非好友不能发消息
  const send2 = await call('/messages', {
    method: 'POST',
    token: a.token,
    deviceId: 'friend-smoke-a',
    body: {
      conversationId: conversationId,
      clientMsgId: `smoke-${Date.now()}-2`,
      msgType: 1,
      content: '删了还想发',
    },
  })
  const send2b = await call('/messages', {
    method: 'POST',
    token: a.token,
    deviceId: 'friend-smoke-a',
    body: {
      peerId: b.userId,
      clientMsgId: `smoke-${Date.now()}-2b`,
      msgType: 1,
      content: '删了还想发（按 peerId）',
    },
  })
  console.log(
    `12. 删除后再发消息：conversationId→code=${send2.code} / peerId→code=${send2b.code}（均应被拒绝）`,
  )
  if (send2.code === 0 || send2b.code === 0) throw new Error('删除好友后仍可发消息，逻辑未生效')

  // 13. 重新加回好友：同样不预建会话；用 peerId 发消息时才复用原会话
  const reApply = await call('/friends/requests', {
    method: 'POST',
    token: a.token,
    deviceId: 'friend-smoke-a',
    body: { toUserId: b.userId },
  })
  if (reApply.code !== 0) throw new Error(`重新申请失败：${reApply.code} ${reApply.message}`)
  const reAccept = await call(`/friends/requests/${reApply.data.requestId}`, {
    method: 'PUT',
    token: b.token,
    deviceId: 'friend-smoke-b',
    body: { action: 'accept' },
  })
  if (reAccept.code !== 0) throw new Error(`重新同意失败：${reAccept.code} ${reAccept.message}`)
  const caBefore13 = await call('/conversations', { token: a.token, deviceId: 'friend-smoke-a' })
  if (convOf(caBefore13.data, b.userId)) {
    throw new Error('重新加回好友后又自动出现了会话，懒创建语义被破坏')
  }
  const sendBack = await call('/messages', {
    method: 'POST',
    token: a.token,
    deviceId: 'friend-smoke-a',
    body: {
      peerId: b.userId,
      clientMsgId: `smoke-${Date.now()}-3`,
      msgType: 1,
      content: '加回来了',
    },
  })
  if (sendBack.code !== 0) throw new Error(`恢复后发消息失败：${sendBack.code} ${sendBack.message}`)
  const restored = convOf(
    (await call('/conversations', { token: a.token, deviceId: 'friend-smoke-a' })).data,
    b.userId,
  )
  if (!restored) throw new Error('重新加回好友并发消息后会话未恢复')
  const hist = await call(`/conversations/${restored.conversationId}/messages?size=20`, {
    token: a.token,
    deviceId: 'friend-smoke-a',
  })
  console.log(
    `13. 重新加回好友：会话=${restored.conversationId}（原 ${conversationId}，应一致）历史 ${hist.data.list.length} 条`,
  )
  if (restored.conversationId !== conversationId) throw new Error('复用了不同会话，产生了重复会话')

  // 14. 恢复后可以继续发消息
  const send3 = await call('/messages', {
    method: 'POST',
    token: a.token,
    deviceId: 'friend-smoke-a',
    body: {
      conversationId: restored.conversationId,
      clientMsgId: `smoke-${Date.now()}-4`,
      msgType: 1,
      content: '继续聊天',
    },
  })
  if (send3.code !== 0) throw new Error(`恢复后发消息失败：${send3.code} ${send3.message}`)
  console.log(`14. 恢复后发消息：seq=${send3.data.seq}`)

  console.log('\n全部通过 ✅')
}

main().catch((e) => {
  console.error('冒烟失败：', e.message)
  process.exit(1)
})
