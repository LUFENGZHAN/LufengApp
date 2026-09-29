import { get, post } from './http'
import type { ChangePasswordReq, LoginReq, RegisterReq, TokenVO, UpdateUserReq, UserVO } from './types'

export const authApi = {
  register: (req: RegisterReq) => post<UserVO>('/v1/auth/register', req),
  login: (req: LoginReq) => post<TokenVO>('/v1/auth/login', req),
  /** 登出当前设备：后端按 Authorization 头定位会话并吊销 */
  logout: () => post<void>('/v1/auth/logout'),
  me: () => get<UserVO>('/v1/users/me'),
  changePassword: (req: ChangePasswordReq) => post<void>('/v1/auth/password', req),
}

export const userApi = {
  me: () => get<UserVO>('/v1/users/me'),
  update: (req: UpdateUserReq) => post<UserVO>('/v1/users/me', req),
  search: (keyword: string, size = 20) => get<UserVO[]>('/v1/users/search', { keyword, size }),
  detail: (userNo: string) => get<UserVO>(`/v1/users/${userNo}`),
}
