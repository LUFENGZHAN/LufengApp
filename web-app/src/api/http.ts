import axios, {
  AxiosError,
  type AxiosInstance,
  type AxiosRequestConfig,
  type InternalAxiosRequestConfig,
} from 'axios'
import type { ApiResult, RefreshReq, TokenVO } from './types'
import { ResultCode } from './types'

const BASE_URL = import.meta.env.VITE_API_BASE || '/api'
const TIMEOUT = 20000

/** 业务异常：携带错误码与链路 ID，UI 直接展示 message */
export class ApiError extends Error {
  readonly code: number
  readonly requestId?: string
  readonly httpStatus?: number

  constructor(code: number, message: string, requestId?: string, httpStatus?: number) {
    super(message)
    this.name = 'ApiError'
    this.code = code
    this.requestId = requestId
    this.httpStatus = httpStatus
  }

  get network(): boolean {
    return this.code === ResultCode.PARAM_ERROR && this.httpStatus === undefined
  }
}

/**
 * 刷新凭证时不能再走带拦截器的实例，否则会递归；这里用裸实例发刷新请求。
 * /auth/refresh 是后端白名单路径，不需要 Authorization 头。
 */
const rawAxios: AxiosInstance = axios.create({ baseURL: BASE_URL, timeout: TIMEOUT })

const http: AxiosInstance = axios.create({ baseURL: BASE_URL, timeout: TIMEOUT })

/* ---------------- 与 store 的握手 ---------------- */

interface AuthBridge {
  accessToken: () => string | null
  refreshToken: () => string | null
  deviceId: () => string
  /** 刷新成功后回调，用于持久化新凭证 */
  onRefreshed: (token: TokenVO) => void
  /** 刷新彻底失败（refresh 过期/被吊销/被踢），清理状态并跳转登录页 */
  onSessionExpired: (code: number, message: string) => void
}

let bridge: AuthBridge | null = null

export function setupHttpClient(impl: AuthBridge) {
  bridge = impl
}

/** 需要静默刷新后重放、以及本身不能再被刷新的路径 */
const SKIP_REFRESH_PATHS = ['/v1/auth/login', '/v1/auth/register', '/v1/auth/refresh']

function shouldSkipRefresh(url: string | undefined): boolean {
  if (!url) return true
  return SKIP_REFRESH_PATHS.some((p) => url.includes(p))
}

/* ---------------- 请求拦截 ---------------- */

http.interceptors.request.use((config: InternalAxiosRequestConfig) => {
  const token = bridge?.accessToken()
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  // 后端用这两个头区分客户端类型与多端会话
  config.headers['X-Client-Type'] = 'web'
  const deviceId = bridge?.deviceId()
  if (deviceId) {
    config.headers['X-Device-Id'] = deviceId
  }
  return config
})

/* ---------------- 响应拦截 ---------------- */

http.interceptors.response.use(
  (response) => response,
  async (error: AxiosError<ApiResult>) => {
    const original = error.config as (AxiosRequestConfig & { _retried?: boolean }) | undefined
    const result = error.response?.data
    const httpStatus = error.response?.status
    const code = result?.code ?? ResultCode.SERVER_ERROR
    const message = result?.message ?? '网络异常，请稍后重试'
    const requestId = result?.requestId ?? (error.response?.headers?.['x-request-id'] as string | undefined)

    const apiError = new ApiError(code, message, requestId, httpStatus)

    // access token 过期：静默刷新一次后重放原请求
    const needRefresh =
      (code === ResultCode.TOKEN_EXPIRED || httpStatus === 401) &&
      original &&
      !original._retried &&
      !shouldSkipRefresh(original.url)

    if (needRefresh && original) {
      original._retried = true
      try {
        await doRefresh()
        return http.request(original)
      } catch (e) {
        // 刷新失败时 doRefresh 内部已经触发 session expired
        return Promise.reject(e instanceof ApiError ? e : apiError)
      }
    }

    // 被踢下线 / 凭证吊销：清状态并要求重新登录
    if (code === ResultCode.KICKED || code === ResultCode.REFRESH_INVALID) {
      bridge?.onSessionExpired(code, message)
    }

    return Promise.reject(apiError)
  },
)

/* ---------------- 刷新控制 ---------------- */

let refreshing: Promise<void> | null = null

/**
 * 单次刷新 + 并发共享：同一时刻 N 个请求都拿到同一个 Promise，
 * 只有一个真正发 /auth/refresh，其余等待结果后各自重放，避免 refresh token 被并发轮换滥用。
 */
function doRefresh(): Promise<void> {
  if (refreshing) return refreshing

  const token = bridge?.refreshToken()
  const deviceId = bridge?.deviceId()
  if (!token || !deviceId) {
    bridge?.onSessionExpired(ResultCode.REFRESH_INVALID, '登录已失效，请重新登录')
    return Promise.reject(new ApiError(ResultCode.REFRESH_INVALID, '登录已失效，请重新登录'))
  }

  const payload: RefreshReq = { refreshToken: token, deviceId }
  refreshing = rawAxios
    .post<ApiResult<TokenVO>>('/v1/auth/refresh', payload)
    .then((res) => {
      const body = res.data
      if (body.code !== ResultCode.SUCCESS || !body.data) {
        bridge?.onSessionExpired(body.code, body.message)
        throw new ApiError(body.code, body.message, body.requestId)
      }
      bridge?.onRefreshed(body.data)
    })
    .catch((err: AxiosError<ApiResult>) => {
      const body = err.response?.data
      const code = body?.code ?? ResultCode.REFRESH_INVALID
      const message = body?.message ?? '登录已失效，请重新登录'
      bridge?.onSessionExpired(code, message)
      throw new ApiError(code, message, body?.requestId)
    })
    .finally(() => {
      refreshing = null
    })

  return refreshing
}

/* ---------------- 对外请求入口 ---------------- */

/**
 * 统一解包：Result.code === 0 返回 data；否则抛 ApiError。
 * Result<Void> 场景 data 为 null，调用方忽略返回值即可。
 */
export async function request<T>(config: AxiosRequestConfig): Promise<T> {
  const response = await http.request<ApiResult<T>>(config)
  const body = response.data
  if (body.code !== ResultCode.SUCCESS) {
    throw new ApiError(body.code, body.message, body.requestId, response.status)
  }
  return body.data as T
}

export function get<T>(url: string, params?: Record<string, unknown>): Promise<T> {
  return request<T>({ method: 'GET', url, params })
}

export function post<T>(url: string, data?: unknown): Promise<T> {
  return request<T>({ method: 'POST', url, data })
}

export function put<T>(url: string, data?: unknown): Promise<T> {
  return request<T>({ method: 'PUT', url, data })
}

export function del<T>(url: string): Promise<T> {
  return request<T>({ method: 'DELETE', url })
}

export default http
