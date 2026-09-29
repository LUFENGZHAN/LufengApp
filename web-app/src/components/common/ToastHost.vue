<script setup lang="ts">
import { useUiStore } from '@/stores/ui'

const ui = useUiStore()
</script>

<template>
  <div class="toast-host">
    <TransitionGroup name="toast">
      <div v-for="toast in ui.toasts" :key="toast.id" class="toast" :class="`toast-${toast.type}`">
        {{ toast.message }}
      </div>
    </TransitionGroup>
  </div>
</template>

<style scoped>
.toast-host {
  position: fixed;
  top: 24px;
  left: 50%;
  transform: translateX(-50%);
  z-index: 9999;
  display: flex;
  flex-direction: column;
  gap: 10px;
  pointer-events: none;
}

.toast {
  min-width: 200px;
  max-width: 460px;
  padding: 10px 16px;
  border-radius: 8px;
  background: #fff;
  color: var(--text);
  box-shadow: var(--shadow-md);
  border-left: 4px solid var(--brand);
  text-align: center;
  font-size: 13px;
}

.toast-success {
  border-left-color: var(--brand);
}

.toast-error {
  border-left-color: var(--danger);
}

.toast-warn {
  border-left-color: var(--warn);
}

.toast-enter-active,
.toast-leave-active {
  transition: all 0.22s ease;
}

.toast-enter-from {
  opacity: 0;
  transform: translateY(-10px);
}

.toast-leave-to {
  opacity: 0;
  transform: translateY(-10px);
}
</style>
