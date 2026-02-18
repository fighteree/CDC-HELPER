<script setup>
import { onMounted, onUnmounted, reactive, ref } from 'vue'
import { Document, Refresh, Download, RefreshLeft, WarningFilled } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { fetchChangeLogs, replayChangeLog } from '@/api/changeLog'
import { formatRowTime, formatDateTime } from '@/utils/format'
import { exportExcel } from '@/utils/exportExcel'

const loading = ref(false)
const list = ref([])
const total = ref(0)
const tableMaxHeight = ref(600)

const query = reactive({
  keyword: '',
  targetType: '',
  success: '',
  operation: '',
  timeRange: null, // [start, end] from el-date-picker
  pageNum: 1,
  pageSize: 20,
})

const loadLogs = async () => {
  loading.value = true
  try {
    let fromTime = null
    let toTime = null
    if (query.timeRange && query.timeRange.length === 2) {
      fromTime = query.timeRange[0]
      toTime = query.timeRange[1]
    }
    const res = await fetchChangeLogs({
      keyword: query.keyword || undefined,
      targetType: query.targetType || undefined,
      success: query.success === '' ? undefined : query.success,
      operation: query.operation || undefined,
      fromTime,
      toTime,
      pageNum: query.pageNum,
      pageSize: query.pageSize,
    })
    // 兼容两种返回：{ list, total } 或 直接数组（旧版）
    if (res && Array.isArray(res.list)) {
      list.value = res.list
      total.value = res.total != null ? res.total : res.list.length
    } else if (res && Array.isArray(res)) {
      list.value = res
      total.value = res.length
    } else {
      list.value = []
      total.value = 0
    }
  } finally {
    loading.value = false
  }
}

const handleSearch = () => {
  query.pageNum = 1
  loadLogs()
}

const handlePageChange = (page) => {
  query.pageNum = Number(page)
  loadLogs()
}

const handleSizeChange = (size) => {
  query.pageSize = Number(size)
  query.pageNum = 1
  loadLogs()
}

const updateTableHeight = () => {
  const h = window.innerHeight || document.documentElement.clientHeight || 800
  tableMaxHeight.value = Math.max(300, h - 260)
}

const logColumns = [
  { label: 'ID', prop: 'id' },
  { label: '映射ID', prop: 'mappingId' },
  { label: '源表', prop: 'sourceTable' },
  { label: '操作', prop: 'operation' },
  { label: '目标类型', prop: 'targetType' },
  { label: '结果', prop: 'success', formatter: (row) => (row.success === 1 ? '成功' : '失败') },
  { label: '耗时(ms)', prop: 'costMs' },
  { label: '重放次数', prop: 'replayCount' },
  { label: '最后重放时间', prop: 'lastReplayAt' },
  { label: '时间', prop: 'createdAt', formatter: (row) => formatRowTime(row) },
  { label: '错误信息', prop: 'errorMessage' },
]

const handleExport = () => {
  exportExcel(list.value, logColumns, '变更日志')
}

const handleReplay = async (row) => {
  try {
    await ElMessageBox.confirm(
      `确定要重放日志 ID=${row.id} 对应的 CDC 事件吗？\n\n` +
        '重放会基于“历史事件”重新执行一次映射，如果源库在此期间已经发生多次变更，' +
        '可能出现 ABA 问题：当前行状态已不同于当时，重放可能导致目标库与业务期望不一致。\n\n' +
        '请在确认风险后再继续。',
      '重放提示',
      {
        type: 'warning',
        confirmButtonText: '我已知晓风险，继续重放',
        cancelButtonText: '取消',
      },
    )
  } catch {
    return
  }

  try {
    const res = await replayChangeLog(row.id)
    if (res && res.success) {
      ElMessage.success(res.message || '已触发重放')
      loadLogs()
    } else {
      ElMessage.error(res?.message || '重放失败')
    }
  } catch (e) {
    ElMessage.error(`重放失败：${e.message || e}`)
  }
}

onMounted(() => {
  updateTableHeight()
  window.addEventListener('resize', updateTableHeight)
  loadLogs()
})

onUnmounted(() => {
  window.removeEventListener('resize', updateTableHeight)
})
</script>

<template>
  <el-card class="cdc-page-card" shadow="never">
    <template #header>
      <div class="cdc-page-card__header">
        <div>
          <div class="cdc-page-card__title">
            <el-icon style="margin-right: 6px; color: #10b981">
              <Document />
            </el-icon>
            变更日志
          </div>
          <div class="cdc-page-card__desc">
            实时追踪每一次 Debezium 捕获的变更在路由过程中的处理结果，支持按时间范围与条件查询、分页与导出
          </div>
        </div>
        <el-space :size="8">
          <el-button :icon="Refresh" @click="loadLogs">刷新</el-button>
          <el-button :icon="Download" @click="handleExport">导出 Excel</el-button>
        </el-space>
      </div>
    </template>

    <div class="cdc-table-toolbar" style="margin-bottom: 10px">
      <el-space :size="10" wrap>
        <el-input
          v-model="query.keyword"
          placeholder="搜索映射ID / 源表 / 操作"
          style="width: 220px"
          clearable
          @keyup.enter="handleSearch"
        />
        <el-select
          v-model="query.targetType"
          placeholder="目标类型"
          style="width: 120px"
          clearable
        >
          <el-option label="DB" value="DB" />
          <el-option label="HTTP" value="HTTP" />
          <el-option label="MQ" value="MQ" />
        </el-select>
        <el-select
          v-model="query.operation"
          placeholder="操作类型"
          style="width: 120px"
          clearable
        >
          <el-option label="INSERT" value="INSERT" />
          <el-option label="UPDATE" value="UPDATE" />
          <el-option label="DELETE" value="DELETE" />
        </el-select>
        <el-select
          v-model="query.success"
          placeholder="结果"
          style="width: 100px"
          clearable
        >
          <el-option label="成功" value="1" />
          <el-option label="失败" value="0" />
        </el-select>
        <el-date-picker
          v-model="query.timeRange"
          type="datetimerange"
          range-separator="至"
          start-placeholder="开始时间"
          end-placeholder="结束时间"
          value-format="YYYY-MM-DD HH:mm:ss"
          :default-time="[new Date(2000, 0, 1, 0, 0, 0), new Date(2000, 0, 1, 23, 59, 59)]"
          style="width: 360px"
        />
        <el-button type="primary" @click="handleSearch">查询</el-button>
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
      <el-table-column prop="id" label="ID" width="80" />
      <el-table-column prop="mappingId" label="映射ID" width="100" />
      <el-table-column prop="sourceTable" label="源表" min-width="160" />
      <el-table-column prop="operation" label="操作" width="90" />
      <el-table-column prop="targetType" label="目标类型" width="100" />
      <el-table-column prop="success" label="结果" width="90">
        <template #default="{ row }">
          <el-tag :type="row.success === 1 ? 'success' : 'danger'" size="small">
            {{ row.success === 1 ? '成功' : '失败' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="costMs" label="耗时(ms)" width="100" />
      <el-table-column prop="replayCount" label="重放次数" width="100" />
      <el-table-column label="最后重放时间" width="180">
        <template #default="{ row }">
          {{ formatDateTime(row.lastReplayAt) }}
        </template>
      </el-table-column>
      <el-table-column label="时间" width="180">
        <template #default="{ row }">
          {{ formatRowTime(row) }}
        </template>
      </el-table-column>
      <el-table-column label="操作" width="160" fixed="right">
        <template #default="{ row }">
          <el-space :size="6">
            <el-tooltip
              v-if="row.errorMessage"
              placement="top"
              effect="dark"
              :content="row.errorMessage"
            >
              <el-link type="danger" :underline="false" style="font-size: 13px">
                <el-icon style="margin-right: 2px">
                  <WarningFilled />
                </el-icon>
                错误详情
              </el-link>
            </el-tooltip>
            <el-button
              v-if="row.success === 0"
              size="small"
              type="primary"
              :icon="RefreshLeft"
              @click="handleReplay(row)"
            >
              重放
            </el-button>
          </el-space>
        </template>
      </el-table-column>
    </el-table>

    <div style="margin-top: 14px; text-align: right">
      <el-pagination
        :current-page="query.pageNum"
        :page-size="query.pageSize"
        :page-sizes="[10, 20, 50, 100]"
        :total="total"
        layout="total, sizes, prev, pager, next, jumper"
        small
        background
        @update:current-page="handlePageChange"
        @update:page-size="handleSizeChange"
      />
    </div>
  </el-card>
</template>

<style scoped>
</style>
