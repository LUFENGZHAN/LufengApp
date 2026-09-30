'use strict'

const { app, BrowserWindow, shell, Menu, dialog } = require('electron')
const path = require('node:path')
const fs = require('node:fs')
const { startServer } = require('./server.cjs')
const { resolveBackend, ensureConfigTemplate } = require('./config.cjs')

const DIST_DIR = path.join(__dirname, '..', 'dist')
const ICON = path.join(__dirname, '..', 'build', 'icon.png')
/** 打包后由 exe 自带图标，无需再传 window icon；仅当图标文件存在时才使用 */
const WINDOW_ICON = fs.existsSync(ICON) ? ICON : undefined

let mainWindow = null
let localServer = null

/**
 * exe 所在目录：便携版(portable)会把程序解包到临时目录运行，
 * 此时真实所在目录由 PORTABLE_EXECUTABLE_DIR 给出，否则取可执行文件所在目录。
 */
function resolveExeDir() {
  if (process.env.PORTABLE_EXECUTABLE_DIR) return process.env.PORTABLE_EXECUTABLE_DIR
  try {
    return path.dirname(app.getPath('exe'))
  } catch {
    return null
  }
}

/* 单实例：重复打开时聚焦已有窗口 */
if (!app.requestSingleInstanceLock()) {
  app.quit()
} else {
  app.on('second-instance', () => {
    if (!mainWindow) return
    if (mainWindow.isMinimized()) mainWindow.restore()
    mainWindow.focus()
  })
}

async function createWindow() {
  // 后端地址可配置：env / exe 同目录 lufeng.config.json / userData 配置 / 内置默认
  const exeDir = resolveExeDir()
  if (app.isPackaged) ensureConfigTemplate(exeDir)
  const { backend, source } = resolveBackend({ exeDir, userDataDir: app.getPath('userData') })
  console.log(`[lufeng] 后端地址：${backend}（来源：${source}）`)

  try {
    localServer = await startServer({ rootDir: DIST_DIR, backend })
  } catch (e) {
    dialog.showErrorBox('启动失败', `本地服务启动失败：${e && e.message ? e.message : e}`)
    app.quit()
    return
  }

  mainWindow = new BrowserWindow({
    width: 1180,
    height: 780,
    minWidth: 960,
    minHeight: 620,
    backgroundColor: '#f5f6f8',
    title: '麓风聊天',
    autoHideMenuBar: true,
    icon: WINDOW_ICON,
    webPreferences: {
      preload: path.join(__dirname, 'preload.cjs'),
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: false,
    },
  })

  // 生产态隐藏默认菜单栏，更像一个原生应用
  Menu.setApplicationMenu(null)

  await mainWindow.loadURL(`http://127.0.0.1:${localServer.port}/`)

  // —— 开发者工具 ——
  // 不自动打开；开发环境（electron . 直接跑）用 Ctrl+Shift+I 或 F12 以「独立窗口（端外 detach）」
  // 形式切换开/关；生产环境（打包后的 exe）禁止打开，避免暴露调试能力。
  mainWindow.webContents.on('before-input-event', (_evt, input) => {
    if (app.isPackaged) return // 生产环境不开放 DevTools
    if (input.key === 'F12' || (input.control && input.shift && input.code === 'KeyI')) {
      const wc = mainWindow?.webContents
      if (!wc) return
      if (wc.isDevToolsOpened()) wc.closeDevTools()
      else wc.openDevTools({ mode: 'detach' })
    }
  })

  // 外链一律丢给系统浏览器打开，桌面程序内不跳走
  mainWindow.webContents.setWindowOpenHandler(({ url }) => {
    if (/^https?:/i.test(url)) shell.openExternal(url)
    return { action: 'deny' }
  })

  mainWindow.on('closed', () => {
    mainWindow = null
  })
}

app.whenReady().then(createWindow)

app.on('window-all-closed', () => {
  if (process.platform !== 'darwin') app.quit()
})

app.on('activate', () => {
  if (BrowserWindow.getAllWindows().length === 0) createWindow()
})

app.on('before-quit', () => {
  try {
    localServer && localServer.close()
  } catch {
    /* ignore */
  }
})
