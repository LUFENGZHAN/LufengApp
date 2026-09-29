import { get, post } from './http'
import type { MessageVO, PageVO, SendMessageReq, SyncItem, SyncReq } from './types'

export const messageApi = {
  /** HTTP 发送：幂等，重试时必须复用同一个 clientMsgId */
  send: (req: SendMessageReq) => post<MessageVO>('/v1/messages', req),
  /**
   * 历史消息游标分页：cursor 为上一页最小 seq，传空表示拉最新一页。
   * 注意返回顺序是最新在前，UI 需反序渲染。
   */
  history: (conversationId: number, cursor?: number, size = 20) =>
    get<PageVO<MessageVO>>(`/v1/conversations/${conversationId}/messages`, {
      cursor,
      size,
    }),
  /** 断线重连后的离线补拉 */
  sync: (items: SyncItem[]) => post<MessageVO[]>('/v1/messages/sync', { items } as SyncReq),
  recall: (msgNo: string) => post<void>(`/v1/messages/${msgNo}/recall`),
}
