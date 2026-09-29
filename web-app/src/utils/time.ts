import dayjs from 'dayjs'
import relativeTime from 'dayjs/plugin/relativeTime'
import 'dayjs/locale/zh-cn'

dayjs.extend(relativeTime)
dayjs.locale('zh-cn')

/** 会话列表时间：今天显示时分，昨天显示"昨天"，本周显示周几，更早显示日期 */
export function formatListTime(ts?: number | null): string {
  if (!ts) return ''
  const t = dayjs(ts)
  const now = dayjs()
  if (t.isSame(now, 'day')) return t.format('HH:mm')
  if (t.isSame(now.subtract(1, 'day'), 'day')) return '昨天'
  if (now.diff(t, 'day') < 7) return t.format('ddd')
  return t.format('M/D')
}

/** 消息气泡悬浮时间：完整日期 */
export function formatFullTime(ts?: number | null): string {
  if (!ts) return ''
  return dayjs(ts).format('YYYY-MM-DD HH:mm:ss')
}

/** 消息分组时间间隔：超过 5 分钟插入时间分隔线 */
export function shouldSplit(prev?: number, next?: number): boolean {
  if (!prev || !next) return true
  return next - prev > 5 * 60 * 1000
}

export function formatDivider(ts: number): string {
  const t = dayjs(ts)
  const now = dayjs()
  if (t.isSame(now, 'day')) return t.format('HH:mm')
  if (t.isSame(now.subtract(1, 'day'), 'day')) return `昨天 ${t.format('HH:mm')}`
  if (t.isSame(now, 'year')) return t.format('M月D日 HH:mm')
  return t.format('YYYY年M月D日 HH:mm')
}
