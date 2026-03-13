import http from './http'

export function getWorkOrders(params) {
  return http.get('/device/work-orders', { params })
}

export function getWorkOrder(id) {
  return http.get(`/device/work-orders/${id}`)
}

export function getInspectionDetail(workOrderId) {
  return http.get(`/device/inspections/${workOrderId}`)
}

export function createRework(data) {
  return http.post('/device/rework', data)
}

export function getReworkHistory(workOrderId) {
  return http.get(`/device/rework/${workOrderId}`)
}
