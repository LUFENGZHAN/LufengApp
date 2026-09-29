<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { ApiError } from '@/api/http'

const router = useRouter()
const auth = useAuthStore()

const account = ref('')
const nickname = ref('')
const password = ref('')
const confirmPassword = ref('')
const submitting = ref(false)
const errorMsg = ref('')

const weak = computed(() => password.value.length > 0 && password.value.length < 6)
const mismatch = computed(
  () => confirmPassword.value.length > 0 && confirmPassword.value !== password.value,
)
const disabled = computed(
  () =>
    submitting.value ||
    account.value.trim().length < 4 ||
    weak.value ||
    mismatch.value ||
    !password.value,
)

async function submit() {
  if (disabled.value) return
  submitting.value = true
  errorMsg.value = ''
  try {
    await auth.register({
      account: account.value.trim(),
      password: password.value,
      nickname: nickname.value.trim() || undefined,
    })
    await router.replace('/chat')
  } catch (e) {
    errorMsg.value = e instanceof ApiError ? e.message : '注册失败，请稍后重试'
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="auth-page">
    <div class="auth-card">
      <div class="brand">
        <div class="logo">麓</div>
        <div>
          <h1>创建账号</h1>
          <p>注册成功自动登录并建立长连接</p>
        </div>
      </div>

      <form @submit.prevent="submit">
        <div class="field">
          <label for="account">账号</label>
          <input
            id="account"
            v-model="account"
            placeholder="4-64 位字母、数字、_.@-"
            @input="errorMsg = ''"
          />
        </div>
        <div class="field">
          <label for="nickname">昵称（选填）</label>
          <input id="nickname" v-model="nickname" placeholder="展示给好友的名字" />
        </div>
        <div class="field">
          <label for="password">密码</label>
          <input
            id="password"
            v-model="password"
            type="password"
            placeholder="6-32 位"
            @input="errorMsg = ''"
          />
        </div>
        <div class="field">
          <label for="confirm">确认密码</label>
          <input id="confirm" v-model="confirmPassword" type="password" placeholder="再输入一次" />
        </div>

        <p v-if="weak" class="field-error">密码长度需为 6-32 位</p>
        <p v-else-if="mismatch" class="field-error">两次输入的密码不一致</p>
        <p v-else-if="errorMsg" class="field-error">{{ errorMsg }}</p>

        <button class="btn submit" type="submit" :disabled="disabled">
          {{ submitting ? '注册中…' : '注册并登录' }}
        </button>
      </form>

      <div class="footer">
        <span>已有账号？</span>
        <span class="text-link" @click="router.push({ name: 'login' })">返回登录</span>
      </div>
    </div>
  </div>
</template>

<style scoped>
.auth-page {
  min-height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: radial-gradient(circle at 20% 20%, #e9fbf1 0%, #eef0f3 45%, #e6eaf0 100%);
  padding: 24px;
}

.auth-card {
  width: 100%;
  max-width: 400px;
  padding: 34px 34px 26px;
  background: #fff;
  border-radius: 16px;
  box-shadow: var(--shadow-lg);
}

.brand {
  display: flex;
  align-items: center;
  gap: 14px;
  margin-bottom: 22px;
}

.logo {
  width: 46px;
  height: 46px;
  border-radius: 12px;
  background: var(--brand);
  color: #fff;
  font-size: 22px;
  display: flex;
  align-items: center;
  justify-content: center;
}

h1 {
  margin: 0;
  font-size: 19px;
  font-weight: 600;
}

.brand p {
  margin: 3px 0 0;
  font-size: 12px;
  color: var(--text-weak);
}

.submit {
  width: 100%;
  height: 42px;
  margin-top: 4px;
}

.footer {
  margin-top: 16px;
  text-align: center;
  font-size: 13px;
  color: var(--text-sub);
}
</style>
