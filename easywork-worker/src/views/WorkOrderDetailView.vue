<template>
  <div class="page">
    <van-notice-bar
      v-if="!isOnline"
      color="#fff"
      background="#ee0a24"
      text="当前离线，操作将排队，恢复联网后自动提交"
    />
    <van-nav-bar
      :title="workorder?.orderNumber ?? '工单详情'"
      left-arrow
      @click-left="$router.back()"
      fixed
    />

    <div class="content" v-if="workorder">
      <!-- 工单基本信息 -->
      <van-cell-group inset style="margin-top: 12px">
        <van-cell title="产品名称" :value="workorder.productName || '-'" />
        <van-cell title="工单类型" :value="workorder.orderType || '-'" />
        <van-cell title="计划数量" :value="workorder.plannedQuantity" />
        <van-cell title="完成数量" :value="workorder.completedQuantity" />
        <van-cell title="剩余数量" :value="workorder.remainingQuantity" />
        <van-cell title="优先级" :value="workorder.priority" />
        <van-cell title="状态">
          <template #value>
            <van-tag :type="getStatusTagType(workorder)">{{ getStatusLabel(workorder) }}</van-tag>
          </template>
        </van-cell>
      </van-cell-group>

      <!-- 工序卡片 -->
      <div class="section-title">工序列表</div>

      <div
        v-for="(op, opIdx) in workorder.operations"
        :key="op.id"
        :ref="el => { if (el) opCardRefs[opIdx] = el }"
        :class="['operation-card', opIdx === activeOpIndex ? 'focused' : '']"
      >
        <div class="op-header">
          <span class="op-name">{{ op.operationName }}</span>
          <van-tag :type="getStatusTagType(null, op.status)" size="small">{{ getStatusLabel(null, op.status) }}</van-tag>
        </div>
        <div class="op-info">
          <span>计划：{{ op.plannedQuantity }}</span>
          <span>完成：{{ op.completedQuantity }}</span>
        </div>

        <!-- 操作按钮 -->
        <div class="op-actions">
          <van-button
            v-if="op.status === 'NOT_STARTED'"
            type="primary"
            size="small"
            :loading="actionLoading[op.id]"
            @click="handleStart(op)"
          >
            {{ getStartLabel(workorder.orderType) }}
          </van-button>

          <!-- 检验工单：STARTED 时显示「提交质检」替代「报工」 -->
          <van-button
            v-if="op.status === 'STARTED' && workorder.orderType === 'INSPECTION'"
            type="success"
            size="small"
            :loading="actionLoading[op.id]"
            @click="openInspectDialog(op)"
          >
            提交质检
          </van-button>

          <!-- 非检验工单：STARTED 时显示报工（文案按类型） -->
          <van-button
            v-if="op.status === 'STARTED' && workorder.orderType !== 'INSPECTION'"
            type="warning"
            size="small"
            :loading="actionLoading[op.id]"
            @click="openReportDialog(op)"
          >
            {{ getReportLabel(workorder.orderType) }}
          </van-button>

          <van-button
            v-if="op.status === 'REPORTED'"
            type="danger"
            size="small"
            plain
            :loading="actionLoading[op.id]"
            @click="openUndoDialog(op)"
          >
            撤销报工
          </van-button>

          <van-button
            v-if="op.status === 'REPORTED' || op.status === 'INSPECT_FAILED'"
            type="warning"
            size="small"
            plain
            :loading="actionLoading[op.id]"
            @click="openReworkDialog(op)"
          >
            返工
          </van-button>
        </div>
      </div>
      <!-- 质检结果卡片 -->
      <div
        v-if="workorder.status === 'INSPECT_PASSED' || workorder.status === 'INSPECT_FAILED'"
        class="section-title"
      >
        质检结果
      </div>
      <van-cell-group
        v-if="(workorder.status === 'INSPECT_PASSED' || workorder.status === 'INSPECT_FAILED') && inspection"
        inset
        style="margin-bottom: 12px"
      >
        <van-cell title="检验结果">
          <template #value>
            <van-tag :type="inspection.inspectionResult === 'PASSED' ? 'success' : 'danger'">
              {{ inspection.inspectionResult === 'PASSED' ? '通过' : '不通过' }}
            </van-tag>
          </template>
        </van-cell>
        <van-cell title="合格数量" :value="inspection.qualifiedQuantity ?? '-'" />
        <van-cell title="不合格数量" :value="inspection.defectQuantity ?? '-'" />
        <van-cell title="不合格原因" :value="inspection.defectReason || '-'" />
        <van-cell title="检验时间" :value="formatDate(inspection.inspectionTime)" />
      </van-cell-group>

      <!-- PRODUCTION 工单报工后：工人端质检入口 -->
      <div
        v-if="workorder.orderType === 'PRODUCTION' && workorder.status === 'REPORTED'"
        class="call-section"
      >
        <van-button round block type="success" icon="passed" @click="openWorkOrderInspectDialog">
          提交质检
        </van-button>
      </div>

      <!-- 呼叫按鈕 -->
      <div class="call-section">
        <van-button round block type="warning" icon="phone-o" @click="goToCall">
          发起呼叫
        </van-button>
      </div>
    </div>

    <van-loading v-else-if="loading" vertical style="padding-top: 40%">加载中...</van-loading>

    <KeyHints :hints="detailHints" />

    <!-- 报工弹窗 -->
    <van-dialog
      v-model:show="reportVisible"
      title="报工"
      show-cancel-button
      :before-close="handleReportConfirm"
    >
      <div style="padding: 16px">
        <van-field
          v-model="reportForm.reportedQuantity"
          type="number"
          label="报工数量"
          placeholder="请输入数量"
          :rules="[{ validator: validateReportQty, message: `数量需大于0且不超过剩余量${reportMaxQty}` }]"
        />
        <van-field
          v-model="reportForm.qualifiedQuantity"
          type="number"
          label="合格数量"
          placeholder="可选"
        />
        <van-field
          v-model="reportForm.defectQuantity"
          type="number"
          label="不合格数量"
          placeholder="可选"
        />
        <van-field
          v-model="reportForm.notes"
          label="备注"
          placeholder="可选"
        />
      </div>
    </van-dialog>

    <!-- 撤销弹窗 -->
    <van-dialog
      v-model:show="undoVisible"
      title="撤销报工"
      show-cancel-button
      :before-close="handleUndoConfirm"
    >
      <div style="padding: 16px">
        <van-field
          v-model="undoForm.undoReason"
          label="撤销原因"
          placeholder="请说明原因"
        />
      </div>
    </van-dialog>

    <!-- 返工弹窗 -->
    <van-dialog
      v-model:show="reworkVisible"
      title="返工"
      show-cancel-button
      :before-close="handleReworkConfirm"
    >
      <div style="padding: 16px">
        <van-field
          v-model="reworkForm.reworkQuantity"
          type="number"
          label="返工数量"
          placeholder="请输入返工数量"
        />
        <van-field
          v-model="reworkForm.reworkReason"
          label="返工原因"
          type="textarea"
          rows="3"
          placeholder="请说明返工原因"
        />
      </div>
    </van-dialog>

    <!-- 质检弹窗（检验工单专用） -->
    <van-dialog
      v-model:show="inspectVisible"
      title="提交质检结果"
      show-cancel-button
      :before-close="handleInspectConfirm"
    >
      <div style="padding: 16px">
        <van-field label="检验结果" required>
          <template #input>
            <van-radio-group v-model="inspectForm.inspectionResult" direction="horizontal">
              <van-radio name="PASSED">合格</van-radio>
              <van-radio name="FAILED">不合格</van-radio>
              <van-radio name="REWORK">返工</van-radio>
              <van-radio name="SCRAP_MATERIAL">料废</van-radio>
              <van-radio name="SCRAP_PROCESS">工废</van-radio>
            </van-radio-group>
          </template>
        </van-field>
        <van-field
          v-model="inspectForm.inspectedQuantity"
          type="number"
          label="检验数量"
          placeholder="可选"
        />
        <van-field
          v-model="inspectForm.qualifiedQuantity"
          type="number"
          label="合格数量"
          placeholder="可选"
        />
        <van-field
          v-model="inspectForm.defectQuantity"
          type="number"
          label="不合格数量"
          placeholder="可选"
        />
        <van-field
          v-model="inspectForm.defectReason"
          label="不合格原因"
          type="textarea"
          rows="2"
          placeholder="可选"
        />
        <van-field
          v-model="inspectForm.notes"
          label="备注"
          placeholder="可选"
        />
      </div>
    </van-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, watch, computed, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { showToast } from 'vant'
import { getWorkOrders, getInspectionDetail, createRework, submitInspection } from '@/api/workorder'
import { startWork, reportWork, undoReport } from '@/api/report'
import { useNetworkStatus } from '@/composables/useNetworkStatus'
import { enqueue, processQueue } from '@/utils/offlineQueue'
import { getStatusLabel, getStatusTagType } from '@/utils/statusLabel'
import { useHardwareInput } from '@/composables/useHardwareInput'
import { scanStart, scanReport } from '@/api/scan'
import KeyHints from '@/components/KeyHints.vue'
import http from '@/api/http'

const route = useRoute()
const router = useRouter()
const loading = ref(false)
const workorder = ref(null)
const inspection = ref(null)
const actionLoading = reactive({})
const activeOpIndex = ref(0)
const opCardRefs = ref([])
const reportVisible = ref(false)
const undoVisible = ref(false)
const reworkVisible = ref(false)
const inspectVisible = ref(false)
const activeOp = ref(null)
const reportForm = reactive({ reportedQuantity: '', qualifiedQuantity: '', defectQuantity: '', notes: '' })
const undoForm = reactive({ undoReason: '' })
const reworkForm = reactive({ reworkQuantity: '', reworkReason: '' })
const inspectForm = reactive({
  inspectionResult: 'PASSED',
  inspectedQuantity: '',
  qualifiedQuantity: '',
  defectQuantity: '',
  defectReason: '',
  notes: '',
})

const { isOnline } = useNetworkStatus()

// Auto-process queue when back online; notify user of any failures
watch(isOnline, async (online) => {
  if (!online) return
  const result = await processQueue(http)
  if (result?.failed?.length > 0) {
    const labels = result.failed.map(f => f.label).join('、')
    showToast({ type: 'fail', message: `以下操作提交失败，请手动重试：${labels}`, duration: 4000 })
  } else if (result?.processed > 0) {
    showToast({ type: 'success', message: `已自动提交 ${result.processed} 条离线操作` })
  }
})

// Button label helpers (Task 2: differentiated by orderType)
function getStartLabel(orderType) {
  if (orderType === 'TRANSPORT') return '开始转运'
  if (orderType === 'ANDON') return '开始处理'
  return '开工'
}

function getReportLabel(orderType) {
  if (orderType === 'TRANSPORT') return '完成转运'
  if (orderType === 'ANDON') return '处理完成'
  return '报工'
}

// Computed shortcuts for KeyHints display
const detailHints = computed(() => {
  if (!workorder.value) return []
  const ops = workorder.value.operations || []
  const hints = []
  if (ops.length > 1) hints.push({ key: '↑↓', label: '切换工序' })
  if (ops.some(op => op.status === 'NOT_STARTED')) hints.push({ key: '1', label: '开工' })
  if (ops.some(op => op.status === 'STARTED' && workorder.value.orderType !== 'INSPECTION')) hints.push({ key: '2', label: '报工' })
  if (ops.some(op => op.status === 'STARTED' && workorder.value.orderType === 'INSPECTION')
    || (workorder.value.orderType === 'PRODUCTION' && workorder.value.status === 'REPORTED')) {
    hints.push({ key: '3', label: '质检' })
  }
  if (ops.some(op => op.status === 'REPORTED')) hints.push({ key: '4', label: '撤销报工' })
  if (ops.some(op => op.status === 'REPORTED' || op.status === 'INSPECT_FAILED')) hints.push({ key: '5', label: '返工' })
  hints.push({ key: 'Scan', label: '扫码' })
  hints.push({ key: 'ESC', label: '返回' })
  return hints
})

useHardwareInput({
  onNavigate(dir) {
    // When a dialog is open, don't navigate the op list
    if (reportVisible.value || undoVisible.value || reworkVisible.value || inspectVisible.value) return
    if (!workorder.value) return
    const ops = workorder.value.operations || []
    if (dir === 'up' || dir === 'left') {
      if (activeOpIndex.value > 0) {
        activeOpIndex.value--
        nextTick(() => opCardRefs.value[activeOpIndex.value]?.scrollIntoView({ behavior: 'smooth', block: 'nearest' }))
      }
    } else if (dir === 'down' || dir === 'right') {
      if (activeOpIndex.value < ops.length - 1) {
        activeOpIndex.value++
        nextTick(() => opCardRefs.value[activeOpIndex.value]?.scrollIntoView({ behavior: 'smooth', block: 'nearest' }))
      }
    }
  },

  onBack() {
    if (reportVisible.value) { reportVisible.value = false; return }
    if (undoVisible.value) { undoVisible.value = false; return }
    if (reworkVisible.value) { reworkVisible.value = false; return }
    if (inspectVisible.value) { inspectVisible.value = false; return }
    router.back()
  },

  onConfirm() {
    if (!workorder.value) return
    // Delegate confirm to open dialog's before-close handler
    if (reportVisible.value) {
      Promise.resolve(handleReportConfirm('confirm')).then(ok => { if (ok !== false) reportVisible.value = false })
      return
    }
    if (undoVisible.value) {
      Promise.resolve(handleUndoConfirm('confirm')).then(ok => { if (ok !== false) undoVisible.value = false })
      return
    }
    if (reworkVisible.value) {
      Promise.resolve(handleReworkConfirm('confirm')).then(ok => { if (ok !== false) reworkVisible.value = false })
      return
    }
    if (inspectVisible.value) {
      Promise.resolve(handleInspectConfirm('confirm')).then(ok => { if (ok !== false) inspectVisible.value = false })
      return
    }

    // No dialog open: primary action (same as old Enter logic)
    const ops = workorder.value.operations || []
    const notStarted = ops.find(op => op.status === 'NOT_STARTED')
    if (notStarted) { handleStart(notStarted); return }
    const started = ops.find(op => op.status === 'STARTED')
    if (started) {
      if (workorder.value.orderType === 'INSPECTION') {
        openInspectDialog(started)
      } else {
        openReportDialog(started)
      }
      return
    }
    if (workorder.value.orderType === 'PRODUCTION' && workorder.value.status === 'REPORTED') {
      openWorkOrderInspectDialog()
    }
  },

  onShortcut(key) {
    // When report dialog is open, digits build the quantity field numerically
    if (reportVisible.value) {
      if (key === 'Backspace') {
        const s = String(reportForm.reportedQuantity || '')
        reportForm.reportedQuantity = s.length > 1 ? s.slice(0, -1) : ''
      } else {
        // Numeric build: avoid leading zeros (treat current as integer)
        const current = parseInt(reportForm.reportedQuantity, 10) || 0
        const next = current * 10 + parseInt(key, 10)
        reportForm.reportedQuantity = String(next)
      }
      return
    }
    if (!workorder.value) return
    const ops = workorder.value.operations || []
    if (key === '1') {
      // Apply to the focused operation if it's NOT_STARTED, else find first NOT_STARTED
      const focusedOp = ops[activeOpIndex.value]
      const op = (focusedOp?.status === 'NOT_STARTED') ? focusedOp : ops.find(o => o.status === 'NOT_STARTED')
      if (op) handleStart(op)
    } else if (key === '2') {
      const focusedOp = ops[activeOpIndex.value]
      const op = (focusedOp?.status === 'STARTED' && workorder.value.orderType !== 'INSPECTION')
        ? focusedOp
        : ops.find(o => o.status === 'STARTED' && workorder.value.orderType !== 'INSPECTION')
      if (op) openReportDialog(op)
    } else if (key === '3') {
      const inspectOp = ops.find(o => o.status === 'STARTED' && workorder.value.orderType === 'INSPECTION')
      if (inspectOp) { openInspectDialog(inspectOp); return }
      if (workorder.value.orderType === 'PRODUCTION' && workorder.value.status === 'REPORTED') {
        openWorkOrderInspectDialog()
      }
    } else if (key === '4') {
      const op = ops.find(o => o.status === 'REPORTED')
      if (op) openUndoDialog(op)
    } else if (key === '5') {
      const op = ops.find(o => o.status === 'REPORTED' || o.status === 'INSPECT_FAILED')
      if (op) openReworkDialog(op)
    }
  },

  async onScan(barcode) {
    try {
      await scanStart(barcode)
      showToast({ type: 'success', message: '扫码开工成功' })
      await loadWorkOrder()
    } catch {
      // Try report
      try {
        await scanReport(barcode)
        showToast({ type: 'success', message: '扫码报工成功' })
        await loadWorkOrder()
      } catch {
        showToast({ type: 'fail', message: '扫码失败，请检查条码' })
      }
    }
  },
})

// Computed remaining quantity for the active operation
const reportMaxQty = computed(() => {
  if (!activeOp.value) return Infinity
  return Number(activeOp.value.plannedQuantity) - Number(activeOp.value.completedQuantity)
})

function validateReportQty(val) {
  const n = Number(val)
  return n > 0 && n <= reportMaxQty.value
}

function formatDate(val) {
  if (!val) return '-'
  return new Date(val).toLocaleString('zh-CN', { hour12: false })
}

async function loadWorkOrder() {
  loading.value = true
  opCardRefs.value = []
  try {
    // No individual endpoint: load list and find by id
    const list = await getWorkOrders()
    const id = Number(route.params.id)
    workorder.value = (Array.isArray(list) ? list : []).find((o) => o.id === id) ?? null
    if (!workorder.value) {
      showToast('工单不存在或无权限访问')
      return
    }
    if (workorder.value.status === 'INSPECT_PASSED' || workorder.value.status === 'INSPECT_FAILED') {
      try {
        inspection.value = await getInspectionDetail(workorder.value.id)
      } catch {
        inspection.value = null
      }
    }
  } catch {
    // handled
  } finally {
    loading.value = false
  }
}


function goToCall() {
  router.push({ name: 'Call', query: { workOrderId: workorder.value?.id } })
}
async function handleStart(op) {
  const chainId = `op-${op.id}`
  if (!isOnline.value) {
    await enqueue({ method: 'post', url: '/device/start', body: { operationId: op.id }, label: '开工', chainId })
    showToast({ type: 'success', message: '已离线排队，联网后自动提交' })
    return
  }
  actionLoading[op.id] = true
  try {
    await startWork({ operationId: op.id })
    showToast({ type: 'success', message: '开工成功' })
    await loadWorkOrder()
  } catch (e) {
    if (!e.response) {
      await enqueue({ method: 'post', url: '/device/start', body: { operationId: op.id }, label: '开工', chainId })
      showToast({ type: 'success', message: '已离线排队，联网后自动提交' })
    }
  } finally {
    actionLoading[op.id] = false
  }
}

function openReportDialog(op) {
  activeOp.value = op
  const remaining = Number(op.plannedQuantity) - Number(op.completedQuantity)
  Object.assign(reportForm, {
    reportedQuantity: remaining > 0 ? String(remaining) : '',
    qualifiedQuantity: '',
    defectQuantity: '',
    notes: '',
  })
  reportVisible.value = true
}

async function handleReportConfirm(action) {
  if (action !== 'confirm') return true
  if (!reportForm.reportedQuantity) {
    showToast('请输入报工数量')
    return false
  }
  const qty = Number(reportForm.reportedQuantity)
  if (qty <= 0) {
    showToast('报工数量必须大于0')
    return false
  }
  if (qty > reportMaxQty.value) {
    showToast(`报工数量不能超过剩余量 ${reportMaxQty.value}`)
    return false
  }
  actionLoading[activeOp.value.id] = true
  const payload = {
    operationId: activeOp.value.id,
    reportedQuantity: qty,
  }
  if (reportForm.qualifiedQuantity) payload.qualifiedQuantity = Number(reportForm.qualifiedQuantity)
  if (reportForm.defectQuantity) payload.defectQuantity = Number(reportForm.defectQuantity)
  if (reportForm.notes) payload.notes = reportForm.notes
  const chainId = `op-${activeOp.value.id}`
  try {
    await reportWork(payload)
    showToast({ type: 'success', message: '报工成功' })
    await loadWorkOrder()
    return true
  } catch (e) {
    if (!e.response) {
      await enqueue({ method: 'post', url: '/device/report', body: payload, label: '报工', chainId })
      showToast({ type: 'success', message: '已离线排队，联网后自动提交' })
      return true
    }
    return false
  } finally {
    actionLoading[activeOp.value.id] = false
  }
}

function openUndoDialog(op) {
  activeOp.value = op
  undoForm.undoReason = ''
  undoVisible.value = true
}

async function handleUndoConfirm(action) {
  if (action !== 'confirm') return true
  if (!undoForm.undoReason) {
    showToast('请填写撤销原因')
    return false
  }
  actionLoading[activeOp.value.id] = true
  try {
    await undoReport({
      operationId: activeOp.value.id,
      undoReason: undoForm.undoReason,
    })
    showToast({ type: 'success', message: '撤销成功' })
    await loadWorkOrder()
    return true
  } catch (e) {
    if (!e.response) {
      await enqueue({ method: 'post', url: '/device/undo-report', body: { operationId: activeOp.value.id, undoReason: undoForm.undoReason }, label: '撤销报工' })
      showToast({ type: 'success', message: '已离线排队，联网后自动提交' })
      return true
    }
    return false
  } finally {
    actionLoading[activeOp.value.id] = false
  }
}

function openReworkDialog(op) {
  activeOp.value = op
  Object.assign(reworkForm, { reworkQuantity: '', reworkReason: '' })
  reworkVisible.value = true
}

async function handleReworkConfirm(action) {
  if (action !== 'confirm') return true
  if (!reworkForm.reworkQuantity) {
    showToast('请输入返工数量')
    return false
  }
  if (!reworkForm.reworkReason) {
    showToast('请填写返工原因')
    return false
  }
  actionLoading[activeOp.value.id] = true
  try {
    await createRework({
      workOrderId: workorder.value.id,
      originalOperationId: activeOp.value.id,
      reworkQuantity: Number(reworkForm.reworkQuantity),
      reworkReason: reworkForm.reworkReason,
    })
    showToast({ type: 'success', message: '返工记录已创建' })
    await loadWorkOrder()
    return true
  } catch (e) {
    if (!e.response) {
      await enqueue({ method: 'post', url: '/device/rework', body: { workOrderId: workorder.value.id, originalOperationId: activeOp.value.id, reworkQuantity: Number(reworkForm.reworkQuantity), reworkReason: reworkForm.reworkReason }, label: '返工' })
      showToast({ type: 'success', message: '已离线排队，联网后自动提交' })
      return true
    }
    return false
  } finally {
    actionLoading[activeOp.value.id] = false
  }
}

function openInspectDialog(op) {
  activeOp.value = op
  Object.assign(inspectForm, {
    inspectionResult: 'PASSED',
    inspectedQuantity: '',
    qualifiedQuantity: '',
    defectQuantity: '',
    defectReason: '',
    notes: '',
  })
  inspectVisible.value = true
}

// Work-order level inspection (PRODUCTION + REPORTED)
function openWorkOrderInspectDialog() {
  activeOp.value = null
  Object.assign(inspectForm, {
    inspectionResult: 'PASSED',
    inspectedQuantity: '',
    qualifiedQuantity: '',
    defectQuantity: '',
    defectReason: '',
    notes: '',
  })
  inspectVisible.value = true
}

const workOrderInspectLoading = ref(false)

async function handleInspectConfirm(action) {
  if (action !== 'confirm') return true
  if (!inspectForm.inspectionResult) {
    showToast('请选择检验结果')
    return false
  }
  const loadingKey = activeOp.value ? activeOp.value.id : '__workorder__'
  if (activeOp.value) {
    actionLoading[loadingKey] = true
  } else {
    workOrderInspectLoading.value = true
  }
  try {
    await submitInspection({
      workOrderId: workorder.value.id,
      ...(activeOp.value && { operationId: activeOp.value.id }),
      inspectionResult: inspectForm.inspectionResult,
      ...(inspectForm.inspectedQuantity && { inspectedQuantity: Number(inspectForm.inspectedQuantity) }),
      ...(inspectForm.qualifiedQuantity && { qualifiedQuantity: Number(inspectForm.qualifiedQuantity) }),
      ...(inspectForm.defectQuantity && { defectQuantity: Number(inspectForm.defectQuantity) }),
      ...(inspectForm.defectReason && { defectReason: inspectForm.defectReason }),
      ...(inspectForm.notes && { notes: inspectForm.notes }),
    })
    showToast({ type: 'success', message: '质检结果已提交' })
    await loadWorkOrder()
    return true
  } catch {
    return false
  } finally {
    if (activeOp.value) {
      actionLoading[loadingKey] = false
    } else {
      workOrderInspectLoading.value = false
    }
  }
}

onMounted(loadWorkOrder)
</script>

<style scoped>
.page {
  min-height: 100vh;
  background: #f7f8fa;
}

.content {
  padding-top: 46px;
  padding-bottom: 24px;
}

.section-title {
  padding: 16px 16px 8px;
  font-size: 15px;
  font-weight: 600;
  color: #333;
}

.operation-card {
  margin: 0 12px 12px;
  padding: 16px;
  background: #fff;
  border-radius: 8px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.08);
  border: 2px solid transparent;
  transition: border-color 0.15s;
}

.operation-card.focused {
  border-color: #1989fa;
  box-shadow: 0 2px 8px rgba(25, 137, 250, 0.2);
}

.op-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 10px;
}

.op-name {
  font-size: 15px;
  font-weight: 600;
  color: #333;
}

.op-info {
  display: flex;
  gap: 20px;
  font-size: 13px;
  color: #666;
  margin-bottom: 12px;
}

.op-actions {
  display: flex;
  gap: 8px;
}
.call-section {
  margin: 16px 12px 24px;
}
</style>
