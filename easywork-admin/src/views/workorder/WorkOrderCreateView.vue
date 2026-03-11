<template>
  <el-card shadow="never">
    <template #header>
      <div style="display: flex; align-items: center; gap: 12px">
        <el-button :icon="ArrowLeft" @click="$router.back()">返回</el-button>
        <span>创建工单</span>
      </div>
    </template>

    <el-form
      ref="formRef"
      :model="form"
      :rules="rules"
      label-width="120px"
      style="max-width: 800px"
    >
      <el-form-item label="工单号" prop="orderNumber">
        <el-input v-model="form.orderNumber" placeholder="如：WO-2026-001" />
      </el-form-item>
      <el-form-item label="工单类型" prop="orderType">
        <el-select v-model="form.orderType" style="width: 100%">
          <el-option label="生产" value="PRODUCTION" />
          <el-option label="返工" value="REWORK" />
          <el-option label="维修" value="REPAIR" />
        </el-select>
      </el-form-item>
      <el-form-item label="产品名称">
        <el-input v-model="form.productName" placeholder="请输入产品名称" />
      </el-form-item>
      <el-form-item label="产品编码">
        <el-input v-model="form.productCode" placeholder="请输入产品编码" />
      </el-form-item>
      <el-form-item label="计划数量" prop="plannedQuantity">
        <el-input-number v-model="form.plannedQuantity" :min="1" style="width: 100%" />
      </el-form-item>
      <el-form-item label="优先级">
        <el-slider v-model="form.priority" :min="0" :max="10" show-stops />
      </el-form-item>
      <el-form-item label="计划开始时间">
        <el-date-picker
          v-model="form.plannedStartTime"
          type="datetime"
          placeholder="选择时间"
          style="width: 100%"
          value-format="YYYY-MM-DDTHH:mm:ss"
        />
      </el-form-item>
      <el-form-item label="计划完成时间">
        <el-date-picker
          v-model="form.plannedEndTime"
          type="datetime"
          placeholder="选择时间"
          style="width: 100%"
          value-format="YYYY-MM-DDTHH:mm:ss"
        />
      </el-form-item>
      <el-form-item label="备注">
        <el-input v-model="form.notes" type="textarea" :rows="3" placeholder="备注信息" />
      </el-form-item>

      <el-divider>工序列表</el-divider>

      <div v-for="(op, idx) in form.operations" :key="idx" class="operation-row">
        <el-row :gutter="12" align="middle">
          <el-col :span="1">
            <span class="op-index">{{ idx + 1 }}</span>
          </el-col>
          <el-col :span="8">
            <el-input v-model="op.operationName" placeholder="工序名称（必填）" />
          </el-col>
          <el-col :span="7">
            <el-input v-model="op.stationCode" placeholder="工位编码（选填）" />
          </el-col>
          <el-col :span="6">
            <el-input-number
              v-model="op.plannedQuantity"
              :min="1"
              placeholder="计划数量"
              style="width: 100%"
            />
          </el-col>
          <el-col :span="2">
            <el-button
              type="danger"
              :icon="Delete"
              circle
              @click="removeOperation(idx)"
              :disabled="form.operations.length <= 1"
            />
          </el-col>
        </el-row>
      </div>

      <el-button :icon="Plus" @click="addOperation" style="width: 100%; margin-bottom: 24px">
        添加工序
      </el-button>

      <el-form-item>
        <el-button type="primary" :loading="submitting" @click="handleSubmit">创建工单</el-button>
        <el-button @click="$router.back()">取消</el-button>
      </el-form-item>
    </el-form>
  </el-card>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { ArrowLeft, Plus, Delete } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { createWorkOrder } from '@/api/workorder'

const router = useRouter()
const formRef = ref(null)
const submitting = ref(false)

const form = reactive({
  orderNumber: '',
  orderType: 'PRODUCTION',
  productName: '',
  productCode: '',
  plannedQuantity: 1,
  priority: 5,
  plannedStartTime: null,
  plannedEndTime: null,
  notes: '',
  operations: [{ operationName: '', stationCode: '', plannedQuantity: 1, sequenceNumber: 1 }],
})

const rules = {
  orderNumber: [{ required: true, message: '请输入工单号', trigger: 'blur' }],
  orderType: [{ required: true, message: '请选择工单类型', trigger: 'change' }],
  plannedQuantity: [{ required: true, message: '请输入计划数量', trigger: 'change' }],
}

function addOperation() {
  form.operations.push({
    operationName: '',
    stationCode: '',
    plannedQuantity: 1,
    sequenceNumber: form.operations.length + 1,
  })
}

function removeOperation(idx) {
  form.operations.splice(idx, 1)
  // renumber
  form.operations.forEach((op, i) => { op.sequenceNumber = i + 1 })
}

async function handleSubmit() {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  submitting.value = true
  try {
    await createWorkOrder(form)
    ElMessage.success('工单创建成功')
    router.push('/workorders')
  } catch {
    // handled in interceptor
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.operation-row {
  margin-bottom: 12px;
}
.op-index {
  font-weight: bold;
  color: #666;
}
</style>
