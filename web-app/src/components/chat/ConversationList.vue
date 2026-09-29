<script setup lang="ts">
import { computed, ref } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import type { ConversationVO } from '@/api/types'
import UserAvatar from '@/components/common/UserAvatar.vue'
import AppIcon from '@/components/common/AppIcon.vue'
import ConnectionBadge from '@/components/common/ConnectionBadge.vue'
import { useConversationStore } from '@/stores/conversation'
import { useFriendStore } from '@/stores/friend'
import { useUiStore } from '@/stores/ui'
import { formatListTime } from '@/utils/time'

const conversationStore = useConversationStore()
const friendStore = useFriendStore()
const ui = useUiStore()
const route = useRoute()
const router = useRouter()
const keyword = ref('')
const pendingId = ref<number | null>(null)

function displayName(c: ConversationVO): string {
  if (c.type !== 1) return c.title || '群聊'
  const remark = friendStore.friends.find((f) => f.userId === c.peer?.userId)?.remark
  return remark || c.peer?.nickname || c.title || '陌生人'
}

function peerOnline(c: ConversationVO): boolean | null {
  if (c.type !== 1 || !c.peer) return null
  return friendStore.friends.find((f) => f.userId === c.peer?.userId)?.online ?? c.peer.online ?? false
}

const items = computed(() => {
  const kw = keyword.value.trim().toLowerCase()
  const source = [...conversationStore.list]
  if (!kw) return source
  return source.filter(
    (c) =>
      displayName(c).toLowerCase().includes(kw) ||
      (c.lastMessage?.preview ?? '').toLowerCase().includes(kw),
  )
})

async function onDelete(c: ConversationVO) {
  if (pendingId.value) return
  const ok = await ui.confirm({
    title: '删除会话',
    message: `确定删除与「${displayName(c)}」的会话吗？\n删除后将从你的列表移除，消息仍保留、对方不受影响。`,
    confirmText: '删除',
    cancelText: '取消',
    danger: true,
  })
  if (!ok) return
  pendingId.value = c.conversationId
  try {
    await conversationStore.deleteConversation(c.conversationId)
    // 删的是当前正在看的会话：回到占位页
    const openId = Number(route.params.id)
    if (openId === c.conversationId) await router.replace({ name: 'chat' })
  } catch (e) {
    ui.error(e instanceof Error ? e.message : '删除失败，请重试')  } finally {
    pendingId.value = null
  }
}
</script>

<template>
  <div class="pane">
    <header class="top">
      <input v-model="keyword" class="search" placeholder="搜索会话" />
      <ConnectionBadge />
    </header>

    <div class="list app-scroll">
      <div v-for="item in items" :key="item.conversationId" class="item-wrap">
        <RouterLink
          class="item"
          :class="{ active: item.conversationId === conversationStore.currentId }"
          :to="{ name: 'chat', params: { id: item.conversationId } }"
        >
          <UserAvatar
            :name="displayName(item)"
            :src="item.type === 1 ? item.peer?.avatar : item.avatar"
            :online="peerOnline(item)"
            :size="42"
            :radius="8"
          />
          <div class="meta">
            <div class="row-1">
              <span class="name ellipsis">{{ displayName(item) }}</span>
              <span class="time">{{ formatListTime(item.lastMessageAt) }}</span>
            </div>
            <div class="row-2">
              <span class="preview ellipsis">
                {{ item.lastMessage?.preview || '暂无消息' }}
              </span>
              <span v-if="item.unreadCount > 0" class="unread">
                {{ item.unreadCount > 99 ? '99+' : item.unreadCount }}
              </span>
            </div>
          </div>
        </RouterLink>

        <button
          class="del"
          :disabled="pendingId === item.conversationId"
          title="删除会话"
          @click="onDelete(item)"
        >
          <AppIcon name="trash" :size="16" />
        </button>
      </div>

      <p v-if="items.length === 0" class="empty">
        {{ keyword ? '没有匹配的会话' : '暂无会话，去联系人页添加好友吧' }}
      </p>
    </div>
  </div>
</template>

<style scoped>
.pane {
  display: flex;
  flex-direction: column;
  height: 100%;
}

.top {
  padding: 12px;
  display: flex;
  flex-direction: column;
  gap: 10px;
  border-bottom: 1px solid var(--border-soft);
}

.search {
  height: 32px;
  padding: 0 10px;
  border-radius: 16px;
  border: 1px solid transparent;
  background: #e9ebee;
  color: var(--text);
}

.search:focus {
  border-color: var(--brand);
  background: #fff;
}

.list {
  flex: 1;
  min-height: 0;
}

.item-wrap {
  position: relative;
  display: flex;
}

.item {
  flex: 1;
  min-width: 0;
  display: flex;
  gap: 10px;
  padding: 10px 12px;
  cursor: pointer;
}

.item-wrap:hover .item {
  /* 给右侧删除按钮留出空间，避免遮挡时间与未读 */
  padding-right: 44px;
}

.item:hover {
  background: var(--list-hover);
}

.item.active {
  background: var(--list-active);
}

.del {
  position: absolute;
  top: 50%;
  right: 10px;
  transform: translateY(-50%);
  width: 28px;
  height: 28px;
  border: none;
  border-radius: 8px;
  background: rgba(250, 81, 81, 0.12);
  color: #fa5151;
  display: none;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: background 0.15s ease, color 0.15s ease;
}

.item-wrap:hover .del {
  display: flex;
}

.del:hover:not(:disabled) {
  background: #fa5151;
  color: #fff;
}

.del:disabled {
  opacity: 0.5;
  cursor: default;
}

.meta {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 4px;
}

.row-1,
.row-2 {
  display: flex;
  align-items: center;
  gap: 8px;
}

.row-1 {
  justify-content: space-between;
}

.name {
  font-size: 14px;
  color: var(--text);
}

.time {
  font-size: 11px;
  color: var(--text-weak);
  flex-shrink: 0;
}

.preview {
  flex: 1;
  font-size: 12.5px;
  color: var(--text-sub);
}

.unread {
  flex-shrink: 0;
  min-width: 17px;
  height: 17px;
  padding: 0 5px;
  border-radius: 9px;
  background: #fa5151;
  color: #fff;
  font-size: 11px;
  line-height: 17px;
  text-align: center;
}

.empty {
  padding: 40px 16px;
  text-align: center;
  color: var(--text-weak);
  font-size: 13px;
}
</style>
