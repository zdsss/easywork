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
        v-for="op in workorder.operations"
        :key="op.id"
        class="operation-card"
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
            开工
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

          <!-- 非检验工单：STARTED 时显示「报工」 -->
          <van-button
            v-if="op.status === 'STARTED' && workorder.orderType !== 'INSPECTION'"
            type="warning"
            size="small"
            :loading="actionLoading[op.id]"
            @click="openReportDialog(op)"
          >
            报工
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

      <!-- 呼叫按鈕 -->
      <div class="call-section">
        <van-button round block type="warning" icon="phone-o" @click="goToCall">
          发起呼叫
        </van-button>
      </div>
    </div>

    <van-loading v-else-if="loading" vertical style="padding-top: 40%">加载中...</van-loading>

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
import { ref, reactive, onMounted, watch, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { showToast } from 'vant'
import { getWorkOrders, getInspectionDetail, createRework, submitInspection } from '@/api/workorder'
import { startWork, reportWork, undoReport } from '@/api/report'
import { useNetworkStatus } from '@/composables/useNetworkStatus'
import { enqueue, processQueue } from '@/utils/offlineQueue'
import { getStatusLabel, getStatusTagType } from '@/utils/statusLabel'
import http from '@/api/http'

const route = useRoute()
const router = useRouter()
const loading = ref(false)
const workorder = ref(null)
const inspection = ref(null)
const actionLoading = reactive({})
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

// Auto-process queue when back online
watch(isOnline, (online) => {
  if (online) processQueue(http)
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
  if (!isOnline.value) {
    await enqueue({ method: 'post', url: '/device/start', body: { operationId: op.id }, label: '开工' })
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
      await enqueue({ method: 'post', url: '/device/start', body: { operationId: op.id }, label: '开工' })
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
  try {
    await reportWork(payload)
    showToast({ type: 'success', message: '报工成功' })
    await loadWorkOrder()
    return true
  } catch (e) {
    if (!e.response) {
      await enqueue({ method: 'post', url: '/device/report', body: payload, label: '报工' })
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

async function handleInspectConfirm(action) {
  if (action !== 'confirm') return true
  if (!inspectForm.inspectionResult) {
    showToast('请选择检验结果')
    return false
  }
  actionLoading[activeOp.value.id] = true
  try {
    await submitInspection({
      workOrderId: workorder.value.id,
      operationId: activeOp.value.id,
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
    actionLoading[activeOp.value.id] = false
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
