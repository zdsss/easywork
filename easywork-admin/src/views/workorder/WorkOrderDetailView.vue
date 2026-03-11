<template>
  <div v-loading="loading">
    <el-button :icon="ArrowLeft" @click="$router.back()" style="margin-bottom: 16px">返回</el-button>

    <el-card shadow="never" v-if="workorder">
      <template #header>
        <div style="display: flex; justify-content: space-between; align-items: center">
          <span>工单详情 — {{ workorder.orderNumber }}</span>
          <el-tag :type="statusTagType(workorder.status)" size="large">
            {{ statusLabel(workorder.status) }}
          </el-tag>
        </div>
      </template>

      <el-descriptions :column="3" border>
        <el-descriptions-item label="产品名称">{{ workorder.productName }}</el-descriptions-item>
        <el-descriptions-item label="产品编码">{{ workorder.productCode }}</el-descriptions-item>
        <el-descriptions-item label="工单类型">{{ workorder.orderType }}</el-descriptions-item>
        <el-descriptions-item label="计划数量">{{ workorder.plannedQuantity }}</el-descriptions-item>
        <el-descriptions-item label="完成数量">{{ workorder.completedQuantity }}</el-descriptions-item>
        <el-descriptions-item label="剩余数量">{{ workorder.remainingQuantity }}</el-descriptions-item>
        <el-descriptions-item label="优先级">{{ workorder.priority }}</el-descriptions-item>
        <el-descriptions-item label="计划开始">{{ formatDate(workorder.plannedStartTime) }}</el-descriptions-item>
        <el-descriptions-item label="计划完成">{{ formatDate(workorder.plannedEndTime) }}</el-descriptions-item>
        <el-descriptions-item label="备注" :span="3">{{ workorder.notes || '-' }}</el-descriptions-item>
      </el-descriptions>

      <div style="margin-top: 24px; display: flex; gap: 12px">
        <el-button type="primary" @click="openAssignDialog">派工</el-button>
      </div>
    </el-card>

    <el-card shadow="never" style="margin-top: 16px" v-if="workorder">
      <template #header>工序列表</template>
      <el-table :data="workorder.operations" stripe>
        <el-table-column prop="operationName" label="工序名称" />
        <el-table-column prop="operationNumber" label="工序编号" width="160" />
        <el-table-column prop="sequenceNumber" label="序号" width="70" />
        <el-table-column prop="plannedQuantity" label="计划数量" width="100" />
        <el-table-column prop="completedQuantity" label="完成数量" width="100" />
        <el-table-column prop="status" label="状态" width="120">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="派工" width="100">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="openAssignForOp(row)">派工</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- Assign Dialog -->
    <el-dialog v-model="assignVisible" title="派工" width="500px">
      <el-form :model="assignForm" label-width="100px">
        <el-form-item label="工序">
          <el-select v-model="assignForm.operationId" placeholder="选择工序（必选）" style="width: 100%">
            <el-option
              v-for="op in workorder?.operations"
              :key="op.id"
              :label="op.operationName"
              :value="op.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="指派类型">
          <el-radio-group v-model="assignForm.assignmentType">
            <el-radio value="USER">按用户</el-radio>
            <el-radio value="TEAM">按班组</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="assignForm.assignmentType === 'USER'" label="指派用户">
          <el-select v-model="assignForm.userIds" multiple placeholder="选择用户" style="width: 100%">
            <el-option
              v-for="u in users"
              :key="u.id"
              :label="`${u.realName} (${u.employeeNumber})`"
              :value="u.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item v-if="assignForm.assignmentType === 'TEAM'" label="指派班组">
          <el-select v-model="assignForm.teamIds" multiple placeholder="选择班组" style="width: 100%">
            <el-option v-for="t in teams" :key="t.id" :label="t.teamName" :value="t.id" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="assignVisible = false">取消</el-button>
        <el-button type="primary" :loading="assigning" @click="handleAssign">确认派工</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { ArrowLeft } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { getWorkOrder, assignWorkOrder } from '@/api/workorder'
import { getUsers } from '@/api/user'
import { getTeams } from '@/api/team'

const route = useRoute()
const loading = ref(false)
const workorder = ref(null)
const assignVisible = ref(false)
const assigning = ref(false)
const users = ref([])
const teams = ref([])

const assignForm = reactive({
  operationId: null,
  assignmentType: 'USER',
  userIds: [],
  teamIds: [],
})

const statusOptions = [
  { value: 'NOT_STARTED', label: '未开始', type: 'info' },
  { value: 'STARTED', label: '进行中', type: 'primary' },
  { value: 'REPORTED', label: '已报工', type: 'warning' },
  { value: 'INSPECT_PASSED', label: '质检通过', type: 'success' },
  { value: 'INSPECT_FAILED', label: '质检失败', type: 'danger' },
  { value: 'COMPLETED', label: '已完成', type: 'success' },
]

function statusLabel(status) {
  return statusOptions.find((s) => s.value === status)?.label ?? status
}
function statusTagType(status) {
  return statusOptions.find((s) => s.value === status)?.type ?? ''
}
function formatDate(val) {
  if (!val) return '-'
  return new Date(val).toLocaleString('zh-CN', { hour12: false })
}

async function loadWorkOrder() {
  loading.value = true
  try {
    workorder.value = await getWorkOrder(route.params.id)
  } catch {
    // handled in interceptor
  } finally {
    loading.value = false
  }
}

async function loadUsersAndTeams() {
  if (!users.value.length) {
    const ud = await getUsers({ size: 100 }).catch(() => null)
    users.value = Array.isArray(ud) ? ud : []
  }
  if (!teams.value.length) {
    const td = await getTeams().catch(() => null)
    teams.value = Array.isArray(td) ? td : []
  }
}

async function openAssignDialog() {
  Object.assign(assignForm, { operationId: null, assignmentType: 'USER', userIds: [], teamIds: [] })
  assignVisible.value = true
  await loadUsersAndTeams()
}

async function openAssignForOp(op) {
  Object.assign(assignForm, { operationId: op.id, assignmentType: 'USER', userIds: [], teamIds: [] })
  assignVisible.value = true
  await loadUsersAndTeams()
}

async function handleAssign() {
  if (!assignForm.operationId) {
    ElMessage.warning('请选择工序')
    return
  }
  assigning.value = true
  try {
    await assignWorkOrder(assignForm)
    ElMessage.success('派工成功')
    assignVisible.value = false
    await loadWorkOrder()
  } catch {
    // handled
  } finally {
    assigning.value = false
  }
}

onMounted(loadWorkOrder)
</script>
