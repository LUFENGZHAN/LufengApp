<script setup lang="ts">
import { computed, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import UserAvatar from '@/components/common/UserAvatar.vue'
import MessageListView from '@/components/chat/MessageListView.vue'
import MessageComposer from '@/components/chat/MessageComposer.vue'
import { useConversationStore } from '@/stores/conversation'
import { useMessageStore, pendingKeyOf } from '@/stores/message'
import { useFriendStore } from '@/stores/friend'
import { convName, convAvatar, convPeerOnline } from '@/utils/display'

const route = useRoute()
const router = useRouter()
const conversationStore = useConversationStore()
const messageStore = useMessageStore()
const friendStore = useFriendStore()

/** 路由上的会话号（已存在的会话） */
const routeConversationId = computed(() => {
  const raw = route.params.id
  const parsed = Number(Array.isArray(raw) ? raw[0] : raw)
  return Number.isFinite(parsed) && parsed > 0 ? parsed : null
})

/** /chat?peer=123：好友还没建会话时的「待创建会话」入口 */
const peerParam = computed(() => {
  const raw = route.query.peer
  const parsed = Number(Array.isArray(raw) ? raw[0] : raw)
  return Number.isFinite(parsed) && parsed > 0 ? parsed : null
})

/** peer 对应的会话（可能已经存在，比如之前聊过或对方先发了消息） */
const peerConversation = computed(() =>
  peerParam.value ? conversationStore.findByPeer(peerParam.value) : null,
)

const conversationId = computed(
  () => routeConversationId.value ?? peerConversation.value?.conversationId ?? null,
)

/**
 * 真正处于「会话尚未创建」的 peer 模式：路由只带了 peer，且列表里查无此会话。
 * 此时不调任何创建接口 —— 只有第一条消息发出才会生成会话。
 */
const pendingPeerId = computed(() =>
  peerParam.value && !peerConversation.value ? peerParam.value : null,
)

const conversation = computed(() =>
  conversationId.value
    ? conversationStore.list.find((c) => c.conversationId === conversationId.value) ?? null
    : null,
)

const peerFriend = computed(() =>
  pendingPeerId.value
    ? friendStore.friends.find((f) => f.userId === pendingPeerId.value) ?? null
    : null,
)

/** peer 模式下本地消息先放在 -peerId 桶，会话建好后迁移到真实会话 */
const pendingKey = computed(() =>
  pendingPeerId.value ? pendingKeyOf(pendingPeerId.value) : null,
)

const title = computed(() =>
  conversation.value
    ? convName(conversation.value, friendStore.friends)
    : peerFriend.value
      ? peerFriend.value.remark || peerFriend.value.nickname
      : '',
)
const avatar = computed(() =>
  conversation.value
    ? convAvatar(conversation.value)
    : peerFriend.value?.avatar ?? null,
)
const online = computed(() =>
  conversation.value
    ? convPeerOnline(conversation.value, friendStore.friends)
    : peerFriend.value?.online ?? null,
)
const userNo = computed(() =>
  conversation.value?.peer?.userNo ?? peerFriend.value?.userNo ?? null,
)
const current = computed(() =>
  conversationId.value ? messageStore.pageStateOf(conversationId.value) : null,
)

async function activate(cid: number) {
  conversationStore.setCurrent(cid)
  await messageStore.loadLatest(cid)
  // 已读上报：以本地最大 seq 为位点，服务端用 GREATEST 防回退
  const seq = messageStore.maxSeq(cid)
  if (seq > 0) await messageStore.markRead(cid, seq)
}

watch(
  conversationId,
  async (cid) => {
    if (cid == null) {
      conversationStore.setCurrent(null)
      return
    }
    await activate(cid)
  },
  { immediate: true },
)

/**
 * 会话已建好（自己/对方发了第一条消息，或刷新后列表里已有）：
 * 把地址栏从 ?peer= 收敛成 /chat/:id，保证刷新后还是同一会话
 */
watch(
  [routeConversationId, peerParam, () => peerConversation.value?.conversationId ?? null],
  () => {
    const cid = peerConversation.value?.conversationId
    if (peerParam.value && !routeConversationId.value && cid) {
      void router.replace({ name: 'chat', params: { id: cid } })
    }
  },
  { immediate: true },
)

/** 进入会话后收到的消息即时标记为已读 */
watch(
  () => messageStore.maxSeq(conversationId.value ?? 0),
  async (seq) => {
    if (conversationId.value && seq > 0 && conversation.value?.unreadCount) {
      await messageStore.markRead(conversationId.value, seq)
    }
  },
)

/** 首条消息发送成功：会话已由服务端懒创建，刷新列表并跳转 */
async function onFirstMessageSent(cid: number) {
  await conversationStore.load()
  await router.replace({ name: 'chat', params: { id: cid } })
}
</script>

<template>
  <div class="chat-view">
    <template v-if="conversationId && conversation">
      <header class="header">
        <UserAvatar :name="title" :src="avatar" :online="online" :size="36" :radius="6" />
        <div class="title-col">
          <h2 class="ellipsis">{{ title }}</h2>
          <span class="sub">
            {{ online ? '在线' : '离线' }}
            <template v-if="userNo">· 麓风号 {{ userNo }}</template>
          </span>
        </div>
      </header>

      <MessageListView
        :conversation-id="conversationId"
        :peer-id="conversation?.peer?.userId ?? peerParam"
      />
      <MessageComposer :conversation-id="conversationId" />
    </template>

    <!-- 好友还没有会话：不预建，等第一条消息发出时才生成 -->
    <template v-else-if="pendingPeerId && pendingKey !== null">
      <header class="header">
        <UserAvatar :name="title" :src="avatar" :online="online" :size="36" :radius="6" />
        <div class="title-col">
          <h2 class="ellipsis">{{ title }}</h2>
          <span class="sub">
            {{ online ? '在线' : '离线' }}
            <template v-if="userNo">· 麓风号 {{ userNo }}</template>
            · 还没有聊天记录
          </span>
        </div>
      </header>

      <MessageListView :conversation-id="pendingKey" :peer-id="pendingPeerId" />
      <MessageComposer
        :conversation-id="pendingKey"
        :peer-id="pendingPeerId"
        @sent="onFirstMessageSent"
      />
    </template>

    <div v-else class="placeholder">
      <div class="logo">麓</div>
      <h3>选择一个会话开始聊天</h3>
      <p>左侧点击会话，或到「联系人」添加好友</p>
      <span v-if="current?.hasMore" class="extra">该会话有更早的消息可以加载</span>
    </div>
  </div>
</template>

<style scoped>
.chat-view {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  height: 100%;
}

.header {
  height: var(--header-h);
  flex-shrink: 0;
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 0 20px;
  border-bottom: 1px solid var(--border);
  background: #fff;
}

.title-col {
  min-width: 0;
}

h2 {
  margin: 0;
  font-size: 15px;
  font-weight: 600;
}

.sub {
  font-size: 12px;
  color: var(--text-weak);
}

.placeholder {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 6px;
  color: var(--text-sub);
}

.logo {
  width: 72px;
  height: 72px;
  border-radius: 20px;
  background: var(--brand);
  color: #fff;
  font-size: 34px;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 8px;
}

h3 {
  margin: 0;
  font-size: 16px;
  color: var(--text);
}

p {
  margin: 0;
  font-size: 13px;
}

.extra {
  margin-top: 10px;
  font-size: 12px;
  color: var(--text-weak);
}
</style>
