<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import UserAvatar from '@/components/common/UserAvatar.vue'
import { useMessageStore } from '@/stores/message'
import { useAuthStore } from '@/stores/auth'
import { useRealtimeStore } from '@/stores/realtime'
import { shouldSplit, formatDivider } from '@/utils/time'
import type { ChatMessage } from '@/stores/message'

const props = defineProps<{ conversationId: number; peerId?: number | null }>()

const messageStore = useMessageStore()
const auth = useAuthStore()
const realtime = useRealtimeStore()

const scroller = ref<HTMLElement | null>(null)
const loadingMore = ref(false)

const messages = computed(() => messageStore.listOf(props.conversationId))
const pageState = computed(() => messageStore.pageStateOf(props.conversationId))

/** 每条消息是否需要插入时间分隔线 */
const rows = computed(() =>
  messages.value.map((message, index) => {
    const prev = index > 0 ? messages.value[index - 1] : undefined
    return {
      message,
      showDivider: !prev || shouldSplit(prev.sendTime, message.sendTime),
    }
  }),
)

const peerTyping = computed(() =>
  props.peerId ? realtime.isTyping(props.conversationId, props.peerId) : false,
)

function isMine(m: ChatMessage) {
  return m.senderId === auth.userId
}

function scrollToBottom(smooth = false) {
  nextTick(() => {
    const el = scroller.value
    if (!el) return
    el.scrollTo({ top: el.scrollHeight, behavior: smooth ? 'smooth' : 'auto' })
  })
}

async function onScroll() {
  const el = scroller.value
  if (!el || loadingMore.value) return
  if (el.scrollTop > 60) return
  const page = pageState.value
  if (!page.hasMore || page.loading) return

  loadingMore.value = true
  const prevHeight = el.scrollHeight
  try {
    await messageStore.loadMore(props.conversationId)
    nextTick(() => {
      el.scrollTop = el.scrollHeight - prevHeight
    })
  } catch {
    /* 忽略：用户可再次上滑重试 */
  } finally {
    loadingMore.value = false
  }
}

watch(
  () => props.conversationId,
  () => scrollToBottom(),
)

watch(
  () => [messages.value.length, messages.value[messages.value.length - 1]?.status],
  () => scrollToBottom(true),
)

onMounted(() => scrollToBottom())
</script>

<template>
  <div ref="scroller" class="scroller app-scroll" @scroll.passive="onScroll">
    <div class="load-more">
      <span v-if="loadingMore || pageState.loading" class="hint">加载中…</span>
      <span v-else-if="pageState.hasMore" class="hint">上滑加载更多</span>
      <span v-else class="hint muted">没有更多了</span>
    </div>

    <template v-for="row in rows" :key="row.message.msgId || row.message.clientMsgId">
      <div v-if="row.showDivider" class="divider">
        <span>{{ formatDivider(row.message.sendTime) }}</span>
      </div>

      <div v-if="row.message.status === 2" class="system-row">
        {{ isMine(row.message) ? '你撤回了一条消息' : '对方撤回了一条消息' }}
      </div>

      <div v-else class="msg-row" :class="{ mine: isMine(row.message) }">
        <UserAvatar
          :name="row.message.senderNickname || '用户'"
          :src="row.message.senderAvatar"
          :size="38"
          :online="null"
          :radius="6"
        />
        <div class="bubble-col">
          <span class="sender" v-if="!isMine(row.message)">
            {{ row.message.senderNickname }}
          </span>
          <div class="bubble" :class="{ failed: row.message.failed, sending: row.message.sending }">
            <span class="content">{{ row.message.content }}</span>
          </div>
          <div v-if="row.message.failed" class="fail-bar">
            <span class="fail-text">发送失败</span>
            <span
              class="text-link retry"
              @click="messageStore.resend(conversationId, row.message.clientMsgId)"
            >
              重试
            </span>
          </div>
        </div>
      </div>
    </template>

    <div class="typing" v-if="peerTyping">对方正在输入<span class="dots">…</span></div>

    <p v-if="messages.length === 0" class="empty">还没有消息，发一条打个招呼吧</p>
  </div>
</template>

<style scoped>
.scroller {
  flex: 1;
  min-height: 0;
  padding: 12px 20px 20px;
  background: #f8f9fb;
}

.load-more {
  text-align: center;
  padding: 6px 0 14px;
}

.hint {
  font-size: 12px;
  color: var(--text-weak);
  cursor: default;
}

.hint.muted {
  opacity: 0.7;
}

.divider {
  display: flex;
  justify-content: center;
  margin: 14px 0;
}

.divider span {
  padding: 2px 10px;
  border-radius: 4px;
  background: rgba(0, 0, 0, 0.05);
  color: var(--text-weak);
  font-size: 11.5px;
}

.msg-row {
  display: flex;
  gap: 10px;
  margin-bottom: 14px;
  align-items: flex-start;
}

.msg-row.mine {
  flex-direction: row-reverse;
}

.bubble-col {
  max-width: min(560px, 62%);
  display: flex;
  flex-direction: column;
  gap: 3px;
}

.msg-row.mine .bubble-col {
  align-items: flex-end;
}

.sender {
  font-size: 11.5px;
  color: var(--text-weak);
  margin: 0 2px;
}

.bubble {
  position: relative;
  padding: 9px 12px;
  border-radius: 6px;
  background: var(--bubble-other);
  box-shadow: var(--shadow-sm);
  font-size: 14px;
  line-height: 1.55;
  word-break: break-word;
  white-space: pre-wrap;
}

.bubble.sending {
  opacity: 0.65;
}

.bubble.failed {
  background: #fff1f1;
  box-shadow: inset 0 0 0 1px #fecaca;
}

.content {
  display: block;
}

.fail-bar {
  display: flex;
  gap: 8px;
  align-items: center;
  font-size: 11.5px;
}

.fail-text {
  color: var(--danger);
}

.retry {
  cursor: pointer;
}

.system-row {
  text-align: center;
  margin: 10px 0;
  font-size: 12px;
  color: var(--text-weak);
}

.typing {
  padding: 2px 4px 10px;
  font-size: 12px;
  color: var(--text-sub);
}

.dots {
  letter-spacing: 2px;
}

.empty {
  padding: 60px 0;
  text-align: center;
  color: var(--text-weak);
  font-size: 13px;
}
</style>
