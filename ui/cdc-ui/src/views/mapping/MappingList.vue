<script setup>
import { computed, onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import { ElMessageBox, ElMessage } from 'element-plus'
import { Collection, Plus, Refresh, EditPen, Delete, QuestionFilled, Download } from '@element-plus/icons-vue'
import { fetchMappingList, fetchMappingDetail, createMapping, updateMapping, deleteMapping } from '@/api/mapping'
import { fetchDataSourceList, fetchTables, fetchColumns } from '@/api/dataSource'
import { exportExcel } from '@/utils/exportExcel'

const loading = ref(false)
const list = ref([])
const dataSources = ref([])
const dialogVisible = ref(false)
const dialogTitle = ref('新建映射配置')

// 编辑弹窗初始化标记：
// - openEdit 会批量回填 form，并主动触发多个 watch
// - 为避免 watch 抢先清空字段/表选择，这里用 initializingForm 在初始化阶段短路 watch
const initializingForm = ref(false)
const sourceTables = ref([])
const targetTables = ref([])
const sourceColumns = ref([])
const targetColumns = ref([])

const query = reactive({
  keyword: '',
  targetType: '',
  enabled: '',
  pageNum: 1,
  pageSize: 10,
})

const form = reactive({
  id: null,
  name: '',
  sourceDataSourceId: null,
  sourceSchema: '',
  sourceTable: '',
  sourceKafkaServers: '',
  targetType: 'DB',
  targetDataSourceId: null,
  targetSchema: '',
  targetTable: '',
  enabled: 1,
  fields: [],
  // HTTP 目标配置
  httpUrl: '',
  httpMethod: 'POST',
  httpHeaders: [
    { key: '', value: '' },
  ],
  httpTimeoutMs: 5000,
  httpRetryTimes: 3,
  // MQ 目标配置
  mqType: 'KAFKA',
  mqServerAddr: '',
  mqTopic: '',
  mqRoutingKey: '',
})

const resetForm = () => {
  // 注意：这里的结构需要与 saveMapping 里 payload 组装保持一致
  form.id = null
  form.name = ''
  form.sourceDataSourceId = null
  form.sourceSchema = ''
  form.sourceTable = ''
  form.sourceKafkaServers = ''
  form.targetType = 'DB'
  form.targetDataSourceId = null
  form.targetSchema = ''
  form.targetTable = ''
  form.enabled = 1
  form.fields = []
  form.httpUrl = ''
  form.httpMethod = 'POST'
  form.httpHeaders = [{ key: '', value: '' }]
  form.httpTimeoutMs = 5000
  form.httpRetryTimes = 3
  form.mqType = 'KAFKA'
  form.mqServerAddr = ''
  form.mqTopic = ''
  form.mqRoutingKey = ''
}

const addField = () => {
  form.fields.push({
    id: null,
    sourceCol: '',
    targetCol: '',
    isPk: 0,
    expr: '',
  })
}

const removeField = (index) => {
  form.fields.splice(index, 1)
}

const loadDataSources = async () => {
  // 数据源用于下拉选择（源/目标）
  const res = await fetchDataSourceList()
  if (res && Array.isArray(res.list)) {
    dataSources.value = res.list
  } else if (res && Array.isArray(res)) {
    dataSources.value = res
  } else {
    dataSources.value = []
  }
}

const loadSourceTables = async () => {
  if (!form.sourceDataSourceId) return
  // 由后端通过 JDBC MetaData 获取表列表；MySQL 会限制只返回当前库的表
  const res = await fetchTables(form.sourceDataSourceId, {
    schema: form.sourceSchema || '',
  })
  sourceTables.value = res || []
}

const loadTargetTables = async () => {
  if (!form.targetDataSourceId) return
  const res = await fetchTables(form.targetDataSourceId, {
    schema: form.targetSchema || '',
  })
  targetTables.value = res || []
}

const loadSourceColumns = async () => {
  if (!form.sourceDataSourceId || !form.sourceTable) return
  // 字段按 ordinalPosition 排序，便于默认“按顺序映射”
  const res = await fetchColumns(form.sourceDataSourceId, {
    schema: form.sourceSchema || '',
    table: form.sourceTable,
  })
  sourceColumns.value = (res || []).sort((a, b) => (a.ordinalPosition || 0) - (b.ordinalPosition || 0))
}

const loadTargetColumns = async () => {
  if (!form.targetDataSourceId || !form.targetTable) return
  const res = await fetchColumns(form.targetDataSourceId, {
    schema: form.targetSchema || '',
    table: form.targetTable,
  })
  targetColumns.value = (res || []).sort((a, b) => (a.ordinalPosition || 0) - (b.ordinalPosition || 0))
}

const generateDefaultMapping = async () => {
  // 生成“默认字段映射”：按源字段顺序与目标字段顺序一一对应（取最短长度）
  if (!sourceColumns.value.length) {
    await loadSourceColumns()
  }
  if (!targetColumns.value.length) {
    await loadTargetColumns()
  }
  if (!sourceColumns.value.length || !targetColumns.value.length) {
    ElMessage.warning('请先确保源表和目标表字段信息已加载')
    return
  }
  const len = Math.min(sourceColumns.value.length, targetColumns.value.length)
  form.fields = []
  for (let i = 0; i < len; i += 1) {
    form.fields.push({
      id: null,
      sourceCol: sourceColumns.value[i].name,
      targetCol: targetColumns.value[i].name,
      isPk: 0,
      expr: '',
    })
  }
}

const moveFieldUp = (index) => {
  if (index <= 0) return
  const tmp = form.fields[index - 1]
  form.fields[index - 1] = form.fields[index]
  form.fields[index] = tmp
}

const moveFieldDown = (index) => {
  if (index >= form.fields.length - 1) return
  const tmp = form.fields[index + 1]
  form.fields[index + 1] = form.fields[index]
  form.fields[index] = tmp
}

const addHttpHeader = () => {
  form.httpHeaders.push({ key: '', value: '' })
}

const removeHttpHeader = (index) => {
  form.httpHeaders.splice(index, 1)
}

const sourceColumnMap = computed(() => {
  const map = {}
  sourceColumns.value.forEach((c) => {
    map[c.name] = c
  })
  return map
})

const targetColumnMap = computed(() => {
  const map = {}
  targetColumns.value.forEach((c) => {
    map[c.name] = c
  })
  return map
})

const totalCount = ref(0)
const tableMaxHeight = ref(600)

const loadList = async () => {
  loading.value = true
  try {
    const res = await fetchMappingList({
      keyword: query.keyword,
      targetType: query.targetType,
      enabled: query.enabled === '' ? undefined : query.enabled,
      pageNum: query.pageNum,
      pageSize: query.pageSize,
    })
    if (res && Array.isArray(res.list)) {
      list.value = res.list
      totalCount.value = res.total != null ? res.total : 0
    } else if (res && Array.isArray(res)) {
      list.value = res
      totalCount.value = res.length
    } else {
      list.value = []
      totalCount.value = 0
    }
  } finally {
    loading.value = false
  }
}

const mappingColumns = [
  { label: '映射名称', prop: 'name' },
  { label: '源表', prop: 'sourceTable', formatter: (row) => (row.sourceSchema ? row.sourceSchema + '.' : '') + (row.sourceTable || '') },
  { label: '类型', prop: 'targetType' },
  { label: '目标', prop: 'targetTable', formatter: (row) => row.targetType === 'DB' ? (row.targetSchema ? row.targetSchema + '.' : '') + (row.targetTable || '') : row.targetType },
  { label: '状态', prop: 'enabled', formatter: (row) => (row.enabled === 1 ? '已启用' : '已停用') },
]

const handleSearch = () => {
  query.pageNum = 1
  loadList()
}

const handlePageChange = (page) => {
  query.pageNum = Number(page)
  loadList()
}

const handleSizeChange = (size) => {
  query.pageSize = Number(size)
  query.pageNum = 1
  loadList()
}

const handleExport = () => {
  exportExcel(list.value, mappingColumns, '映射配置')
}

const updateTableHeight = () => {
  const h = window.innerHeight || document.documentElement.clientHeight || 800
  tableMaxHeight.value = Math.max(300, h - 260)
}

const openCreate = () => {
  dialogTitle.value = '新建映射配置'
  resetForm()
  dialogVisible.value = true
}

const openEdit = async (row) => {
  // 编辑：先拉取详情，再回填表单；回填过程中要避免 watch 清空数据
  initializingForm.value = true
  const detail = await fetchMappingDetail(row.id)
  if (!detail || !detail.mapping) return
  dialogTitle.value = '编辑映射配置'
  const { mapping, fields } = detail
  form.id = mapping.id
  form.name = mapping.name
  form.sourceDataSourceId = mapping.sourceDataSourceId
  form.sourceSchema = mapping.sourceSchema
  form.sourceTable = mapping.sourceTable
  form.sourceKafkaServers = mapping.sourceKafkaServers || ''
  form.targetType = mapping.targetType
  form.targetDataSourceId = mapping.targetDataSourceId
  form.targetSchema = mapping.targetSchema
  form.targetTable = mapping.targetTable
  form.enabled = mapping.enabled
  form.fields = (fields || []).map((f) => ({
    id: f.id,
    sourceCol: f.sourceCol,
    targetCol: f.targetCol,
    isPk: f.isPk,
    expr: f.expr,
  }))
  if (detail.httpTarget) {
    form.httpUrl = detail.httpTarget.url
    form.httpMethod = detail.httpTarget.method || 'POST'
    try {
      const parsed = detail.httpTarget.headersJson ? JSON.parse(detail.httpTarget.headersJson) : {}
      const headers = Object.entries(parsed).map(([key, value]) => ({ key, value }))
      form.httpHeaders = headers.length ? headers : [{ key: '', value: '' }]
    } catch {
      form.httpHeaders = [{ key: '', value: '' }]
    }
    form.httpTimeoutMs = detail.httpTarget.timeoutMs || 5000
    form.httpRetryTimes = detail.httpTarget.retryTimes || 3
  } else {
    form.httpUrl = ''
    form.httpMethod = 'POST'
    form.httpHeaders = [{ key: '', value: '' }]
    form.httpTimeoutMs = 5000
    form.httpRetryTimes = 3
  }
  if (detail.mqTarget) {
    form.mqType = detail.mqTarget.mqType || 'KAFKA'
    form.mqServerAddr = detail.mqTarget.serverAddr || ''
    form.mqTopic = detail.mqTarget.topic || ''
    form.mqRoutingKey = detail.mqTarget.routingKey || ''
  } else {
    form.mqType = 'KAFKA'
    form.mqServerAddr = ''
    form.mqTopic = ''
    form.mqRoutingKey = ''
  }

  // 确保源/目标表下拉和字段信息加载出来
  await loadSourceTables()
  await loadTargetTables()
  await loadSourceColumns()
  await loadTargetColumns()

  // 如果当前还没有字段映射，则根据源/目标字段顺序自动生成一份默认映射
  if (!form.fields.length && sourceColumns.value.length && targetColumns.value.length) {
    await generateDefaultMapping()
  }

  initializingForm.value = false
  dialogVisible.value = true
}

const saveMapping = async () => {
  // 最小必填校验：保证后端能正确建立 mapping 与 CDC 规则
  if (!form.name || !form.sourceDataSourceId || !form.sourceTable) {
    ElMessage.warning('请至少填写映射名称、源数据源和源表')
    return
  }
  // 组装后端 DTO：MappingDetailDTO = { mapping, fields, httpTarget, mqTarget }
  const payload = {
    mapping: {
      id: form.id,
      name: form.name,
      sourceDataSourceId: form.sourceDataSourceId,
      sourceSchema: form.sourceSchema,
      sourceTable: form.sourceTable,
      sourceKafkaServers: form.sourceKafkaServers,
      targetType: form.targetType,
      targetDataSourceId: form.targetType === 'DB' ? form.targetDataSourceId : null,
      targetSchema: form.targetType === 'DB' ? form.targetSchema : null,
      targetTable: form.targetType === 'DB' ? form.targetTable : null,
      enabled: form.enabled,
    },
    fields: form.fields.map((f) => ({
      id: f.id,
      mappingId: form.id,
      sourceCol: f.sourceCol,
      targetCol: f.targetCol,
      isPk: f.isPk,
      expr: f.expr,
    })),
    httpTarget:
      form.targetType === 'HTTP'
        ? {
            url: form.httpUrl,
            method: form.httpMethod,
            headersJson: JSON.stringify(
              Object.fromEntries(
                form.httpHeaders
                  .filter((h) => h.key)
                  .map((h) => [h.key, h.value]),
              ),
            ),
            timeoutMs: form.httpTimeoutMs,
            retryTimes: form.httpRetryTimes,
          }
        : null,
    mqTarget:
      form.targetType === 'MQ'
        ? {
            mqType: form.mqType,
            serverAddr: form.mqServerAddr,
            topic: form.mqTopic,
            routingKey: form.mqRoutingKey,
          }
        : null,
  }
  if (form.id) {
    await updateMapping(form.id, payload)
    ElMessage.success('更新成功')
  } else {
    await createMapping(payload)
    ElMessage.success('创建成功')
  }
  dialogVisible.value = false
  loadList()
}

const handleDelete = (row) => {
  ElMessageBox.confirm(`确认删除映射「${row.name}」吗？`, '提示', { type: 'warning' })
    .then(async () => {
      await deleteMapping(row.id)
      ElMessage.success('删除成功')
      loadList()
    })
    .catch(() => {})
}

onMounted(() => {
  updateTableHeight()
  window.addEventListener('resize', updateTableHeight)
  loadDataSources()
  loadList()
})

onUnmounted(() => {
  window.removeEventListener('resize', updateTableHeight)
})

watch(
  () => [form.sourceDataSourceId, form.sourceSchema],
  () => {
    if (initializingForm.value) return
    form.sourceTable = ''
    sourceTables.value = []
    sourceColumns.value = []
    // 选中数据源后，如果是 MySQL，则强制使用数据源自身的 dbName 作为 schema，不允许跨库
    const ds = dataSources.value.find((d) => d.id === form.sourceDataSourceId)
    if (ds && ds.dbType && ['mysql', 'mariadb'].includes(ds.dbType.toLowerCase())) {
      form.sourceSchema = ds.dbName || ''
    }
    if (form.sourceDataSourceId) {
      loadSourceTables()
    }
  },
)

watch(
  () => [form.targetDataSourceId, form.targetSchema],
  () => {
    if (initializingForm.value) return
    form.targetTable = ''
    targetTables.value = []
    targetColumns.value = []
    const ds = dataSources.value.find((d) => d.id === form.targetDataSourceId)
    if (ds && ds.dbType && ['mysql', 'mariadb'].includes(ds.dbType.toLowerCase())) {
      form.targetSchema = ds.dbName || ''
    }
    if (form.targetDataSourceId && form.targetType === 'DB') {
      loadTargetTables()
    }
  },
)

watch(
  () => form.sourceTable,
  () => {
    if (initializingForm.value) return
    sourceColumns.value = []
    form.fields = []
  },
)

watch(
  () => form.targetTable,
  () => {
    if (initializingForm.value) return
    targetColumns.value = []
    form.fields = []
  },
)
</script>

<template>
  <el-card class="cdc-page-card" shadow="never">
    <template #header>
      <div class="cdc-page-card__header">
        <div>
          <div class="cdc-page-card__title">
            <el-icon style="margin-right: 6px; color: #0ea5e9">
              <Collection />
            </el-icon>
            映射配置
          </div>
          <div class="cdc-page-card__desc">
            为 Debezium 捕获的变更配置路由规则：数据库同步、HTTP 调用、消息队列等
          </div>
        </div>
        <el-space :size="8">
          <el-button :icon="Refresh" @click="loadList">刷新</el-button>
          <el-button :icon="Download" @click="handleExport">导出 Excel</el-button>
          <el-button type="primary" :icon="Plus" @click="openCreate">新建映射配置</el-button>
        </el-space>
      </div>
    </template>

    <div class="cdc-table-toolbar" style="margin-bottom: 10px">
      <el-space :size="10">
        <el-input
          v-model="query.keyword"
          placeholder="搜索映射名称 / 源表 / 目标表"
          style="width: 260px"
          clearable
          @keyup.enter="handleSearch"
          @clear="handleSearch"
        />
        <el-select
          v-model="query.targetType"
          placeholder="目标类型"
          style="width: 140px"
          clearable
          @change="handleSearch"
        >
          <el-option label="DB → DB" value="DB" />
          <el-option label="HTTP 接口" value="HTTP" />
          <el-option label="消息队列" value="MQ" />
        </el-select>
        <el-select
          v-model="query.enabled"
          placeholder="状态"
          style="width: 120px"
          clearable
          @change="handleSearch"
        >
          <el-option label="已启用" value="1" />
          <el-option label="已停用" value="0" />
        </el-select>
      </el-space>
    </div>

    <el-table
      v-loading="loading"
      :data="list"
      border
      stripe
      size="small"
      style="width: 100%"
      :max-height="tableMaxHeight"
    >
      <el-table-column prop="name" label="映射名称" min-width="200" />
      <el-table-column label="源表" min-width="180">
        <template #default="{ row }">
          {{ row.sourceSchema ? row.sourceSchema + '.' : '' }}{{ row.sourceTable }}
        </template>
      </el-table-column>
      <el-table-column prop="targetType" label="类型" width="120" />
      <el-table-column label="目标" min-width="180">
        <template #default="{ row }">
          <template v-if="row.targetType === 'DB'">
            {{ row.targetSchema ? row.targetSchema + '.' : '' }}{{ row.targetTable }}
          </template>
          <template v-else-if="row.targetType === 'HTTP'">
            HTTP
          </template>
          <template v-else-if="row.targetType === 'MQ'">
            MQ
          </template>
        </template>
      </el-table-column>
      <el-table-column prop="enabled" label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="row.enabled === 1 ? 'success' : 'info'" size="small">
            {{ row.enabled === 1 ? '已启用' : '已停用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="160" fixed="right">
        <template #default="{ row }">
          <el-space :size="4">
            <el-button link type="primary" :icon="EditPen" @click="openEdit(row)">编辑</el-button>
            <el-button link type="danger" :icon="Delete" @click="handleDelete(row)">删除</el-button>
          </el-space>
        </template>
      </el-table-column>
    </el-table>

    <div style="margin-top: 14px; text-align: right">
      <el-pagination
        :current-page="query.pageNum"
        :page-size="query.pageSize"
        :page-sizes="[10, 20, 50]"
        :total="totalCount"
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
      :fullscreen="true"
      destroy-on-close
    >
      <el-row :gutter="16">
        <el-col :span="12">
          <div style="font-weight: 600; margin-bottom: 8px">基础信息</div>
          <el-form label-width="90px" label-position="right" size="small">
            <el-form-item label="映射名称">
              <el-input v-model="form.name" placeholder="例如：订单主表 -> 报表订单事实表" />
            </el-form-item>
            <el-form-item label="源数据源">
              <el-select v-model="form.sourceDataSourceId" placeholder="请选择">
                <el-option
                  v-for="ds in dataSources"
                  :key="ds.id"
                  :label="ds.name"
                  :value="ds.id"
                />
              </el-select>
            </el-form-item>
            <el-form-item label="源 Schema">
              <el-input
                v-model="form.sourceSchema"
                :disabled="
                  !!dataSources.find(
                    (d) => d.id === form.sourceDataSourceId && ['mysql', 'mariadb'].includes(d.dbType?.toLowerCase() || ''),
                  )
                "
                placeholder="MySQL/MariaDB 时自动等于数据源库名"
              />
            </el-form-item>
            <el-form-item label="源表">
              <el-select
                v-model="form.sourceTable"
                filterable
                placeholder="选择或输入源表"
                allow-create
                @change="loadSourceColumns"
              >
                <el-option
                  v-for="t in sourceTables"
                  :key="t.name"
                  :label="t.comment ? `${t.name}（${t.comment}）` : t.name"
                  :value="t.name"
                />
              </el-select>
            </el-form-item>
            <el-form-item label="Debezium Kafka">
              <el-input
                v-model="form.sourceKafkaServers"
                placeholder="例如：10.0.0.10:9092,10.0.0.11:9092（用于消费变更）"
              />
            </el-form-item>
            <el-form-item label="目标类型">
              <el-radio-group v-model="form.targetType">
                <el-radio-button label="DB">DB → DB</el-radio-button>
                <el-radio-button label="HTTP">HTTP 接口</el-radio-button>
                <el-radio-button label="MQ">消息队列</el-radio-button>
              </el-radio-group>
            </el-form-item>
            <template v-if="form.targetType === 'DB'">
              <el-form-item label="目标数据源">
                <el-select v-model="form.targetDataSourceId" placeholder="请选择">
                  <el-option
                    v-for="ds in dataSources"
                    :key="ds.id"
                    :label="ds.name"
                    :value="ds.id"
                  />
                </el-select>
              </el-form-item>
              <el-form-item label="目标 Schema">
                <el-input
                  v-model="form.targetSchema"
                  :disabled="
                    !!dataSources.find(
                      (d) => d.id === form.targetDataSourceId && ['mysql', 'mariadb'].includes(d.dbType?.toLowerCase() || ''),
                    )
                  "
                  placeholder="MySQL/MariaDB 时自动等于目标数据源库名"
                />
              </el-form-item>
              <el-form-item label="目标表">
                <el-select
                  v-model="form.targetTable"
                  filterable
                  placeholder="选择或输入目标表"
                  allow-create
                  @change="loadTargetColumns"
                >
                  <el-option
                    v-for="t in targetTables"
                    :key="t.name"
                    :label="t.comment ? `${t.name}（${t.comment}）` : t.name"
                    :value="t.name"
                  />
                </el-select>
              </el-form-item>
            </template>
            <template v-else-if="form.targetType === 'HTTP'">
              <el-form-item label="接口地址">
                <el-input v-model="form.httpUrl" placeholder="例如：https://api.xxx.com/cdc/order" />
              </el-form-item>
              <el-form-item label="请求方法">
                <el-select v-model="form.httpMethod" style="width: 120px">
                  <el-option label="POST" value="POST" />
                  <el-option label="GET" value="GET" />
                  <el-option label="PUT" value="PUT" />
                  <el-option label="DELETE" value="DELETE" />
                </el-select>
              </el-form-item>
              <el-form-item label="请求头">
                <div style="width: 100%">
                  <el-row
                    v-for="(h, index) in form.httpHeaders"
                    :key="index"
                    :gutter="4"
                    style="margin-bottom: 4px"
                  >
                    <el-col :span="9">
                      <el-input v-model="h.key" placeholder="Header 名称，如 Authorization" />
                    </el-col>
                    <el-col :span="11">
                      <el-input v-model="h.value" placeholder="Header 值" />
                    </el-col>
                    <el-col :span="4" style="text-align: right">
                      <el-button
                        link
                        type="danger"
                        size="small"
                        @click="removeHttpHeader(index)"
                      >
                        删除
                      </el-button>
                    </el-col>
                  </el-row>
                  <el-button size="small" type="primary" plain @click="addHttpHeader">
                    新增请求头
                  </el-button>
                </div>
              </el-form-item>
              <el-form-item label="超时(ms)">
                <el-input-number
                  v-model="form.httpTimeoutMs"
                  :min="1000"
                  :max="60000"
                  :step="500"
                  style="width: 160px"
                />
              </el-form-item>
              <el-form-item label="重试次数">
                <el-input-number
                  v-model="form.httpRetryTimes"
                  :min="0"
                  :max="10"
                  style="width: 160px"
                />
              </el-form-item>
            </template>
            <template v-else-if="form.targetType === 'MQ'">
              <el-form-item label="MQ 类型">
                <el-select v-model="form.mqType" style="width: 140px">
                  <el-option label="Kafka" value="KAFKA" />
                  <el-option label="RabbitMQ" value="RABBITMQ" />
                  <el-option label="RocketMQ" value="ROCKETMQ" />
                </el-select>
              </el-form-item>
              <el-form-item label="服务地址">
                <el-input
                  v-model="form.mqServerAddr"
                  placeholder="Kafka: host1:9092,host2:9092 / Rabbit: amqp://user:pass@host:5672/vhost"
                />
              </el-form-item>
              <el-form-item label="Topic/Exchange">
                <el-input
                  v-model="form.mqTopic"
                  placeholder="Kafka Topic / RabbitMQ Exchange / RocketMQ Topic"
                />
              </el-form-item>
              <el-form-item label="RoutingKey">
                <el-input
                  v-model="form.mqRoutingKey"
                  placeholder="可选：RabbitMQ/RocketMQ 使用的路由键"
                />
              </el-form-item>
            </template>
            <el-form-item label="状态">
              <el-switch
                v-model="form.enabled"
                :active-value="1"
                :inactive-value="0"
                active-text="启用"
                inactive-text="停用"
              />
            </el-form-item>
          </el-form>
        </el-col>
        <el-col v-if="form.targetType === 'DB' && form.sourceTable && form.targetTable" :span="12">
          <div style="display: flex; align-items: center; justify-content: space-between">
            <div style="font-weight: 600; margin-bottom: 4px">字段映射与表达式</div>
            <el-popover
              placement="left"
              trigger="click"
              width="420"
            >
              <template #reference>
                <el-button link type="primary" size="small">
                  <el-icon style="margin-right: 2px">
                    <QuestionFilled />
                  </el-icon>
                  表达式示例
                </el-button>
              </template>
              <div style="font-size: 12px; line-height: 1.6">
                <div style="font-weight: 600; margin-bottom: 4px">常用表达式示例：</div>
                <ul style="padding-left: 16px; margin: 0">
                  <li>
                    直接取字段：
                    <code>${'{source.order_main.amount}'}</code>
                  </li>
                  <li>
                    字符串拼接：
                    <code>${'{source.order_main.province}'}-${'{source.order_main.city}'}</code>
                  </li>
                  <li>
                    简单数学运算：
                    <code>${'{source.order_main.amount}'} * 100</code>
                  </li>
                  <li>
                    截取前 10 位（假设后端支持自定义函数）：
                    <code>substr(${'{source.order_main.remark}'}, 0, 10)</code>
                  </li>
                </ul>
                <div style="margin-top: 6px; color: #9ca3af">
                  后端会在处理 CDC 事件时解析
                  <code>${'{source.xxx}'}</code>
                  占位符并替换为真实值。
                </div>
              </div>
            </el-popover>
          </div>
          <div style="margin-bottom: 6px; font-size: 12px; color: #6b7280">
            默认按字段顺序自动生成映射，你可以上下调整顺序、标记主键，并在右侧编辑
            <strong>转换表达式</strong>（支持使用
            <code>${'{source.table.column}'}</code>
            取源字段值）。
          </div>
          <el-table
            :data="form.fields"
            size="small"
            border
            height="360"
            row-key="id"
          >
            <el-table-column label="#" width="60">
              <template #default="{ $index }">
                <el-space :size="4">
                  <span>{{ $index + 1 }}</span>
                  <el-button
                    link
                    size="small"
                    @click="moveFieldUp($index)"
                    :disabled="$index === 0"
                  >
                    上
                  </el-button>
                  <el-button
                    link
                    size="small"
                    @click="moveFieldDown($index)"
                    :disabled="$index === form.fields.length - 1"
                  >
                    下
                  </el-button>
                </el-space>
              </template>
            </el-table-column>
            <el-table-column label="源字段" min-width="170">
              <template #default="{ row }">
                <div>
                  <el-input v-model="row.sourceCol" placeholder="source_col" />
                  <div
                    v-if="sourceColumnMap[row.sourceCol]"
                    style="font-size: 11px; color: #9ca3af; margin-top: 2px"
                  >
                    {{ sourceColumnMap[row.sourceCol].type }}
                    <span v-if="sourceColumnMap[row.sourceCol].comment">
                      · {{ sourceColumnMap[row.sourceCol].comment }}
                    </span>
                  </div>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="目标字段" min-width="170">
              <template #default="{ row }">
                <div>
                  <el-input v-model="row.targetCol" placeholder="target_col" />
                  <div
                    v-if="targetColumnMap[row.targetCol]"
                    style="font-size: 11px; color: #9ca3af; margin-top: 2px"
                  >
                    {{ targetColumnMap[row.targetCol].type }}
                    <span v-if="targetColumnMap[row.targetCol].comment">
                      · {{ targetColumnMap[row.targetCol].comment }}
                    </span>
                  </div>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="主键" width="70">
              <template #default="{ row }">
                <el-switch v-model="row.isPk" :active-value="1" :inactive-value="0" />
              </template>
            </el-table-column>
            <el-table-column label="转换表达式" min-width="200">
              <template #default="{ row }">
                <el-input
                  v-model="row.expr"
                  placeholder="如：${'{source.order_main.amount}'} * 100"
                />
              </template>
            </el-table-column>
            <el-table-column label="操作" width="70" fixed="right">
              <template #default="{ $index }">
                <el-button link type="danger" @click="removeField($index)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
          <div style="margin-top: 8px">
            <el-button size="small" type="primary" plain @click="generateDefaultMapping">
              重新按顺序生成映射
            </el-button>
            <el-button size="small" plain @click="addField">新增字段映射</el-button>
          </div>
        </el-col>
      </el-row>

      <template #footer>
        <div class="cdc-dialog__footer">
          <el-button @click="dialogVisible = false">取 消</el-button>
          <el-button type="primary" @click="saveMapping">保 存</el-button>
        </div>
      </template>
    </el-dialog>
  </el-card>
</template>

<style scoped>
</style>

