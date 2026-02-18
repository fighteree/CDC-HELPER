import request from './request'

// 映射配置相关 API（对应后端 /api/mappings）
export function fetchMappingList(params) {
  return request.get('/mappings', { params })
}

export function fetchMappingDetail(id) {
  return request.get(`/mappings/${id}`)
}

export function createMapping(data) {
  return request.post('/mappings', data)
}

export function updateMapping(id, data) {
  return request.put(`/mappings/${id}`, data)
}

export function deleteMapping(id) {
  return request.delete(`/mappings/${id}`)
}

