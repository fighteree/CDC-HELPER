import request from './request'

export function fetchDataSourceList(params) {
  return request.get('/data-sources', { params })
}

export function createDataSource(data) {
  return request.post('/data-sources', data)
}

export function updateDataSource(id, data) {
  return request.put(`/data-sources/${id}`, data)
}

export function deleteDataSource(id) {
  return request.delete(`/data-sources/${id}`)
}

export function testDataSource(id) {
  return request.post(`/data-sources/${id}/test`)
}

// 使用当前表单填写的配置直接测试连接（不依赖已保存的数据源记录）
export function testDataSourceByConfig(data) {
  return request.post('/data-sources/test-connection', data)
}

export function fetchTables(dataSourceId, params) {
  return request.get(`/data-sources/${dataSourceId}/tables`, { params })
}

export function fetchColumns(dataSourceId, params) {
  return request.get(`/data-sources/${dataSourceId}/columns`, { params })
}

