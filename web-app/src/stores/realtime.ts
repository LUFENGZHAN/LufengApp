import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { MessageVO } from '@/api/types'
import type {
  FriendNotifyPayload,
  KickPayload,
  ReadPayload,
  RecallPayload,
  TypingPayload,
  ErrorPayload,
  WsState,
} from '@/ws/protocol'
import { ws } from '@/ws/client'
import { useAuthStore } from './auth'
import { useConversationStore } from './conversation'
import { useMessageStore } from './message'
import { useFriendStore } from './friend'
import { useUiStore } from './ui'

/** typing 状态有效期，超时自动清理，避免对方异常断线后一直显示「正在输入」 */
const TYPING_TTL = 6000

export const useRealtimeStore = defineStore('realtime', () => {
  const state = ref<WsState>('idle')
  /** conversationId → { userId, at } */
  const typing = ref<Record<number, { userId: number; at: number }>>({})
  const syncing = ref(false)

  let disposers: Array<() => void> = []
  let typingTimer: ReturnType<typeof setInterval> | null = null

  const online = ref(false)

  function isTyping(conversationId: number, userId: number) {
    const record = typing.value[conversationId]
    return !!record && record.userId === userId && Date.now() - record.at < TYPING_TTL
  }

  /* ---------------- 事件处理 ---------------- */

  async function onChat(message: MessageVO) {
    const messageStore = useMessageStore()
    const conversationStore = useConversationStore()
    const auth = useAuthStore()

    const known = conversationStore.list.some((c) => c.conversationId === message.conversationId)
    if (!known) {
      // 首次收到该会话的消息：会话列表里还没有它
      await conversationStore.load()
    }

    messageStore.upsertIncoming(message)
    const mine = message.senderId === auth.userId
    conversationStore.patchSummary(message.conversationId, {
      msgId: message.msgId,
      msgNo: message.msgNo,
      seq: message.seq,
      senderId: message.senderId,
      msgType: message.msgType,
      preview: message.status === 2 ? '[已撤回]' : message.content ?? '',
      sendTime: message.sendTime,
    }, mine ? {} : { incrementUnread: true })

    // 当前会话且非本人发的：立即上报已读
    if (conversationStore.currentId === message.conversationId && !mine) {
      void messageStore.markRead(message.conversationId, message.seq)
    }
  }

  function onAck(payload: { clientMsgId: string; seq: number; msgNo: string; sendTime: number; duplicated: boolean }) {
    useMessageStore().resolveAck(payload)
  }

  function onRead(payload: ReadPayload) {
    // 对方把会话标记已读：本端只是感知，单聊无「已读/未读」UI，可在此扩展双勾状态
    void payload
  }

  function onTyping(payload: TypingPayload) {
    typing.value[payload.conversationId] = { userId: payload.userId, at: Date.now() }
  }

  function onNotify(payload: RecallPayload | FriendNotifyPayload) {
    const friendStore = useFriendStore()
    const conversationStore = useConversationStore()
    const ui = useUiStore()
    if (payload.kind === 'recall') {
      useMessageStore().applyRecall(payload as RecallPayload)
      return
    }
    const notify = payload as FriendNotifyPayload
    // 好友关系变化会连带影响会话：加回好友要恢复会话，被删除要移除会话
    const needReloadConversations =
      notify.kind === 'friend_accepted' ||
      notify.kind === 'friend_removed' ||
      notify.kind === 'friend_deleted'
    switch (notify.kind) {
      case 'friend_request':
        ui.info('收到一条好友申请')
        break
      case 'friend_accepted':
        ui.success('好友申请已通过')
        break
      case 'friend_rejected':
        ui.info('好友申请被拒绝')
        break
      case 'friend_removed':
        ui.info('对方已将你从好友中删除')
        break
      case 'friend_deleted':
        // 本人其他端触发的删除，无需提示，只同步会话列表
        break
      default:
        break
    }
    // 关系链变化：刷新好友列表与申请列表
    void friendStore.loadRequests()
    void friendStore.load()
    if (needReloadConversations) {
      void conversationStore.load()
    }
  }

  function onKick(payload: KickPayload) {
    void useAuthStore().handleSessionExpired(20005, payload?.reason || '账号已在其他设备登录')
  }

  function onError(payload: ErrorPayload) {
    useUiStore().error(payload?.message || '服务端返回错误')
  }

  /** 连接建立：先刷会话摘要，再按本地 seq 位点批量补拉离线消息 */
  async function syncAfterConnected() {
    const conversationStore = useConversationStore()
    const messageStore = useMessageStore()
    syncing.value = true
    try {
      await conversationStore.load()
      const items = conversationStore.list.map((c) => ({
        conversationId: c.conversationId,
        afterSeq: messageStore.maxSeq(c.conversationId),
      }))
      await messageStore.sync(items)
    } catch (e) {
      console.warn('重连补拉失败', e)
    } finally {
      syncing.value = false
    }
  }

  /* ---------------- 生命周期 ---------------- */

  function start() {
    if (disposers.length > 0) return
    disposers.push(
      ws.onStateChange(async (next) => {
        state.value = next
        online.value = next === 'open'
        if (next === 'open') await syncAfterConnected()
      }),
      ws.on<MessageVO>('chat', (payload) => void onChat(payload)),
      ws.on<Parameters<typeof onAck>[0]>('ack', onAck),
      ws.on<ReadPayload>('read', onRead),
      ws.on<TypingPayload>('typing', onTyping),
      ws.on<RecallPayload | FriendNotifyPayload>('notify', onNotify),
      ws.on<KickPayload>('kick', onKick),
      ws.on<ErrorPayload>('error', onError),
    )
    if (typingTimer === null) {
      typingTimer = setInterval(() => {
        const now = Date.now()
        const next: Record<number, { userId: number; at: number }> = {}
        for (const [cid, record] of Object.entries(typing.value)) {
          if (now - record.at < TYPING_TTL) next[Number(cid)] = record
        }
        typing.value = next
      }, 2000)
    }
  }

  function stop() {
    for (const dispose of disposers) dispose()
    disposers = []
    if (typingTimer) {
      clearInterval(typingTimer)
      typingTimer = null
    }
    ws.close('logout')
    state.value = 'idle'
    online.value = false
    typing.value = {}
  }

  /** 输入中：节流由调用方控制，此处直接发 */
  function sendTyping(conversationId: number) {
    ws.send('typing', { conversationId }, { queued: false })
  }

  return {
    state,
    online,
    typing,
    syncing,
    start,
    stop,
    sendTyping,
    isTyping,
  }
})
