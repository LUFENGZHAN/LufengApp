import { request, get, post, del } from './http'
import type { ConversationReq, ConversationVO, ReadReq } from './types'

export const conversationApi = {
  createPrivate: (targetUserId: number) =>
    post<ConversationVO>('/v1/conversations/private', { targetUserId } as ConversationReq),
  list: (page = 1, size = 50) => get<ConversationVO[]>('/v1/conversations', { page, size }),
  detail: (conversationId: number) => get<ConversationVO>(`/v1/conversations/${conversationId}`),
  remove: (conversationId: number) => del<void>(`/v1/conversations/${conversationId}`),
  /** 已读上报，返回剩余未读数 */
  markRead: (conversationId: number, lastReadSeq: number) =>
    post<{ unreadCount: number }>(
      `/v1/conversations/${conversationId}/read`,
      { lastReadSeq } as ReadReq,
    ),
  mute: (conversationId: number, muted: boolean) =>
    request<void>({
      method: 'PUT',
      url: `/v1/conversations/${conversationId}/mute`,
      params: { muted },
    }),
}
