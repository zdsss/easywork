<template>
  <div class="page">
    <van-nav-bar title="我的工单" fixed>
      <template #right>
        <van-button v-if="!batchMode" size="small" type="primary" @click="batchMode = true">批量</van-button>
        <van-button v-else size="small" @click="exitBatchMode">取消</van-button>
      </template>
    </van-nav-bar>

    <div class="content">
      <div v-if="batchMode" class="batch-toolbar">
        <van-button size="small" @click="toggleSelectAll">{{ allSelected ? '取消全选' : '全选' }}</van-button>
        <van-button size="small" type="primary" :disabled="selectedCount === 0" @click="batchStart">批量开工</van-button>
        <van-button size="small" type="success" :disabled="selectedCount === 0" @click="batchReport">批量报工</van-button>
      </div>
      <van-pull-refresh v-model="refreshing" @refresh="onRefresh">
        <van-list
          v-model:loading="loading"
          :finished="finished"
          finished-text="没有更多了"
          @load="onLoad"
        >
          <div
            v-for="order in list"
            :key="order.id"
            class="order-card"
            @click="handleCardClick(order)"
          >
            <van-checkbox v-if="batchMode" v-model="order.checked" @click.stop />
            <div class="card-header">
              <span class="order-number">{{ order.orderNumber }}</span>
              <van-tag :type="getStatusTagType(order)">{{ getStatusLabel(order) }}</van-tag>
            </div>
            <div class="card-body">
              <div class="info-row">
                <span class="label">产品：</span>
                <span>{{ order.productName }}</span>
              </div>
              <div class="info-row">
                <span class="label">计划数量：</span>
                <span>{{ order.plannedQuantity }}</span>
                <span class="label" style="margin-left: 16px">完成：</span>
                <span>{{ order.completedQuantity }}</span>
              </div>
              <div class="info-row">
                <span class="label">优先级：</span>
                <van-tag
                  :type="order.priority >= 8 ? 'danger' : order.priority >= 5 ? 'warning' : 'default'"
                  size="small"
                >
                  P{{ order.priority }}
                </van-tag>
                <span class="label" style="margin-left: 16px">计划开始：</span>
                <span>{{ formatDate(order.plannedStartTime) }}</span>
              </div>
            </div>
          </div>

          <van-empty v-if="!loading && list.length === 0" description="暂无工单" />
        </van-list>
      </van-pull-refresh>
    </div>

    <van-tabbar route>
      <van-tabbar-item icon="orders-o" to="/workorders">工单</van-tabbar-item>
      <van-tabbar-item icon="scan" to="/scan">扫码</van-tabbar-item>
      <van-tabbar-item icon="phone-o" to="/call">呼叫</van-tabbar-item>
    </van-tabbar>
  </div>
</template>

<script setup>
import { ref, onMounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import { getWorkOrders } from '@/api/workorder'
import { showToast, showConfirmDialog } from 'vant'
import { getStatusLabel, getStatusTagType } from '@/utils/statusLabel'
import http from '@/api/http'

const router = useRouter()
const loading = ref(false)
const refreshing = ref(false)
const finished = ref(false)
const list = ref([])
const batchMode = ref(false)
let allOrders = []
const pageSize = 10

const selectedCount = computed(() => list.value.filter(o => o.checked).length)
const allSelected = computed(() => list.value.length > 0 && list.value.every(o => o.checked))

function toggleSelectAll() {
  const newValue = !allSelected.value
  list.value.forEach(o => o.checked = newValue)
}

function exitBatchMode() {
  batchMode.value = false
  list.value.forEach(o => o.checked = false)
}

function handleCardClick(order) {
  if (batchMode.value) {
    order.checked = !order.checked
  } else {
    goDetail(order.id)
  }
}

async function batchStart() {
  const selected = list.value.filter(o => o.checked)
  const operationIds = selected.flatMap(o =>
    (o.operations || []).filter(op => op.status === 'NOT_STARTED').map(op => op.id)
  )

  if (operationIds.length === 0) {
    showToast('没有可开工的工序')
    return
  }

  try {
    await showConfirmDialog({ title: '确认批量开工', message: `将开工 ${operationIds.length} 个工序` })
    await http.post('/device/batch/start', { operationIds })
    showToast('批量开工成功')
    exitBatchMode()
    onRefresh()
  } catch {}
}

async function batchReport() {
  const selected = list.value.filter(o => o.checked)
  const operationIds = selected.flatMap(o =>
    (o.operations || []).filter(op => op.status === 'STARTED').map(op => op.id)
  )

  if (operationIds.length === 0) {
    showToast('没有可报工的工序')
    return
  }

  try {
    await showConfirmDialog({ title: '确认批量报工', message: `将报工 ${operationIds.length} 个工序` })
    await http.post('/device/batch/report', { operationIds })
    showToast('批量报工成功')
    exitBatchMode()
    onRefresh()
  } catch {}
}

function formatDate(val) {
  if (!val) return '-'
  const d = new Date(val)
  return `${d.getMonth() + 1}/${d.getDate()} ${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`
}

async function onLoad() {
  if (allOrders.length === 0 && !finished.value) {
    try {
      const data = await getWorkOrders()
      allOrders = (Array.isArray(data) ? data : []).map(o => ({ ...o, checked: false }))
    } catch {}
  }

  const start = list.value.length
  const nextPage = allOrders.slice(start, start + pageSize)
  list.value = [...list.value, ...nextPage]

  if (list.value.length >= allOrders.length) {
    finished.value = true
  }
  loading.value = false
}

async function onRefresh() {
  allOrders = []
  list.value = []
  finished.value = false
  await onLoad()
  refreshing.value = false
}

function goDetail(id) {
  router.push(`/workorders/${id}`)
}
</script>

<style scoped>
.page {
  min-height: 100vh;
  background: #f7f8fa;
}

.content {
  padding-top: 46px;
  padding-bottom: 50px;
}

.order-card {
  margin: 12px;
  padding: 16px;
  background: #fff;
  border-radius: 8px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.08);
  cursor: pointer;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.order-number {
  font-weight: bold;
  font-size: 15px;
  color: #333;
}

.card-body {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.info-row {
  display: flex;
  align-items: center;
  font-size: 13px;
  color: #333;
}

.label {
  color: #999;
}

.batch-toolbar {
  display: flex;
  gap: 8px;
  padding: 12px;
  background: #fff;
  border-bottom: 1px solid #eee;
}

.order-card {
  position: relative;
}

.order-card .van-checkbox {
  position: absolute;
  left: 16px;
  top: 16px;
}

.order-card.batch-mode {
  padding-left: 48px;
}
</style>
