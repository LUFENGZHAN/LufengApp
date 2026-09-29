<script setup lang="ts">
import { RouterView } from 'vue-router'
import ToastHost from '@/components/common/ToastHost.vue'
import ConfirmDialog from '@/components/common/ConfirmDialog.vue'
</script>

<template>
  <RouterView />
  <ToastHost />
  <ConfirmDialog />
</template>

<style scoped></style>
