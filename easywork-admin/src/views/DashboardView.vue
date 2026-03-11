<template>
  <div>
    <el-row :gutter="16" class="stat-row">
      <el-col :span="6" v-for="stat in stats" :key="stat.label">
        <el-card shadow="never">
          <el-statistic :title="stat.label" :value="stat.value" />
        </el-card>
      </el-col>
    </el-row>

    <el-card shadow="never" style="margin-top: 16px">
      <template #header>
        <span>工人产出统计</span>
      </template>
      <el-table :data="workerOutput" v-loading="loading" stripe>
        <el-table-column prop="realName" label="姓名" />
        <el-table-column prop="employeeNumber" label="员工号" />
        <el-table-column prop="reportCount" label="报工次数" />
        <el-table-column prop="totalReported" label="总报工量" />
      </el-table>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { getDashboard } from '@/api/statistics'

const loading = ref(false)
const stats = ref([
  { label: '总工单数', value: 0 },
  { label: '进行中', value: 0 },
  { label: '待质检', value: 0 },
  { label: '已完成', value: 0 },
])
const workerOutput = ref([])

async function loadData() {
  loading.value = true
  try {
    const data = await getDashboard()
    if (data) {
      stats.value = [
        { label: '总工单数', value: data.totalWorkOrders ?? 0 },
        { label: '进行中', value: data.startedCount ?? 0 },
        { label: '已报工', value: data.reportedCount ?? 0 },
        { label: '已完成', value: data.completedCount ?? 0 },
      ]
      workerOutput.value = data.workerStats ?? []
    }
  } catch {
    // error handled in http interceptor
  } finally {
    loading.value = false
  }
}

onMounted(loadData)
</script>

<style scoped>
.stat-row {
  margin-bottom: 0;
}
</style>
