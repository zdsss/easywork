<template>
  <el-card shadow="never">
    <template #header>
      <div style="display: flex; justify-content: space-between; align-items: center">
        <span>用户管理</span>
        <el-button type="primary" :icon="Plus" @click="openDrawer(null)">新增用户</el-button>
      </div>
    </template>

    <el-table :data="list" v-loading="loading" stripe>
      <el-table-column prop="employeeNumber" label="员工号" width="140" />
      <el-table-column prop="realName" label="姓名" />
      <el-table-column prop="role" label="角色" width="100">
        <template #default="{ row }">
          <el-tag :type="row.role === 'ADMIN' ? 'danger' : 'primary'" size="small">
            {{ row.role }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="phone" label="手机号" />
      <el-table-column label="操作" width="120">
        <template #default="{ row }">
          <el-button type="primary" link @click="openDrawer(row)">编辑</el-button>
          <el-popconfirm title="确定删除？" @confirm="handleDelete(row.id)">
            <template #reference>
              <el-button type="danger" link>删除</el-button>
            </template>
          </el-popconfirm>
        </template>
      </el-table-column>
    </el-table>

    <div style="margin-top: 12px; display: flex; justify-content: flex-end; gap: 8px">
      <el-button :disabled="pagination.current <= 1" @click="loadData(pagination.current - 1)">上一页</el-button>
      <span style="line-height: 32px; color: #666">第 {{ pagination.current }} 页</span>
      <el-button :disabled="list.length < pagination.size" @click="loadData(pagination.current + 1)">下一页</el-button>
    </div>

    <el-drawer v-model="drawerVisible" :title="editingUser ? '编辑用户' : '新增用户'" width="400px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="80px">
        <el-form-item label="员工号" prop="employeeNumber">
          <el-input v-model="form.employeeNumber" :disabled="!!editingUser" />
        </el-form-item>
        <el-form-item label="用户名" prop="username">
          <el-input v-model="form.username" :disabled="!!editingUser" placeholder="登录用户名" />
        </el-form-item>
        <el-form-item label="姓名" prop="realName">
          <el-input v-model="form.realName" />
        </el-form-item>
        <el-form-item label="密码" :prop="editingUser ? '' : 'password'">
          <el-input v-model="form.password" type="password" :placeholder="editingUser ? '不修改请留空' : '请输入密码'" />
        </el-form-item>
        <el-form-item label="角色" prop="role">
          <el-select v-model="form.role" style="width: 100%">
            <el-option label="WORKER" value="WORKER" />
            <el-option label="ADMIN" value="ADMIN" />
          </el-select>
        </el-form-item>
        <el-form-item label="手机号">
          <el-input v-model="form.phone" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="drawerVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmit">保存</el-button>
      </template>
    </el-drawer>
  </el-card>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { Plus } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { getUsers, createUser, updateUser, deleteUser } from '@/api/user'

const loading = ref(false)
const list = ref([])
const pagination = reactive({ current: 1, size: 10, total: 0 })
const drawerVisible = ref(false)
const submitting = ref(false)
const editingUser = ref(null)
const formRef = ref(null)

const form = reactive({
  employeeNumber: '',
  username: '',
  realName: '',
  password: '',
  role: 'WORKER',
  phone: '',
})

const rules = {
  employeeNumber: [{ required: true, message: '必填', trigger: 'blur' }],
  username: [{ required: true, message: '必填', trigger: 'blur' }],
  realName: [{ required: true, message: '必填', trigger: 'blur' }],
  password: [{ required: true, message: '必填', trigger: 'blur' }],
  role: [{ required: true, message: '必填', trigger: 'change' }],
}

async function loadData(page = pagination.current) {
  loading.value = true
  try {
    const data = await getUsers({ page, size: pagination.size })
    // API returns array directly
    list.value = Array.isArray(data) ? data : []
    pagination.current = page
  } catch {
    // handled
  } finally {
    loading.value = false
  }
}

function openDrawer(user) {
  editingUser.value = user
  if (user) {
    Object.assign(form, { ...user, password: '' })
  } else {
    Object.assign(form, { employeeNumber: '', username: '', realName: '', password: '', role: 'WORKER', phone: '' })
  }
  drawerVisible.value = true
}

async function handleSubmit() {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  submitting.value = true
  try {
    if (editingUser.value) {
      const payload = { ...form }
      if (!payload.password) delete payload.password
      await updateUser(editingUser.value.id, payload)
      ElMessage.success('更新成功')
    } else {
      await createUser(form)
      ElMessage.success('创建成功')
    }
    drawerVisible.value = false
    loadData(1)
  } catch {
    // handled
  } finally {
    submitting.value = false
  }
}

async function handleDelete(id) {
  try {
    await deleteUser(id)
    ElMessage.success('删除成功')
    loadData(1)
  } catch {
    // handled
  }
}

onMounted(() => loadData())
</script>
