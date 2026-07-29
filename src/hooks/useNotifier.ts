import { message } from '@tauri-apps/plugin-dialog'
import { useCallback, useMemo } from 'react'

/**
 * 统一的前端通知 hook —— 把 Tauri dialog 的错误/成功/警告/信息弹出
 * 收敛到一处，业务组件只调 notify，不用关心具体怎么弹。
 *
 * 用法：
 *   const { notify } = useNotifier()
 *   await notify.error(error)
 *   await notify.warn('信息不完整')
 */
export type NotifierKind = 'error' | 'warning' | 'info'

export interface Notifier {
  error: (error: unknown, title?: string) => Promise<void>
  warn: (text: string, title?: string) => Promise<void>
  info: (text: string, title?: string) => Promise<void>
}

function formatError(error: unknown): string {
  if (error instanceof Error) return error.message
  if (typeof error === 'string') return error
  try {
    return JSON.stringify(error)
  } catch {
    return String(error)
  }
}

export function useNotifier(): { notify: Notifier } {
  const error = useCallback(async (err: unknown, title = '操作失败') => {
    await message(formatError(err), { title, kind: 'error' })
  }, [])

  const warn = useCallback(async (text: string, title = '提示') => {
    await message(text, { title, kind: 'warning' })
  }, [])

  const info = useCallback(async (text: string, title = '提示') => {
    await message(text, { title, kind: 'info' })
  }, [])

  const notify = useMemo<Notifier>(
    () => ({ error, warn, info }),
    [error, warn, info],
  )

  return { notify }
}
