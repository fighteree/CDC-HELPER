import request from './request'

export function fetchChangeLogs(params) {
  return request.get('/change-logs', { params })
}

export function replayChangeLog(id) {
  return request.post(`/change-logs/${id}/replay`)
}


