/* eslint-disable react-refresh/only-export-components */

import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
  type PropsWithChildren,
} from 'react'

import { defaultSearchFilters } from '../constants'
import { exportService } from '../services/exportService'
import { filterService } from '../services/filterService'
import { interactionService } from '../services/interactionService'
import { profileRepository } from '../services/profileRepository'
import {
  reminderService,
  type NotificationPermissionState,
} from '../services/reminderService'
import { settingsService } from '../services/settingsService'
import type {
  AppSettingsRecord,
  CreateTagInput,
  DashboardData,
  InteractionInput,
  ProfileDetailRecord,
  ProfileInput,
  SavedFilterInput,
  SavedFilterRecord,
  SearchFilters,
  StudentProfileRecord,
  TagRecord,
  UpdateSettingsInput,
} from '../types'

interface ArchiveState {
  bootstrapping: boolean
  profilesLoading: boolean
  detailLoading: boolean
  busyLabel: string | null
  filters: SearchFilters
  profiles: StudentProfileRecord[]
  tags: TagRecord[]
  savedFilters: SavedFilterRecord[]
  dashboard: DashboardData | null
  settings: AppSettingsRecord | null
  activeDetail: ProfileDetailRecord | null
  notificationPermission: NotificationPermissionState
}

export type { ArchiveState }

export interface ArchiveActions {
  setFilters: (patch: Partial<SearchFilters>) => void
  resetFilters: () => void
  refreshAll: () => Promise<void>
  loadProfileDetail: (studentProfileId: number) => Promise<void>
  clearActiveDetail: () => void
  createProfile: (input: ProfileInput) => Promise<StudentProfileRecord>
  updateProfile: (studentProfileId: number, input: ProfileInput) => Promise<StudentProfileRecord>
  archiveProfile: (studentProfileId: number) => Promise<void>
  addInteraction: (input: InteractionInput) => Promise<ProfileDetailRecord>
  saveFilter: (input: SavedFilterInput) => Promise<SavedFilterRecord>
  deleteSavedFilter: (filterId: number) => Promise<void>
  applySavedFilter: (savedFilter: SavedFilterRecord) => void
  createTag: (input: CreateTagInput) => Promise<TagRecord>
  updateSettings: (input: UpdateSettingsInput) => Promise<void>
  requestNotificationPermission: () => Promise<NotificationPermissionState>
  exportProfilesToXlsx: () => Promise<void>
  exportProfilesToCsv: () => Promise<void>
  exportSingleProfileTimeline: (studentProfileId: number, fullName: string) => Promise<void>
  backupDatabase: () => Promise<void>
  notifyError: (error: unknown, title?: string) => Promise<void>
}

export type ArchiveAppContextValue = ArchiveState & ArchiveActions

const ArchiveAppContext = createContext<ArchiveAppContextValue | null>(null)

function formatError(error: unknown): string {
  if (error instanceof Error) return error.message
  if (typeof error === 'string') return error
  return String(error)
}

export function ArchiveAppProvider({ children }: PropsWithChildren) {
  // ----- state -----
  const [bootstrapping, setBootstrapping] = useState(true)
  const [profilesLoading, setProfilesLoading] = useState(false)
  const [detailLoading, setDetailLoading] = useState(false)
  const [busyLabel, setBusyLabel] = useState<string | null>(null)
  const [filters, setFiltersState] = useState<SearchFilters>(defaultSearchFilters)
  const [profiles, setProfiles] = useState<StudentProfileRecord[]>([])
  const [tags, setTags] = useState<TagRecord[]>([])
  const [savedFilters, setSavedFilters] = useState<SavedFilterRecord[]>([])
  const [dashboard, setDashboard] = useState<DashboardData | null>(null)
  const [settings, setSettings] = useState<AppSettingsRecord | null>(null)
  const [activeDetail, setActiveDetail] = useState<ProfileDetailRecord | null>(null)
  const [notificationPermission, setNotificationPermission] =
    useState<NotificationPermissionState>('default')

  // ----- helpers -----
  const notifyError = useCallback(async (error: unknown, title = '操作失败') => {
    const { message } = await import('@tauri-apps/plugin-dialog')
    await message(formatError(error), { title, kind: 'error' })
  }, [])

  const withBusy = useCallback(
    async <T,>(label: string, job: () => Promise<T>): Promise<T> => {
      setBusyLabel(label)
      try {
        return await job()
      } catch (error) {
        await notifyError(error)
        throw error
      } finally {
        setBusyLabel(null)
      }
    },
    [notifyError],
  )

  // ----- low-level refreshers (stable references) -----
  const refreshMeta = useCallback(async () => {
    const data = await settingsService.getBootstrapData()
    setTags(data.tags)
    setSavedFilters(data.savedFilters)
    setSettings(data.settings)
    setDashboard(data.dashboard)
  }, [])

  const refreshProfiles = useCallback(async (nextFilters: SearchFilters) => {
    setProfilesLoading(true)
    try {
      const rows = await profileRepository.searchProfiles(nextFilters)
      setProfiles(rows)
    } finally {
      setProfilesLoading(false)
    }
  }, [])

  const refreshDashboard = useCallback(async () => {
    const refreshed = await settingsService.getDashboardData()
    setDashboard(refreshed)
  }, [])

  const refreshSavedFilters = useCallback(async () => {
    setSavedFilters(await filterService.listSavedFilters())
  }, [])

  const refreshTags = useCallback(async () => {
    setTags(await settingsService.listTags())
  }, [])

  const syncReminders = useCallback(async () => {
    const reminders = await reminderService.listDueReminders()
    const permission = await reminderService.syncLocalNotifications(reminders)
    setNotificationPermission(permission)
  }, [])

  const refreshAll = useCallback(
    async (nextFilters: SearchFilters = filters) => {
      await Promise.all([refreshMeta(), refreshProfiles(nextFilters)])
      await syncReminders()
    },
    [filters, refreshMeta, refreshProfiles, syncReminders],
  )

  // ----- bootstrap -----
  useEffect(() => {
    let cancelled = false
    void (async () => {
      try {
        await refreshMeta()
        await refreshProfiles(defaultSearchFilters)
        await syncReminders()
      } catch (error) {
        if (!cancelled) await notifyError(error, '启动失败')
      } finally {
        if (!cancelled) setBootstrapping(false)
      }
    })()
    return () => {
      cancelled = true
    }
  }, [notifyError, refreshMeta, refreshProfiles, syncReminders])

  // ----- filter actions -----
  const setFilters = useCallback(
    (patch: Partial<SearchFilters>) => {
      setFiltersState((current) => {
        const nextFilters = { ...current, ...patch }
        void refreshProfiles(nextFilters)
        return nextFilters
      })
    },
    [refreshProfiles],
  )

  const resetFilters = useCallback(() => {
    setFiltersState(defaultSearchFilters)
    void refreshProfiles(defaultSearchFilters)
  }, [refreshProfiles])

  // ----- profile actions -----
  const loadProfileDetail = useCallback(
    async (studentProfileId: number) => {
      setDetailLoading(true)
      try {
        const detail = await profileRepository.getProfileDetail(studentProfileId)
        setActiveDetail(detail)
      } catch (error) {
        await notifyError(error)
        throw error
      } finally {
        setDetailLoading(false)
      }
    },
    [notifyError],
  )

  const clearActiveDetail = useCallback(() => setActiveDetail(null), [])

  const createProfile = useCallback(
    (input: ProfileInput) =>
      withBusy('正在创建档案', async () => {
        const created = await profileRepository.createProfile(input)
        await refreshAll(filters)
        return created
      }),
    [filters, refreshAll, withBusy],
  )

  const updateProfile = useCallback(
    (studentProfileId: number, input: ProfileInput) =>
      withBusy('正在保存档案', async () => {
        const updated = await profileRepository.updateProfile(studentProfileId, input)
        await refreshAll(filters)
        if (activeDetail?.profile.id === studentProfileId) {
          await loadProfileDetail(studentProfileId)
        }
        return updated
      }),
    [activeDetail?.profile.id, filters, loadProfileDetail, refreshAll, withBusy],
  )

  const archiveProfile = useCallback(
    async (studentProfileId: number) => {
      await withBusy('正在归档档案', async () => {
        await profileRepository.archiveProfile(studentProfileId)
        await refreshAll(filters)
        if (activeDetail?.profile.id === studentProfileId) {
          setActiveDetail(null)
        }
      })
    },
    [activeDetail?.profile.id, filters, refreshAll, withBusy],
  )

  // ----- interaction -----
  const addInteraction = useCallback(
    (input: InteractionInput) =>
      withBusy('正在记录沟通', async () => {
        const detail = await interactionService.addInteractionAndRefreshProfile(input)
        setActiveDetail(detail)
        await refreshAll(filters)
        return detail
      }),
    [filters, refreshAll, withBusy],
  )

  // ----- saved filter -----
  const saveFilter = useCallback(
    (input: SavedFilterInput) =>
      withBusy('正在保存分组', async () => {
        const saved = await filterService.saveFilter(input)
        await refreshSavedFilters()
        await refreshDashboard()
        return saved
      }),
    [refreshDashboard, refreshSavedFilters, withBusy],
  )

  const deleteSavedFilter = useCallback(
    async (filterId: number) => {
      await withBusy('正在删除分组', async () => {
        await filterService.deleteSavedFilter(filterId)
        await refreshSavedFilters()
        await refreshDashboard()
      })
    },
    [refreshDashboard, refreshSavedFilters, withBusy],
  )

  const applySavedFilter = useCallback(
    (savedFilter: SavedFilterRecord) => {
      const nextFilters = { ...savedFilter.filters, sortBy: savedFilter.sortBy }
      setFiltersState(nextFilters)
      void refreshProfiles(nextFilters)
    },
    [refreshProfiles],
  )

  // ----- tags -----
  const createTag = useCallback(
    (input: CreateTagInput) =>
      withBusy('正在创建标签', async () => {
        const created = await settingsService.createTag(input)
        await refreshTags()
        return created
      }),
    [refreshTags, withBusy],
  )

  // ----- settings / notifications -----
  const updateSettings = useCallback(
    async (input: UpdateSettingsInput) => {
      await withBusy('正在保存设置', async () => {
        const nextSettings = await settingsService.updateSettings(input)
        setSettings(nextSettings)
      })
    },
    [withBusy],
  )

  const requestNotificationPermission = useCallback(
    () =>
      withBusy('正在请求通知权限', async () => {
        const permission = await reminderService.ensurePermission()
        setNotificationPermission(permission)
        await syncReminders()
        return permission
      }),
    [syncReminders, withBusy],
  )

  // ----- export / backup -----
  const exportProfilesToXlsx = useCallback(
    () =>
      withBusy('正在导出 Excel', async () => {
        await exportService.exportProfilesToXlsx(filters)
      }),
    [filters, withBusy],
  )

  const exportProfilesToCsv = useCallback(
    () =>
      withBusy('正在导出 CSV', async () => {
        await exportService.exportProfilesToCsv(filters)
      }),
    [filters, withBusy],
  )

  const exportSingleProfileTimeline = useCallback(
    (studentProfileId: number, fullName: string) =>
      withBusy('正在导出档案', async () => {
        await exportService.exportSingleProfileTimeline(studentProfileId, fullName)
      }),
    [withBusy],
  )

  const backupDatabase = useCallback(
    () =>
      withBusy('正在备份数据库', async () => {
        const output = await exportService.backupDatabase()
        if (output) {
          setSettings(await settingsService.getSettings())
        }
      }),
    [withBusy],
  )

  // ----- value (stable identity) -----
  const state = useMemo<ArchiveState>(
    () => ({
      bootstrapping,
      profilesLoading,
      detailLoading,
      busyLabel,
      filters,
      profiles,
      tags,
      savedFilters,
      dashboard,
      settings,
      activeDetail,
      notificationPermission,
    }),
    [
      bootstrapping,
      profilesLoading,
      detailLoading,
      busyLabel,
      filters,
      profiles,
      tags,
      savedFilters,
      dashboard,
      settings,
      activeDetail,
      notificationPermission,
    ],
  )

  const actions = useMemo<ArchiveActions>(
    () => ({
      setFilters,
      resetFilters,
      refreshAll: () => refreshAll(),
      loadProfileDetail,
      clearActiveDetail,
      createProfile,
      updateProfile,
      archiveProfile,
      addInteraction,
      saveFilter,
      deleteSavedFilter,
      applySavedFilter,
      createTag,
      updateSettings,
      requestNotificationPermission,
      exportProfilesToXlsx,
      exportProfilesToCsv,
      exportSingleProfileTimeline,
      backupDatabase,
      notifyError,
    }),
    [
      setFilters,
      resetFilters,
      refreshAll,
      loadProfileDetail,
      clearActiveDetail,
      createProfile,
      updateProfile,
      archiveProfile,
      addInteraction,
      saveFilter,
      deleteSavedFilter,
      applySavedFilter,
      createTag,
      updateSettings,
      requestNotificationPermission,
      exportProfilesToXlsx,
      exportProfilesToCsv,
      exportSingleProfileTimeline,
      backupDatabase,
      notifyError,
    ],
  )

  const value = useMemo<ArchiveAppContextValue>(
    () => ({ ...state, ...actions }),
    [state, actions],
  )

  return <ArchiveAppContext.Provider value={value}>{children}</ArchiveAppContext.Provider>
}

export function useArchiveApp(): ArchiveAppContextValue {
  const context = useContext(ArchiveAppContext)
  if (!context) {
    throw new Error('useArchiveApp must be used inside ArchiveAppProvider')
  }
  return context
}
