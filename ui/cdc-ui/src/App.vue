<script setup>
import { useRoute, useRouter } from 'vue-router'
import { computed } from 'vue'
import { DataLine, Connection, Collection, Document } from '@element-plus/icons-vue'

const route = useRoute()
const router = useRouter()

const activeMenu = computed(() => route.meta.menu || route.path)

const go = (path) => {
  if (path !== route.path) {
    router.push(path)
  }
}
</script>

<template>
  <el-container class="cdc-layout">
    <el-aside width="220px" class="cdc-layout__aside">
      <div class="cdc-logo">
        <div class="cdc-logo__icon">
          <Connection />
        </div>
        <div>
          <div class="cdc-logo__text-main">CDC Helper</div>
          <div class="cdc-logo__text-sub">Change Data Capture Studio</div>
        </div>
      </div>

      <el-menu
        class="cdc-menu"
        :default-active="activeMenu"
        background-color="transparent"
        text-color="#e5e7eb"
        active-text-color="#fefce8"
      >
        <el-menu-item index="/data-sources" @click="go('/data-sources')">
          <el-icon><DataLine /></el-icon>
          <span>数据源管理</span>
        </el-menu-item>
        <el-menu-item index="/mappings" @click="go('/mappings')">
          <el-icon><Collection /></el-icon>
          <span>映射配置</span>
        </el-menu-item>
        <el-menu-item index="/logs" @click="go('/logs')">
          <el-icon><Document /></el-icon>
          <span>变更日志</span>
        </el-menu-item>
        <el-menu-item index="/monitor" @click="go('/monitor')">
          <el-icon><Connection /></el-icon>
          <span>运行状态</span>
        </el-menu-item>
      </el-menu>
    </el-aside>

    <el-container>
      <el-header height="80px" class="cdc-layout__header">
        <div>
          <div class="cdc-header__title">CDC 管理控制台</div>
          <div class="cdc-header__subtitle">基于 Debezium 的实时数据同步编排</div>
          <div class="cdc-header__disclaimer">
            本项目仅供技术研究与学习使用，禁止任何形式的商业用途，由此产生的风险由使用者自行承担。
          </div>
        </div>
        <div class="cdc-header__right">
          <div class="cdc-author-badge">
            <span class="cdc-author-label">作者：</span>
            <span class="cdc-author-name">一只大笨熊</span>
          </div>
        </div>
      </el-header>

      <el-main class="cdc-layout__main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<style scoped>
.cdc-header__right {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 0;
  font-size: 12px;
  color: #e5e7eb;
}

.cdc-author-badge {
  display: inline-flex;
  align-items: center;
  padding: 2px 10px;
  border-radius: 999px;
  background: rgba(15, 23, 42, 0.6);
  border: 1px solid rgba(250, 204, 21, 0.7);
  box-shadow: 0 0 0 1px rgba(15, 23, 42, 0.8);
}

.cdc-author-label {
  opacity: 0.8;
  margin-right: 4px;
}

.cdc-author-name {
  font-weight: 600;
  color: #facc15;
}

.cdc-header__disclaimer {
  margin-top: 4px;
  font-size: 11px;
  line-height: 1.4;
  padding: 3px 10px;
  border-radius: 999px;
  display: inline-block;
  background: rgba(248, 113, 113, 0.08);
  border: 1px solid rgba(248, 113, 113, 0.4);
  color: #b91c1c;
}
</style>
