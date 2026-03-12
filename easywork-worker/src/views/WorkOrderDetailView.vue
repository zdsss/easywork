<template>
  <div class="page">
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
            <van-tag :type="statusTagType(workorder.status)">{{ statusLabel(workorder.status) }}</van-tag>
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
          <van-tag :type="statusTagType(op.status)" size="small">{{ statusLabel(op.status) }}</van-tag>
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

          <van-button
            v-if="op.status === 'STARTED'"
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
        </div>
      </div>
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
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { showToast } from 'vant'
import { getWorkOrders } from '@/api/workorder'
import { startWork, reportWork, undoReport } from '@/api/report'

const route = useRoute()
const router = useRouter()
const loading = ref(false)
const workorder = ref(null)
const actionLoading = reactive({})
const reportVisible = ref(false)
const undoVisible = ref(false)
const activeOp = ref(null)
const reportForm = reactive({ reportedQuantity: '', qualifiedQuantity: '', defectQuantity: '', notes: '' })
const undoForm = reactive({ undoReason: '' })

const statusMap = {
  NOT_STARTED: { label: '未开始', type: 'default' },
  STARTED: { label: '进行中', type: 'primary' },
  REPORTED: { label: '已报工', type: 'warning' },
  INSPECT_PASSED: { label: '质检通过', type: 'success' },
  INSPECT_FAILED: { label: '质检失败', type: 'danger' },
  COMPLETED: { label: '已完成', type: 'success' },
}
function statusLabel(s) { return statusMap[s]?.label ?? s }
function statusTagType(s) { return statusMap[s]?.type ?? 'default' }

async function loadWorkOrder() {
  loading.value = true
  try {
    // No individual endpoint: load list and find by id
    const list = await getWorkOrders()
    const id = Number(route.params.id)
    workorder.value = (Array.isArray(list) ? list : []).find((o) => o.id === id) ?? null
    if (!workorder.value) {
      showToast('工单不存在或无权限访问')
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
  actionLoading[op.id] = true
  try {
    await startWork({ operationId: op.id })
    showToast({ type: 'success', message: '开工成功' })
    await loadWorkOrder()
  } catch {
    // handled
  } finally {
    actionLoading[op.id] = false
  }
}

function openReportDialog(op) {
  activeOp.value = op
  Object.assign(reportForm, { reportedQuantity: '', qualifiedQuantity: '', defectQuantity: '', notes: '' })
  reportVisible.value = true
}

async function handleReportConfirm(action) {
  if (action !== 'confirm') return true
  if (!reportForm.reportedQuantity) {
    showToast('请输入报工数量')
    return false
  }
  actionLoading[activeOp.value.id] = true
  try {
    const payload = {
      operationId: activeOp.value.id,
      reportedQuantity: Number(reportForm.reportedQuantity),
    }
    if (reportForm.qualifiedQuantity) payload.qualifiedQuantity = Number(reportForm.qualifiedQuantity)
    if (reportForm.defectQuantity) payload.defectQuantity = Number(reportForm.defectQuantity)
    if (reportForm.notes) payload.notes = reportForm.notes

    await reportWork(payload)
    showToast({ type: 'success', message: '报工成功' })
    await loadWorkOrder()
    return true
  } catch {
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
