import {
  EventBus,
  envelope,
  type ConnectedPayload,
  type WsEnvelope,
  type WsMessageType,
  type WsState,
} from './protocol'

/**
 * 浏览器 WebSocket 不可自定义请求头，握手凭据走 URL 参数（后端 AuthHandshakeInterceptor 已支持）：
 *   ws(s)://<host>/ws?token=<accessToken>&deviceId=<deviceId>
 */

const HEARTBEAT_INTERVAL = 25000
/** 发出 ping 后多久没拿到 pong 就判定链路已死 */
const PONG_TIMEOUT = 10000
const MAX_BACKOFF = 30000
const MAX_QUEUE = 50

export type StateListener = (state: WsState, detail?: unknown) => void

class WebSocketClient {
  private socket: WebSocket | null = null
  private accessToken: string | null = null
  private deviceId: string | null = null

  private heartbeatTimer: ReturnType<typeof setInterval> | null = null
  private pongTimer: ReturnType<typeof setTimeout> | null = null
  private reconnectTimer: ReturnType<typeof setTimeout> | null = null

  private attempt = 0
  private manualClosed = false
  private queue: WsEnvelope[] = []

  readonly bus = new EventBus()
  private stateListeners = new Set<StateListener>()

  private _state: WsState = 'idle'
  private _lastConnectedAt = 0

  get state(): WsState {
    return this._state
  }

  /** 供 UI 展示「已连接 / 重连中 / 离线」 */
  get connected(): boolean {
    return this._state === 'open'
  }

  private setState(next: WsState, detail?: unknown) {
    if (this._state === next) return
    this._state = next
    for (const listener of this.stateListeners) {
      try {
        listener(next, detail)
      } catch (e) {
        console.error('[ws] 状态监听异常', e)
      }
    }
  }

  onStateChange(listener: StateListener): () => void {
    this.stateListeners.add(listener)
    listener(this._state)
    return () => this.stateListeners.delete(listener)
  }

  /* ---------------- 生命周期 ---------------- */

  /**
   * 登录成功后立即调用；已建立连接时幂等。
   * 注意：access token 轮换（静默刷新）不会重建连接——握手鉴权只在连接建立时做一次，
   * 之后由服务端的心跳续期维持，重建反而会造成频繁抖动。
   */
  connect(accessToken: string, deviceId: string) {
    this.accessToken = accessToken
    this.deviceId = deviceId
    this.manualClosed = false

    if (this.socket) {
      const ready = this.socket.readyState
      if (ready === WebSocket.OPEN || ready === WebSocket.CONNECTING) return
    }
    this.open()
  }

  /** 静默刷新后更新凭据，供后续重连使用 */
  updateToken(accessToken: string) {
    this.accessToken = accessToken
  }

  /** 登出 / 被踢：主动断开且不再重连 */
  close(reason = 'logout') {
    this.manualClosed = true
    this.clearTimers()
    this.attempt = 0
    this.queue = []
    if (this.socket) {
      try {
        this.socket.close(1000, reason)
      } catch {
        /* 已经关闭 */
      }
      this.socket = null
    }
    this.setState('closed', reason)
  }

  private open() {
    if (!this.accessToken || !this.deviceId) return
    this.clearTimers()
    this.setState(this.attempt === 0 ? 'connecting' : 'reconnecting')

    const scheme = window.location.protocol === 'https:' ? 'wss' : 'ws'
    let base = import.meta.env.VITE_WS_BASE
    if (!base) {
      base = `${scheme}://${window.location.host}/ws`
    }
    const url = `${base}?token=${encodeURIComponent(this.accessToken)}&deviceId=${encodeURIComponent(this.deviceId)}`

    let socket: WebSocket
    try {
      socket = new WebSocket(url)
    } catch (e) {
      this.scheduleReconnect(e)
      return
    }
    this.socket = socket

    socket.onopen = () => {
      this.attempt = 0
      this.setState('open')
      this._lastConnectedAt = Date.now()
      this.startHeartbeat()
      this.flushQueue()
    }

    socket.onmessage = (event) => {
      this.handleMessage(event.data)
    }

    socket.onerror = () => {
      // 具体信息浏览器不暴露，交给 onclose 统一处理重连
    }

    socket.onclose = (event) => {
      this.socket = null
      this.clearTimers()
      if (this.manualClosed) {
        this.setState('closed', event.reason)
        return
      }
      // 4001 / 1008 通常由握手鉴权失败引起：刷新也无济于事，交上层重新登录
      if (event.code === 4001 || event.code === 1008) {
        this.setState('closed', event.reason || 'auth-failed')
        return
      }
      this.scheduleReconnect()
    }
  }

  private handleMessage(raw: unknown) {
    if (typeof raw !== 'string') return
    let env: WsEnvelope
    try {
      env = JSON.parse(raw) as WsEnvelope
    } catch {
      console.warn('[ws] 收到非 JSON 消息')
      return
    }
    if (!env || !env.type) return
    // pong 用于心跳判定，不再向外广播
    if (env.type === 'pong') {
      this.armPongWatchdog(false)
      return
    }
    this.bus.emit(env)
  }

  /* ---------------- 发送 ---------------- */

  /**
   * 发送上行帧。未连接时入队（上限 MAX_QUEUE，超出丢弃最旧），连接恢复后自动补发。
   * ping / typing 这类时效消息不入队。
   */
  send<T>(type: WsMessageType, data: T, opts: { queued?: boolean; seq?: number | null } = {}) {
    const env = envelope(type, data, opts.seq ?? null)
    if (!this.connected) {
      if (opts.queued !== false) {
        if (this.queue.length >= MAX_QUEUE) this.queue.shift()
        this.queue.push(env as WsEnvelope)
      }
      return false
    }
    return this.write(env as WsEnvelope)
  }

  private write(env: WsEnvelope): boolean {
    const socket = this.socket
    if (!socket || socket.readyState !== WebSocket.OPEN) return false
    try {
      socket.send(JSON.stringify(env))
      return true
    } catch (e) {
      console.error('[ws] 发送失败', e)
      return false
    }
  }

  private flushQueue() {
    if (this.queue.length === 0) return
    const pending = this.queue
    this.queue = []
    for (const env of pending) {
      this.write(env)
    }
  }

  /* ---------------- 心跳 ---------------- */

  private startHeartbeat() {
    this.stopHeartbeat()
    this.heartbeatTimer = setInterval(() => {
      if (!this.connected) return
      this.write(envelope('ping', { ts: Date.now() }))
      this.armPongWatchdog(true)
    }, HEARTBEAT_INTERVAL)
  }

  private stopHeartbeat() {
    if (this.heartbeatTimer) {
      clearInterval(this.heartbeatTimer)
      this.heartbeatTimer = null
    }
    if (this.pongTimer) {
      clearTimeout(this.pongTimer)
      this.pongTimer = null
    }
  }

  /** 发出 ping 后等待 pong；超时认为链路半死，主动断开触发快速重连 */
  private armPongWatchdog(armed: boolean) {
    if (this.pongTimer) {
      clearTimeout(this.pongTimer)
      this.pongTimer = null
    }
    if (!armed) return
    this.pongTimer = setTimeout(() => {
      console.warn('[ws] 心跳超时，主动重连')
      this.socket?.close(4000, 'heartbeat timeout')
    }, PONG_TIMEOUT)
  }

  /* ---------------- 重连 ---------------- */

  private scheduleReconnect(reason?: unknown) {
    if (this.manualClosed) return
    this.setState('reconnecting', reason)
    if (this.reconnectTimer) clearTimeout(this.reconnectTimer)

    this.attempt++
    // 指数退避 1s→30s，加抖动避免多端同时重连打爆服务端
    const base = Math.min(MAX_BACKOFF, 1000 * 2 ** (this.attempt - 1))
    const delay = base + Math.floor(Math.random() * 1000)

    this.reconnectTimer = setTimeout(() => {
      this.reconnectTimer = null
      if (this.manualClosed) return
      this.open()
    }, delay)
  }

  private clearTimers() {
    this.stopHeartbeat()
    if (this.reconnectTimer) {
      clearTimeout(this.reconnectTimer)
      this.reconnectTimer = null
    }
  }

  /* ---------------- 便捷订阅 ---------------- */

  on<T>(type: WsMessageType, handler: (payload: T, env: WsEnvelope<T>) => void) {
    return this.bus.on<T>(type, handler)
  }

  /** 握手成功载荷（无则等待 connected 帧） */
  waitConnected(): Promise<ConnectedPayload> {
    if (this.connected) {
      return Promise.resolve({
        userId: 0,
        deviceId: this.deviceId ?? '',
        serverTime: this._lastConnectedAt,
      } as ConnectedPayload)
    }
    return new Promise((resolve) => {
      const off = this.on<ConnectedPayload>('connected', (payload) => {
        off()
        resolve(payload)
      })
    })
  }
}

export const ws = new WebSocketClient()
