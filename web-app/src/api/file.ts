import { post } from './http'
import type { FileUploadResult } from './types'

/**
 * 媒体文件上传：聊天发图片/视频的前置步骤。
 * 走 post()（已解包 Result.data），并自动带鉴权头、过期静默刷新。
 * data 为 FormData 时 axios 会自动设置 multipart boundary，无需手动指定 Content-Type。
 */
export const fileApi = {
  upload: (file: File, category: 'image' | 'video') => {
    const form = new FormData()
    form.append('file', file)
    form.append('category', category)
    return post<FileUploadResult>('/v1/files/upload', form)
  },
}
