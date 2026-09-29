/**
 * 导航解耦：store 里不应该反向依赖 router（会被打包器判为循环引用，也让单测变困难）。
 * 由应用入口注册真正的跳转实现，未注册时退化为整页跳转。
 */
type Navigate = (to: string) => void

let impl: Navigate | null = null

export function registerNavigator(fn: Navigate) {
  impl = fn
}

export function goLogin(redirect?: string) {
  const target = redirect ? `/login?redirect=${encodeURIComponent(redirect)}` : '/login'
  if (!impl) {
    window.location.href = target
    return
  }
  impl(target)
}
