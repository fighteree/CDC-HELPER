<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Refresh, Link, EditPen, Delete, Connection, Search, Download } from '@element-plus/icons-vue'
import {
  fetchDataSourceList,
  createDataSource,
  updateDataSource,
  deleteDataSource,
  testDataSource,
  testDataSourceByConfig,
} from '@/api/dataSource'
import { formatRowTime } from '@/utils/format'
import { exportExcel } from '@/utils/exportExcel'

const loading = ref(false)
const tableData = ref([])
const total = ref(0)
const query = reactive({
  keyword: '',
  dbType: '',
  status: '',
  pageNum: 1,
  pageSize: 10,
})

const dialogVisible = ref(false)
const dialogTitle = ref('新建数据源')
const formRef = ref()
const formModel = reactive({
  id: null,
  name: '',
  dbType: 'mysql',
  host: '',
  port: 3306,
  dbName: '',
  username: '',
  password: '',
  remark: '',
  status: 1,
})

const resetForm = () => {
  formModel.id = null
  formModel.name = ''
  formModel.dbType = 'mysql'
  formModel.host = ''
  formModel.port = 3306
  formModel.dbName = ''
  formModel.username = ''
  formModel.password = ''
  formModel.remark = ''
  formModel.status = 1
}

const rules = {
  name: [{ required: true, message: '请输入数据源名称', trigger: 'blur' }],
  dbType: [{ required: true, message: '请选择数据库类型', trigger: 'change' }],
  host: [{ required: true, message: '请输入主机地址', trigger: 'blur' }],
  port: [{ required: true, message: '请输入端口号', trigger: 'blur' }],
  dbName: [{ required: true, message: '请输入数据库名', trigger: 'blur' }],
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
}

const fetchList = async () => {
  loading.value = true
  try {
    const res = await fetchDataSourceList({
      keyword: query.keyword,
      dbType: query.dbType,
      status: query.status === '' ? undefined : query.status,
      pageNum: query.pageNum,
      pageSize: query.pageSize,
    })
    if (res && Array.isArray(res.list)) {
      tableData.value = res.list
      total.value = res.total != null ? res.total : 0
    } else if (res && Array.isArray(res)) {
      tableData.value = res
      total.value = res.length
    } else {
      tableData.value = []
      total.value = 0
    }
  } finally {
    loading.value = false
  }
}

const handleSearch = () => {
  query.pageNum = 1
  fetchList()
}

const handlePageChange = (page) => {
  query.pageNum = Number(page)
  fetchList()
}

const handleSizeChange = (size) => {
  query.pageSize = Number(size)
  query.pageNum = 1
  fetchList()
}

const dataSourceColumns = [
  { label: '数据源名称', prop: 'name' },
  { label: '类型', prop: 'dbType' },
  { label: '主机', prop: 'host' },
  { label: '端口', prop: 'port' },
  { label: '数据库名', prop: 'dbName' },
  { label: '用户名', prop: 'username' },
  { label: '状态', prop: 'status', formatter: (row) => (row.status === 1 ? '启用' : '停用') },
  { label: '创建时间', prop: 'createdAt', formatter: (row) => formatRowTime(row) },
]

const handleDbTypeChange = (dbType) => {
  const defaultPorts = {
    mysql: 3306,
    mariadb: 3306,
    postgresql: 5432,
    oracle: 1521,
    sqlserver: 1433,
    db2: 50000,
    mongodb: 27017,
  }
  if (defaultPorts[dbType]) {
    formModel.port = defaultPorts[dbType]
  }
}

const getDbTypeTagType = (dbType) => {
  const typeMap = {
    mysql: 'success',
    mariadb: 'success',
    postgresql: 'primary',
    oracle: 'warning',
    sqlserver: 'info',
    db2: '',
    mongodb: 'danger',
  }
  return typeMap[dbType] || 'info'
}

const getDbTypeDisplayName = (dbType) => {
  const nameMap = {
    mysql: 'MySQL',
    mariadb: 'MariaDB',
    postgresql: 'PostgreSQL',
    oracle: 'Oracle',
    sqlserver: 'SQL Server',
    db2: 'Db2',
    mongodb: 'MongoDB',
  }
  return nameMap[dbType] || (dbType ? dbType.toUpperCase() : '')
}

const handleExport = () => {
  exportExcel(tableData.value, dataSourceColumns, '数据源列表')
}

const openCreate = () => {
  dialogTitle.value = '新建数据源'
  resetForm()
  dialogVisible.value = true
}

const openEdit = (row) => {
  dialogTitle.value = '编辑数据源'
  Object.assign(formModel, row)
  dialogVisible.value = true
}

const submitForm = () => {
  formRef.value.validate(async (valid) => {
    if (!valid) return
    const payload = {
      name: formModel.name,
      dbType: formModel.dbType,
      host: formModel.host,
      port: formModel.port,
      dbName: formModel.dbName,
      username: formModel.username,
      password: formModel.password,
      remark: formModel.remark,
      status: formModel.status,
    }
    if (formModel.id) {
      await updateDataSource(formModel.id, payload)
      ElMessage.success('更新成功')
    } else {
      await createDataSource(payload)
      ElMessage.success('创建成功')
    }
    dialogVisible.value = false
    fetchList()
  })
}

// 在弹窗里测试“当前填写的配置”，避免因为还没保存导致用的是旧 IP/端口
const handleDialogTest = async () => {
  const payload = {
    name: formModel.name,
    dbType: formModel.dbType,
    host: formModel.host,
    port: formModel.port,
    dbName: formModel.dbName,
    username: formModel.username,
    password: formModel.password,
    status: formModel.status,
  }
  const res = await testDataSourceByConfig(payload)
  if (res && res.success) {
    ElMessage.success('当前配置可连接')
  } else {
    ElMessage.error('当前配置无法连接，请检查 IP/端口/账号密码')
  }
}

const handleTest = async (row) => {
  const res = await testDataSource(row.id)
  if (res && res.success) {
    ElMessage.success(`连接成功：${row.name}`)
  } else {
    ElMessage.error(`连接失败：${row.name}`)
  }
}

const handleDelete = (row) => {
  ElMessageBox.confirm(`确认删除数据源「${row.name}」吗？`, '提示', {
    type: 'warning',
  })
    .then(async () => {
      await deleteDataSource(row.id)
      ElMessage.success('删除成功')
      fetchList()
    })
    .catch(() => {})
}

onMounted(() => {
  fetchList()
})
</script>

<template>
  <el-card class="cdc-page-card" shadow="never">
    <template #header>
      <div class="cdc-page-card__header">
        <div>
          <div class="cdc-page-card__title">
            <el-icon style="margin-right: 6px; color: #2563eb">
              <Connection />
            </el-icon>
            数据源管理
          </div>
          <div class="cdc-page-card__desc">统一管理 MySQL / Oracle / SQLServer 等源库连接配置</div>
        </div>
        <el-space :size="8">
          <el-button :icon="Refresh" @click="fetchList">刷新</el-button>
          <el-button :icon="Download" @click="handleExport">导出 Excel</el-button>
          <el-button type="primary" :icon="Plus" @click="openCreate">新建数据源</el-button>
        </el-space>
      </div>
    </template>

    <div class="cdc-table-toolbar">
      <el-space :size="10">
        <el-input
          v-model="query.keyword"
          placeholder="搜索名称 / 数据库 / 主机"
          style="width: 260px"
          clearable
          @keyup.enter="handleSearch"
          @clear="handleSearch"
        >
          <template #prefix>
            <el-icon><Search /></el-icon>
          </template>
        </el-input>
        <el-select
          v-model="query.dbType"
          placeholder="数据库类型"
          style="width: 150px"
          clearable
          @change="handleSearch"
        >
          <el-option label="MySQL" value="mysql" />
          <el-option label="MariaDB" value="mariadb" />
          <el-option label="PostgreSQL" value="postgresql" />
          <el-option label="Oracle" value="oracle" />
          <el-option label="SQL Server" value="sqlserver" />
          <el-option label="Db2" value="db2" />
          <el-option label="MongoDB" value="mongodb" />
        </el-select>
        <el-select
          v-model="query.status"
          placeholder="状态"
          style="width: 120px"
          clearable
          @change="handleSearch"
        >
          <el-option label="启用" value="1" />
          <el-option label="停用" value="0" />
        </el-select>
      </el-space>
    </div>

    <el-table
      v-loading="loading"
      :data="tableData"
      border
      stripe
      size="small"
      style="width: 100%"
    >
      <el-table-column prop="name" label="数据源名称" min-width="150" />
      <el-table-column prop="dbType" label="类型" width="120">
        <template #default="{ row }">
          <el-tag
            class="cdc-tag-dbtype"
            :type="getDbTypeTagType(row.dbType)"
          >
            {{ getDbTypeDisplayName(row.dbType) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="连接信息" min-width="220">
        <template #default="{ row }">
          {{ row.host }}:{{ row.port }}
          <span style="color: #9ca3af"> / {{ row.dbName }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="username" label="用户名" width="120" />
      <el-table-column prop="status" label="状态" width="120">
        <template #default="{ row }">
          <span
            class="cdc-status-dot"
            :class="row.status === 1 ? 'cdc-status-dot--success' : 'cdc-status-dot--danger'"
          />
          <span>{{ row.status === 1 ? '启用' : '停用' }}</span>
        </template>
      </el-table-column>
      <el-table-column label="创建时间" width="170">
        <template #default="{ row }">{{ formatRowTime(row) }}</template>
      </el-table-column>
      <el-table-column label="操作" fixed="right" width="220">
        <template #default="{ row }">
          <el-space :size="4">
            <el-button link type="primary" :icon="EditPen" @click="openEdit(row)">编辑</el-button>
            <el-button link type="success" :icon="Link" @click="handleTest(row)">
              测试连接
            </el-button>
            <el-button link type="danger" :icon="Delete" @click="handleDelete(row)">
              删除
            </el-button>
          </el-space>
        </template>
      </el-table-column>
    </el-table>

    <div style="margin-top: 14px; text-align: right">
      <el-pagination
        :current-page="query.pageNum"
        :page-size="query.pageSize"
        :page-sizes="[10, 20, 50]"
        :total="total"
        layout="total, sizes, prev, pager, next, jumper"
        small
        background
        @update:current-page="handlePageChange"
        @update:page-size="handleSizeChange"
      />
    </div>

    <el-dialog
      v-model="dialogVisible"
      :title="dialogTitle"
      width="520px"
      destroy-on-close
      align-center
    >
      <el-form
        ref="formRef"
        :model="formModel"
        :rules="rules"
        label-width="90px"
        label-position="right"
        status-icon
      >
        <el-form-item label="数据源名称" prop="name">
          <el-input v-model="formModel.name" placeholder="例如：生产订单库 / 报表库" />
        </el-form-item>
        <el-form-item label="数据库类型" prop="dbType">
          <el-select v-model="formModel.dbType" placeholder="请选择" @change="handleDbTypeChange">
            <el-option label="MySQL" value="mysql" />
            <el-option label="MariaDB" value="mariadb" />
            <el-option label="PostgreSQL" value="postgresql" />
            <el-option label="Oracle" value="oracle" />
            <el-option label="SQL Server" value="sqlserver" />
            <el-option label="Db2" value="db2" />
            <el-option label="MongoDB" value="mongodb" />
          </el-select>
        </el-form-item>
        <el-form-item label="主机地址" prop="host">
          <el-input v-model="formModel.host" placeholder="例如：10.0.0.10 或 db.company.com" />
        </el-form-item>
        <el-form-item label="端口" prop="port">
          <el-input-number v-model="formModel.port" :min="1" :max="65535" style="width: 100%" />
        </el-form-item>
        <el-form-item label="数据库名" prop="dbName">
          <el-input v-model="formModel.dbName" placeholder="schema / service name" />
        </el-form-item>
        <el-form-item label="用户名" prop="username">
          <el-input v-model="formModel.username" />
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input v-model="formModel.password" type="password" show-password />
        </el-form-item>
        <el-form-item label="备注">
          <el-input
            v-model="formModel.remark"
            type="textarea"
            :rows="2"
            placeholder="用途说明，方便团队理解这个数据源"
          />
        </el-form-item>
        <el-form-item label="状态">
          <el-switch
            v-model="formModel.status"
            :active-value="1"
            :inactive-value="0"
            active-text="启用"
            inactive-text="停用"
          />
        </el-form-item>
      </el-form>

      <template #footer>
        <div class="cdc-dialog__footer">
          <el-button @click="dialogVisible = false">取 消</el-button>
          <el-button @click="handleDialogTest">测试当前配置</el-button>
          <el-button type="primary" @click="submitForm">保 存</el-button>
        </div>
      </template>
    </el-dialog>
  </el-card>
</template>

<style scoped>
</style>

