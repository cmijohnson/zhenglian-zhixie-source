import { useState } from 'react'

import { tagCategories, tagColorOptions } from '../constants'
import { useArchiveApp } from '../context/AppContext'
import { formatDateTime } from '../lib/date'
import type { TagDraft } from '../types'

const permissionLabelMap: Record<NotificationPermission, string> = {
  default: '未设置',
  denied: '已拒绝',
  granted: '已允许',
}

export function SettingsPage() {
  const {
    busyLabel,
    settings,
    tags,
    notificationPermission,
    updateSettings,
    createTag,
    requestNotificationPermission,
    backupDatabase,
  } = useArchiveApp()
  const [draft, setDraft] = useState<TagDraft>({
    name: '',
    category: '自定义',
    color: tagColorOptions[0],
  })
  const [reminderDraft, setReminderDraft] = useState<string | null>(null)
  const reminderTime = reminderDraft ?? settings?.defaultReminderTime ?? '09:00'

  return (
    <div className="page-stack">
      <section className="card page-header compact workspace-banner">
        <div>
          <p className="eyebrow">设置</p>
          <h2>本地设置</h2>
          <p className="hero-copy">提醒、备份和标签都在这里维护。</p>
        </div>
        <div className="hero-actions">
          <button type="button" className="secondary-button" onClick={() => void backupDatabase()}>
            备份数据库
          </button>
        </div>
      </section>

      <section className="grid two-panels">
        <div className="card detail-card">
          <div className="section-title-row">
            <div>
              <p className="eyebrow">提醒</p>
              <h3>默认跟进时间与系统通知</h3>
            </div>
            <span className="status-chip">{permissionLabelMap[notificationPermission]}</span>
          </div>

          <label className="inline-field">
            <span>默认提醒时间</span>
            <input
              type="time"
              value={reminderTime}
              onChange={(event) => setReminderDraft(event.target.value)}
            />
          </label>

          <div className="button-row">
            <button
              type="button"
              className="primary-button"
              disabled={busyLabel === '正在保存设置'}
              onClick={async () => {
                await updateSettings({ defaultReminderTime: reminderTime })
                setReminderDraft(null)
              }}
            >
              保存提醒设置
            </button>
            <button
              type="button"
              className="secondary-button"
              onClick={() => void requestNotificationPermission()}
            >
              重新请求通知权限
            </button>
          </div>

          <p className="helper-text">系统通知用于到期提醒，首页待跟进列表会继续作为兜底。</p>
        </div>

        <div className="card detail-card">
          <div className="section-title-row">
            <div>
              <p className="eyebrow">数据库</p>
              <h3>数据库与备份</h3>
            </div>
          </div>

          <div className="note-block">
            <strong>数据库路径</strong>
            <code>{settings?.databasePath || '加载中...'}</code>
          </div>

          <div className="note-block">
            <strong>最近一次备份</strong>
            <p>{formatDateTime(settings?.lastBackupAt, '还没有备份记录')}</p>
          </div>
        </div>
      </section>

      <section className="grid two-panels">
        <div className="card detail-card">
          <div className="section-title-row">
            <div>
              <p className="eyebrow">标签管理</p>
              <h3>当前标签</h3>
            </div>
            <span className="count-pill">{tags.length}</span>
          </div>

          <div className="tag-grid">
            {tags.map((tag) => (
              <div
                key={tag.id}
                className="tag-tile active"
                style={{ borderColor: tag.color, backgroundColor: `${tag.color}1f` }}
              >
                <span>{tag.name}</span>
                <small>
                  {tag.category} · {tag.isSystem ? '系统' : '自定义'}
                </small>
              </div>
            ))}
          </div>
        </div>

        <div className="card detail-card">
          <div className="section-title-row">
            <div>
              <p className="eyebrow">新增标签</p>
              <h3>新建标签</h3>
            </div>
          </div>

          <div className="form-grid">
            <label>
              <span>标签名称</span>
              <input
                value={draft.name}
                onChange={(event) => setDraft({ ...draft, name: event.target.value })}
                placeholder="例如：活动到场"
              />
            </label>
            <label>
              <span>标签类别</span>
              <select
                value={draft.category}
                onChange={(event) => setDraft({ ...draft, category: event.target.value })}
              >
                {tagCategories.map((option) => (
                  <option key={option} value={option}>
                    {option}
                  </option>
                ))}
              </select>
            </label>
          </div>

          <div className="chip-wrap">
            {tagColorOptions.map((color) => (
              <button
                key={color}
                type="button"
                className={draft.color === color ? 'color-swatch active' : 'color-swatch'}
                style={{ backgroundColor: color }}
                onClick={() => setDraft({ ...draft, color })}
                aria-label={color}
              />
            ))}
          </div>

          <button
            type="button"
            className="primary-button"
            disabled={!draft.name.trim() || busyLabel === '正在创建标签'}
            onClick={async () => {
              await createTag(draft)
              setDraft({ name: '', category: '自定义', color: tagColorOptions[0] })
            }}
          >
            创建标签
          </button>
        </div>
      </section>
    </div>
  )
}
