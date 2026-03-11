<template>
  <el-card shadow="never">
    <template #header>
      <div style="display: flex; justify-content: space-between; align-items: center">
        <span>班组管理</span>
        <el-button type="primary" :icon="Plus" @click="openCreate">新增班组</el-button>
      </div>
    </template>

    <el-table :data="list" v-loading="loading" stripe>
      <el-table-column prop="teamCode" label="班组编码" width="140" />
      <el-table-column prop="teamName" label="班组名称" />
      <el-table-column prop="description" label="描述" />
      <el-table-column label="成员数" width="90">
        <template #default="{ row }">{{ row.members?.length ?? 0 }}</template>
      </el-table-column>
      <el-table-column label="操作" width="140">
        <template #default="{ row }">
          <el-button type="primary" link @click="openMembers(row)">成员管理</el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- Create Dialog -->
    <el-dialog v-model="createVisible" title="新增班组" width="400px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="班组编码" prop="teamCode">
          <el-input v-model="form.teamCode" />
        </el-form-item>
        <el-form-item label="班组名称" prop="teamName">
          <el-input v-model="form.teamName" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.description" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleCreate">创建</el-button>
      </template>
    </el-dialog>

    <!-- Members Dialog -->
    <el-dialog v-model="membersVisible" :title="`班组成员 — ${activeTeam?.teamName}`" width="600px">
      <div style="display: flex; gap: 16px; margin-bottom: 12px">
        <el-select v-model="addUserId" placeholder="选择用户" clearable style="flex: 1">
          <el-option
            v-for="u in allUsers"
            :key="u.id"
            :label="`${u.realName} (${u.employeeNumber})`"
            :value="u.id"
          />
        </el-select>
        <el-button type="primary" @click="handleAddMember" :disabled="!addUserId" :loading="addingMember">
          添加成员
        </el-button>
      </div>
      <el-table :data="members" stripe>
        <el-table-column prop="employeeNumber" label="员工号" width="140" />
        <el-table-column prop="realName" label="姓名" />
      </el-table>
      <p style="margin-top: 8px; color: #999; font-size: 12px">
        注：移除成员功能暂不支持，请联系管理员处理。
      </p>
    </el-dialog>
  </el-card>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { Plus } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { getTeams, createTeam, addTeamMembers } from '@/api/team'
import { getUsers } from '@/api/user'

const loading = ref(false)
const list = ref([])
const createVisible = ref(false)
const membersVisible = ref(false)
const submitting = ref(false)
const addingMember = ref(false)
const formRef = ref(null)
const activeTeam = ref(null)
const members = ref([])
const allUsers = ref([])
const addUserId = ref(null)

const form = reactive({ teamCode: '', teamName: '', description: '' })
const rules = {
  teamCode: [{ required: true, message: '必填', trigger: 'blur' }],
  teamName: [{ required: true, message: '必填', trigger: 'blur' }],
}

async function loadData() {
  loading.value = true
  try {
    const data = await getTeams()
    list.value = Array.isArray(data) ? data : []
  } catch {
    // handled
  } finally {
    loading.value = false
  }
}

function openCreate() {
  Object.assign(form, { teamCode: '', teamName: '', description: '' })
  createVisible.value = true
}

async function handleCreate() {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  submitting.value = true
  try {
    await createTeam(form)
    ElMessage.success('创建成功')
    createVisible.value = false
    loadData()
  } catch {
    // handled
  } finally {
    submitting.value = false
  }
}

async function openMembers(team) {
  activeTeam.value = team
  members.value = team.members ?? []
  membersVisible.value = true
  if (!allUsers.value.length) {
    const ud = await getUsers({ size: 100 }).catch(() => null)
    allUsers.value = Array.isArray(ud) ? ud : []
  }
}

async function handleAddMember() {
  addingMember.value = true
  try {
    await addTeamMembers(activeTeam.value.id, [addUserId.value])
    ElMessage.success('添加成功')
    addUserId.value = null
    // Refresh team list to get updated members
    await loadData()
    // Update local members from refreshed list
    const updated = list.value.find((t) => t.id === activeTeam.value.id)
    if (updated) {
      members.value = updated.members ?? []
      activeTeam.value = updated
    }
  } catch {
    // handled
  } finally {
    addingMember.value = false
  }
}

onMounted(() => loadData())
</script>
