import http from './http'

export function getUsers(params) {
  return http.get('/admin/users', { params })
}

export function createUser(data) {
  return http.post('/admin/users', data)
}

export function updateUser(id, data) {
  return http.put(`/admin/users/${id}`, data)
}

export function deleteUser(id) {
  return http.delete(`/admin/users/${id}`)
}
