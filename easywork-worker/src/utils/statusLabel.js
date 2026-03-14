/**
 * Status label and tag-type mapping for work orders and operations,
 * keyed by orderType × status.
 *
 * Usage:
 *   import { getStatusLabel, getStatusTagType } from '@/utils/statusLabel'
 *   getStatusLabel(workOrder)       // for work orders (uses orderType)
 *   getStatusLabel(null, status)    // for operations (orderType-independent)
 *   getStatusTagType(workOrder)
 */

const WORK_ORDER_LABELS = {
  PRODUCTION: {
    NOT_STARTED:    { label: '未开工',   type: 'default' },
    STARTED:        { label: '已开工',   type: 'primary' },
    REPORTED:       { label: '已报工',   type: 'warning' },
    INSPECT_PASSED: { label: '检验合格', type: 'success' },
    INSPECT_FAILED: { label: '检验不合格', type: 'danger' },
    SCRAPPED:       { label: '已报废',   type: 'danger'  },
    COMPLETED:      { label: '已完成',   type: 'success' },
  },
  INSPECTION: {
    NOT_STARTED: { label: '待检验', type: 'default' },
    STARTED:     { label: '检验中', type: 'primary' },
    COMPLETED:   { label: '已检验', type: 'success' },
  },
  TRANSPORT: {
    NOT_STARTED: { label: '待转运', type: 'default' },
    STARTED:     { label: '转运中', type: 'primary' },
    COMPLETED:   { label: '已转运', type: 'success' },
  },
  ANDON: {
    NOT_STARTED: { label: '待处理', type: 'default' },
    STARTED:     { label: '处理中', type: 'primary' },
    COMPLETED:   { label: '已处理', type: 'success' },
  },
}

// Fallback for operations (no orderType context) and unknown orderTypes
const OPERATION_LABELS = {
  NOT_STARTED:    { label: '未开始', type: 'default' },
  STARTED:        { label: '进行中', type: 'primary' },
  REPORTED:       { label: '已报工', type: 'warning' },
  INSPECT_PASSED: { label: '质检通过', type: 'success' },
  INSPECT_FAILED: { label: '质检失败', type: 'danger' },
  SCRAPPED:       { label: '已报废',   type: 'danger'  },
  COMPLETED:      { label: '已完成',  type: 'success' },
}

/**
 * Get status label for a work order (uses orderType for context).
 * Also used for operation statuses when orderType is null.
 *
 * @param {object|null} workOrder  - work order object (with .orderType and .status)
 * @param {string} [status]        - status string (used when workOrder is null)
 */
export function getStatusLabel(workOrder, status) {
  if (workOrder) {
    const map = WORK_ORDER_LABELS[workOrder.orderType]
    if (map && map[workOrder.status]) return map[workOrder.status].label
    // Fallback
    return OPERATION_LABELS[workOrder.status]?.label ?? workOrder.status
  }
  return OPERATION_LABELS[status]?.label ?? status
}

/**
 * Get Vant tag type for a work order or operation status.
 */
export function getStatusTagType(workOrder, status) {
  if (workOrder) {
    const map = WORK_ORDER_LABELS[workOrder.orderType]
    if (map && map[workOrder.status]) return map[workOrder.status].type
    return OPERATION_LABELS[workOrder.status]?.type ?? 'default'
  }
  return OPERATION_LABELS[status]?.type ?? 'default'
}
