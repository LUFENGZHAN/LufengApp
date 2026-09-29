<script setup lang="ts">
import { computed } from 'vue'
import { useRealtimeStore } from '@/stores/realtime'

const realtime = useRealtimeStore()

const label = computed(
  () =>
    ({
      idle: '未连接',
      connecting: '连接中…',
      open: '实时在线',
      reconnecting: '重连中…',
      closed: '连接已断开',
    })[realtime.state],
)

const level = computed(() => {
  switch (realtime.state) {
    case 'open':
      return 'ok'
    case 'connecting':
    case 'reconnecting':
      return 'warn'
    default:
      return 'down'
  }
})
</script>

<template>
  <div class="badge" :class="`badge-${level}`" :title="label">
    <i class="pulse" />
    <span>{{ label }}</span>
  </div>
</template>

<style scoped>
.badge {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 3px 10px;
  border-radius: 999px;
  font-size: 12px;
  background: var(--border-soft);
  color: var(--text-sub);
}

.badge-ok {
  background: var(--brand-soft);
  color: var(--brand-dark);
}

.badge-warn {
  background: #fff7e6;
  color: #b45309;
}

.badge-down {
  background: #fee2e2;
  color: #b91c1c;
}

.pulse {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: currentColor;
}

.badge-warn .pulse,
.badge-ok .pulse {
  animation: breathe 1.4s ease-in-out infinite;
}

@keyframes breathe {
  0%,
  100% {
    opacity: 1;
  }
  50% {
    opacity: 0.25;
  }
}
</style>
