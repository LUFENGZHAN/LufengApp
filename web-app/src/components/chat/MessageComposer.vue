<script setup lang="ts">
import { ref } from 'vue'
import AppIcon from '@/components/common/AppIcon.vue'
import { useMessageStore } from '@/stores/message'
import { useRealtimeStore } from '@/stores/realtime'
import { useUiStore } from '@/stores/ui'
import { ApiError } from '@/api/http'

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
/** typing 节流间隔：避免每敲一个字符都发一帧 */
const TYPING_THROTTLE = 3000
let lastTypingAt = 0

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

function onKeydown(event: KeyboardEvent) {
    if (event.key !== 'Enter' || event.shiftKey || event.isComposing) return
    event.preventDefault()
    void submit()
}
</script>

<template>
    <div class="composer">
        <textarea v-model="text" class="editor" placeholder="输入消息…" rows="4" @input="notifyTyping"
            @keydown="onKeydown" />
        <div class="toolbar">
            <span class="tip">Enter 发送 · Shift + Enter 换行</span>
            <button class="btn send" :disabled="!text.trim() || busy" @click="submit">
                <AppIcon name="send" :size="15" />
                发送
            </button>
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

.send {
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
