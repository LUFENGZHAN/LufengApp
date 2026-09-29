<script setup lang="ts">
import { computed } from 'vue'

/**
 * 轻量内联图标组件。
 * 不引入图标库：路径为静态常量，颜色继承 currentColor，尺寸由 size 控制。
 * 用 SVG 而非 CSS 几何块拼图 —— 后者无法表达语义，多个图标看起来完全一样。
 */
export type IconName =
  | 'chat'
  | 'contacts'
  | 'user'
  | 'logout'
  | 'search'
  | 'plus'
  | 'send'
  | 'close'
  | 'back'
  | 'check'
  | 'trash'
  | 'image'
  | 'spinner'

const ICONS: Record<IconName, string> = {
  // 会话气泡
  chat: '<path d="M7.9 20A9 9 0 1 0 4 16.1L2 22Z"/>',
  // 联系人（双人）
  contacts:
    '<path d="M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/><path d="M22 21v-2a4 4 0 0 0-3-3.87"/><path d="M16 3.13a4 4 0 0 1 0 7.75"/>',
  // 我的（单人）
  user: '<path d="M19 21v-2a4 4 0 0 0-4-4H9a4 4 0 0 0-4 4v2"/><circle cx="12" cy="7" r="4"/>',
  // 退出
  logout:
    '<path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4"/><path d="M16 17l5-5-5-5"/><path d="M21 12H9"/>',
  search: '<circle cx="11" cy="11" r="7"/><path d="M20.5 20.5 16.8 16.8"/>',
  plus: '<path d="M12 5v14"/><path d="M5 12h14"/>',
  // 发送（纸飞机）
  send: '<path d="M22 2 11 13"/><path d="M22 2 15 22l-4-9-9-4Z"/>',
  close: '<path d="M18 6 6 18"/><path d="m6 6 12 12"/>',
  back: '<path d="m15 18-6-6 6-6"/>',
  check: '<path d="M20 6 9 17l-5-5"/>',
  trash:
    '<path d="M3 6h18"/><path d="M8 6V4h8v2"/><path d="M19 6l-1 14H6L5 6"/><path d="M10 11v6"/><path d="M14 11v6"/>',
  image:
    '<rect x="3" y="3" width="18" height="18" rx="2"/><circle cx="9" cy="9" r="2"/><path d="m21 15-5-5L5 21"/>',
  spinner: '<path d="M21 12a9 9 0 1 1-6.2-8.6"/>',
}

const props = withDefaults(defineProps<{ name: IconName; size?: number }>(), { size: 20 })

const inner = computed(() => ICONS[props.name] ?? '')
</script>

<template>
  <svg
    class="app-icon"
    :class="{ spin: name === 'spinner' }"
    :width="size"
    :height="size"
    viewBox="0 0 24 24"
    fill="none"
    stroke="currentColor"
    stroke-width="1.9"
    stroke-linecap="round"
    stroke-linejoin="round"
    aria-hidden="true"
    v-html="inner"
  />
</template>

<style scoped>
.app-icon {
  display: block;
  flex-shrink: 0;
}

.spin {
  animation: icon-spin 0.9s linear infinite;
}

@keyframes icon-spin {
  to {
    transform: rotate(360deg);
  }
}
</style>
