/// <reference types="vite/client" />

declare module '*.vue' {
  import type { DefineComponent } from 'vue'
  const component: DefineComponent<{}, {}, any>
  export default component
}

interface ImportMetaEnv {
  /** REST 基础路径，默认 /api（开发走 Vite 代理） */
  readonly VITE_API_BASE?: string
  /** WebSocket  origin，默认跟随当前页面（开发走 Vite 代理 /ws） */
  readonly VITE_WS_BASE?: string
  /** Vite 代理目标后端地址 */
  readonly VITE_PROXY_TARGET?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
