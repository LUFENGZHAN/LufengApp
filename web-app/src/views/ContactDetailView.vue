<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import UserAvatar from '@/components/common/UserAvatar.vue'
import AppIcon from '@/components/common/AppIcon.vue'
import { useFriendStore } from '@/stores/friend'
import { useConversationStore } from '@/stores/conversation'
import { useUiStore } from '@/stores/ui'
import { ApiError } from '@/api/http'

const route = useRoute()
const router = useRouter()
const friendStore = useFriendStore()
const conversationStore = useConversationStore()
const ui = useUiStore()

const busy = ref(false)
const confirmRemove = ref(false)

const friendId = computed(() => {
  const raw = route.params.userId
  const parsed = Number(Array.isArray(raw) ? raw[0] : raw)
  return Number.isFinite(parsed) && parsed > 0 ? parsed : null
})

const friend = computed(() =>
  friendId.value ? friendStore.friends.find((f) => f.userId === friendId.value) ?? null : null,
)

// 切换好友时收起二次确认
watch(friendId, () => {
  confirmRemove.value = false
})

async function openChat() {
  if (!friend.value) return
  // 不预建会话：只带 peer 进入聊天，第一条消息发出时服务端才创建单聊
  await router.push({ name: 'chat', query: { peer: String(friend.value.userId) } })
}

async function removeFriend() {
  if (!friend.value) return
  try {
    busy.value = true
    await friendStore.remove(friend.value.userId)
    conversationStore.remove(
      conversationStore.findByPeer(friend.value.userId)?.conversationId ?? -1,
    )
    ui.success('已删除好友')
    await router.replace({ name: 'contacts' })
  } catch (e) {
    ui.error(e instanceof ApiError ? e.message : '删除失败')
  } finally {
    busy.value = false
    confirmRemove.value = false
  }
}
</script>

<template>
  <div class="detail app-scroll">
    <template v-if="friend">
      <div class="inner">
        <section class="card hero">
          <UserAvatar
            :name="friend.remark || friend.nickname"
            :src="friend.avatar"
            :size="72"
            :radius="12"
            :online="friend.online ?? false"
          />
          <div class="who">
            <h2>{{ friend.remark || friend.nickname }}</h2>
            <p>
              <span class="state" :class="{ on: friend.online }">
                {{ friend.online ? '在线' : '离线' }}
              </span>
              · 麓风号 {{ friend.userNo }}
            </p>
          </div>
        </section>

        <section class="card block">
          <h3>资料</h3>
          <dl class="rows">
            <div class="row">
              <dt>昵称</dt>
              <dd>{{ friend.nickname }}</dd>
            </div>
            <div class="row">
              <dt>备注</dt>
              <dd>{{ friend.remark || '未设置' }}</dd>
            </div>
            <div class="row">
              <dt>个性签名</dt>
              <dd>{{ friend.signature || '这个人很懒，什么都没写' }}</dd>
            </div>
          </dl>
        </section>

        <section class="card block">
          <h3>操作</h3>
          <div class="ops">
            <button class="btn" :disabled="busy" @click="openChat">
              <AppIcon name="chat" :size="16" />
              发消息
            </button>
            <template v-if="!confirmRemove">
              <button class="btn btn-danger" @click="confirmRemove = true">删除好友</button>
            </template>
            <template v-else>
              <button class="btn btn-danger" :disabled="busy" @click="removeFriend">
                确认删除
              </button>
              <button class="btn btn-ghost" @click="confirmRemove = false">取消</button>
            </template>
          </div>
        </section>
      </div>
    </template>

    <div v-else class="placeholder">
      <div class="logo">
        <AppIcon name="contacts" :size="30" />
      </div>
      <h3>选择一位好友查看资料</h3>
      <p>左侧点击好友，或点右上角 + 添加新的好友</p>
    </div>
  </div>
</template>

<style scoped>
.detail {
  flex: 1;
  min-height: 0;
  background: #f8f9fb;
}

.inner {
  max-width: 560px;
  margin: 0 auto;
  padding: 24px 20px 40px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.hero {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 18px;
}

h2 {
  margin: 0 0 5px;
  font-size: 17px;
}

.who p {
  margin: 0;
  font-size: 12.5px;
  color: var(--text-weak);
}

.state {
  color: var(--text-weak);
}

.state.on {
  color: var(--brand);
}

.block {
  padding: 18px;
}

h3 {
  margin: 0 0 14px;
  font-size: 14px;
  font-weight: 600;
}

.rows {
  margin: 0;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.row {
  display: flex;
  gap: 12px;
  font-size: 13px;
}

dt {
  width: 68px;
  flex-shrink: 0;
  color: var(--text-weak);
}

dd {
  margin: 0;
  color: var(--text);
  word-break: break-word;
}

.ops {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.placeholder {
  flex: 1;
  height: 100%;
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
  background: var(--brand-soft);
  color: var(--brand);
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 8px;
}

.placeholder h3 {
  margin: 0;
  font-size: 15px;
  color: var(--text);
  font-weight: 600;
}

.placeholder p {
  margin: 0;
  font-size: 13px;
}
</style>
