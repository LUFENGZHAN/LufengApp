import { createApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import router from './router'
import './styles/global.css'
import { useAuthStore } from './stores/auth'
import { useRealtimeStore } from './stores/realtime'
import { useUiStore } from './stores/ui'
import { ws } from './ws/client'
import { registerNavigator } from './utils/navigator'

const app = createApp(App)
app.use(createPinia())
app.use(router)

registerNavigator((to) => {
  void router.replace(to)
})

/**
 * 会话恢复：本地 token 存在时拉取用户信息（顺带校验 token 是否还有效），
 * 校验通过立刻重建长连接 —— 「登录即连接」在刷新页面后依然成立。
 */
async function bootstrap() {
  await router.isReady()
  const auth = useAuthStore()
  const realtime = useRealtimeStore()
  const ui = useUiStore()

  if (!auth.accessToken) {
    app.mount('#app')
    return
  }

  try {
    await auth.refreshUserInfo()
  } catch (e) {
    console.warn('本地凭证校验失败，需要重新登录', e)
  }

  if (auth.accessToken) {
    ws.connect(auth.accessToken, auth.deviceId)
    realtime.start()
  } else {
    ui.info('登录已失效，请重新登录')
  }

  // 多标签页同步：任一标签登出后，其余标签一并退出
  window.addEventListener('storage', (event) => {
    if (event.key === 'lufeng.accessToken' && !event.newValue) {
      if (auth.isLogin) {
        void auth.logout(true)
        realtime.stop()
      }
    }
  })

  app.mount('#app')
}

void bootstrap()
