import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const boardRoot = fileURLToPath(new URL('./Box_Warehouse_Dashboard', import.meta.url))
const apiTarget = process.env.VITE_API_PROXY_TARGET || 'http://127.0.0.1:8080'
const backendProxy = { target: apiTarget, changeOrigin: true }

function boxWarehouseBoard() {
  const contentTypes = {
    '.html': 'text/html; charset=utf-8',
    '.css': 'text/css; charset=utf-8',
    '.js': 'text/javascript; charset=utf-8',
    '.svg': 'image/svg+xml',
    '.png': 'image/png',
    '.jpg': 'image/jpeg',
    '.jpeg': 'image/jpeg',
  }

  const serveBoard = (req, res, next) => {
    if (!req.url?.startsWith('/box-dashboard')) return next()
    const requestPath = decodeURIComponent(req.url.split('?')[0].replace(/^\/box-dashboard\/?/, ''))
    const relativePath = requestPath || 'index.html'
    const filePath = path.resolve(boardRoot, relativePath)
    if (filePath !== boardRoot && !filePath.startsWith(`${boardRoot}${path.sep}`)) return next()
    if (!fs.existsSync(filePath) || !fs.statSync(filePath).isFile()) return next()
    res.statusCode = 200
    res.setHeader('Content-Type', contentTypes[path.extname(filePath).toLowerCase()] || 'application/octet-stream')
    res.end(fs.readFileSync(filePath))
  }

  return {
    name: 'box-warehouse-board',
    configureServer(server) {
      server.middlewares.use(serveBoard)
    },
    configurePreviewServer(server) {
      server.middlewares.use(serveBoard)
    },
  }
}

export default defineConfig({
  plugins: [vue(), boxWarehouseBoard()],
  server: {
    port: 5173,
    proxy: {
      '/api': backendProxy,
      '/v3/api-docs': backendProxy,
      '/swagger-ui': backendProxy,
      '/swagger-ui.html': backendProxy,
    },
  },
  preview: {
    proxy: {
      '/api': backendProxy,
      '/v3/api-docs': backendProxy,
      '/swagger-ui': backendProxy,
      '/swagger-ui.html': backendProxy,
    },
  },
})