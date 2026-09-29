'use strict'

/**
 * 桌面端配置：把后端地址从「写死在代码里」改为「可配置」。
 *
 * 解析优先级（先命中先返回）：
 *   1. 环境变量 LUFENG_BACKEND        —— 开发/临时覆盖
 *   2. exe 同目录 lufeng.config.json  —— 运维最常改的地方（换域名就改这里）
 *   3. 用户数据目录 <userData>/lufeng.config.json
 *   4. 内置默认值 http://127.0.0.1:3001
 *
 * 打包后首次启动时，若 exe 同目录没有配置文件，会自动写一份带注释的模板，
 * 方便用户直接照抄改域名（目录不可写则静默跳过，不影响启动）。
 */

const fs = require('node:fs')
const path = require('node:path')

const CONFIG_FILENAME = 'lufeng.config.json'
const DEFAULT_BACKEND = 'http://127.0.0.1:3001'

function readJson(file) {
  try {
    const data = JSON.parse(fs.readFileSync(file, 'utf8'))
    return data && typeof data === 'object' ? data : null
  } catch {
    return null
  }
}

/**
 * 规范化后端地址：去首尾空白与结尾斜杠，仅接受 http/https，非法返回 null。
 * 支持带路径前缀（如 https://example.com/chat）。
 */
function normalizeBackend(value) {
  if (typeof value !== 'string' || !value.trim()) return null
  const trimmed = value.trim().replace(/\/+$/, '')
  try {
    const url = new URL(trimmed)
    if (url.protocol !== 'http:' && url.protocol !== 'https:') return null
    return trimmed
  } catch {
    return null
  }
}

/**
 * 解析出最终后端地址。
 * @returns {{ backend: string, source: string }}
 */
function resolveBackend({ exeDir, userDataDir, env = process.env } = {}) {
  const exeConfig = exeDir ? readJson(path.join(exeDir, CONFIG_FILENAME)) : null
  const userConfig = userDataDir ? readJson(path.join(userDataDir, CONFIG_FILENAME)) : null

  const candidates = [
    { source: '环境变量 LUFENG_BACKEND', value: env.LUFENG_BACKEND },
    { source: `${exeDir || 'exe 目录'}/${CONFIG_FILENAME}`, value: exeConfig && exeConfig.backend },
    { source: `${userDataDir || 'userData'}/${CONFIG_FILENAME}`, value: userConfig && userConfig.backend },
  ]

  for (const c of candidates) {
    const backend = normalizeBackend(c.value)
    if (backend) return { backend, source: c.source }
  }
  return { backend: DEFAULT_BACKEND, source: '内置默认值' }
}

const CONFIG_TEMPLATE = `{
  "//": "麓风聊天桌面端配置。backend = 后端服务地址；换成自己的域名即可，例如 https://chat.example.com",
  "//1": "改完保存、重启程序生效；留空或删除本文件则回退到 http://127.0.0.1:3001",
  "backend": "${DEFAULT_BACKEND}"
}
`

/** 若 exe 同目录缺少配置文件则写一份模板；返回写入的路径或 null */
function ensureConfigTemplate(exeDir) {
  if (!exeDir) return null
  const file = path.join(exeDir, CONFIG_FILENAME)
  try {
    if (fs.existsSync(file)) return file
    fs.writeFileSync(file, CONFIG_TEMPLATE, 'utf8')
    return file
  } catch {
    return null
  }
}

module.exports = {
  resolveBackend,
  ensureConfigTemplate,
  normalizeBackend,
  CONFIG_FILENAME,
  DEFAULT_BACKEND,
}
