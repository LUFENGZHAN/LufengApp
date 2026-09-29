import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import type { ConversationVO, LastMessageVO } from '@/api/types'
import { conversationApi } from '@/api/conversation'

function sortByLatest(list: ConversationVO[]): ConversationVO[] {
  return [...list].sort((a, b) => (b.lastMessageAt ?? 0) - (a.lastMessageAt ?? 0))
}

export const useConversationStore = defineStore('conversation', () => {
  const list = ref<ConversationVO[]>([])
  const loading = ref(false)
  const currentId = ref<number | null>(null)

  const current = computed(
    () => list.value.find((c) => c.conversationId === currentId.value) ?? null,
  )
  const totalUnread = computed(() =>
    list.value.reduce((sum, c) => sum + (c.unreadCount ?? 0), 0),
  )

  async function load() {
    loading.value = true
    try {
      const data = await conversationApi.list(1, 100)
      list.value = sortByLatest(data ?? [])
    } finally {
      loading.value = false
    }
  }

  function upsert(conversation: ConversationVO) {
    const idx = list.value.findIndex((c) => c.conversationId === conversation.conversationId)
    if (idx >= 0) {
      list.value[idx] = { ...list.value[idx], ...conversation }
    } else {
      list.value.unshift(conversation)
    }
    list.value = sortByLatest(list.value)
  }

  /** 新建或复用单聊会话（后端用确定性 conversation_no 保证幂等） */
  async function ensurePrivate(targetUserId: number): Promise<ConversationVO> {
    const existing = list.value.find((c) => c.peer?.userId === targetUserId)
    if (existing) return existing
    const conv = await conversationApi.createPrivate(targetUserId)
    upsert(conv)
    return conv
  }

  /** 收到/发出消息后更新会话摘要：只在 seq 更大时覆盖，避免旧消息倒灌 */
  function patchSummary(conversationId: number, lastMessage: LastMessageVO | null, opts: {
    incrementUnread?: boolean
    unreadCount?: number
  } = {}) {
    const conv = list.value.find((c) => c.conversationId === conversationId)
    if (!conv) return
    const newer = !conv.lastMessage || !lastMessage || lastMessage.seq >= conv.lastMessage.seq
    if (!newer) return
    conv.lastMessage = lastMessage ?? conv.lastMessage
    conv.lastMessageAt = lastMessage?.sendTime ?? conv.lastMessageAt
    if (opts.unreadCount !== undefined) {
      conv.unreadCount = opts.unreadCount
    } else if (opts.incrementUnread && conversationId !== currentId.value) {
      conv.unreadCount = (conv.unreadCount ?? 0) + 1
    }
    list.value = sortByLatest(list.value)
  }

  function setUnread(conversationId: number, count: number) {
    const conv = list.value.find((c) => c.conversationId === conversationId)
    if (conv) conv.unreadCount = count
  }

  function setCurrent(conversationId: number | null) {
    currentId.value = conversationId
  }

  function remove(conversationId: number) {
    list.value = list.value.filter((c) => c.conversationId !== conversationId)
    if (currentId.value === conversationId) currentId.value = null
  }

  function findByPeer(userId: number) {
    return list.value.find((c) => c.peer?.userId === userId) ?? null
  }

  function clear() {
    list.value = []
    currentId.value = null
  }

  return {
    list,
    loading,
    currentId,
    current,
    totalUnread,
    load,
    upsert,
    ensurePrivate,
    patchSummary,
    setUnread,
    setCurrent,
    remove,
    findByPeer,
    clear,
  }
})
