<script setup lang="ts">
import { ref, watch } from 'vue'
import { userApi } from '@/api/auth'
import { friendApi } from '@/api/friend'
import type { UserVO } from '@/api/types'
import UserAvatar from '@/components/common/UserAvatar.vue'
import { useAuthStore } from '@/stores/auth'
import { useFriendStore } from '@/stores/friend'
import { useUiStore } from '@/stores/ui'
import { ApiError } from '@/api/http'

const props = defineProps<{ open: boolean }>()
const emit = defineEmits<{ (e: 'update:open', value: boolean): void }>()

const auth = useAuthStore()
const friendStore = useFriendStore()
const ui = useUiStore()

const keyword = ref('')
const applyMsg = ref('')
const results = ref<UserVO[]>([])
const searching = ref(false)
const appliedIds = ref<Set<number>>(new Set())
let timer: ReturnType<typeof setTimeout> | null = null

function close() {
  emit('update:open', false)
}

watch(
  () => props.open,
  (open) => {
    if (open) {
      keyword.value = ''
      applyMsg.value = ''
      results.value = []
      appliedIds.value = new Set()
    }
  },
)

watch(keyword, (value) => {
  if (timer) clearTimeout(timer)
  const kw = value.trim()
  if (!kw) {
    results.value = []
    return
  }
  timer = setTimeout(async () => {
    searching.value = true
    try {
      const list = await userApi.search(kw, 20)
      results.value = (list ?? []).filter((u) => u.userId !== auth.userId)
    } catch (e) {
      ui.error(e instanceof ApiError ? e.message : '搜索失败')
    } finally {
      searching.value = false
    }
  }, 300)
})

const isFriend = (userId: number) => friendStore.friends.some((f) => f.userId === userId)

async function apply(user: UserVO) {
  try {
    // 留言可留空，后端会用默认文案兜底
    const msg = applyMsg.value.trim()
    await friendApi.apply({ toUserId: user.userId, applyMsg: msg || undefined })
    appliedIds.value = new Set(appliedIds.value).add(user.userId)
    ui.success('好友申请已发送')
  } catch (e) {
    ui.error(e instanceof ApiError ? e.message : '申请失败')
  }
}
</script>

<template>
  <div v-if="open" class="mask" @click.self="close">
    <div class="dialog">
      <header>
        <h3>添加好友</h3>
        <button class="close" @click="close">×</button>
      </header>

      <div class="body">
        <input v-model="keyword" class="search" placeholder="按账号或昵称搜索" />
        <p class="hint">提示：可以按麓风号精确查找，例如 LF100001</p>

        <input
          v-model="applyMsg"
          class="search msg"
          maxlength="100"
          placeholder="验证消息（可选，对方可见）"
          @keyup.enter.stop
        />

        <div class="result app-scroll">
          <div v-for="user in results" :key="user.userId" class="row">
            <UserAvatar :name="user.nickname" :src="user.avatar" :size="38" :radius="6" />
            <div class="meta">
              <span class="name ellipsis">{{ user.nickname }}</span>
              <span class="sub ellipsis">麓风号 {{ user.userNo }}</span>
            </div>
            <button
              v-if="isFriend(user.userId)"
              class="btn btn-ghost"
              disabled
            >
              已是好友
            </button>
            <button
              v-else-if="appliedIds.has(user.userId)"
              class="btn btn-ghost"
              disabled
            >
              已申请
            </button>
            <button v-else class="btn" @click="apply(user)">申请</button>
          </div>

          <p v-if="searching" class="placeholder">搜索中…</p>
          <p v-else-if="keyword && results.length === 0" class="placeholder">
            没有找到匹配的用户
          </p>
          <p v-else-if="!keyword" class="placeholder">输入关键词开始查找</p>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.mask {
  position: fixed;
  inset: 0;
  z-index: 100;
  background: rgba(17, 24, 39, 0.45);
  display: flex;
  align-items: center;
  justify-content: center;
}

.dialog {
  width: min(460px, calc(100vw - 40px));
  background: #fff;
  border-radius: 12px;
  box-shadow: var(--shadow-lg);
  overflow: hidden;
}

header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 18px;
  border-bottom: 1px solid var(--border-soft);
}

h3 {
  margin: 0;
  font-size: 15px;
  font-weight: 600;
}

.close {
  font-size: 20px;
  line-height: 1;
  color: var(--text-weak);
}

.body {
  padding: 14px 18px 18px;
}

.search {
  width: 100%;
  height: 38px;
  padding: 0 12px;
  border-radius: 8px;
  border: 1px solid var(--border);
}

.search:focus {
  border-color: var(--brand);
}

.msg {
  margin-top: -4px;
  font-size: 13px;
}

.hint {
  margin: 8px 0 12px;
  font-size: 12px;
  color: var(--text-weak);
}

.result {
  max-height: 320px;
  min-height: 120px;
}

.row {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 4px;
  border-bottom: 1px solid var(--border-soft);
}

.meta {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
}

.name {
  font-size: 14px;
}

.sub {
  font-size: 12px;
  color: var(--text-weak);
}

.row .btn {
  height: 30px;
  padding: 0 14px;
  font-size: 13px;
}

.placeholder {
  padding: 36px 0;
  text-align: center;
  color: var(--text-weak);
  font-size: 13px;
}
</style>
