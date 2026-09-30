<script setup lang="ts">
import { ref } from 'vue'
import AppIcon from '@/components/common/AppIcon.vue'
import { useMessageStore } from '@/stores/message'
import { useRealtimeStore } from '@/stores/realtime'
import { useUiStore } from '@/stores/ui'
import { ApiError } from '@/api/http'
import { fileApi } from '@/api/file'
import { MsgType } from '@/api/types'

const props = defineProps<{
    conversationId: number
    /** 有值表示会话尚未创建，走「首条消息懒创建」通道 */
    peerId?: number | null
}>()

const emit = defineEmits<{ (e: 'sent', conversationId: number): void }>()

const messageStore = useMessageStore()
const realtime = useRealtimeStore()
const ui = useUiStore()

const text = ref('')
const busy = ref(false)
/** 媒体上传中：期间禁用发送与附件按钮，避免并发乱序 */
const uploading = ref(false)
const imageInput = ref<HTMLInputElement | null>(null)
const videoInput = ref<HTMLInputElement | null>(null)
/** typing 节流间隔：避免每敲一个字符都发一帧 */
const TYPING_THROTTLE = 3000
let lastTypingAt = 0

/**
 * 选中图片/视频后：先上传到后端拿到 /static/... 的 URL，再按消息发出。
 * 视频在上传前用 <video> 元素读一次元数据（时长/宽高），一并写进 extra。
 */
async function sendMedia(file: File, msgType: number) {
    if (busy.value || uploading.value) return
    uploading.value = true
    try {
        const category = msgType === MsgType.VIDEO ? 'video' : 'image'
        const result = await fileApi.upload(file, category)
        const meta: Record<string, number> = {}
        if (result.width) meta.w = result.width
        if (result.height) meta.h = result.height
        if (msgType === MsgType.VIDEO) {
            const v = await readVideoMeta(file)
            if (v) {
                if (v.duration) meta.duration = Number(v.duration.toFixed(1))
                if (v.w) meta.w = v.w
                if (v.h) meta.h = v.h
            }
        }
        const extra = Object.keys(meta).length ? JSON.stringify(meta) : undefined
        const content = result.url

        if (props.peerId) {
            const r = await messageStore.sendToPeer(props.peerId, content, msgType, extra)
            if (r.ok && r.conversationId) emit('sent', r.conversationId)
            else if (!r.ok) ui.error(r.error instanceof ApiError ? r.error.message : '发送失败')
        } else {
            const r = await messageStore.send(props.conversationId, content, msgType, extra)
            if (!r.ok) ui.error(r.error instanceof ApiError ? r.error.message : '发送失败')
        }
    } catch (e) {
        ui.error(e instanceof ApiError ? e.message : '上传失败，请重试')
    } finally {
        uploading.value = false
        if (imageInput.value) imageInput.value.value = ''
        if (videoInput.value) videoInput.value.value = ''
    }
}

function onImagePick(e: Event) {
    const file = (e.target as HTMLInputElement).files?.[0]
    if (file) void sendMedia(file, MsgType.IMAGE)
}

function onVideoPick(e: Event) {
    const file = (e.target as HTMLInputElement).files?.[0]
    if (file) void sendMedia(file, MsgType.VIDEO)
}

/** 读取视频元数据：loadedmetadata 后拿时长/宽高；失败或超时返回 null */
function readVideoMeta(file: File): Promise<{ duration: number; w: number; h: number } | null> {
    return new Promise((resolve) => {
        const url = URL.createObjectURL(file)
        const v = document.createElement('video')
        v.preload = 'metadata'
        const done = (val: { duration: number; w: number; h: number } | null) => {
            URL.revokeObjectURL(url)
            resolve(val)
        }
        v.onloadedmetadata = () => done({ duration: v.duration, w: v.videoWidth, h: v.videoHeight })
        v.onerror = () => done(null)
        v.src = url
        setTimeout(() => done(null), 8000)
    })
}

function notifyTyping() {
    // 会话还没建好时没有 conversationId，跳过输入中提示
    if (props.peerId) return
    const now = Date.now()
    if (now - lastTypingAt < TYPING_THROTTLE) return
    lastTypingAt = now
    realtime.sendTyping(props.conversationId)
}

async function submit() {
    const content = text.value.trim()
    if (!content || busy.value) return
    busy.value = true
    // 先清空输入框，发送结果由消息气泡状态反馈，避免用户等待
    text.value = ''
    try {
        if (props.peerId) {
            const result = await messageStore.sendToPeer(props.peerId, content)
            if (!result.ok) {
                const error = result.error
                ui.error(error instanceof ApiError ? error.message : '发送失败，请重试')
            } else if (result.conversationId) {
                // 会话已由服务端懒创建：通知外层切到真实会话路由
                emit('sent', result.conversationId)
            }
            return
        }
        const result = await messageStore.send(props.conversationId, content)
        if (!result.ok) {
            const error = result.error
            ui.error(error instanceof ApiError ? error.message : '发送失败，请重试')
        }
    } finally {
        busy.value = false
    }
}

/**
 * 粘贴即上传：从剪贴板挑出图片/视频文件，直接走上传+发送通道，
 * 无需先点附件按钮。支持两种来源：
 *  - 从文件管理器/浏览器复制出来的文件对象（clipboardData.files）
 *  - 截图工具（微信截图、Snipaste 等）复制的图片 blob（clipboardData.items）
 * 纯文本粘贴不拦截，正常进输入框。
 */
async function onPaste(e: ClipboardEvent) {
  if (busy.value || uploading.value) return
  const dt = e.clipboardData
  if (!dt) return
  const files: File[] = []
  if (dt.files && dt.files.length) {
    for (const f of Array.from(dt.files)) {
      if (mediaKindOf(f)) files.push(f)
    }
  }
  if (!files.length && dt.items && dt.items.length) {
    for (const it of Array.from(dt.items)) {
      if (it.kind === 'file' && it.type.startsWith('image/')) {
        const f = it.getAsFile()
        if (f) files.push(f)
      }
    }
  }
  if (!files.length) return // 纯文本：放行，不拦截
  e.preventDefault()
  for (const f of files) {
    const kind = mediaKindOf(f)
    await sendMedia(f, kind === 'video' ? MsgType.VIDEO : MsgType.IMAGE)
  }
}

/** 判定文件是图片/视频，否则返回 null（MIME 优先，扩展名兜底） */
function mediaKindOf(file: File): 'image' | 'video' | null {
  if (file.type.startsWith('image/')) return 'image'
  if (file.type.startsWith('video/')) return 'video'
  const ext = (file.name.split('.').pop() || '').toLowerCase()
  if (['png', 'jpg', 'jpeg', 'gif', 'webp', 'bmp', 'heic', 'svg'].includes(ext)) return 'image'
  if (['mp4', 'webm', 'ogg', 'mov', 'mkv', 'avi', 'm4v'].includes(ext)) return 'video'
  return null
}

function onKeydown(event: KeyboardEvent) {
    if (event.key !== 'Enter' || event.shiftKey || event.isComposing) return
    event.preventDefault()
    void submit()
}
</script>

<template>
    <div class="composer">
        <textarea v-model="text" class="editor" placeholder="输入消息…" rows="4"
            @input="notifyTyping" @keydown="onKeydown" @paste="onPaste" />
        <div class="toolbar">
            <div class="tools">
                <button class="icon-btn" type="button" :disabled="busy || uploading" title="发送图片"
                    @click="imageInput?.click()">
                    <AppIcon name="image" :size="18" />
                </button>
                <button class="icon-btn" type="button" :disabled="busy || uploading" title="发送视频"
                    @click="videoInput?.click()">
                    <AppIcon name="video" :size="18" />
                </button>
                <input ref="imageInput" type="file" accept="image/*" hidden @change="onImagePick" />
                <input ref="videoInput" type="file" accept="video/*" hidden @change="onVideoPick" />
            </div>
            <div>
                <span class="tip">
                    <span v-if="uploading" class="uploading">
                        <AppIcon name="spinner" :size="13" /> 上传中…
                    </span>
                    <template v-else>Enter 发送 · Shift + Enter 换行</template>
                </span>
                <button class="btn send" :disabled="!text.trim() || busy || uploading" @click="submit">
                    <AppIcon name="send" :size="15" />
                    发送
                </button>
            </div>
        </div>
    </div>
</template>

<style scoped>
.composer {
    border-top: 1px solid var(--border);
    background: #fff;
    padding: 8px 16px 12px;
}

.toolbar {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding-bottom: 6px;
}

.tip {
    font-size: 11.5px;
    color: var(--text-weak);
}

.tools {
    display: flex;
    align-items: center;
    gap: 6px;
}

.icon-btn {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    width: 30px;
    height: 30px;
    border: none;
    border-radius: 7px;
    background: transparent;
    color: var(--text-sub);
    cursor: pointer;
    transition: background 0.15s, color 0.15s;
}

.icon-btn:hover:not(:disabled) {
    background: rgba(0, 0, 0, 0.06);
    color: var(--brand);
}

.icon-btn:disabled {
    opacity: 0.4;
    cursor: not-allowed;
}

.uploading {
    display: inline-flex;
    align-items: center;
    gap: 4px;
    color: var(--brand);
}

.send {
    margin-left: 12px;
    height: 30px;
    padding: 0 20px;
    font-size: 13px;
}

.editor {
    width: 100%;
    min-height: 92px;
    padding: 6px 4px;
    border: none;
    resize: none;
    line-height: 1.6;
    color: var(--text);
    background: transparent;
}
</style>
