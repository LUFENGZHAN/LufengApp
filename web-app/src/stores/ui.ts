import { defineStore } from 'pinia'
import { ref } from 'vue'

export type ToastType = 'info' | 'success' | 'error' | 'warn'

export interface Toast {
  id: number
  type: ToastType
  message: string
}

export interface ConfirmOptions {
  title?: string
  message: string
  confirmText?: string
  cancelText?: string
  /** 危险操作（删除等）：确认按钮显示为红色 */
  danger?: boolean
}

export interface ConfirmState {
  open: boolean
  title: string
  message: string
  confirmText: string
  cancelText: string
  danger: boolean
  resolve?: (value: boolean) => void
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

  /* ---------------- 全局确认弹窗（Promise 化，替代丑陋的 window.confirm） ---------------- */

  const confirmState = ref<ConfirmState>({
    open: false,
    title: '提示',
    message: '',
    confirmText: '确定',
    cancelText: '取消',
    danger: false,
  })

  /**
   * 弹出确认框，返回 Promise<boolean>。
   * 用户点「确定」→ resolve(true)；点「取消」或遮罩或按 ESC → resolve(false)。
   */
  function confirm(opts: ConfirmOptions): Promise<boolean> {
    return new Promise<boolean>((resolve) => {
      confirmState.value = {
        open: true,
        title: opts.title ?? '提示',
        message: opts.message,
        confirmText: opts.confirmText ?? '确定',
        cancelText: opts.cancelText ?? '取消',
        danger: opts.danger ?? false,
        resolve,
      }
    })
  }

  function resolveConfirm(value: boolean) {
    confirmState.value.resolve?.(value)
    confirmState.value = { ...confirmState.value, open: false, resolve: undefined }
  }

  return {
    toasts,
    push,
    dismiss,
    info,
    success,
    error,
    warn,
    confirmState,
    confirm,
    resolveConfirm,
  }
})
