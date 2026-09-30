/** 媒体消息 extra 字段的解析：存的是 JSON 字符串，带宽高/时长等元数据 */
export interface MediaMeta {
  w?: number
  h?: number
  duration?: number
}

export function parseMediaExtra(extra: string | null | undefined): MediaMeta {
  if (!extra) return {}
  try {
    const obj = JSON.parse(extra) as Record<string, unknown>
    const meta: MediaMeta = {}
    if (typeof obj.w === 'number') meta.w = obj.w
    if (typeof obj.h === 'number') meta.h = obj.h
    if (typeof obj.duration === 'number') meta.duration = obj.duration
    return meta
  } catch {
    return {}
  }
}
