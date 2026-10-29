import { message, save } from '@tauri-apps/plugin-dialog'

import { fileStamp } from '../lib/date'
import type { SearchFilters } from '../types'
import { api } from './api'

async function savePath(name: string, extension: 'xlsx' | 'csv' | 'sqlite') {
  return save({
    title: '选择导出位置',
    defaultPath: `${name}-${fileStamp()}.${extension}`,
    filters: [
      {
        name: extension.toUpperCase(),
        extensions: [extension],
      },
    ],
  })
}

export const exportService = {
  async exportProfilesToXlsx(filters: SearchFilters) {
    const filePath = await savePath('学生档案导出', 'xlsx')
    if (!filePath) return null
    const output = await api.exportProfilesToXlsx({ filePath, filters })
    await message(`已导出到：\n${output}`, { title: '导出完成', kind: 'info' })
    return output
  },

  async exportProfilesToCsv(filters: SearchFilters) {
    const filePath = await savePath('学生档案导出', 'csv')
    if (!filePath) return null
    const output = await api.exportProfilesToCsv({ filePath, filters })
    await message(`已导出到：\n${output}`, { title: '导出完成', kind: 'info' })
    return output
  },

  async exportSingleProfileTimeline(studentProfileId: number, fullName: string) {
    const filePath = await save({
      title: '导出单个档案',
      defaultPath: `${fullName}-时间线-${fileStamp()}.xlsx`,
      filters: [{ name: 'Excel', extensions: ['xlsx'] }],
    })
    if (!filePath) return null
    const output = await api.exportSingleProfileTimeline({ filePath, studentProfileId })
    await message(`已导出到：\n${output}`, { title: '导出完成', kind: 'info' })
    return output
  },

  async backupDatabase() {
    const filePath = await savePath('yingge-student-archive-backup', 'sqlite')
    if (!filePath) return null
    const output = await api.backupDatabase(filePath)
    await message(`数据库备份已保存到：\n${output}`, { title: '备份完成', kind: 'info' })
    return output
  },
}
