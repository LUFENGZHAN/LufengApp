/**
 * 桌面端（Electron）运行环境判定 —— 全局唯一判定入口。
 *
 * - 首选预加载脚本经 contextBridge 注入的 `window.lufengDesktop.isDesktop`；
 * - 若预加载未生效（安全策略拦截等），退化为 UA 判断：Electron 的 UA 里
 *   必定带有 `Electron/<version>` 标识，因此这条兜底足够可靠。
 *
 * 判定只与运行环境有关，运行期间不会变化，故按常量导出，供多处复用，
 * 避免「main.ts 认为在桌面端、组件认为不在」这类不一致。
 */
export const IS_DESKTOP: boolean = (() => {
  if (typeof window === 'undefined' || typeof navigator === 'undefined') return false

  const bridge = window as unknown as { lufengDesktop?: { isDesktop?: boolean } }
  if (bridge.lufengDesktop?.isDesktop) return true

  return navigator.userAgent.includes('Electron')
})()
