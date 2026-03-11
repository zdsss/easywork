<template>
  <el-card shadow="never">
    <template #header>
      <div style="display: flex; justify-content: space-between; align-items: center">
        <span>工单列表</span>
        <el-button type="primary" :icon="Plus" @click="$router.push('/workorders/create')">
          创建工单
        </el-button>
      </div>
    </template>

    <el-form inline class="filter-form">
      <el-form-item label="状态">
        <el-select v-model="filters.status" clearable placeholder="全部" style="width: 140px">
          <el-option
            v-for="s in statusOptions"
            :key="s.value"
            :label="s.label"
            :value="s.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="产品名称">
        <el-input v-model="filters.productName" placeholder="搜索产品" clearable />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="loadData(1)">查询</el-button>
      </el-form-item>
    </el-form>

    <el-table :data="list" v-loading="loading" stripe @row-click="(row) => goDetail(row.id)">
      <el-table-column prop="orderNumber" label="工单号" width="160" />
      <el-table-column prop="productName" label="产品名称" />
      <el-table-column prop="plannedQuantity" label="计划数量" width="100" />
      <el-table-column prop="completedQuantity" label="完成数量" width="100" />
      <el-table-column prop="status" label="状态" width="120">
        <template #default="{ row }">
          <el-tag :type="statusTagType(row.status)">{{ statusLabel(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="priority" label="优先级" width="90">
        <template #default="{ row }">
          <el-tag :type="row.priority >= 8 ? 'danger' : row.priority >= 5 ? 'warning' : 'info'" size="small">
            {{ row.priority }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="plannedStartTime" label="计划开始" width="160">
        <template #default="{ row }">{{ formatDate(row.plannedStartTime) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="100">
        <template #default="{ row }">
          <el-button type="primary" link @click.stop="goDetail(row.id)">详情</el-button>
        </template>
      </el-table-column>
    </el-table>

    <div style="margin-top: 12px; display: flex; justify-content: flex-end; gap: 8px">
      <el-button :disabled="pagination.current <= 1" @click="loadData(pagination.current - 1)">上一页</el-button>
      <span style="line-height: 32px; color: #666">第 {{ pagination.current }} 页（每页 {{ pagination.size }} 条）</span>
      <el-button :disabled="list.length < pagination.size" @click="loadData(pagination.current + 1)">下一页</el-button>
    </div>
  </el-card>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { Plus } from '@element-plus/icons-vue'
import { getWorkOrders } from '@/api/workorder'

const router = useRouter()
const loading = ref(false)
const list = ref([])

const filters = reactive({ status: '', productName: '' })
const pagination = reactive({ current: 1, size: 10, total: 0 })

const statusOptions = [
  { value: 'NOT_STARTED', label: '未开始' },
  { value: 'STARTED', label: '进行中' },
  { value: 'REPORTED', label: '已报工' },
  { value: 'INSPECT_PASSED', label: '质检通过' },
  { value: 'INSPECT_FAILED', label: '质检失败' },
  { value: 'COMPLETED', label: '已完成' },
]

function statusLabel(status) {
  return statusOptions.find((s) => s.value === status)?.label ?? status
}

function statusTagType(status) {
  const map = {
    NOT_STARTED: 'info',
    STARTED: 'primary',
    REPORTED: 'warning',
    INSPECT_PASSED: 'success',
    INSPECT_FAILED: 'danger',
    COMPLETED: 'success',
  }
  return map[status] ?? ''
}

function formatDate(val) {
  if (!val) return '-'
  return new Date(val).toLocaleString('zh-CN', { hour12: false })
}

async function loadData(page = pagination.current) {
  loading.value = true
  try {
    const params = {
      page,
      size: pagination.size,
      ...(filters.status && { status: filters.status }),
    }
    const data = await getWorkOrders(params)
    // API returns array directly
    list.value = Array.isArray(data) ? data : []
    // No server-side total, compute from full list
    pagination.current = page
  } catch {
    // handled in interceptor
  } finally {
    loading.value = false
  }
}

function goDetail(id) {
  router.push(`/workorders/${id}`)
}

onMounted(() => loadData())
</script>

<style scoped>
.filter-form {
  margin-bottom: 16px;
}
</style>
