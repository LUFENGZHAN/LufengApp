<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import UserAvatar from '@/components/common/UserAvatar.vue'
import { useAuthStore } from '@/stores/auth'
import { useFriendStore } from '@/stores/friend'
import { useConversationStore } from '@/stores/conversation'
import { useRealtimeStore } from '@/stores/realtime'
import { useUiStore } from '@/stores/ui'
import { userApi, authApi } from '@/api/auth'
import { ApiError } from '@/api/http'

const router = useRouter()
const auth = useAuthStore()
const friendStore = useFriendStore()
const conversationStore = useConversationStore()
const realtime = useRealtimeStore()
const ui = useUiStore()

const profile = reactive({ nickname: '', signature: '', gender: 0 })
const saving = ref(false)

const pwd = reactive({ oldPassword: '', newPassword: '', confirm: '' })
const changing = ref(false)
const pwdError = computed(() => {
  if (!pwd.newPassword) return ''
  if (pwd.newPassword.length < 6) return '密码长度需为 6-32 位'
  if (pwd.confirm && pwd.confirm !== pwd.newPassword) return '两次输入不一致'
  return ''
})

watch(
  () => auth.user?.userId,
  () => {
    profile.nickname = auth.user?.nickname ?? ''
    profile.signature = auth.user?.signature ?? ''
    profile.gender = auth.user?.gender ?? 0
  },
  { immediate: true },
)

async function save() {
  saving.value = true
  try {
    const updated = await userApi.update({
      nickname: profile.nickname,
      signature: profile.signature,
      gender: profile.gender,
    })
    auth.updateUser(updated)
    ui.success('资料已保存')
    await conversationStore.load()
  } catch (e) {
    ui.error(e instanceof ApiError ? e.message : '保存失败')
  } finally {
    saving.value = false
  }
}

async function changePassword() {
  if (pwdError.value || !pwd.oldPassword || !pwd.newPassword) return
  changing.value = true
  try {
    await authApi.changePassword({ oldPassword: pwd.oldPassword, newPassword: pwd.newPassword })
    ui.success('密码修改成功')
    pwd.oldPassword = ''
    pwd.newPassword = ''
    pwd.confirm = ''
  } catch (e) {
    ui.error(e instanceof ApiError ? e.message : '修改失败')
  } finally {
    changing.value = false
  }
}

async function logout() {
  realtime.stop()
  await auth.logout()
  conversationStore.clear()
  friendStore.clear()
  await router.replace({ name: 'login' })
}
</script>

<template>
  <div class="profile app-scroll">
    <div class="inner">
      <section class="hero">
        <UserAvatar :name="auth.nickname" :src="auth.avatar" :size="64" :radius="10" :online="null" />
        <div class="who">
          <h2>{{ auth.nickname }}</h2>
          <p>麓风号 {{ auth.user?.userNo }} · 账号 {{ auth.user?.account }}</p>
        </div>
      </section>

      <section class="card block">
        <h3>基本资料</h3>
        <div class="field">
          <label>昵称</label>
          <input v-model="profile.nickname" maxlength="32" placeholder="不超过 32 个字符" />
        </div>
        <div class="field">
          <label>个性签名</label>
          <textarea v-model="profile.signature" maxlength="255" placeholder="写点什么…" />
        </div>
        <button class="btn" :disabled="saving" @click="save">
          {{ saving ? '保存中…' : '保存资料' }}
        </button>
      </section>

      <section class="card block">
        <h3>修改密码</h3>
        <div class="field">
          <label>当前密码</label>
          <input v-model="pwd.oldPassword" type="password" placeholder="请输入当前密码" />
        </div>
        <div class="field">
          <label>新密码</label>
          <input v-model="pwd.newPassword" type="password" placeholder="6-32 位" />
        </div>
        <div class="field">
          <label>确认新密码</label>
          <input v-model="pwd.confirm" type="password" placeholder="再输入一次" />
        </div>
        <p v-if="pwdError" class="field-error">{{ pwdError }}</p>
        <button
          class="btn"
          :disabled="changing || !!pwdError || !pwd.oldPassword || !pwd.newPassword"
          @click="changePassword"
        >
          {{ changing ? '提交中…' : '修改密码' }}
        </button>
      </section>

      <section class="card block danger">
        <h3>账号</h3>
        <p class="desc">退出后本机 token 会被服务端吊销，需要使用密码重新登录。</p>
        <button class="btn btn-danger" @click="logout">退出登录</button>
      </section>
    </div>
  </div>
</template>

<style scoped>
.profile {
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
  gap: 18px;
}

.hero {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 18px;
  background: #fff;
  border-radius: 12px;
  box-shadow: var(--shadow-sm);
}

h2 {
  margin: 0 0 4px;
  font-size: 17px;
}

.who p {
  margin: 0;
  font-size: 12.5px;
  color: var(--text-weak);
}

.block {
  padding: 18px;
}

h3 {
  margin: 0 0 14px;
  font-size: 14px;
  font-weight: 600;
}

.desc {
  margin: 0 0 12px;
  font-size: 12.5px;
  color: var(--text-sub);
}

.danger h3 {
  color: var(--text);
}
</style>
