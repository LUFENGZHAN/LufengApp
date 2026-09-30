/**
 * 与后端 DTO 严格 1:1 的 TS 契约。
 * 后端字段为驼峰（mybatis map-underscore-to-camel-case），此处保持一致，不改字段名。
 */

/** 统一响应体 */
export interface ApiResult<T = unknown> {
  /** 0 成功，非 0 见 ResultCode */
  code: number
  message: string
  data: T | null
  requestId?: string
  timestamp?: number
}

/** 游标分页结果 */
export interface PageVO<T> {
  list: T[]
  hasMore: boolean
  nextCursor: number | null
}

/* ---------------- 用户 / 认证 ---------------- */

export interface UserVO {
  userId: number
  userNo: string
  account: string
  nickname: string
  avatar: string | null
  gender: number | null
  signature: string | null
  online: boolean | null
}

export interface TokenVO {
  accessToken: string
  refreshToken: string
  expiresIn: number
  refreshExpiresIn: number
  user: UserVO
}

export interface RegisterReq {
  account: string
  password: string
  nickname?: string
  deviceId?: string
  deviceType?: string
}

export interface LoginReq {
  account: string
  password: string
  deviceId: string
  deviceType?: string
}

export interface RefreshReq {
  refreshToken: string
  deviceId: string
}

export interface ChangePasswordReq {
  oldPassword: string
  newPassword: string
}

export interface UpdateUserReq {
  nickname?: string
  avatar?: string
  gender?: number
  signature?: string
}

/* ---------------- 好友 ---------------- */

export interface FriendVO {
  userId: number
  userNo: string
  nickname: string
  avatar: string | null
  signature: string | null
  remark: string | null
  online: boolean | null
}

export interface FriendRequestVO {
  requestId: number
  requestNo: string
  userId: number
  userNo: string
  nickname: string
  avatar: string | null
  applyMsg: string | null
  /** 0待处理 1已同意 2已拒绝 3已忽略 */
  status: number
  createdAt: string
}

export interface ApplyFriendReq {
  toUserId: number
  applyMsg?: string
}

export interface HandleFriendReq {
  /** accept / reject */
  action: string
}

/* ---------------- 会话 ---------------- */

export interface PeerVO {
  userId: number
  userNo: string
  nickname: string
  avatar: string | null
  online: boolean | null
}

export interface LastMessageVO {
  msgId: number
  msgNo: string
  seq: number
  senderId: number
  msgType: number
  preview: string | null
  sendTime: number
}

export interface ConversationVO {
  conversationId: number
  conversationNo: string
  /** 1单聊 2群聊 3系统通知 */
  type: number
  title: string | null
  avatar: string | null
  peer: PeerVO | null
  lastMessage: LastMessageVO | null
  unreadCount: number
  lastReadSeq: number
  lastAckSeq: number
  lastMessageAt: number | null
  muted: number
}

export interface ConversationReq {
  targetUserId: number
}

export interface ReadReq {
  lastReadSeq: number
}

/* ---------------- 消息 ---------------- */

/** 消息类型：1文本 2图片 3语音 4视频 5文件 6位置 */
export const MsgType = {
  TEXT: 1,
  IMAGE: 2,
  VOICE: 3,
  VIDEO: 4,
  FILE: 5,
  LOCATION: 6,
} as const

export type MsgTypeValue = (typeof MsgType)[keyof typeof MsgType]

export interface MessageVO {
  msgId: number
  msgNo: string
  clientMsgId: string
  conversationId: number
  /** 会话内递增序号：排序、分页、离线补拉的唯一基准 */
  seq: number
  senderId: number
  senderNo: string | null
  senderNickname: string | null
  senderAvatar: string | null
  msgType: number
  content: string | null
  extra: string | null
  /** 1正常 2已撤回 3已删除 */
  status: number
  /** 服务端毫秒时间戳 */
  sendTime: number
  duplicated: boolean | null
}

export interface SendMessageReq {
  clientMsgId: string
  /**
   * 已有会话传它；尚未建会话时传 null 并改用 peerId，由服务端懒创建单聊。
   * 加好友不再预建空会话，第一条消息落地时才生成会话。
   */
  conversationId: number | null
  /** 单聊对象 userId，与 conversationId 二选一 */
  peerId?: number | null
  msgType: number
  content?: string
  extra?: string
}

export interface SyncItem {
  conversationId: number
  afterSeq: number
}

/* ---------------- 文件上传 ---------------- */

export interface FileUploadResult {
  /** 形如 /static/2026/09/<uuid>.jpg，前端直接当 src 用（同源回源） */
  url: string
  contentType: string
  size: number
  width: number | null
  height: number | null
  duration: number | null
}

export interface SyncReq {
  items: SyncItem[]
}

/* ---------------- 错误码 ---------------- */

export const ResultCode = {
  SUCCESS: 0,
  PARAM_ERROR: 10001,
  UNAUTHORIZED: 10002,
  TOKEN_EXPIRED: 10003,
  FORBIDDEN: 10004,
  NOT_FOUND: 10005,
  TOO_MANY_REQUESTS: 10006,
  SERVER_ERROR: 10007,
  ACCOUNT_EXISTS: 20001,
  BAD_CREDENTIALS: 20002,
  ACCOUNT_DISABLED: 20003,
  REFRESH_INVALID: 20004,
  KICKED: 20005,
  PASSWORD_WEAK: 20006,
  ACCOUNT_INVALID: 20007,
  FRIEND_SELF: 30001,
  FRIEND_DUPLICATE: 30002,
  FRIEND_REQUEST_NOT_FOUND: 30003,
  NOT_FRIEND: 30004,
  CONVERSATION_NOT_FOUND: 40001,
  MESSAGE_EMPTY: 40002,
  MESSAGE_TOO_LARGE: 40003,
  MESSAGE_NOT_FOUND: 40005,
  RECALL_EXPIRED: 40006,
} as const
