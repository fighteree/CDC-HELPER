import * as XLSX from 'xlsx'
import { ElLoading, ElMessage } from 'element-plus'

/**
 * 导出当前页数据为 Excel，带全局 loading，防止重复点击
 * @param {Array<Object>} data - 表格数据
 * @param {Array<{ label: string, prop: string, formatter?: (row) => string }>} columns - 列配置，与表格列对应
 * @param {string} filename - 文件名（不含 .xlsx）
 */
export function exportExcel(data, columns, filename = '导出') {
  if (!data || !Array.isArray(data) || data.length === 0) {
    ElMessage.warning('暂无数据可导出')
    return
  }
  const loading = ElLoading.service({
    lock: true,
    text: '正在导出，请稍候...',
    background: 'rgba(255,255,255,0.8)',
  })
  try {
    const headers = columns.map((c) => c.label)
    const rows = data.map((row) =>
      columns.map((col) => {
        const val = row[col.prop]
        if (col.formatter) return col.formatter(row)
        if (val == null) return ''
        if (typeof val === 'object' && val !== null) return JSON.stringify(val)
        return String(val)
      }),
    )
    const sheetData = [headers, ...rows]
    const ws = XLSX.utils.aoa_to_sheet(sheetData)
    const wb = XLSX.utils.book_new()
    XLSX.utils.book_append_sheet(wb, ws, 'Sheet1')
    const name = `${filename}_${new Date().toISOString().slice(0, 10)}.xlsx`
    XLSX.writeFile(wb, name)
    ElMessage.success('导出成功')
  } catch (e) {
    ElMessage.error('导出失败：' + (e && e.message ? e.message : '未知错误'))
  } finally {
    loading.close()
  }
}
