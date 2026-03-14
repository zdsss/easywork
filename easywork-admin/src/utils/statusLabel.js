/**
 * Status label and tag-type mapping for work orders and operations.
 * Mirrors the worker-side statusLabel.js but uses Element Plus tag types.
 */

const WORK_ORDER_LABELS = {
  PRODUCTION: {
    NOT_STARTED:    { label: '未开工',     type: 'info'    },
    STARTED:        { label: '已开工',     type: 'primary' },
    REPORTED:       { label: '已报工',     type: 'warning' },
    INSPECT_PASSED: { label: '检验合格',   type: 'success' },
    INSPECT_FAILED: { label: '检验不合格', type: 'danger'  },
    SCRAPPED:       { label: '已报废',     type: 'danger'  },
    COMPLETED:      { label: '已完成',     type: 'success' },
  },
  INSPECTION: {
    NOT_STARTED: { label: '待检验', type: 'info'    },
    STARTED:     { label: '检验中', type: 'primary' },
    COMPLETED:   { label: '已检验', type: 'success' },
  },
  TRANSPORT: {
    NOT_STARTED: { label: '待转运', type: 'info'    },
    STARTED:     { label: '转运中', type: 'primary' },
    COMPLETED:   { label: '已转运', type: 'success' },
  },
  ANDON: {
    NOT_STARTED: { label: '待处理', type: 'info'    },
    STARTED:     { label: '处理中', type: 'primary' },
    COMPLETED:   { label: '已处理', type: 'success' },
  },
}

const OPERATION_LABELS = {
  NOT_STARTED:    { label: '未开始',   type: 'info'    },
  STARTED:        { label: '进行中',   type: 'primary' },
  REPORTED:       { label: '已报工',   type: 'warning' },
  INSPECT_PASSED: { label: '质检通过', type: 'success' },
  INSPECT_FAILED: { label: '质检失败', type: 'danger'  },
  SCRAPPED:       { label: '已报废',   type: 'danger'  },
  COMPLETED:      { label: '已完成',   type: 'success' },
}

/**
 * Get label for a work order (uses orderType for context) or a plain status string.
 * @param {object|null} workOrder
 * @param {string} [status]
 */
export function getStatusLabel(workOrder, status) {
  if (workOrder) {
    const map = WORK_ORDER_LABELS[workOrder.orderType]
    if (map && map[workOrder.status]) return map[workOrder.status].label
    return OPERATION_LABELS[workOrder.status]?.label ?? workOrder.status
  }
  return OPERATION_LABELS[status]?.label ?? status
}

/**
 * Get Element Plus tag type.
 */
export function getStatusTagType(workOrder, status) {
  if (workOrder) {
    const map = WORK_ORDER_LABELS[workOrder.orderType]
    if (map && map[workOrder.status]) return map[workOrder.status].type
    return OPERATION_LABELS[workOrder.status]?.type ?? ''
  }
  return OPERATION_LABELS[status]?.type ?? ''
}
