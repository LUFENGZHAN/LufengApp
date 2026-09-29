import { nanoid } from '@/utils/id'

const LS_KEY_ACCESS = 'lufeng.accessToken'
const LS_KEY_REFRESH = 'lufeng.refreshToken'
const LS_KEY_USER = 'lufeng.user'
const LS_KEY_DEVICE = 'lufeng.deviceId'

/**
 * 会话级 / 持久化的本地存储读写。
 * 不做全套加密——Web 端能拿到的东西理论上都能被调试者拿到，
 * 真正的安全边界在服务端（JWT 有效期 + token_version + refresh 轮换）。
 */
export const tokenStore = {
  get access() {
    return localStorage.getItem(LS_KEY_ACCESS)
  },
  set access(value: string | null) {
    value ? localStorage.setItem(LS_KEY_ACCESS, value) : localStorage.removeItem(LS_KEY_ACCESS)
  },
  get refresh() {
    return localStorage.getItem(LS_KEY_REFRESH)
  },
  set refresh(value: string | null) {
    value ? localStorage.setItem(LS_KEY_REFRESH, value) : localStorage.removeItem(LS_KEY_REFRESH)
  },
  clearAll() {
    localStorage.removeItem(LS_KEY_ACCESS)
    localStorage.removeItem(LS_KEY_REFRESH)
    localStorage.removeItem(LS_KEY_USER)
  },
}

export const userStore = {
  get(): string | null {
    return localStorage.getItem(LS_KEY_USER)
  },
  set(value: string) {
    localStorage.setItem(LS_KEY_USER, value)
  },
  clear() {
    localStorage.removeItem(LS_KEY_USER)
  },
}

/**
 * 设备唯一 ID：登录后写入凭据的一部分，服务端据此做多端会话管理与单端踢下线。
 * 同一个浏览器标签集群共用一个值，换浏览器即视为新设备。
 */
export function resolveDeviceId(): string {
  let id = localStorage.getItem(LS_KEY_DEVICE)
  if (!id) {
    id = 'web-' + nanoid(16)
    localStorage.setItem(LS_KEY_DEVICE, id)
  }
  return id
}
