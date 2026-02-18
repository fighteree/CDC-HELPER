import request from './request'

export function fetchEngineStatus() {
  return request.get('/monitor/engines')
}

export function fetchDataSourceHealth() {
  return request.get('/monitor/datasources')
}

