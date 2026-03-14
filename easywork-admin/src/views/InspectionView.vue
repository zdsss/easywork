<template>
  <el-card shadow="never">
    <template #header>质检记录（只读查看）</template>

    <el-alert
      type="info"
      :closable="false"
      style="margin-bottom: 16px"
      description="质检操作已移至工人端（检验员使用手持设备提交），此页面仅供管理端查看历史记录。"
    />

    <el-table :data="list" v-loading="loading" stripe>
      <el-table-column prop="orderNumber" label="工单号" width="160" />
      <el-table-column prop="productName" label="产品名称" />
      <el-table-column prop="completedQuantity" label="已完成数量" width="110" />
      <el-table-column prop="status" label="状态" width="120">
        <template #default="{ row }">
          <el-tag :type="statusTagType(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
        </template>
      </el-table-column>
    </el-table>

    <div style="margin-top: 12px; display: flex; justify-content: flex-end; gap: 8px">
      <el-button @click="loadData">刷新</el-button>
    </div>
  </el-card>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { getReportedWorkOrders } from '@/api/inspection'

const loading = ref(false)
const list = ref([])

const statusMap = {
  NOT_STARTED: { label: '未开始', type: 'info' },
  STARTED:     { label: '进行中', type: 'primary' },
  REPORTED:    { label: '已报工', type: 'warning' },
  INSPECT_PASSED: { label: '质检通过', type: 'success' },
  INSPECT_FAILED: { label: '质检失败', type: 'danger' },
  SCRAPPED:    { label: '已报废',   type: 'danger' },
  COMPLETED:   { label: '已完成',   type: 'success' },
}

function statusLabel(s) { return statusMap[s]?.label ?? s }
function statusTagType(s) { return statusMap[s]?.type ?? '' }

async function loadData() {
  loading.value = true
  try {
    const data = await getReportedWorkOrders({ page: 1, size: 50 })
    list.value = Array.isArray(data) ? data : []
  } catch {
    // handled
  } finally {
    loading.value = false
  }
}

onMounted(() => loadData())
</script>
