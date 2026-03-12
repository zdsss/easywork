import http from './http'

export function getCalls(params) {
  return http.get('/admin/calls', { params })
}

export function handleCall(id) {
  return http.put(`/admin/calls/${id}/handle`)
}

export function completeCall(id, data) {
  return http.put(`/admin/calls/${id}/complete`, data)
}
