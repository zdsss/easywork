<template>
  <div class="login-page">
    <div class="login-header">
      <h1>XiaoBai 工单系统</h1>
      <p>工人端</p>
    </div>

    <van-form @submit="handleLogin" class="login-form">
      <van-cell-group inset>
        <van-field
          v-model="form.employeeNumber"
          name="employeeNumber"
          label="员工号"
          placeholder="请输入员工号"
          :rules="[{ required: true, message: '请填写员工号' }]"
        />
        <!-- 密码使用 T9 九宫格键盘输入 -->
        <van-field
          :model-value="form.password"
          type="password"
          name="password"
          label="密码"
          placeholder="请使用下方键盘输入"
          readonly
          :rules="[{ required: true, message: '请填写密码' }]"
        />
        <van-field
          v-model="form.deviceCode"
          name="deviceCode"
          label="设备编号"
          placeholder="可选"
        />
      </van-cell-group>

      <!-- T9 九宫格密码键盘 -->
      <div style="margin: 0 16px 8px">
        <T9Input v-model="form.password" />
      </div>

      <div style="margin: 12px 16px">
        <van-button
          round
          block
          type="primary"
          native-type="submit"
          :loading="loading"
          loading-text="登录中..."
        >
          登录
        </van-button>
      </div>
    </van-form>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { showToast } from 'vant'
import { useAuthStore } from '@/stores/auth'
import T9Input from '@/components/T9Input.vue'

const router = useRouter()
const authStore = useAuthStore()
const loading = ref(false)

const form = reactive({
  employeeNumber: '',
  password: '',
  deviceCode: '',
})

async function handleLogin() {
  loading.value = true
  try {
    const payload = { ...form }
    if (!payload.deviceCode) delete payload.deviceCode
    await authStore.login(payload)
    showToast({ type: 'success', message: '登录成功' })
    router.push('/workorders')
  } catch {
    // handled in http interceptor
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-page {
  min-height: 100vh;
  background: linear-gradient(135deg, #1989fa 0%, #0066cc 100%);
  display: flex;
  flex-direction: column;
}

.login-header {
  padding: 60px 24px 40px;
  color: #fff;
  text-align: center;
}

.login-header h1 {
  font-size: 28px;
  font-weight: bold;
  margin-bottom: 8px;
}

.login-header p {
  font-size: 16px;
  opacity: 0.85;
}

.login-form {
  background: #fff;
  border-radius: 16px 16px 0 0;
  flex: 1;
  padding-top: 32px;
}
</style>
