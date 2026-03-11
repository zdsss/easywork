import http from './http'

export function getMesStats() {
  return http.get('/admin/mes-integration/stats')
}

export function getMesLogs(params) {
  return http.get('/admin/mes-integration/logs', { params })
}
