<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { RouterView, useRoute, useRouter } from 'vue-router'
import UserAvatar from '@/components/common/UserAvatar.vue'
import AppIcon from '@/components/common/AppIcon.vue'
import ConversationList from '@/components/chat/ConversationList.vue'
import ContactList from '@/components/contact/ContactList.vue'
import { useAuthStore } from '@/stores/auth'
import { useConversationStore } from '@/stores/conversation'
import { useFriendStore } from '@/stores/friend'
import { useRealtimeStore } from '@/stores/realtime'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const conversationStore = useConversationStore()
const friendStore = useFriendStore()
const realtime = useRealtimeStore()

/**
 * 桌面端（Electron）：预加载脚本暴露的标记。
 * 桌面端界面铺满窗口，不套用网页版「居中悬浮卡片」的 20px 外边距与圆角。
 */
const isDesktop = Boolean(
  (window as unknown as { lufengDesktop?: { isDesktop?: boolean } }).lufengDesktop?.isDesktop,
)

/**
 * 当前一级 Tab。由路由推导，不用本地状态 —— 否则刷新页面、浏览器前进后退
 * 时会出现「路由在联系人、侧栏高亮在消息」的错位。
 */
const activeTab = computed<'chat' | 'contacts' | 'profile'>(() => {
  const name = route.name as string
  if (name === 'contacts') return 'contacts'
  if (name === 'profile') return 'profile'
  return 'chat'
})

/** 记住上一次选中的好友，切走再切回来时恢复右侧详情 */
const lastContactId = ref<number | null>(null)
watch(
  () => route.params.userId,
  (raw) => {
    const parsed = Number(Array.isArray(raw) ? raw[0] : raw)
    if (Number.isFinite(parsed) && parsed > 0) lastContactId.value = parsed
  },
  { immediate: true },
)

/** 切回「消息」时恢复到上次打开的会话，而不是丢掉选中项 */
function goChat() {
  const id = conversationStore.currentId
  router.push(id ? { name: 'chat', params: { id } } : { name: 'chat' })
}

function goContacts() {
  const id = lastContactId.value
  router.push(id ? { name: 'contacts', params: { userId: id } } : { name: 'contacts' })
}

async function logout() {
  realtime.stop()
  await auth.logout()
  conversationStore.clear()
  friendStore.clear()
}

onMounted(() => {
  void conversationStore.load()
  void friendStore.load()
  void friendStore.loadRequests()
  realtime.start()
})
</script>

<template>
  <div class="shell" :class="{ 'is-desktop': isDesktop }">
    <div class="window">
      <aside class="nav">
        <button class="me" title="我的" @click="router.push({ name: 'profile' })">
          <UserAvatar :name="auth.nickname" :src="auth.avatar" :size="38" :online="null" />
        </button>

        <nav class="tabs">
          <button
            class="tab"
            :class="{ active: activeTab === 'chat' }"
            title="消息"
            @click="goChat"
          >
            <AppIcon name="chat" :size="21" />
            <em v-if="conversationStore.totalUnread > 0" class="badge">
              {{ conversationStore.totalUnread > 99 ? '99+' : conversationStore.totalUnread }}
            </em>
          </button>
          <button
            class="tab"
            :class="{ active: activeTab === 'contacts' }"
            title="联系人"
            @click="goContacts"
          >
            <AppIcon name="contacts" :size="21" />
            <em v-if="friendStore.incoming.length > 0" class="badge">
              {{ friendStore.incoming.length }}
            </em>
          </button>
          <button
            class="tab"
            :class="{ active: activeTab === 'profile' }"
            title="我的"
            @click="router.push({ name: 'profile' })"
          >
            <AppIcon name="user" :size="21" />
          </button>
        </nav>

        <button class="tab exit" title="退出登录" @click="logout">
          <AppIcon name="logout" :size="20" />
        </button>
      </aside>

      <!-- 列表栏随 Tab 切换；「我的」页是整页表单，不需要列表栏 -->
      <section v-if="activeTab !== 'profile'" class="list-pane">
        <ConversationList v-if="activeTab === 'chat'" />
        <ContactList v-else />
      </section>

      <section class="content-pane">
        <RouterView />
      </section>
    </div>
  </div>
</template>

<style scoped>
.shell {
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 20px;
  background: radial-gradient(circle at 20% 20%, #e7f6ee 0%, #eef0f3 45%, #e4e8ef 100%);
}

.window {
  width: min(1220px, 100%);
  height: min(860px, 100%);
  display: flex;
  overflow: hidden;
  background: var(--panel);
  border-radius: 14px;
  box-shadow: var(--shadow-lg);
  border: 1px solid #e3e6ea;
}

.nav {
  width: var(--nav-w);
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  padding: 18px 0 14px;
  background: #2f3238;
}

.me {
  border-radius: 8px;
  line-height: 0;
  transition: opacity 0.15s ease;
}

.me:hover {
  opacity: 0.85;
}

.tabs {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin-top: 14px;
  width: 100%;
  align-items: center;
}

.tab {
  position: relative;
  width: 42px;
  height: 42px;
  border-radius: 9px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #9aa0a8;
  transition: background 0.15s ease, color 0.15s ease;
}

.tab:hover {
  background: #3c4046;
  color: #e2e5e9;
}

.tab.active {
  background: #3f434a;
  color: var(--brand);
}

.exit {
  margin-top: auto;
}

.badge {
  position: absolute;
  top: 1px;
  right: 1px;
  min-width: 16px;
  height: 16px;
  padding: 0 4px;
  border-radius: 8px;
  background: #fa5151;
  color: #fff;
  font-size: 11px;
  line-height: 16px;
  text-align: center;
  font-style: normal;
}

.list-pane {
  width: var(--list-w);
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  background: var(--aside);
  border-right: 1px solid var(--border);
}

.content-pane {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  background: #f8f9fb;
}

/**
 * 桌面端（Electron）：去掉「悬浮卡片」的 20px 外边距、渐变底与圆角/边框，
 * 让界面铺满整个窗口，避免四周出现难看的一圈空白。
 */
:global(html.is-desktop) .shell {
  padding: 0;
  background: var(--panel);
}

:global(html.is-desktop) .window {
  width: 100%;
  height: 100%;
  border-radius: 0;
  border: none;
  box-shadow: none;
}

@media (max-width: 960px) {
  .shell {
    padding: 0;
  }
  .window {
    border-radius: 0;
    border: none;
  }
  .list-pane {
    width: 240px;
  }
}
</style>
