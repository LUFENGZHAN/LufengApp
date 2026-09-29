<script setup lang="ts">
import { computed, ref } from 'vue'
import { RouterLink } from 'vue-router'
import type { ConversationVO } from '@/api/types'
import UserAvatar from '@/components/common/UserAvatar.vue'
import ConnectionBadge from '@/components/common/ConnectionBadge.vue'
import { useConversationStore } from '@/stores/conversation'
import { useFriendStore } from '@/stores/friend'
import { formatListTime } from '@/utils/time'

const conversationStore = useConversationStore()
const friendStore = useFriendStore()
const keyword = ref('')

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
</script>

<template>
  <div class="pane">
    <header class="top">
      <input v-model="keyword" class="search" placeholder="搜索会话" />
      <ConnectionBadge />
    </header>

    <div class="list app-scroll">
      <RouterLink
        v-for="item in items"
        :key="item.conversationId"
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

.item {
  display: flex;
  gap: 10px;
  padding: 10px 12px;
  cursor: pointer;
}

.item:hover {
  background: var(--list-hover);
}

.item.active {
  background: var(--list-active);
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
