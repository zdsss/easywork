<template>
  <div v-loading="loading">
    <el-button :icon="ArrowLeft" @click="$router.back()" style="margin-bottom: 16px">返回</el-button>

    <el-card shadow="never" v-if="workorder">
      <template #header>
        <div style="display: flex; justify-content: space-between; align-items: center">
          <span>工单详情 — {{ workorder.orderNumber }}</span>
          <el-tag :type="getStatusTagType(workorder)" size="large">
            {{ getStatusLabel(workorder) }}
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
        <el-button @click="router.push(`/workorders/${workorder.id}/edit`)">编辑</el-button>
        <el-button type="primary" @click="openAssignDialog">派工</el-button>
        <el-button
          v-if="workorder.status === 'INSPECT_PASSED'"
          type="success"
          :loading="completing"
          @click="handleComplete"
        >完成工单</el-button>
        <el-button
          v-if="workorder.status === 'INSPECT_FAILED'"
          type="warning"
          :loading="reopening"
          @click="handleReopen"
        >返工</el-button>
      </div>
    </el-card>

    <el-card shadow="never" style="margin-top: 16px" v-if="workorder">
      <template #header>
        <div style="display: flex; justify-content: space-between; align-items: center">
          <span>工序列表</span>
          <el-button type="info" size="small" @click="openGraphDialog">依赖图</el-button>
        </div>
      </template>
      <el-table :data="workorder.operations" stripe>
        <el-table-column prop="operationName" label="工序名称" />
        <el-table-column prop="operationNumber" label="工序编号" width="160" />
        <el-table-column prop="sequenceNumber" label="序号" width="70" />
        <el-table-column prop="plannedQuantity" label="计划数量" width="100" />
        <el-table-column prop="completedQuantity" label="完成数量" width="100" />
        <el-table-column prop="status" label="状态" width="120">
          <template #default="{ row }">
            <el-tag :type="getStatusTagType(null, row.status)" size="small">{{ getStatusLabel(null, row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="派工" width="100">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="openAssignForOp(row)">派工</el-button>
          </template>
        </el-table-column>
        <el-table-column label="依赖" width="100">
          <template #default="{ row }">
            <el-button type="info" link size="small" @click="openDepDialog(row)">配置依赖</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- Dependency Graph Dialog -->
    <el-dialog v-model="graphVisible" title="工序依赖图" width="800px">
      <div v-if="graphLoading" style="height: 500px; display: flex; align-items: center; justify-content: center">
        <el-text>加载中...</el-text>
      </div>
      <div v-else style="height: 500px">
        <VueFlow
          v-if="graphNodes.length > 0"
          :nodes="graphNodes"
          :edges="graphEdges"
          :fit-view-on-init="true"
          style="width: 100%; height: 100%"
        />
        <div v-else style="height: 100%; display: flex; align-items: center; justify-content: center; color: #999">
          暂无工序依赖关系
        </div>
      </div>
    </el-dialog>

    <!-- Dependency Dialog -->
    <el-dialog v-model="depVisible" title="配置工序依赖" width="600px">
      <div v-if="deps.length > 0" style="margin-bottom: 16px">
        <div style="font-weight: 600; margin-bottom: 8px">当前依赖</div>
        <el-table :data="deps" size="small" stripe>
          <el-table-column prop="predecessorOperationId" label="前置工序ID" width="120" />
          <el-table-column prop="dependencyType" label="依赖类型" width="120" />
        </el-table>
      </div>
      <div v-else style="color: #999; margin-bottom: 16px">暂无依赖配置</div>

      <el-divider>添加依赖</el-divider>
      <el-form :model="depForm" label-width="100px">
        <el-form-item label="前置工序">
          <el-select v-model="depForm.predecessorId" placeholder="选择前置工序" style="width: 100%">
            <el-option
              v-for="op in workorder?.operations"
              :key="op.id"
              :label="op.operationName"
              :value="op.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="依赖类型">
          <el-radio-group v-model="depForm.type">
            <el-radio value="SERIAL">串行 — 前置必须完成</el-radio>
            <el-radio value="PARALLEL">并行 — 仅参考</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="depVisible = false">关闭</el-button>
        <el-button type="primary" :loading="addingDep" @click="handleAddDep">添加依赖</el-button>
      </template>
    </el-dialog>

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
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { VueFlow, MarkerType } from '@vue-flow/core'
import '@vue-flow/core/dist/style.css'
import { getWorkOrder, assignWorkOrder, completeWorkOrder, reopenWorkOrder } from '@/api/workorder'
import { getUsers } from '@/api/user'
import { getTeams } from '@/api/team'
import { getDependencies, addDependency } from '@/api/dependency'
import { getStatusLabel, getStatusTagType } from '@/utils/statusLabel'

const route = useRoute()
const router = useRouter()
const loading = ref(false)
const workorder = ref(null)
const assignVisible = ref(false)
const assigning = ref(false)
const completing = ref(false)
const reopening = ref(false)
const users = ref([])
const teams = ref([])

const assignForm = reactive({
  operationId: null,
  assignmentType: 'USER',
  userIds: [],
  teamIds: [],
})

const depVisible = ref(false)
const activeDepOp = ref(null)
const deps = ref([])
const addingDep = ref(false)
const depForm = reactive({ predecessorId: null, type: 'SERIAL' })

const graphVisible = ref(false)
const graphLoading = ref(false)
const graphNodes = ref([])
const graphEdges = ref([])

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

async function handleComplete() {
  completing.value = true
  try {
    await completeWorkOrder(workorder.value.id)
    ElMessage.success('工单已完成')
    await loadWorkOrder()
  } catch {
    // handled in interceptor
  } finally {
    completing.value = false
  }
}

async function handleReopen() {
  reopening.value = true
  try {
    await reopenWorkOrder(workorder.value.id)
    ElMessage.success('工单已返工，等待重新报工')
    await loadWorkOrder()
  } catch {
    // handled in interceptor
  } finally {
    reopening.value = false
  }
}

async function openDepDialog(op) {
  activeDepOp.value = op
  Object.assign(depForm, { predecessorId: null, type: 'SERIAL' })
  deps.value = []
  depVisible.value = true
  try {
    deps.value = (await getDependencies(op.id)) ?? []
  } catch {
    // handled
  }
}

async function handleAddDep() {
  if (!depForm.predecessorId) {
    ElMessage.warning('请选择前置工序')
    return
  }
  addingDep.value = true
  try {
    const params = {
      operationId: activeDepOp.value.id,
      predecessorId: depForm.predecessorId,
      type: depForm.type,
    }
    await addDependency(params)
    ElMessage.success('依赖添加成功')
    deps.value = (await getDependencies(activeDepOp.value.id)) ?? []
    Object.assign(depForm, { predecessorId: null, type: 'SERIAL' })
  } catch {
    // handled
  } finally {
    addingDep.value = false
  }
}

async function openGraphDialog() {
  graphVisible.value = true
  graphLoading.value = true
  graphNodes.value = []
  graphEdges.value = []

  const ops = workorder.value?.operations ?? []
  if (!ops.length) {
    graphLoading.value = false
    return
  }

  try {
    // Fetch dependencies for all operations concurrently
    const depResults = await Promise.all(
      ops.map((op) => getDependencies(op.id).catch(() => []))
    )

    // Build edge list: source = predecessorOperationId, target = operationId
    const edges = []
    ops.forEach((op, i) => {
      const opDeps = depResults[i] ?? []
      opDeps.forEach((dep) => {
        const predId = dep.predecessorOperationId ?? dep.predecessorId
        if (predId) {
          edges.push({
            id: `e${predId}-${op.id}`,
            source: String(predId),
            target: String(op.id),
            label: dep.dependencyType ?? dep.type,
            markerEnd: { type: MarkerType.ArrowClosed },
          })
        }
      })
    })
    graphEdges.value = edges

    // Topological sort (Kahn's algorithm) for meaningful graph layout
    const opIds = ops.map((o) => o.id)
    const inDegree = new Map(opIds.map((id) => [id, 0]))
    const adjacency = new Map(opIds.map((id) => [id, []]))

    for (const edge of edges) {
      const src = Number(edge.source)
      const tgt = Number(edge.target)
      if (inDegree.has(tgt)) inDegree.set(tgt, inDegree.get(tgt) + 1)
      if (adjacency.has(src)) adjacency.get(src).push(tgt)
    }

    // Kahn's BFS: produces layers (nodes at the same topological depth share a column)
    const layers = []
    let queue = opIds.filter((id) => inDegree.get(id) === 0)
    const visited = new Set()

    while (queue.length > 0) {
      layers.push([...queue])
      queue.forEach((id) => visited.add(id))
      const nextQueue = []
      for (const id of queue) {
        for (const neighbor of (adjacency.get(id) ?? [])) {
          if (!visited.has(neighbor)) {
            inDegree.set(neighbor, inDegree.get(neighbor) - 1)
            if (inDegree.get(neighbor) === 0) nextQueue.push(neighbor)
          }
        }
      }
      queue = nextQueue
    }

    // Any nodes not reached (e.g., cycle remnants) go in an extra layer
    const unreached = opIds.filter((id) => !visited.has(id))
    if (unreached.length > 0) layers.push(unreached)

    // Position nodes: each layer is a column, nodes within a layer are rows
    const NODE_W = 200
    const NODE_H = 100
    const opMap = new Map(ops.map((o) => [o.id, o]))
    const nodes = []
    layers.forEach((layer, colIdx) => {
      layer.forEach((id, rowIdx) => {
        const op = opMap.get(id)
        nodes.push({
          id: String(id),
          label: op?.operationName ?? String(id),
          position: { x: colIdx * NODE_W, y: rowIdx * NODE_H },
        })
      })
    })
    graphNodes.value = nodes
  } catch {
    // handled
  } finally {
    graphLoading.value = false
  }
}

onMounted(loadWorkOrder)
</script>
