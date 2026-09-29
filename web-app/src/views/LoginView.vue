<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { useUiStore } from '@/stores/ui'
import { ApiError } from '@/api/http'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const ui = useUiStore()

const account = ref('zhangsan')
const password = ref('123456')
const submitting = ref(false)
const errorMsg = ref('')

const disabled = computed(() => !account.value.trim() || !password.value || submitting.value)

/** 被踢下线等场景由 store 记录的原因 */
const notice = computed(() => auth.expiredMessage)

async function submit() {
  if (disabled.value) return
  submitting.value = true
  errorMsg.value = ''
  try {
    await auth.login(account.value.trim(), password.value)
    auth.expiredMessage = null
    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : '/chat'
    await router.replace(redirect)
  } catch (e) {
    errorMsg.value =
      e instanceof ApiError ? e.message : '登录失败，请稍后重试'
  } finally {
    submitting.value = false
  }
}

function goRegister() {
  router.push({ name: 'register' })
}

onMounted(() => {
  if (auth.expiredMessage) {
    ui.info(auth.expiredMessage)
  }
})
</script>

<template>
  <div class="auth-page">
    <div class="auth-card">
      <div class="brand">
        <div class="logo">麓</div>
        <div>
          <h1>麓风聊天</h1>
          <p>登录后可实时收发消息</p>
        </div>
      </div>

      <div v-if="notice" class="notice">{{ notice }}</div>

      <form @submit.prevent="submit">
        <div class="field">
          <label for="account">账号</label>
          <input
            id="account"
            v-model="account"
            autocomplete="username"
            placeholder="账号 / 手机号 / 邮箱"
            @input="errorMsg = ''"
          />
        </div>
        <div class="field">
          <label for="password">密码</label>
          <input
            id="password"
            v-model="password"
            type="password"
            autocomplete="current-password"
            placeholder="请输入密码"
            @input="errorMsg = ''"
            @keyup.enter="submit"
          />
        </div>

        <p v-if="errorMsg" class="field-error error-line">{{ errorMsg }}</p>

        <button class="btn submit" type="submit" :disabled="disabled">
          {{ submitting ? '登录中…' : '登 录' }}
        </button>
      </form>

      <div class="footer">
        <span>还没有账号？</span>
        <span class="text-link" @click="goRegister">立即注册</span>
      </div>

      <p class="demo-tip">演示账号：zhangsan / lisi，密码 123456</p>
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
  padding: 36px 34px 28px;
  background: #fff;
  border-radius: 16px;
  box-shadow: var(--shadow-lg);
}

.brand {
  display: flex;
  align-items: center;
  gap: 14px;
  margin-bottom: 26px;
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
  margin-top: 6px;
}

.notice {
  margin-bottom: 16px;
  padding: 8px 12px;
  border-radius: 8px;
  background: #fff7e6;
  color: #b45309;
  font-size: 12.5px;
}

.error-line {
  margin: -6px 0 12px;
}

.footer {
  margin-top: 18px;
  text-align: center;
  font-size: 13px;
  color: var(--text-sub);
}

.demo-tip {
  margin: 16px 0 0;
  text-align: center;
  font-size: 12px;
  color: var(--text-weak);
}
</style>
