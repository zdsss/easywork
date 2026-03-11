<template>
  <el-card shadow="never">
    <template #header>质检管理（待质检工单）</template>

    <el-table :data="list" v-loading="loading" stripe>
      <el-table-column prop="orderNumber" label="工单号" width="160" />
      <el-table-column prop="productName" label="产品名称" />
      <el-table-column prop="completedQuantity" label="已完成数量" width="110" />
      <el-table-column prop="status" label="状态" width="100">
        <template #default="{ row }">
          <el-tag type="warning" size="small">已报工</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="200">
        <template #default="{ row }">
          <el-button type="success" link @click="openInspect(row, 'PASSED')">通过</el-button>
          <el-button type="danger" link @click="openInspect(row, 'FAILED')">不通过</el-button>
        </template>
      </el-table-column>
    </el-table>

    <div style="margin-top: 12px; display: flex; justify-content: flex-end; gap: 8px">
      <el-button @click="loadData">刷新</el-button>
    </div>

    <el-dialog
      v-model="dialogVisible"
      :title="inspectResult === 'PASSED' ? '质检通过' : '质检不通过'"
      width="480px"
    >
      <el-form :model="inspectForm" label-width="100px">
        <el-form-item label="关联工序">
          <el-select v-model="inspectForm.operationId" placeholder="选择工序（可选）" clearable style="width: 100%">
            <el-option
              v-for="op in activeRow?.operations"
              :key="op.id"
              :label="op.operationName"
              :value="op.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="检验数量">
          <el-input-number v-model="inspectForm.inspectedQuantity" :min="0" style="width: 100%" />
        </el-form-item>
        <el-form-item label="合格数量">
          <el-input-number v-model="inspectForm.qualifiedQuantity" :min="0" style="width: 100%" />
        </el-form-item>
        <el-form-item label="不合格数量">
          <el-input-number v-model="inspectForm.defectQuantity" :min="0" style="width: 100%" />
        </el-form-item>
        <el-form-item label="不合格原因">
          <el-input
            v-model="inspectForm.defectReason"
            type="textarea"
            :rows="2"
            placeholder="可选"
          />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="inspectForm.notes" type="textarea" :rows="2" placeholder="可选" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button
          :type="inspectResult === 'PASSED' ? 'success' : 'danger'"
          :loading="submitting"
          @click="handleInspect"
        >
          确认
        </el-button>
      </template>
    </el-dialog>
  </el-card>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getReportedWorkOrders, createInspection } from '@/api/inspection'

const loading = ref(false)
const list = ref([])
const dialogVisible = ref(false)
const submitting = ref(false)
const activeRow = ref(null)
const inspectResult = ref('PASSED')
const inspectForm = reactive({
  operationId: null,
  inspectedQuantity: 0,
  qualifiedQuantity: 0,
  defectQuantity: 0,
  defectReason: '',
  notes: '',
})

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

function openInspect(row, result) {
  activeRow.value = row
  inspectResult.value = result
  Object.assign(inspectForm, {
    operationId: null,
    inspectedQuantity: Number(row.completedQuantity) || 0,
    qualifiedQuantity: result === 'PASSED' ? Number(row.completedQuantity) || 0 : 0,
    defectQuantity: result === 'PASSED' ? 0 : Number(row.completedQuantity) || 0,
    defectReason: '',
    notes: '',
  })
  dialogVisible.value = true
}

async function handleInspect() {
  submitting.value = true
  try {
    await createInspection({
      workOrderId: activeRow.value.id,
      ...(inspectForm.operationId && { operationId: inspectForm.operationId }),
      inspectionResult: inspectResult.value,
      inspectedQuantity: inspectForm.inspectedQuantity,
      qualifiedQuantity: inspectForm.qualifiedQuantity,
      defectQuantity: inspectForm.defectQuantity,
      ...(inspectForm.defectReason && { defectReason: inspectForm.defectReason }),
      ...(inspectForm.notes && { notes: inspectForm.notes }),
    })
    ElMessage.success('质检记录已提交')
    dialogVisible.value = false
    loadData()
  } catch {
    // handled
  } finally {
    submitting.value = false
  }
}

onMounted(() => loadData())
</script>
