import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import type { TokenVO, UserVO } from '@/api/types'
import { authApi } from '@/api/auth'
import { setupHttpClient } from '@/api/http'
import { resolveDeviceId, tokenStore, userStore } from '@/utils/storage'
import { goLogin } from '@/utils/navigator'
import { ws } from '@/ws/client'

function safeParseUser(): UserVO | null {
  const raw = userStore.get()
  if (!raw) return null
  try {
    return JSON.parse(raw) as UserVO
  } catch {
    userStore.clear()
    return null
  }
}

function goToLogin() {
  const path = window.location.pathname + window.location.search
  goLogin(path)
}

export const useAuthStore = defineStore('auth', () => {
  const accessToken = ref<string | null>(tokenStore.access)
  const refreshToken = ref<string | null>(tokenStore.refresh)
  const user = ref<UserVO | null>(safeParseUser())
  /** 浏览器级设备标识：多端会话、单端登出的依据 */
  const deviceId = resolveDeviceId()

  const loading = ref(false)
  /** 会话失效原因，登录页据此给出提示（如「账号已在其他设备登录」） */
  const expiredMessage = ref<string | null>(null)

  const isLogin = computed(() => !!accessToken.value)
  const userId = computed(() => user.value?.userId ?? 0)
  const nickname = computed(() => user.value?.nickname || user.value?.account || '我')
  const avatar = computed(() => user.value?.avatar || '')

  /** 写入并持久化凭证，同时建立长连接 */
  function applyToken(value: TokenVO) {
    accessToken.value = value.accessToken
    refreshToken.value = value.refreshToken
    user.value = value.user

    tokenStore.access = value.accessToken
    tokenStore.refresh = value.refreshToken
    userStore.set(JSON.stringify(value.user))
    expiredMessage.value = null

    // 登录成功即刻建立 WebSocket —— 这是实时收发消息的前提
    ws.connect(value.accessToken, deviceId)
  }

  function setLocalAccessToken(token: string) {
    accessToken.value = token
    tokenStore.access = token
    ws.updateToken(token)
  }

  async function login(account: string, password: string) {
    loading.value = true
    try {
      const value = await authApi.login({ account, password, deviceId, deviceType: 'web' })
      applyToken(value)
      return value.user
    } finally {
      loading.value = false
    }
  }

  async function register(payload: { account: string; password: string; nickname?: string }) {
    loading.value = true
    try {
      await authApi.register({ ...payload, deviceId, deviceType: 'web' })
      return login(payload.account, payload.password)
    } finally {
      loading.value = false
    }
  }

  /** 主动登出：先通知服务端吊销会话，再断连清态 */
  async function logout(silent = false) {
    ws.close('logout')
    if (!silent) {
      try {
        await authApi.logout()
      } catch (e) {
        console.warn('登出接口调用失败，本地状态仍然清除', e)
      }
    }
    accessToken.value = null
    refreshToken.value = null
    user.value = null
    tokenStore.clearAll()
    userStore.clear()
    goToLogin()
  }

  /**
   * 凭证失效 / 被踢：与 logout 的区别是不调用服务端接口（凭证已经不能用了）。
   * 多标签页场景由 storage 事件或下次请求自然发现。
   */
  async function handleSessionExpired(code: number, message: string) {
    ws.close('session-expired')
    accessToken.value = null
    refreshToken.value = null
    user.value = null
    tokenStore.clearAll()
    userStore.clear()
    expiredMessage.value = code === 20005 ? '账号已在其他设备登录' : message || '登录已失效，请重新登录'
    goToLogin()
  }

  async function refreshUserInfo() {
    if (!isLogin.value) return
    try {
      const data = await authApi.me()
      user.value = data
      userStore.set(JSON.stringify(data))
    } catch (e) {
      console.warn('拉取用户信息失败', e)
    }
  }

  function updateUser(patch: Partial<UserVO>) {
    if (!user.value) return
    user.value = { ...user.value, ...patch }
    userStore.set(JSON.stringify(user.value))
  }

  return {
    accessToken,
    refreshToken,
    user,
    deviceId,
    loading,
    expiredMessage,
    isLogin,
    userId,
    nickname,
    avatar,
    login,
    register,
    logout,
    handleSessionExpired,
    applyToken,
    setLocalAccessToken,
    refreshUserInfo,
    updateUser,
  }
})

// 装配 HTTP 层与 store 的桥接（此时 store 已注册，运行时调用安全）
setupHttpClient({
  accessToken: () => useAuthStore().accessToken,
  refreshToken: () => useAuthStore().refreshToken,
  deviceId: () => useAuthStore().deviceId,
  onRefreshed: (value) => {
    const store = useAuthStore()
    store.setLocalAccessToken(value.accessToken)
    // 只用新返回的 refresh token —— 后端每次刷新都会轮换，旧值已失效
    store.refreshToken = value.refreshToken
    tokenStore.refresh = value.refreshToken
  },
  onSessionExpired: (code, message) => {
    void useAuthStore().handleSessionExpired(code, message)
  },
})
