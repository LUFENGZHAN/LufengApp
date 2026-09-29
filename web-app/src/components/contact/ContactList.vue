<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import UserAvatar from '@/components/common/UserAvatar.vue'
import AppIcon from '@/components/common/AppIcon.vue'
import AddFriendDialog from '@/components/contact/AddFriendDialog.vue'
import { useFriendStore } from '@/stores/friend'
import { useConversationStore } from '@/stores/conversation'
import { useUiStore } from '@/stores/ui'
import { ApiError } from '@/api/http'

const route = useRoute()
const router = useRouter()
const friendStore = useFriendStore()
const conversationStore = useConversationStore()
const ui = useUiStore()

const keyword = ref('')
const tab = ref<'friends' | 'requests'>('friends')
const dialogOpen = ref(false)

const selectedId = computed(() => {
  const raw = route.params.userId
  const parsed = Number(Array.isArray(raw) ? raw[0] : raw)
  return Number.isFinite(parsed) && parsed > 0 ? parsed : null
})

const sortedFriends = computed(() => {
  const kw = keyword.value.trim().toLowerCase()
  return friendStore.friends
    .filter((f) => {
      if (!kw) return true
      return (
        (f.remark || '').toLowerCase().includes(kw) ||
        (f.nickname || '').toLowerCase().includes(kw) ||
        (f.userNo || '').toLowerCase().includes(kw)
      )
    })
    .sort((a, b) => {
      if ((a.online ?? false) !== (b.online ?? false)) return a.online ? -1 : 1
      return (a.remark || a.nickname || '').localeCompare(b.remark || b.nickname || '', 'zh-CN')
    })
})

function select(userId: number) {
  router.push({ name: 'contacts', params: { userId } })
}

async function handleRequest(requestId: number, action: 'accept' | 'reject') {
  try {
    await friendStore.handle(requestId, action)
    await conversationStore.load()
    ui.success(action === 'accept' ? '已添加好友' : '已拒绝申请')
  } catch (e) {
    ui.error(e instanceof ApiError ? e.message : '操作失败')
  }
}
</script>

<template>
  <div class="pane">
    <header class="top">
      <div class="search-box">
        <AppIcon name="search" :size="15" class="search-ico" />
        <input v-model="keyword" class="search" placeholder="搜索好友" />
      </div>
      <button class="icon-btn" title="添加好友" @click="dialogOpen = true">
        <AppIcon name="plus" :size="18" />
      </button>
    </header>

    <div class="tabs">
      <button class="tab" :class="{ active: tab === 'friends' }" @click="tab = 'friends'">
        好友
        <em class="count">{{ friendStore.friends.length }}</em>
      </button>
      <button class="tab" :class="{ active: tab === 'requests' }" @click="tab = 'requests'">
        新的朋友
        <em v-if="friendStore.incoming.length" class="count red">
          {{ friendStore.incoming.length }}
        </em>
      </button>
    </div>

    <div class="list app-scroll">
      <template v-if="tab === 'friends'">
        <button
          v-for="friend in sortedFriends"
          :key="friend.userId"
          class="item"
          :class="{ active: friend.userId === selectedId }"
          @click="select(friend.userId)"
        >
          <UserAvatar
            :name="friend.remark || friend.nickname"
            :src="friend.avatar"
            :online="friend.online ?? false"
            :size="40"
            :radius="8"
          />
          <div class="meta">
            <span class="name ellipsis">{{ friend.remark || friend.nickname }}</span>
            <span class="sub ellipsis">{{ friend.signature || `麓风号 ${friend.userNo}` }}</span>
          </div>
        </button>

        <p v-if="sortedFriends.length === 0" class="empty">
          {{ keyword ? '没有匹配的好友' : '还没有好友，点右上角 + 添加' }}
        </p>
      </template>

      <template v-else>
        <div v-for="req in friendStore.incoming" :key="req.requestId" class="item static">
          <UserAvatar :name="req.nickname" :src="req.avatar" :size="40" :radius="8" :online="null" />
          <div class="meta">
            <span class="name ellipsis">{{ req.nickname }}</span>
            <span class="sub ellipsis">{{ req.applyMsg || '请求添加你为好友' }}</span>
          </div>
          <div class="actions">
            <button class="mini primary" @click="handleRequest(req.requestId, 'accept')">
              <AppIcon name="check" :size="13" />
              接受
            </button>
            <button class="mini" @click="handleRequest(req.requestId, 'reject')">拒绝</button>
          </div>
        </div>

        <p v-if="friendStore.incoming.length === 0" class="empty">暂无待处理的好友申请</p>
      </template>
    </div>

    <AddFriendDialog v-model:open="dialogOpen" />
  </div>
</template>

<style scoped>
.pane {
  display: flex;
  flex-direction: column;
  height: 100%;
}

.top {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px;
  border-bottom: 1px solid var(--border-soft);
}

.search-box {
  position: relative;
  flex: 1;
  min-width: 0;
  display: flex;
  align-items: center;
}

.search-ico {
  position: absolute;
  left: 9px;
  color: var(--text-weak);
  pointer-events: none;
}

.search {
  width: 100%;
  height: 32px;
  padding: 0 10px 0 28px;
  border-radius: 16px;
  border: 1px solid transparent;
  background: #e9ebee;
  color: var(--text);
}

.search:focus {
  border-color: var(--brand);
  background: #fff;
}

.icon-btn {
  width: 32px;
  height: 32px;
  flex-shrink: 0;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--text-sub);
}

.icon-btn:hover {
  background: #e9ebee;
  color: var(--brand);
}

.tabs {
  display: flex;
  gap: 4px;
  padding: 8px 10px 4px;
}

.tab {
  flex: 1;
  height: 30px;
  border-radius: 6px;
  font-size: 13px;
  color: var(--text-sub);
}

.tab:hover {
  background: var(--border-soft);
}

.tab.active {
  background: var(--brand-soft);
  color: var(--brand-dark);
}

.count {
  font-style: normal;
  margin-left: 3px;
  color: var(--text-weak);
}

.count.red {
  color: #fa5151;
}

.list {
  flex: 1;
  min-height: 0;
  padding: 6px;
}

.item {
  width: 100%;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 9px 10px;
  border-radius: 8px;
  text-align: left;
}

.item.static {
  cursor: default;
}

button.item:hover {
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
  gap: 3px;
}

.name {
  font-size: 14px;
  color: var(--text);
}

.sub {
  font-size: 12px;
  color: var(--text-weak);
}

.actions {
  display: flex;
  gap: 6px;
  flex-shrink: 0;
}

.mini {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  height: 26px;
  padding: 0 9px;
  border-radius: 6px;
  border: 1px solid var(--border);
  background: #fff;
  color: var(--text-sub);
  font-size: 12px;
}

.mini:hover {
  background: var(--border-soft);
  color: var(--text);
}

.mini.primary {
  border-color: transparent;
  background: var(--brand);
  color: #fff;
}

.mini.primary:hover {
  background: var(--brand-dark);
  color: #fff;
}

.empty {
  padding: 40px 16px;
  text-align: center;
  color: var(--text-weak);
  font-size: 13px;
}
</style>
