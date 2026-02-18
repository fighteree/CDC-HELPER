import { createRouter, createWebHistory } from 'vue-router'

const DataSourceList = () => import('../views/data-source/DataSourceList.vue')
const MappingList = () => import('../views/mapping/MappingList.vue')
const ChangeLogList = () => import('../views/log/ChangeLogList.vue')
const EngineMonitor = () => import('../views/monitor/EngineMonitor.vue')

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/',
      redirect: '/data-sources',
    },
    {
      path: '/data-sources',
      name: 'DataSources',
      component: DataSourceList,
      meta: { menu: '/data-sources', title: '数据源管理' },
    },
    {
      path: '/mappings',
      name: 'Mappings',
      component: MappingList,
      meta: { menu: '/mappings', title: '映射配置管理' },
    },
    {
      path: '/logs',
      name: 'Logs',
      component: ChangeLogList,
      meta: { menu: '/logs', title: '变更日志' },
    },
    {
      path: '/monitor',
      name: 'Monitor',
      component: EngineMonitor,
      meta: { menu: '/monitor', title: '运行状态监控' },
    },
  ],
})

export default router
