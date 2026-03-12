<template>
  <div class="page">
    <van-nav-bar title="我的工单" fixed>
      <template #right>
        <van-icon name="phone-o" size="20" @click="$router.push('/call')" />
      </template>
    </van-nav-bar>

    <div class="content">
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
            @click="goDetail(order.id)"
          >
            <div class="card-header">
              <span class="order-number">{{ order.orderNumber }}</span>
              <van-tag :type="statusTagType(order.status)">{{ statusLabel(order.status) }}</van-tag>
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
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { getWorkOrders } from '@/api/workorder'

const router = useRouter()
const loading = ref(false)
const refreshing = ref(false)
const finished = ref(false)
const list = ref([])
let current = 1
const pageSize = 10

const statusMap = {
  NOT_STARTED: { label: '未开始', type: 'default' },
  STARTED: { label: '进行中', type: 'primary' },
  REPORTED: { label: '已报工', type: 'warning' },
  INSPECT_PASSED: { label: '质检通过', type: 'success' },
  INSPECT_FAILED: { label: '质检失败', type: 'danger' },
  COMPLETED: { label: '已完成', type: 'success' },
}

function statusLabel(s) {
  return statusMap[s]?.label ?? s
}
function statusTagType(s) {
  return statusMap[s]?.type ?? 'default'
}
function formatDate(val) {
  if (!val) return '-'
  const d = new Date(val)
  return `${d.getMonth() + 1}/${d.getDate()} ${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`
}

async function fetchData(page) {
  try {
    // API returns array directly (no pagination)
    const data = await getWorkOrders()
    const records = Array.isArray(data) ? data : []
    list.value = records
    finished.value = true  // All records returned at once
  } catch {
    // handled
  }
}

async function onLoad() {
  loading.value = true
  await fetchData(current)
  loading.value = false
}

async function onRefresh() {
  current = 1
  finished.value = false
  await fetchData(1)
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
</style>
