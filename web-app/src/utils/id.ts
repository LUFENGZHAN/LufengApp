/**
 * 轻量 UUID/nanoid 实现，避免为此引入依赖。
 * clientMsgId 只需在客户端保证唯一即可——服务端按 (conversation_id, client_msg_id) 做幂等。
 */
const ALPHABET = 'abcdefghijklmnopqrstuvwxyz0123456789'

function randomBytes(size: number): Uint8Array {
  const buf = new Uint8Array(size)
  if (typeof crypto !== 'undefined' && crypto.getRandomValues) {
    crypto.getRandomValues(buf)
  } else {
    for (let i = 0; i < size; i++) {
      buf[i] = Math.floor(Math.random() * 256)
    }
  }
  return buf
}

export function nanoid(size = 21): string {
  const bytes = randomBytes(size)
  let out = ''
  for (let i = 0; i < size; i++) {
    out += ALPHABET[bytes[i] % ALPHABET.length]
  }
  return out
}

export function uuidV4(): string {
  const bytes = randomBytes(16)
  bytes[6] = (bytes[6] & 0x0f) | 0x40
  bytes[8] = (bytes[8] & 0x3f) | 0x80
  const hex: string[] = []
  for (let i = 0; i < 16; i++) {
    hex.push(bytes[i].toString(16).padStart(2, '0'))
  }
  return [
    hex.slice(0, 4).join(''),
    hex.slice(4, 6).join(''),
    hex.slice(6, 8).join(''),
    hex.slice(8, 10).join(''),
    hex.slice(10, 16).join(''),
  ].join('-')
}

/** 客户端幂等 ID：时间戳 + 随机串，可读且够唯一 */
export function clientMsgId(): string {
  return Date.now().toString(36) + '-' + nanoid(12)
}
