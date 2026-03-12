import http from './http'

export function scanStart(barcode) {
  return http.post('/device/scan/start', { barcode })
}

export function scanReport(barcode) {
  return http.post('/device/scan/report', { barcode })
}
