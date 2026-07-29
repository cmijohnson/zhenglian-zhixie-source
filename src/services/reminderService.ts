import {
  Schedule,
  cancelAll,
  isPermissionGranted,
  requestPermission,
  sendNotification,
} from '@tauri-apps/plugin-notification'

import { api } from './api'
import type { ReminderEventRecord } from '../types'

export type NotificationPermissionState = NotificationPermission

async function resolvePermission() {
  const granted = await isPermissionGranted()
  if (granted) return 'granted' as const
  return requestPermission()
}

export const reminderService = {
  async ensurePermission(): Promise<NotificationPermissionState> {
    return resolvePermission()
  },

  async syncLocalNotifications(reminders: ReminderEventRecord[]) {
    await cancelAll()
    const permission = await resolvePermission()
    if (permission !== 'granted') {
      return permission
    }

    const futureReminders = reminders.filter((reminder) => {
      return reminder.status === 'pending' && new Date(reminder.remindAt).getTime() > Date.now()
    })

    for (const reminder of futureReminders) {
      sendNotification({
        id: reminder.id,
        title: reminder.title,
        body: '打开英格学生档案工具，查看本次跟进详情。',
        schedule: Schedule.at(new Date(reminder.remindAt)),
      })
    }

    return permission
  },

  listDueReminders: () => api.listDueReminders(),
}
