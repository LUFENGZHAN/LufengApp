'use strict'

/**
 * Electron 桌面端内置的本地服务：
 *  1. 静态托管 Vite 打包产物 dist/
 *  2. 把 /api、/static 反代到后端（HTTP）
 *  3. 把 /ws 升级连接反代到后端（WebSocket）
 *
 * 这样渲染进程始终与后端「同源」（都在 http://127.0.0.1:<随机端口>），
 * 前端既有的相对路径 /api、ws://<host>/ws、/static 无需任何改动即可工作，
 * 也不用开 CORS、不用担心 WS 握手被拦。
 */

const http = require('node:http')
const https = require('node:https')
const net = require('node:net')
const tls = require('node:tls')
const fs = require('node:fs')
const path = require('node:path')

const MIME = {
  '.html': 'text/html; charset=utf-8',
  '.js': 'text/javascript; charset=utf-8',
  '.mjs': 'text/javascript; charset=utf-8',
  '.css': 'text/css; charset=utf-8',
  '.json': 'application/json; charset=utf-8',
  '.svg': 'image/svg+xml',
  '.png': 'image/png',
  '.jpg': 'image/jpeg',
  '.jpeg': 'image/jpeg',
  '.gif': 'image/gif',
  '.webp': 'image/webp',
  '.ico': 'image/x-icon',
  '.woff': 'font/woff',
  '.woff2': 'font/woff2',
  '.ttf': 'font/ttf',
  '.map': 'application/json; charset=utf-8',
  '.txt': 'text/plain; charset=utf-8',
}

/** 需要转发到后端的路径前缀，其余一律当静态资源处理 */
const PROXY_PREFIXES = ['/api', '/static']

function shouldProxy(url) {
  return PROXY_PREFIXES.some((p) => url === p || url.startsWith(p + '/') || url.startsWith(p + '?'))
}

/**
 * 解析后端地址。支持 http/https 与带路径前缀（如 https://example.com/chat），
 * 域名后面挂反代/网关时前缀会被原样拼回去。
 */
function targetInfo(backend) {
  const url = new URL(backend)
  const secure = url.protocol === 'https:'
  return {
    secure,
    hostname: url.hostname,
    port: Number(url.port) || (secure ? 443 : 80),
    host: url.host,
    basePath: url.pathname.replace(/\/+$/, ''), // '' 或 '/prefix'
  }
}

/** 把后端 URL 的路径前缀补到请求路径上 */
function joinPath(basePath, reqUrl) {
  if (!basePath) return reqUrl
  return basePath + (reqUrl.startsWith('/') ? reqUrl : '/' + reqUrl)
}

/** HTTP 反代（按协议自动选择 http / https） */
function proxyHttp(req, res, backend) {
  const t = targetInfo(backend)
  const mod = t.secure ? https : http
  const upstream = mod.request(
    {
      hostname: t.hostname,
      port: t.port,
      path: joinPath(t.basePath, req.url),
      method: req.method,
      headers: { ...req.headers, host: t.host },
    },
    (upRes) => {
      res.writeHead(upRes.statusCode || 502, upRes.headers)
      upRes.pipe(res)
    },
  )
  upstream.on('error', (err) => {
    const body = JSON.stringify({
      code: 10007,
      message: `无法连接后端服务（${backend}），请确认后端已启动：${err.message}`,
      timestamp: Date.now(),
    })
    if (!res.headersSent) {
      res.writeHead(502, {
        'Content-Type': 'application/json; charset=utf-8',
        'Content-Length': Buffer.byteLength(body),
      })
    }
    res.end(body)
  })
  req.pipe(upstream)
}

/**
 * WebSocket 反代：手写升级请求转发，避免引入 http-proxy 依赖。
 * https 后端（域名）走 tls.connect + SNI，http 后端走 net.connect。
 */
function proxyWs(req, socket, head, backend) {
  const t = targetInfo(backend)

  const onConnect = () => {
    const headers = { ...req.headers, host: t.host }
    const lines = [`${req.method} ${joinPath(t.basePath, req.url)} HTTP/1.1`]
    for (const [k, v] of Object.entries(headers)) {
      if (v === undefined) continue
      if (Array.isArray(v)) for (const item of v) lines.push(`${k}: ${item}`)
      else lines.push(`${k}: ${v}`)
    }
    upstream.write(lines.join('\r\n') + '\r\n\r\n')
    if (head && head.length) upstream.write(head)
    upstream.pipe(socket)
    socket.pipe(upstream)
  }

  const upstream = t.secure
    ? tls.connect(
        {
          host: t.hostname,
          port: t.port,
          // IP 直连时不能设置 SNI（RFC 6066），仅域名传 servername
          servername: net.isIP(t.hostname) ? undefined : t.hostname,
        },
        onConnect,
      )
    : net.connect(t.port, t.hostname, onConnect)

  const teardown = () => {
    socket.destroy()
    upstream.destroy()
  }
  upstream.on('error', teardown)
  upstream.on('close', teardown)
  socket.on('error', teardown)
  socket.on('close', teardown)
}

/** 静态文件 + SPA 兜底（未命中的路径回退 index.html，交给前端路由） */
function serveStatic(req, res, rootDir) {
  let pathname
  try {
    pathname = decodeURIComponent((req.url || '/').split('?')[0])
  } catch {
    pathname = '/'
  }
  if (!pathname || pathname === '/') pathname = '/index.html'

  const safeRel = path.normalize(pathname).replace(/^(\.\.[/\\])+/, '').replace(/^[/\\]+/, '')
  const filePath = path.join(rootDir, safeRel)

  fs.stat(filePath, (err, stat) => {
    if (err || !stat.isFile()) {
      const fallback = path.join(rootDir, 'index.html')
      fs.readFile(fallback, (e2, data) => {
        if (e2) {
          res.writeHead(404, { 'Content-Type': 'text/plain; charset=utf-8' })
          res.end('404 Not Found（未找到前端产物，请先执行 npm run build）')
          return
        }
        res.writeHead(200, { 'Content-Type': MIME['.html'] })
        res.end(data)
      })
      return
    }
    const ext = path.extname(filePath).toLowerCase()
    res.writeHead(200, {
      'Content-Type': MIME[ext] || 'application/octet-stream',
      'Cache-Control': ext === '.html' ? 'no-cache' : 'public, max-age=3600',
    })
    fs.createReadStream(filePath).pipe(res)
  })
}

/**
 * 启动本地服务，监听 127.0.0.1 的随机空闲端口。
 * @returns {Promise<{ port:number, close:() => void }>}
 */
function startServer({ rootDir, backend }) {
  const server = http.createServer((req, res) => {
    if (shouldProxy(req.url || '')) return proxyHttp(req, res, backend)
    serveStatic(req, res, rootDir)
  })

  server.on('upgrade', (req, socket, head) => {
    if ((req.url || '').startsWith('/ws')) return proxyWs(req, socket, head, backend)
    socket.destroy()
  })

  return new Promise((resolve, reject) => {
    server.on('error', reject)
    server.listen(0, '127.0.0.1', () => {
      resolve({ port: server.address().port, close: () => server.close() })
    })
  })
}

module.exports = { startServer }
