import { confirm } from '@tauri-apps/plugin-dialog'
import { startTransition, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'

import { savedFilterSortOptions } from '../constants'
import { useArchiveApp } from '../context/AppContext'
import { useNotifier } from '../hooks/useNotifier'
import { toProfileInput } from '../lib/forms'
import { formatDateTime } from '../lib/date'
import { profileRepository } from '../services/profileRepository'
import type { ProfileFormState, SavedFilterRecord } from '../types'
import { ProfileEditorDialog } from './ProfileEditorDialog'

function TagPills({ labels }: { labels: string[] }) {
  if (labels.length === 0) return <span className="muted-inline">暂无标签</span>
  return (
    <div className="tag-pill-wrap">
      {labels.map((label) => (
        <span key={label} className="tag-pill">
          {label}
        </span>
      ))}
    </div>
  )
}

function SavedFilterList({
  items,
  onOpen,
  onDelete,
}: {
  items: SavedFilterRecord[]
  onOpen: (filter: SavedFilterRecord) => void
  onDelete: (filterId: number) => Promise<void>
}) {
  return (
    <div className="saved-filter-list">
      {items.length === 0 ? (
        <p className="empty-copy">还没有保存分组。</p>
      ) : (
        items.map((item) => (
          <div key={item.id} className="saved-filter-item">
            <button type="button" className="saved-filter-open" onClick={() => onOpen(item)}>
              <strong>{item.name}</strong>
              <span>
                {item.filters.grade || '全部'} · {item.filters.focusDirection || '全部'} ·{' '}
                {item.filters.status || '全部'}
              </span>
            </button>
            <button type="button" className="ghost-button small" onClick={() => void onDelete(item.id)}>
              删除
            </button>
          </div>
        ))
      )}
    </div>
  )
}

export function ProfilesPage() {
  const navigate = useNavigate()
  const {
    busyLabel,
    filters,
    profiles,
    profilesLoading,
    tags,
    savedFilters,
    setFilters,
    resetFilters,
    createProfile,
    deleteSavedFilter,
    applySavedFilter,
    saveFilter,
    exportProfilesToCsv,
    exportProfilesToXlsx,
  } = useArchiveApp()
  const { notify } = useNotifier()

  const [createOpen, setCreateOpen] = useState(false)
  const [filterName, setFilterName] = useState('')
  const [pinToHomepage, setPinToHomepage] = useState(false)

  async function handleCreate(form: ProfileFormState) {
    if (!form.fullName.trim() || (!form.phone.trim() && !form.wechatId.trim())) {
      await notify.warn('请至少填写姓名，并在手机号或微信号中填写一个。', '信息不完整')
      return
    }

    const duplicates = await profileRepository.findPotentialDuplicates({
      fullName: form.fullName,
      phone: form.phone || null,
      wechatId: form.wechatId || null,
    })

    if (duplicates.length > 0) {
      const summary = duplicates
        .slice(0, 3)
        .map((match) => `${match.profile.fullName}：${match.reasons.join(' / ')}`)
        .join('\n')
      const approved = await confirm(
        `检测到可能重复的档案：\n${summary}\n\n仍然继续创建吗？`,
        {
          title: '可能重复',
          kind: 'warning',
        },
      )
      if (!approved) return
    }

    const created = await createProfile(toProfileInput(form))
    setCreateOpen(false)
    navigate(`/profiles/${created.id}`)
  }

  async function handleSaveFilter() {
    if (!filterName.trim()) {
      await notify.warn('先给这个分组起个名字。', '无法保存')
      return
    }

    await saveFilter({
      name: filterName,
      filters,
      sortBy: filters.sortBy || 'updatedDesc',
      showOnHomepage: pinToHomepage,
    })
    setFilterName('')
    setPinToHomepage(false)
  }

  async function handleDeleteFilter(filterId: number) {
    const approved = await confirm('删除这个保存分组后，筛选条件不会再被保留。确定删除吗？', {
      title: '删除保存分组',
      kind: 'warning',
    })
    if (!approved) return
    await deleteSavedFilter(filterId)
  }

  return (
    <div className="page-stack">
      <section className="card page-header compact workspace-banner">
        <div>
          <p className="eyebrow">档案库</p>
          <h2>档案检索</h2>
          <p className="hero-copy">搜索、筛选、保存分组、导出和建档都从这里处理。</p>
        </div>
        <div className="hero-actions">
          <button type="button" className="secondary-button" onClick={() => void exportProfilesToXlsx()}>
            导出 Excel
          </button>
          <button type="button" className="secondary-button" onClick={() => void exportProfilesToCsv()}>
            导出 CSV
          </button>
          <button type="button" className="primary-button" onClick={() => setCreateOpen(true)}>
            新建档案
          </button>
        </div>
      </section>

      <section className="grid layout-profiles">
        <div className="card filter-card">
          <div className="section-title-row">
            <div>
              <p className="eyebrow">筛选</p>
              <h3>筛选条件</h3>
            </div>
            <button type="button" className="ghost-button small" onClick={resetFilters}>
              重置
            </button>
          </div>

          <div className="form-grid two-column">
            <label>
              <span>搜索</span>
              <input
                value={filters.searchText || ''}
                onChange={(event) => setFilters({ searchText: event.target.value })}
                placeholder="姓名 / 微信 / 手机 / 学校"
              />
            </label>
            <label>
              <span>年级</span>
              <input
                value={filters.grade || ''}
                onChange={(event) => setFilters({ grade: event.target.value })}
                placeholder="例如 2025"
              />
            </label>
            <label>
              <span>关注方向</span>
              <input
                value={filters.focusDirection || ''}
                onChange={(event) => setFilters({ focusDirection: event.target.value })}
                placeholder="就业 / 考研 / 考公"
              />
            </label>
            <label>
              <span>当前状态</span>
              <input
                value={filters.status || ''}
                onChange={(event) => setFilters({ status: event.target.value })}
                placeholder="跟进中 / 高意向"
              />
            </label>
            <label>
              <span>报名意向</span>
              <input
                value={filters.intentionLevel || ''}
                onChange={(event) => setFilters({ intentionLevel: event.target.value })}
                placeholder="低 / 中 / 高"
              />
            </label>
            <label>
              <span>核实状态</span>
              <input
                value={filters.verificationStatus || ''}
                onChange={(event) => setFilters({ verificationStatus: event.target.value })}
                placeholder="未核实 / 已核实"
              />
            </label>
            <label>
              <span>排序方式</span>
              <select
                value={filters.sortBy || 'updatedDesc'}
                onChange={(event) => setFilters({ sortBy: event.target.value })}
              >
                {savedFilterSortOptions.map((option) => (
                  <option key={option.value} value={option.value}>
                    {option.label}
                  </option>
                ))}
              </select>
            </label>
            <label className="checkbox-field">
              <input
                type="checkbox"
                checked={filters.includeArchived}
                onChange={(event) => setFilters({ includeArchived: event.target.checked })}
              />
              <span>包含归档档案</span>
            </label>
          </div>

          <div className="chip-section">
            <div className="section-title-row">
              <span>按标签筛选</span>
              <small>可多选</small>
            </div>
            <div className="tag-grid compact">
              {tags.map((tag) => {
                const active = filters.tagIds.includes(tag.id)
                return (
                  <button
                    key={tag.id}
                    type="button"
                    className={active ? 'tag-tile active' : 'tag-tile'}
                    style={{ borderColor: tag.color, backgroundColor: active ? `${tag.color}1f` : undefined }}
                    onClick={() => {
                      startTransition(() => {
                        setFilters({
                          tagIds: active
                            ? filters.tagIds.filter((id) => id !== tag.id)
                            : [...filters.tagIds, tag.id],
                        })
                      })
                    }}
                  >
                    <span>{tag.name}</span>
                    <small>{tag.category}</small>
                  </button>
                )
              })}
            </div>
          </div>

          <div className="save-filter-box">
            <input
              value={filterName}
              onChange={(event) => setFilterName(event.target.value)}
              placeholder="保存当前分组名称"
            />
            <label className="checkbox-field">
              <input
                type="checkbox"
                checked={pinToHomepage}
                onChange={(event) => setPinToHomepage(event.target.checked)}
              />
              <span>显示到首页</span>
            </label>
            <button type="button" className="primary-button" onClick={() => void handleSaveFilter()}>
              保存分组
            </button>
          </div>
        </div>

        <div className="card filter-card">
          <div className="section-title-row">
            <div>
              <p className="eyebrow">分组</p>
              <h3>已保存分组</h3>
            </div>
            <span className="count-pill">{savedFilters.length}</span>
          </div>
          <SavedFilterList
            items={savedFilters}
            onOpen={(savedFilter) => applySavedFilter(savedFilter)}
            onDelete={handleDeleteFilter}
          />
        </div>
      </section>

      <section className="card table-card">
        <div className="section-title-row">
          <div>
            <p className="eyebrow">结果</p>
            <h3>{profilesLoading ? '正在刷新列表...' : `共 ${profiles.length} 条档案`}</h3>
          </div>
          {busyLabel ? <span className="status-line">{busyLabel}</span> : null}
        </div>

        {profiles.length === 0 ? (
          <p className="empty-copy">当前条件下没有结果。</p>
        ) : (
          <div className="profiles-table">
            {profiles.map((profile) => (
              <Link key={profile.id} to={`/profiles/${profile.id}`} className="table-row">
                <div className="table-main">
                  <strong>{profile.fullName}</strong>
                  <p>
                    {profile.school || '学校未填'} · {profile.major || '专业未填'} ·{' '}
                    {profile.focusDirections.join(' / ') || '方向未填'}
                  </p>
                  <TagPills labels={profile.tags.map((tag) => tag.name)} />
                </div>
                <div className="table-meta">
                  <span className="status-chip">{profile.currentStatus}</span>
                  <small>意向：{profile.intentionLevel}</small>
                  <small>最近联系：{formatDateTime(profile.lastContactAt)}</small>
                  <small>下次跟进：{formatDateTime(profile.nextFollowUpAt)}</small>
                </div>
              </Link>
            ))}
          </div>
        )}
      </section>

      <ProfileEditorDialog
        key={createOpen ? 'create-open' : 'create-closed'}
        open={createOpen}
        title="新建同学档案"
        tags={tags}
        busy={busyLabel === '正在创建档案'}
        onClose={() => setCreateOpen(false)}
        onSubmit={handleCreate}
      />
    </div>
  )
}
