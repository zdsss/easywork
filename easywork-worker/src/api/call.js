import http from './http'

export function callAndon(data) {
  return http.post('/device/call/andon', data)
}

export function callInspection(data) {
  return http.post('/device/call/inspection', data)
}

export function callTransport(data) {
  return http.post('/device/call/transport', data)
}
