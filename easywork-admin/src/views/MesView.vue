<template>
  <div>
    <el-row :gutter="16" style="margin-bottom: 16px">
      <el-col :span="6">
        <el-card shadow="never">
          <el-statistic title="总同步记录" :value="stats.totalLogs" />
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never">
          <el-statistic title="成功" :value="stats.successCount" />
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never">
          <el-statistic title="失败" :value="stats.failedCount" />
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never">
          <el-statistic title="待处理" :value="stats.pendingCount" />
        </el-card>
      </el-col>
    </el-row>

    <el-card shadow="never">
      <template #header>
        <div style="display: flex; justify-content: space-between; align-items: center">
          <span>同步日志</span>
          <el-form inline>
            <el-form-item label="方向">
              <el-select v-model="filters.direction" clearable placeholder="全部" style="width: 120px">
                <el-option label="从MES" value="INBOUND" />
                <el-option label="到MES" value="OUTBOUND" />
              </el-select>
            </el-form-item>
            <el-form-item label="状态">
              <el-select v-model="filters.status" clearable placeholder="全部" style="width: 120px">
                <el-option label="成功" value="SUCCESS" />
                <el-option label="失败" value="FAILED" />
                <el-option label="待处理" value="PENDING" />
              </el-select>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="loadLogs(1)">查询</el-button>
            </el-form-item>
          </el-form>
        </div>
      </template>

      <el-table :data="logs" v-loading="logsLoading" stripe>
        <el-table-column prop="syncType" label="类型" width="160" />
        <el-table-column prop="direction" label="方向" width="100">
          <template #default="{ row }">
            <el-tag size="small">{{ row.direction === 'INBOUND' ? '从MES' : '到MES' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="90">
          <template #default="{ row }">
            <el-tag
              :type="row.status === 'SUCCESS' ? 'success' : row.status === 'FAILED' ? 'danger' : 'warning'"
              size="small"
            >
              {{ { SUCCESS: '成功', FAILED: '失败', PENDING: '待处理', RETRYING: '重试中' }[row.status] ?? row.status }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="businessKey" label="业务键" width="140" />
        <el-table-column prop="errorMessage" label="错误信息" />
        <el-table-column prop="createdAt" label="时间" width="180">
          <template #default="{ row }">{{ formatDate(row.createdAt) }}</template>
        </el-table-column>
      </el-table>

      <el-pagination
        style="margin-top: 16px; justify-content: flex-end; display: flex"
        v-model:current-page="pagination.current"
        :total="pagination.total"
        layout="total, prev, pager, next"
        @current-change="loadLogs"
      />
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { getMesStats, getMesLogs } from '@/api/mes'

const logsLoading = ref(false)
const logs = ref([])
const pagination = reactive({ current: 1, size: 10, total: 0 })
const filters = reactive({ direction: '', status: '' })
const stats = reactive({
  totalLogs: 0,
  successCount: 0,
  failedCount: 0,
  pendingCount: 0,
})

async function loadStats() {
  try {
    const data = await getMesStats()
    if (data) {
      Object.assign(stats, data)
    }
  } catch {
    // handled
  }
}

async function loadLogs(page = pagination.current) {
  logsLoading.value = true
  try {
    const params = {
      page,
      size: pagination.size || 10,
      ...(filters.direction && { direction: filters.direction }),
      ...(filters.status && { status: filters.status }),
    }
    const data = await getMesLogs(params)
    logs.value = data?.records ?? []
    pagination.total = data?.total ?? 0
    pagination.current = data?.current ?? page
  } catch {
    // handled
  } finally {
    logsLoading.value = false
  }
}

function formatDate(val) {
  if (!val) return '-'
  return new Date(val).toLocaleString('zh-CN', { hour12: false })
}

onMounted(() => {
  loadStats()
  loadLogs()
})
</script>

<style scoped>
.hint {
  color: #999;
  font-size: 13px;
}
</style>
