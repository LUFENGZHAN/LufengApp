<script setup lang="ts">
import { watch, onUnmounted } from 'vue'
import { useUiStore } from '@/stores/ui'

const ui = useUiStore()

function onConfirm() {
  ui.resolveConfirm(true)
}

function onCancel() {
  ui.resolveConfirm(false)
}

function onKey(e: KeyboardEvent) {
  if (!ui.confirmState.open) return
  if (e.key === 'Escape') {
    e.preventDefault()
    onCancel()
  } else if (e.key === 'Enter') {
    e.preventDefault()
    onConfirm()
  }
}

watch(
  () => ui.confirmState.open,
  (open) => {
    if (open) window.addEventListener('keydown', onKey)
    else window.removeEventListener('keydown', onKey)
  },
)

onUnmounted(() => window.removeEventListener('keydown', onKey))
</script>

<template>
  <Transition name="confirm">
    <div v-if="ui.confirmState.open" class="mask" @click.self="onCancel">
      <div class="dialog" role="alertdialog" aria-modal="true">
        <h3 v-if="ui.confirmState.title" class="title">{{ ui.confirmState.title }}</h3>
        <p class="message">{{ ui.confirmState.message }}</p>
        <div class="actions">
          <button class="btn btn-ghost" @click="onCancel">
            {{ ui.confirmState.cancelText }}
          </button>
          <button class="btn" :class="{ 'btn-danger': ui.confirmState.danger }" @click="onConfirm">
            {{ ui.confirmState.confirmText }}
          </button>
        </div>
      </div>
    </div>
  </Transition>
</template>

<style scoped>
.mask {
  position: fixed;
  inset: 0;
  z-index: 200;
  background: rgba(17, 24, 39, 0.45);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 20px;
}

.dialog {
  width: min(380px, calc(100vw - 40px));
  background: #fff;
  border-radius: 14px;
  box-shadow: var(--shadow-lg);
  padding: 22px 22px 18px;
  text-align: center;
}

.title {
  margin: 0 0 10px;
  font-size: 16px;
  font-weight: 600;
  color: var(--text);
}

.message {
  margin: 0 0 22px;
  font-size: 14px;
  line-height: 1.6;
  color: var(--text-sub);
  white-space: pre-line;
  word-break: break-word;
}

.actions {
  display: flex;
  gap: 12px;
}

.actions .btn {
  flex: 1;
}

/* 进入/离开动画 */
.confirm-enter-active,
.confirm-leave-active {
  transition: opacity 0.18s ease;
}

.confirm-enter-active .dialog,
.confirm-leave-active .dialog {
  transition: transform 0.18s ease, opacity 0.18s ease;
}

.confirm-enter-from,
.confirm-leave-to {
  opacity: 0;
}

.confirm-enter-from .dialog,
.confirm-leave-to .dialog {
  transform: translateY(8px) scale(0.97);
  opacity: 0;
}
</style>
