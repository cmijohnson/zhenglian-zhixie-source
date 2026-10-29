import dayjs from 'dayjs'

export function formatDateTime(value?: string | null, fallback = '未记录') {
  if (!value) return fallback
  return dayjs(value).format('YYYY-MM-DD HH:mm')
}

export function toLocalDateTimeInput(value?: string | null) {
  if (!value) return ''
  return dayjs(value).format('YYYY-MM-DDTHH:mm')
}

export function fromLocalDateTimeInput(value?: string | null) {
  if (!value) return null
  return dayjs(value).toISOString()
}

export function isOverdue(value?: string | null) {
  if (!value) return false
  return dayjs(value).isBefore(dayjs())
}

export function daysSince(value?: string | null) {
  if (!value) return null
  return dayjs().diff(dayjs(value), 'day')
}

export function fileStamp() {
  return dayjs().format('YYYYMMDD-HHmm')
}
