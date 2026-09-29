'use strict'

/**
 * 预加载脚本：在渲染进程里安全地暴露「是否在桌面端」等环境信息，
 * 不开启 nodeIntegration，仅通过 contextBridge 提供只读标记。
 */
const { contextBridge } = require('electron')

contextBridge.exposeInMainWorld('lufengDesktop', {
  isDesktop: true,
  platform: process.platform,
  versions: {
    electron: process.versions.electron,
    chrome: process.versions.chrome,
    node: process.versions.node,
  },
})
