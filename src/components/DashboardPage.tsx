import { Link, useNavigate } from 'react-router-dom'

import { useArchiveApp } from '../context/AppContext'
import { formatDateTime } from '../lib/date'
import type { SavedFilterRecord, StudentProfileRecord } from '../types'

function ProfileListSection({
  title,
  description,
  profiles,
}: {
  title: string
  description: string
  profiles: StudentProfileRecord[]
}) {
  return (
    <section className="card list-card">
      <div className="section-title-row">
        <div>
          <p className="eyebrow">{description}</p>
          <h3>{title}</h3>
        </div>
        <span className="count-pill">{profiles.length}</span>
      </div>
      {profiles.length === 0 ? (
        <p className="empty-copy">当前没有记录。</p>
      ) : (
        <div className="profile-list">
          {profiles.map((profile) => (
            <Link key={profile.id} to={`/profiles/${profile.id}`} className="profile-row">
              <div>
                <strong>{profile.fullName}</strong>
                <p>
                  {profile.school || '学校未填'} · {profile.focusDirections.join(' / ') || '方向未填'}
                </p>
              </div>
              <div className="profile-row-meta">
                <span>{profile.currentStatus}</span>
                <small>{formatDateTime(profile.nextFollowUpAt || profile.lastContactAt)}</small>
              </div>
            </Link>
          ))}
        </div>
      )}
    </section>
  )
}

function HomepageFilterCard({
  savedFilter,
  onOpen,
}: {
  savedFilter: SavedFilterRecord
  onOpen: () => void
}) {
  return (
    <button type="button" className="homepage-filter-card" onClick={onOpen}>
      <div>
        <strong>{savedFilter.name}</strong>
        <p>
          {savedFilter.filters.grade || '全部年级'} ·{' '}
          {savedFilter.filters.focusDirection || '全部方向'} ·{' '}
          {savedFilter.filters.status || '全部状态'}
        </p>
      </div>
      <span>{savedFilter.sortBy}</span>
    </button>
  )
}

export function DashboardPage() {
  const { dashboard, applySavedFilter } = useArchiveApp()
  const navigate = useNavigate()

  if (!dashboard) {
    return <div className="page-loading">正在加载首页概览...</div>
  }

  return (
    <div className="page-stack">
      <section className="card page-header workspace-banner">
        <div>
          <p className="eyebrow">今日概览</p>
          <h2>今日工作台</h2>
          <p className="hero-copy">先处理今天和逾期，再回看最近更新和高意向档案。</p>
        </div>
        <div className="hero-stats">
          <div className="hero-stat">
            <strong>{dashboard.stats.todayDueCount}</strong>
            <span>今日待跟进</span>
          </div>
          <div className="hero-stat warning">
            <strong>{dashboard.stats.overdueCount}</strong>
            <span>逾期未处理</span>
          </div>
          <div className="hero-stat success">
            <strong>{dashboard.stats.highIntentionCount}</strong>
            <span>高意向档案</span>
          </div>
        </div>
      </section>

      <section className="grid two-panels">
        <ProfileListSection
          title="今日待跟进"
          description="今天"
          profiles={dashboard.dueToday}
        />
        <ProfileListSection
          title="逾期未处理"
          description="已过期"
          profiles={dashboard.overdue}
        />
      </section>

      <section className="grid two-panels">
        <ProfileListSection
          title="最近新增"
          description="最近 7 天"
          profiles={dashboard.recentCreated}
        />
        <ProfileListSection
          title="最近更新"
          description="最近 7 天"
          profiles={dashboard.recentUpdated}
        />
      </section>

      <section className="grid two-panels">
        <ProfileListSection
          title="高意向但 3 天未联系"
          description="待回访"
          profiles={dashboard.staleHighIntention}
        />

        <section className="card list-card">
          <div className="section-title-row">
            <div>
              <p className="eyebrow">保存分组</p>
              <h3>常用分组</h3>
            </div>
            <Link to="/profiles" className="text-link">
              查看全部
            </Link>
          </div>
          {dashboard.homepageFilters.length === 0 ? (
            <p className="empty-copy">保存常用筛选后，这里会出现快捷入口。</p>
          ) : (
            <div className="homepage-filter-grid">
              {dashboard.homepageFilters.map((savedFilter) => (
                <HomepageFilterCard
                  key={savedFilter.id}
                  savedFilter={savedFilter}
                  onOpen={() => {
                    applySavedFilter(savedFilter)
                    navigate('/profiles')
                  }}
                />
              ))}
            </div>
          )}
        </section>
      </section>
    </div>
  )
}
