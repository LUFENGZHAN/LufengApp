import { get, post, put, del } from './http'
import type { ApplyFriendReq, FriendRequestVO, FriendVO, HandleFriendReq } from './types'

export const friendApi = {
  apply: (req: ApplyFriendReq) => post<{ requestId: number }>('/v1/friends/requests', req),
  /** direction: in（收到的）/ out（发出的）；status 留空表示全部 */
  requests: (direction: 'in' | 'out' = 'in', status?: number) =>
    get<FriendRequestVO[]>('/v1/friends/requests', { direction, status }),
  handle: (requestId: number, action: 'accept' | 'reject') =>
    put<void>(`/v1/friends/requests/${requestId}`, { action } as HandleFriendReq),
  list: () => get<FriendVO[]>('/v1/friends'),
  remove: (friendId: number) => del<void>(`/v1/friends/${friendId}`),
  remark: (friendId: number, remark: string) =>
    put<void>(`/v1/friends/${friendId}/remark`, { remark }),
}
