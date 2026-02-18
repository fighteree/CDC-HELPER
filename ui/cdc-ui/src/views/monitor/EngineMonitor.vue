<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { Connection, WarningFilled, SuccessFilled } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { fetchEngineStatus, fetchDataSourceHealth } from '@/api/monitor'
import { formatDateTime } from '@/utils/format'

const loadingEngines = ref(false)
const loadingDs = ref(false)
const engines = ref([])
const dataSourceHealth = ref([])

const query = reactive({
  onlyProblematic: false,
})

const loadEngines = async () => {
  loadingEngines.value = true
  try {
    const res = await fetchEngineStatus()
    engines.value = Array.isArray(res) ? res : []
  } catch (e) {
    ElMessage.error(`加载引擎状态失败：${e.message || e}`)
  } finally {
    loadingEngines.value = false
  }
}

const loadDataSources = async () => {
  loadingDs.value = true
  try {
    const res = await fetchDataSourceHealth()
    const items = Array.isArray(res?.items) ? res.items : []
    dataSourceHealth.value = items
  } catch (e) {
    ElMessage.error(`加载数据源健康状态失败：${e.message || e}`)
  } finally {
    loadingDs.value = false
  }
}

const refreshAll = () => {
  loadEngines()
  loadDataSources()
}

const filteredEngines = computed(() => {
  if (!query.onlyProblematic) return engines.value || []
  return (engines.value || []).filter((e) => {
    const hasError = !!e.lastErrorMessage
    const idleTooLong = e.idleSeconds != null && e.idleSeconds > 60 * 5
    return !e.running || hasError || idleTooLong
  })
})

onMounted(() => {
  refreshAll()
})
</script>

<template>
  <el-card class="cdc-page-card" shadow="never">
    <template #header>
      <div class="cdc-page-card__header">
        <div>
          <div class="cdc-page-card__title">
            <el-icon style="margin-right: 6px; color: #0ea5e9">
              <Connection />
            </el-icon>
            运行状态监控
          </div>
          <div class="cdc-page-card__desc">
            查看各映射对应的 Debezium 引擎运行状态与数据源健康状况，快速发现 CDC 运行异常。
          </div>
        </div>
        <el-space :size="8">
          <el-switch
            v-model="query.onlyProblematic"
            active-text="仅显示异常/空闲过久"
          />
          <el-button :icon="Connection" @click="refreshAll">刷新</el-button>
        </el-space>
      </div>
    </template>

    <el-row :gutter="16">
      <el-col :span="16">
        <el-card shadow="never" body-style="{ padding: '8px 12px 12px' }" style="margin-bottom: 8px">
          <div style="font-weight: 600; margin-bottom: 6px">CDC 引擎状态（按映射维度）</div>
          <el-table
            v-loading="loadingEngines"
            :data="filteredEngines"
            border
            size="small"
            :max-height="420"
          >
            <el-table-column prop="mappingId" label="映射ID" width="90" />
            <el-table-column prop="mappingName" label="映射名称" min-width="180" />
            <el-table-column prop="sourceTable" label="源表" min-width="170" />
            <el-table-column prop="target" label="目标" min-width="150" />
            <el-table-column label="状态" width="110">
              <template #default="{ row }">
                <el-tag :type="row.running ? 'success' : 'danger'" size="small">
                  {{ row.running ? '运行中' : '未运行' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="最后心跳" width="180">
              <template #default="{ row }">
                {{ row.lastHeartbeat ? formatDateTime(row.lastHeartbeat) : '-' }}
              </template>
            </el-table-column>
            <el-table-column label="空闲时长" width="120">
              <template #default="{ row }">
                <span v-if="row.idleSeconds != null">
                  {{ row.idleSeconds }} s
                </span>
                <span v-else>-</span>
              </template>
            </el-table-column>
            <el-table-column label="最近错误" min-width="220">
              <template #default="{ row }">
                <span v-if="row.lastErrorMessage" style="color: #b91c1c">
                  {{ row.lastErrorMessage }}
                </span>
                <span v-else style="color: #9ca3af">无</span>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>

      <el-col :span="8">
        <el-card shadow="never" body-style="{ padding: '8px 12px 12px' }">
          <div style="font-weight: 600; margin-bottom: 6px">数据源健康检查</div>
          <el-table
            v-loading="loadingDs"
            :data="dataSourceHealth"
            border
            size="small"
            :max-height="420"
          >
            <el-table-column prop="name" label="数据源" min-width="120" />
            <el-table-column prop="dbType" label="类型" width="80" />
            <el-table-column label="状态" width="90">
              <template #default="{ row }">
                <el-tag
                  v-if="row.ok"
                  type="success"
                  size="small"
                >
                  正常
                </el-tag>
                <el-tag
                  v-else
                  type="danger"
                  size="small"
                >
                  异常
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="最近检测" min-width="150">
              <template #default="{ row }">
                {{ formatDateTime(row.checkedAt) }}
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>
    </el-row>
  </el-card>
</template>

<style scoped>
</style>

