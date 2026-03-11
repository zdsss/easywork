import http from './http'

export function startWork(data) {
  return http.post('/device/start', data)
}

export function reportWork(data) {
  return http.post('/device/report', data)
}

export function undoReport(data) {
  return http.post('/device/report/undo', data)
}
