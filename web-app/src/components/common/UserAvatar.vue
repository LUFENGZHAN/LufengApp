<script setup lang="ts">
import { computed, ref, watch } from 'vue'

const props = withDefaults(
  defineProps<{
    name?: string
    src?: string | null
    size?: number
    online?: boolean | null
    radius?: number
  }>(),
  { size: 40, radius: 6, online: null },
)

/** 图片加载失败（后端没有该资源、URL 失效等）时退回文字头像，避免出现破图 */
const broken = ref(false)

watch(
  () => props.src,
  () => {
    broken.value = false
  },
)

const showImage = computed(() => !!props.src && !broken.value)

const initials = computed(() => {
  const text = (props.name || '').trim()
  if (!text) return '?'
  // 中文取末字，英文取首字母
  if (/[一-龥]/.test(text)) return text.slice(-1)
  return text.slice(0, 2).toUpperCase()
})

const style = computed(() => ({
  width: `${props.size}px`,
  height: `${props.size}px`,
  borderRadius: `${props.radius}px`,
  fontSize: `${Math.max(12, Math.round(props.size * 0.36))}px`,
}))

/** 用昵称哈希取色，保证同一用户颜色稳定 */
const hue = computed(() => {
  let hash = 0
  const text = props.name || 'unknown'
  for (let i = 0; i < text.length; i++) {
    hash = (hash * 31 + text.charCodeAt(i)) % 360
  }
  return hash
})
</script>

<template>
  <div class="avatar-wrap" :style="{ width: `${size}px`, height: `${size}px` }">
    <div v-if="showImage" class="avatar" :style="style" :title="name">
      <img :src="src!" :alt="name" @error="broken = true" />
    </div>
    <div
      v-else
      class="avatar fallback"
      :style="[style, { background: `hsl(${hue}, 52%, 58%)` }]"
      :title="name"
    >
      {{ initials }}
    </div>
    <span v-if="online !== null" class="dot" :class="{ 'dot-on': online }" />
  </div>
</template>

<style scoped>
.avatar-wrap {
  position: relative;
  flex-shrink: 0;
}

.avatar {
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  background: #d9dce1;
  color: #fff;
  font-weight: 500;
  user-select: none;
}

.avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.dot {
  position: absolute;
  right: -2px;
  bottom: -2px;
  width: 10px;
  height: 10px;
  border-radius: 50%;
  border: 2px solid #fff;
  background: #c8ccd2;
}

.dot-on {
  background: #34c759;
}
</style>
