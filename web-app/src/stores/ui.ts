import { defineStore } from 'pinia'
import { ref } from 'vue'

export type ToastType = 'info' | 'success' | 'error' | 'warn'

export interface Toast {
  id: number
  type: ToastType
  message: string
}

/** 极简全局提示，避免为几个 toast 引入整套 UI 库 */
export const useUiStore = defineStore('ui', () => {
  const toasts = ref<Toast[]>([])
  let seed = 0

  function push(message: string, type: ToastType = 'info', duration = 3000) {
    const id = ++seed
    toasts.value.push({ id, type, message })
    setTimeout(() => dismiss(id), duration)
    return id
  }

  function dismiss(id: number) {
    toasts.value = toasts.value.filter((t) => t.id !== id)
  }

  function info(message: string) {
    return push(message, 'info')
  }

  function success(message: string) {
    return push(message, 'success')
  }

  function error(message: string) {
    return push(message, 'error', 4000)
  }

  function warn(message: string) {
    return push(message, 'warn', 3500)
  }

  return { toasts, push, dismiss, info, success, error, warn }
})
