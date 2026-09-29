import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { FriendRequestVO, FriendVO } from '@/api/types'
import { friendApi } from '@/api/friend'

export const useFriendStore = defineStore('friend', () => {
  const friends = ref<FriendVO[]>([])
  const incoming = ref<FriendRequestVO[]>([])
  const outgoing = ref<FriendRequestVO[]>([])
  const loading = ref(false)

  async function load() {
    loading.value = true
    try {
      friends.value = (await friendApi.list()) ?? []
    } finally {
      loading.value = false
    }
  }

  async function loadRequests() {
    const [inList, outList] = await Promise.all([
      friendApi.requests('in'),
      friendApi.requests('out'),
    ])
    incoming.value = inList ?? []
    outgoing.value = outList ?? []
  }

  async function apply(toUserId: number, applyMsg?: string) {
    return friendApi.apply({ toUserId, applyMsg })
  }

  async function handle(requestId: number, action: 'accept' | 'reject') {
    await friendApi.handle(requestId, action)
    await Promise.all([loadRequests(), load()])
  }

  async function remove(friendId: number) {
    await friendApi.remove(friendId)
    friends.value = friends.value.filter((f) => f.userId !== friendId)
  }

  function setOnline(userId: number, online: boolean) {
    const target = friends.value.find((f) => f.userId === userId)
    if (target) target.online = online
  }

  function clear() {
    friends.value = []
    incoming.value = []
    outgoing.value = []
  }

  return {
    friends,
    incoming,
    outgoing,
    loading,
    load,
    loadRequests,
    apply,
    handle,
    remove,
    setOnline,
    clear,
  }
})
