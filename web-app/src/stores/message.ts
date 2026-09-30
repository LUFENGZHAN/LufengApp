import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import type { MessageVO, SendMessageReq, SyncItem } from '@/api/types'
import { messageApi } from '@/api/message'
import { conversationApi } from '@/api/conversation'
import { ws } from '@/ws/client'
import type { AckPayload, RecallPayload } from '@/ws/protocol'
import { clientMsgId } from '@/utils/id'
import { ApiError } from '@/api/http'
import { useAuthStore } from './auth'
import { useConversationStore } from './conversation'

/**
 * 「临时会话」桶的 key：会话还没创建时用 -peerId 存放本地消息。
 * 加好友不再预建会话，第一条消息发出、服务端返回真实 conversationId 后迁移过去。
 */
export function pendingKeyOf(peerId: number): number {
  return -Math.abs(peerId)
}

export function peerOfPendingKey(key: number): number {
  return Math.abs(key)
}

/** 本地消息 = 服务端消息 + 发送态。sending/failed 只存在于本地 */
export interface ChatMessage extends MessageVO {
  sending?: boolean
  failed?: boolean
}

/** WS 上行 ACK 等待上限，超时即降级 HTTP 重发（同一个 clientMsgId，服务端幂等兜底） */
const WS_ACK_TIMEOUT = 5000
/** WS 不可用时是否允许 HTTP 兜底 */
const HTTP_FALLBACK = true

function keyOf(m: ChatMessage): string {
  return m.msgId > 0 ? `m:${m.msgId}` : `c:${m.clientMsgId}`
}

function orderKey(m: ChatMessage): number {
  // 未确认的本地消息永远排在最末
  return m.sending ? Number.MAX_SAFE_INTEGER : m.seq ?? 0
}

function sortMessages(list: ChatMessage[]): ChatMessage[] {
  return list.sort((a, b) => orderKey(a) - orderKey(b) || (a.sendTime ?? 0) - (b.sendTime ?? 0))
}

/** 会话列表预览文案：媒体类只显示类型标签，不暴露文件 URL */
function previewText(msgType: number, content?: string | null): string {
  switch (msgType) {
    case 2:
      return '[图片]'
    case 3:
      return '[语音]'
    case 4:
      return '[视频]'
    case 5:
      return '[文件]'
    case 6:
      return '[位置]'
    default:
      return content ?? ''
  }
}

/** 合并服务端消息：相同 clientMsgId 的本地 sending 版本会被服务端权威数据替换 */
function merge(existing: ChatMessage[], incoming: ChatMessage[]): ChatMessage[] {
  const map = new Map<string, ChatMessage>()
  for (const m of existing) map.set(keyOf(m), m)
  for (const m of incoming) {
    if (!m.sending && m.clientMsgId) {
      map.delete(`c:${m.clientMsgId}`)
    }
    map.set(keyOf(m), m)
  }
  return sortMessages([...map.values()])
}

export const useMessageStore = defineStore('message', () => {
  const bundles = ref<Record<number, ChatMessage[]>>({})
  const pages = ref<Record<number, { hasMore: boolean; nextCursor: number | null; loading: boolean }>>(
    {},
  )
  /** 等待 WS ACK 的回调表：clientMsgId → resolve/reject */
  const ackWaiters = new Map<string, { resolve: (p: AckPayload) => void; reject: (e: unknown) => void }>()

  const auth = useAuthStore()
  const conversationStore = useConversationStore()

  function listOf(conversationId: number): ChatMessage[] {
    if (!bundles.value[conversationId]) bundles.value[conversationId] = []
    return bundles.value[conversationId]!
  }

  /** 本地已确认接收的最大 seq，作为重连补拉的起点 */
  function maxSeq(conversationId: number): number {
    const list = (bundles.value[conversationId] ?? []).filter((m) => !m.sending)
    if (list.length === 0) return 0
    return Math.max(...list.map((m) => m.seq ?? 0))
  }

  /* ---------------- 历史消息 ---------------- */

  async function loadLatest(conversationId: number, size = 30) {
    pages.value[conversationId] = { hasMore: false, nextCursor: null, loading: false }
    const page = await messageApi.history(conversationId, undefined, size)
    const list = [...(page?.list ?? [])].reverse()
    bundles.value[conversationId] = merge(bundles.value[conversationId] ?? [], list)
    pages.value[conversationId] = {
      hasMore: !!page?.hasMore,
      nextCursor: page?.nextCursor ?? null,
      loading: false,
    }
  }

  /** 向上翻页：cursor 为当前最早一条的 seq */
  async function loadMore(conversationId: number, size = 20): Promise<boolean> {
    const page = pages.value[conversationId]
    if (!page || !page.hasMore || page.loading || !page.nextCursor) return false
    page.loading = true
    try {
      const result = await messageApi.history(conversationId, page.nextCursor, size)
      const list = [...(result?.list ?? [])].reverse()
      bundles.value[conversationId] = merge(bundles.value[conversationId] ?? [], list)
      pages.value[conversationId] = {
        hasMore: !!result?.hasMore,
        nextCursor: result?.nextCursor ?? null,
        loading: false,
      }
      return list.length > 0
    } catch (e) {
      if (pages.value[conversationId]) pages.value[conversationId]!.loading = false
      throw e
    }
  }

  function pageStateOf(conversationId: number) {
    if (!pages.value[conversationId]) {
      pages.value[conversationId] = { hasMore: false, nextCursor: null, loading: false }
    }
    return pages.value[conversationId]!
  }

  /* ---------------- 收发 ---------------- */

  function upsertIncoming(message: MessageVO) {
    const list = listOf(message.conversationId)
    bundles.value[message.conversationId] = merge(list, [message as ChatMessage])
  }

  /** WS 下行的 ack：收敛本地发送态 */
  function resolveAck(payload: AckPayload) {
    const waiter = ackWaiters.get(payload.clientMsgId)
    if (waiter) {
      ackWaiters.delete(payload.clientMsgId)
      waiter.resolve(payload)
      return
    }
    patchLocal(payload.clientMsgId, {
      sending: false,
      failed: false,
      seq: payload.seq,
      msgNo: payload.msgNo,
      sendTime: payload.sendTime,
    })
  }

  function patchLocal(clientId: string, patch: Partial<ChatMessage>) {
    for (const conversationId of Object.keys(bundles.value)) {
      const list = bundles.value[Number(conversationId)] ?? []
      const idx = list.findIndex((m) => m.clientMsgId === clientId)
      if (idx >= 0) {
        list[idx] = { ...list[idx], ...patch }
        bundles.value[Number(conversationId)] = [...list]
        return
      }
    }
  }

  /**
   * 发送消息：优先走 WebSocket（低延迟），ACK 超时或连接不可用降级 HTTP。
   * 两条通道复用同一个 clientMsgId，服务端靠唯一索引 (conversation_id, client_msg_id) 幂等去重。
   */
  async function send(
    conversationId: number,
    content: string,
    msgType = 1,
    extra?: string,
  ): Promise<{ ok: boolean; error?: unknown }> {
    const trimmed = content?.trim()
    if (!trimmed) return { ok: false, error: new ApiError(40002, '消息内容为空') }

    const cid = clientMsgId()
    const req: SendMessageReq = { clientMsgId: cid, conversationId, msgType, content: trimmed, extra }

    const local: ChatMessage = {
      msgId: 0,
      msgNo: '',
      clientMsgId: cid,
      conversationId,
      seq: 0,
      senderId: auth.userId,
      senderNo: auth.user?.userNo ?? null,
      senderNickname: auth.nickname,
      senderAvatar: auth.avatar,
      msgType,
      content: trimmed,
      extra: extra ?? null,
      status: 1,
      sendTime: Date.now(),
      duplicated: false,
      sending: true,
      failed: false,
    }
    const list = listOf(conversationId)
    bundles.value[conversationId] = sortMessages([...list, local])

    try {
      if (ws.connected) {
        const ack = await sendViaWs(req)
        patchLocal(cid, {
          sending: false,
          failed: false,
          seq: ack.seq,
          msgNo: ack.msgNo,
          sendTime: ack.sendTime,
        })
        const current = listOf(conversationId).find((m) => m.clientMsgId === cid)
        if (current) {
          conversationStore.patchSummary(conversationId, {
            msgId: current.msgId,
            msgNo: ack.msgNo,
            seq: ack.seq,
            senderId: local.senderId,
            msgType,
            preview: previewText(msgType, trimmed),
            sendTime: ack.sendTime,
          })
        }
        return { ok: true }
      }

      if (!HTTP_FALLBACK) throw new ApiError(-1, '长连接不可用')
      const vo = await messageApi.send(req)
      bundles.value[conversationId] = merge(listOf(conversationId), [vo as ChatMessage])
      conversationStore.patchSummary(conversationId, {
        msgId: vo.msgId,
        msgNo: vo.msgNo,
        seq: vo.seq,
        senderId: vo.senderId,
        msgType: vo.msgType,
        preview: previewText(msgType, trimmed),
        sendTime: vo.sendTime,
      })
      return { ok: true }
    } catch (error) {
      patchLocal(cid, { sending: false, failed: true })
      return { ok: false, error }
    }
  }

  /** HTTP 发送结果 → ACK 结构，供降级路径与懒创建会话复用 */
  function toAck(clientId: string, vo: MessageVO): AckPayload {
    return {
      clientMsgId: clientId,
      msgNo: vo.msgNo,
      seq: vo.seq,
      sendTime: vo.sendTime,
      conversationId: vo.conversationId,
      duplicated: !!vo.duplicated,
    }
  }

  /* ---------------- 懒创建会话：给好友发第一条消息 ---------------- */

  /** 把临时桶里的本地消息迁到真实会话桶，并清掉临时分页态 */
  function migratePending(fromKey: number, conversationId: number) {
    const pending = bundles.value[fromKey]
    if (!pending || pending.length === 0) return
    const migrated = pending.map((m) => ({ ...m, conversationId }))
    delete bundles.value[fromKey]
    delete pages.value[fromKey]
    bundles.value[conversationId] = merge(listOf(conversationId), migrated)
    if (!pages.value[conversationId]) {
      pages.value[conversationId] = { hasMore: false, nextCursor: null, loading: false }
    }
  }

  /** 兜底定位会话：老版本后端的 ACK 不带 conversationId，此时回查会话列表 */
  async function resolveConversationId(peerId: number, hint: number | null): Promise<number> {
    if (hint) return hint
    await conversationStore.load()
    const found = conversationStore.findByPeer(peerId)
    if (found) return found.conversationId
    const created = await conversationApi.createPrivate(peerId)
    conversationStore.upsert(created)
    return created.conversationId
  }

  /**
   * 发消息给好友（会话可能还不存在）：服务端校验好友关系后懒创建单聊，
   * 本地先用 -peerId 临时桶承载乐观消息，拿到真实 conversationId 后迁移。
   */
  async function sendToPeer(
    peerId: number,
    content: string,
    msgType = 1,
    extra?: string,
    clientId: string = clientMsgId(),
  ): Promise<{ ok: boolean; conversationId?: number; error?: unknown }> {
    const trimmed = content?.trim()
    if (!trimmed) return { ok: false, error: new ApiError(40002, '消息内容为空') }

    const key = pendingKeyOf(peerId)
    const req: SendMessageReq = {
      clientMsgId: clientId,
      conversationId: null,
      peerId,
      msgType,
      content: trimmed,
      extra,
    }
    const exists = listOf(key).some((m) => m.clientMsgId === clientId)
    if (!exists) {
      const local: ChatMessage = {
        msgId: 0,
        msgNo: '',
        clientMsgId: clientId,
        conversationId: key,
        seq: 0,
        senderId: auth.userId,
        senderNo: auth.user?.userNo ?? null,
        senderNickname: auth.nickname,
        senderAvatar: auth.avatar,
        msgType,
        content: trimmed,
        extra: extra ?? null,
        status: 1,
        sendTime: Date.now(),
        duplicated: false,
        sending: true,
        failed: false,
      }
      bundles.value[key] = sortMessages([...listOf(key), local])
    } else {
      patchLocal(clientId, { sending: true, failed: false })
    }

    try {
      let ack: AckPayload
      if (ws.connected) {
        ack = await sendViaWs(req)
      } else if (HTTP_FALLBACK) {
        ack = toAck(clientId, await messageApi.send(req))
      } else {
        throw new ApiError(-1, '长连接不可用')
      }
      patchLocal(clientId, {
        sending: false,
        failed: false,
        seq: ack.seq,
        msgNo: ack.msgNo,
        sendTime: ack.sendTime,
      })
      const conversationId = await resolveConversationId(peerId, ack.conversationId ?? null)
      migratePending(key, conversationId)
      return { ok: true, conversationId }
    } catch (error) {
      patchLocal(clientId, { sending: false, failed: true })
      return { ok: false, error }
    }
  }

  /** WS 上行：等待本端 ACK，超时降级（Promise 由调用方 await） */
  function sendViaWs(req: SendMessageReq): Promise<AckPayload> {
    return new Promise<AckPayload>((resolve, reject) => {
      let timer: ReturnType<typeof setTimeout> | null = null
      const off = ws.on<AckPayload>('ack', (payload) => {
        if (payload.clientMsgId !== req.clientMsgId) return
        cleanup()
        resolve(payload)
      })
      function cleanup() {
        if (timer) clearTimeout(timer)
        off()
        ackWaiters.delete(req.clientMsgId)
      }
      ackWaiters.set(req.clientMsgId, { resolve, reject })

      timer = setTimeout(() => {
        cleanup()
        if (!HTTP_FALLBACK) {
          reject(new ApiError(-1, 'ACK 超时'))
          return
        }
        // 降级：用同一个 clientMsgId 走 HTTP，服务端幂等，不会重复落库
        messageApi.send(req).then((vo) => resolve(toAck(req.clientMsgId, vo)), (err) => reject(err))
      }, WS_ACK_TIMEOUT)

      const delivered = ws.send('chat', req, { queued: false })
      if (!delivered) {
        cleanup()
        messageApi.send(req).then((vo) => resolve(toAck(req.clientMsgId, vo)), (err) => reject(err))
      }
    })
  }

  /** 失败重发：复用原 clientMsgId，保证重试不产生重复消息 */
  async function resend(conversationId: number, clientId: string) {
    const target = (bundles.value[conversationId] ?? []).find((m) => m.clientMsgId === clientId)
    if (!target || target.sending) return
    // 临时会话（会话还没创建）：按 peerId 重发，成功后再迁移到真实会话
    if (conversationId < 0) {
      const peerId = peerOfPendingKey(conversationId)
      const result = await sendToPeer(
        peerId,
        target.content ?? '',
        target.msgType,
        target.extra ?? undefined,
        clientId,
      )
      if (result.ok && result.conversationId) await conversationStore.load()
      return
    }
    patchLocal(clientId, { sending: true, failed: false })
    try {
      const req: SendMessageReq = {
        clientMsgId: clientId,
        conversationId,
        msgType: target.msgType,
        content: target.content ?? '',
        extra: target.extra ?? undefined,
      }
      if (ws.connected) {
        const ack = await sendViaWs(req)
        patchLocal(clientId, {
          sending: false,
          failed: false,
          seq: ack.seq,
          msgNo: ack.msgNo,
          sendTime: ack.sendTime,
        })
      } else {
        const vo = await messageApi.send(req)
        bundles.value[conversationId] = merge(listOf(conversationId), [vo as ChatMessage])
      }
    } catch {
      patchLocal(clientId, { sending: false, failed: true })
    }
  }

  async function recall(conversationId: number, msgNo: string) {
    await messageApi.recall(msgNo)
    const list = listOf(conversationId)
    const target = list.find((m) => m.msgNo === msgNo)
    if (target) {
      target.status = 2
      target.content = '消息已撤回'
      bundles.value[conversationId] = [...list]
    }
  }

  /** 收到远端撤回推送 */
  function applyRecall(payload: RecallPayload) {
    const list = listOf(payload.conversationId)
    const target = list.find((m) => m.msgNo === payload.msgNo || m.seq === payload.seq)
    if (!target) return
    target.status = 2
    target.content = target.msgId > 0 ? '对方撤回了一条消息' : '消息已撤回'
    bundles.value[payload.conversationId] = [...list]
  }

  /* ---------------- 已读 ---------------- */

  async function markRead(conversationId: number, lastReadSeq: number) {
    try {
      const res = await conversationApi.markRead(conversationId, lastReadSeq)
      conversationStore.setUnread(conversationId, res?.unreadCount ?? 0)
    } catch (e) {
      console.warn('已读上报失败', e)
    }
    // 实时通道同步给对方，失败无妨（HTTP 已成功）
    ws.send('read', { conversationId, lastReadSeq }, { queued: false })
  }

  /* ---------------- 重连补拉 ---------------- */

  /** 断线重连后批量补齐离线消息 */
  async function sync(items: SyncItem[]) {
    if (items.length === 0) return
    const messages = await messageApi.sync(items)
    const grouped = new Map<number, ChatMessage[]>()
    for (const m of messages ?? []) {
      const arr = grouped.get(m.conversationId) ?? []
      arr.push(m as ChatMessage)
      grouped.set(m.conversationId, arr)
    }
    for (const [conversationId, arr] of grouped) {
      bundles.value[conversationId] = merge(listOf(conversationId), arr)
      const latest = arr[arr.length - 1]
      if (latest) {
        conversationStore.patchSummary(conversationId, {
          msgId: latest.msgId,
          msgNo: latest.msgNo,
          seq: latest.seq,
          senderId: latest.senderId,
          msgType: latest.msgType,
          preview: previewText(latest.msgType, latest.content),
          sendTime: latest.sendTime,
        })
      }
    }
  }

  function clear() {
    bundles.value = {}
    pages.value = {}
    ackWaiters.clear()
  }

  function clearConversation(conversationId: number) {
    delete bundles.value[conversationId]
    delete pages.value[conversationId]
  }

  const totalOf = computed(() => (conversationId: number) => (bundles.value[conversationId] ?? []).length)

  return {
    bundles,
    pages,
    listOf,
    maxSeq,
    loadLatest,
    loadMore,
    pageStateOf,
    upsertIncoming,
    resolveAck,
    send,
    sendToPeer,
    resend,
    recall,
    applyRecall,
    markRead,
    sync,
    clear,
    clearConversation,
    totalOf,
  }
})
