import http from './http'

// 获取 REPORTED 状态工单（待质检）
export function getReportedWorkOrders(params) {
  return http.get('/admin/work-orders', { params: { ...params, status: 'REPORTED' } })
}

export function createInspection(data) {
  return http.post('/admin/inspections', data)
}
