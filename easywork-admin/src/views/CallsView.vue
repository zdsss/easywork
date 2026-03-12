<template>
  <el-card shadow="never">
    <template #header>
      <div style="display: flex; justify-content: space-between; align-items: center">
        <span>呼叫管理</span>
        <div style="display: flex; gap: 12px; align-items: center">
          <el-select v-model="statusFilter" placeholder="全部状态" clearable style="width: 140px" @change="loadCalls">
            <el-option label="全部" value="" />
            <el-option label="待处理" value="NOT_HANDLED" />
            <el-option label="处理中" value="HANDLING" />
            <el-option label="已完成" value="HANDLED" />
          </el-select>
          <el-button @click="loadCalls">刷新</el-button>
        </div>
      </div>
    </template>

    <el-table :data="calls" v-loading="loading" border>
      <el-table-column prop="id" label="ID" width="80" />
      <el-table-column prop="workOrderId" label="工单ID" width="100" />
      <el-table-column label="呼叫类型" width="120">
        <template #default="{ row }">{{ callTypeLabel(row.callType) }}</template>
      </el-table-column>
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="statusType(row.status)">{{ statusLabel(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="description" label="描述" />
      <el-table-column label="呼叫时间" width="160">
        <template #default="{ row }">{{ row.callTime ? row.callTime.replace('T', ' ').substring(0, 16) : '-' }}</template>
      </el-table-column>
      <el-table-column label="处理时间" width="160">
        <template #default="{ row }">{{ row.handleTime ? row.handleTime.replace('T', ' ').substring(0, 16) : '-' }}</template>
      </el-table-column>
      <el-table-column label="操作" width="160" fixed="right">
        <template #default="{ row }">
          <el-button v-if="row.status === 'NOT_HANDLED'" type="primary" size="small" @click="onHandleCall(row.id)">接单</el-button>
          <el-button v-if="row.status === 'HANDLING'" type="success" size="small" @click="openCompleteDialog(row.id)">完成</el-button>
        </template>
      </el-table-column>
    </el-table>

    <div style="margin-top: 16px; display: flex; justify-content: flex-end">
      <el-pagination
        v-model:current-page="currentPage"
        v-model:page-size="pageSize"
        layout="prev, pager, next"
        :total="totalEstimate"
        @current-change="loadCalls"
      />
    </div>

    <el-dialog v-model="completeDialogVisible" title="完成处理" width="400px">
      <el-form label-width="80px">
        <el-form-item label="处理结果">
          <el-input v-model="handleResult" type="textarea" :rows="3" placeholder="请输入处理结果（可选）" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="completeDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="onCompleteCall">确认完成</el-button>
      </template>
    </el-dialog>
  </el-card>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getCalls, handleCall, completeCall } from '@/api/call'

const calls = ref([])
const loading = ref(false)
const submitting = ref(false)
const currentPage = ref(1)
const pageSize = ref(20)
const totalEstimate = ref(0)
const statusFilter = ref('')
const completeDialogVisible = ref(false)
const currentCallId = ref(null)
const handleResult = ref('')

const callTypeLabel = (type) => {
  const map = { ANDON: '安灯呼叫', INSPECTION: '质检呼叫', TRANSPORT: '搬运呼叫' }
  return map[type] || type
}

const statusLabel = (status) => {
  const map = { NOT_HANDLED: '待处理', HANDLING: '处理中', HANDLED: '已完成' }
  return map[status] || status
}

const statusType = (status) => {
  const map = { NOT_HANDLED: 'danger', HANDLING: 'warning', HANDLED: 'success' }
  return map[status] || ''
}

const loadCalls = async () => {
  loading.value = true
  try {
    const params = { page: currentPage.value, size: pageSize.value }
    if (statusFilter.value) params.status = statusFilter.value
    calls.value = await getCalls(params)
    // Backend returns plain array; estimate total to enable pager navigation
    totalEstimate.value = calls.value.length === pageSize.value
      ? currentPage.value * pageSize.value + 1
      : (currentPage.value - 1) * pageSize.value + calls.value.length
  } catch (e) {
    // error shown by http interceptor
  } finally {
    loading.value = false
  }
}

const onHandleCall = async (id) => {
  try {
    await handleCall(id)
    ElMessage.success('已接单')
    loadCalls()
  } catch (e) {}
}

const openCompleteDialog = (id) => {
  currentCallId.value = id
  handleResult.value = ''
  completeDialogVisible.value = true
}

const onCompleteCall = async () => {
  submitting.value = true
  try {
    await completeCall(currentCallId.value, { handleResult: handleResult.value })
    ElMessage.success('已完成处理')
    completeDialogVisible.value = false
    loadCalls()
  } catch (e) {
  } finally {
    submitting.value = false
  }
}

onMounted(loadCalls)
</script>
