import http from './http'

export function getWorkOrders(params) {
  return http.get('/admin/work-orders', { params })
}

export function getWorkOrder(id) {
  return http.get(`/admin/work-orders/${id}`)
}

export function createWorkOrder(data) {
  return http.post('/admin/work-orders', data)
}

export function assignWorkOrder(data) {
  return http.post('/admin/work-orders/assign', data)
}
