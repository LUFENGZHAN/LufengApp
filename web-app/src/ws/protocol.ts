/**
 * WebSocket 协议：与后端 WsEnvelope / WsMessageType 严格对应。
 * type 为小写字符串，data 为业务载荷。
 */

export type WsMessageType =
  | 'connected'
  | 'chat'
  | 'ack'
  | 'read'
  | 'typing'
  | 'notify'
  | 'ping'
  | 'pong'
  | 'kick'
  | 'error'

export interface WsEnvelope<T = unknown> {
  type: WsMessageType
  seq: number | null
  ts: number | null
  data: T
}

export type WsState = 'idle' | 'connecting' | 'open' | 'reconnecting' | 'closed'

/* ---------------- 各类型载荷 ---------------- */

export interface ConnectedPayload {
  userId: number
  deviceId: string
  serverTime: number
}

export interface AckPayload {
  clientMsgId: string
  msgNo: string
  seq: number
  sendTime: number
  duplicated: boolean
  /** 首条消息懒创建会话时返回真实会话号；旧版本后端可能不返回 */
  conversationId?: number | null
}

export interface ReadPayload {
  conversationId: number
  userId: number
  lastReadSeq: number
}

export interface TypingPayload {
  conversationId: number
  userId: number
}

/** kind=recall */
export interface RecallPayload {
  kind: 'recall'
  conversationId: number
  msgNo: string
  seq: number
}

/** kind=friend_request / friend_accepted / friend_rejected / friend_removed / friend_deleted */
export interface FriendNotifyPayload {
  kind:
    | 'friend_request'
    | 'friend_accepted'
    | 'friend_rejected'
    | 'friend_removed'
    | 'friend_deleted'
    | string
  payload: Record<string, unknown>
}

export interface ErrorPayload {
  code?: number
  message: string
}

export interface KickPayload {
  reason?: string
  code?: number
}

/* ---------------- 构造工具 ---------------- */

export function envelope<T>(type: WsMessageType, data: T, seq: number | null = null): WsEnvelope<T> {
  return { type, seq, ts: Date.now(), data }
}

/* ---------------- 极简事件总线 ---------------- */

type Handler<T = unknown> = (payload: T, env: WsEnvelope<T>) => void

export class EventBus {
  private readonly map = new Map<string, Set<Handler<any>>>()

  on<T>(type: WsMessageType, handler: Handler<T>): () => void {
    let set = this.map.get(type)
    if (!set) {
      set = new Set()
      this.map.set(type, set)
    }
    set.add(handler as Handler<any>)
    return () => set!.delete(handler as Handler<any>)
  }

  emit<T>(env: WsEnvelope<T>) {
    const set = this.map.get(env.type)
    if (!set) return
    for (const handler of set) {
      try {
        handler(env.data, env as WsEnvelope<T>)
      } catch (e) {
        console.error('[ws] 事件处理异常', env.type, e)
      }
    }
  }

  clear() {
    this.map.clear()
  }
}
