import type { ConversationVO, FriendVO } from '@/api/types'

/** 会话展示名：群聊用标题，单聊优先好友备注 */
export function convName(conversation: ConversationVO, friends: FriendVO[] = []): string {
  if (conversation.type !== 1) return conversation.title || '群聊'
  const peerId = conversation.peer?.userId
  const remark = friends.find((f) => f.userId === peerId)?.remark
  return remark || conversation.peer?.nickname || conversation.title || '陌生人'
}

export function convAvatar(conversation: ConversationVO): string | null {
  return conversation.type === 1 ? conversation.peer?.avatar ?? null : conversation.avatar ?? null
}

export function convPeerOnline(conversation: ConversationVO, friends: FriendVO[] = []): boolean | null {
  if (conversation.type !== 1 || !conversation.peer) return null
  return friends.find((f) => f.userId === conversation.peer?.userId)?.online ?? conversation.peer.online ?? false
}
