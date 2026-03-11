import http from './http'

export function getDashboard() {
  return http.get('/admin/statistics/dashboard')
}

export function getWorkerOutput(params) {
  return http.get('/admin/statistics/worker-output', { params })
}
