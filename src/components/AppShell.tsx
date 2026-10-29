import { useEffect, useState, type FormEvent } from 'react'
import { NavLink, Outlet, useLocation, useNavigate } from 'react-router-dom'

import { useArchiveApp } from '../context/AppContext'

const navItems = [
  { to: '/', label: '首页', description: '今日重点', icon: 'dashboard' as const },
  { to: '/profiles', label: '档案库', description: '筛选与保存分组', icon: 'profiles' as const },
  { to: '/settings', label: '设置', description: '备份、标签、提醒', icon: 'settings' as const },
]

type IconName = 'add' | 'dashboard' | 'profiles' | 'search' | 'settings'

function ShellIcon({ name }: { name: IconName }) {
  const commonProps = {
    viewBox: '0 0 24 24',
    fill: 'none',
    stroke: 'currentColor',
    strokeWidth: '1.8',
    strokeLinecap: 'round' as const,
    strokeLinejoin: 'round' as const,
    'aria-hidden': true,
  }

  switch (name) {
    case 'dashboard':
      return (
        <svg {...commonProps}>
          <rect x="3" y="3" width="8" height="8" rx="2" />
          <rect x="13" y="3" width="8" height="5" rx="2" />
          <rect x="13" y="10" width="8" height="11" rx="2" />
          <rect x="3" y="13" width="8" height="8" rx="2" />
        </svg>
      )
    case 'profiles':
      return (
        <svg {...commonProps}>
          <path d="M16 21v-2a4 4 0 0 0-4-4H7a4 4 0 0 0-4 4v2" />
          <circle cx="9.5" cy="7" r="3.5" />
          <path d="M20 21v-2a4 4 0 0 0-3-3.87" />
          <path d="M15.5 3.13a3.5 3.5 0 0 1 0 6.74" />
        </svg>
      )
    case 'settings':
      return (
        <svg {...commonProps}>
          <circle cx="12" cy="12" r="3.2" />
          <path d="M19.4 15a1.7 1.7 0 0 0 .34 1.87l.06.06a2 2 0 1 1-2.83 2.83l-.06-.06a1.7 1.7 0 0 0-1.87-.34 1.7 1.7 0 0 0-1.04 1.56V21a2 2 0 1 1-4 0v-.09a1.7 1.7 0 0 0-1.11-1.57 1.7 1.7 0 0 0-1.87.34l-.06.06A2 2 0 1 1 4.13 17l.06-.06A1.7 1.7 0 0 0 4.53 15a1.7 1.7 0 0 0-1.56-1.04H2.9a2 2 0 1 1 0-4H3a1.7 1.7 0 0 0 1.57-1.11 1.7 1.7 0 0 0-.34-1.87L4.17 6.9A2 2 0 0 1 7 4.07l.06.06a1.7 1.7 0 0 0 1.87.34H9a1.7 1.7 0 0 0 1-1.56V2.9a2 2 0 1 1 4 0V3a1.7 1.7 0 0 0 1.11 1.57 1.7 1.7 0 0 0 1.87-.34l.06-.06A2 2 0 1 1 19.87 7l-.06.06a1.7 1.7 0 0 0-.34 1.87V9c.24.64.85 1.05 1.53 1.03h.1a2 2 0 1 1 0 4H21a1.7 1.7 0 0 0-1.6.97Z" />
        </svg>
      )
    case 'add':
      return (
        <svg {...commonProps}>
          <path d="M12 5v14" />
          <path d="M5 12h14" />
        </svg>
      )
    case 'search':
      return (
        <svg {...commonProps}>
          <circle cx="11" cy="11" r="6.5" />
          <path d="m20 20-3.5-3.5" />
        </svg>
      )
  }
}

export function AppShell() {
  const { dashboard, busyLabel, filters, setFilters } = useArchiveApp()
  const location = useLocation()
  const navigate = useNavigate()
  const [searchText, setSearchText] = useState(() => filters.searchText ?? '')

  // 当外部筛选条件被清空时同步本地输入框（例如点击"重置筛选"后）。
  // 这是 store → local state 的同步，filters.searchText 是真实来源。
  /* eslint-disable react-hooks/set-state-in-effect */
  useEffect(() => {
    setSearchText(filters.searchText ?? '')
  }, [filters.searchText])
  /* eslint-enable react-hooks/set-state-in-effect */

  const currentTitle = (() => {
    if (location.pathname.startsWith('/profiles/')) return '档案详情'
    if (location.pathname.startsWith('/profiles')) return '档案库'
    if (location.pathname.startsWith('/settings')) return '设置'
    return '首页'
  })()

  function handleSearch(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setFilters({ searchText })
    navigate('/profiles')
  }

  return (
    <div className="app-shell">
      <div className="app-window">
        <aside className="sidebar">
          <div className="sidebar-top">
            <div className="window-controls" aria-hidden="true">
              <span className="window-dot red" />
              <span className="window-dot yellow" />
              <span className="window-dot green" />
            </div>

            <div className="brand-card">
              <div className="brand-mark">
                <span>英</span>
              </div>
              <div className="brand-copy">
                <p className="eyebrow">英格科技</p>
                <h1>学生档案工具</h1>
                <p>本地优先 · 单用户工作台</p>
              </div>
            </div>
          </div>

          <nav className="sidebar-nav">
            {navItems.map((item) => (
              <NavLink
                key={item.to}
                to={item.to}
                end={item.to === '/'}
                className={({ isActive }) => (isActive ? 'nav-item active' : 'nav-item')}
              >
                <span className="nav-icon">
                  <ShellIcon name={item.icon} />
                </span>
                <span className="nav-copy">
                  <strong>{item.label}</strong>
                  <span>{item.description}</span>
                </span>
              </NavLink>
            ))}
          </nav>

          <div className="sidebar-footer">
            <div className="sidebar-card status-card">
              <div className="section-title-row">
                <span>状态</span>
                <small>{busyLabel ? '处理中' : '本地模式'}</small>
              </div>
              <p className="status-line">
                {busyLabel ?? '数据只保存在当前 Mac，提醒、备份和标签都在本地维护。'}
              </p>
            </div>

            {dashboard ? (
              <div className="sidebar-card stat-stack">
                <div className="section-title-row">
                  <span>概览</span>
                  <small>实时统计</small>
                </div>
                <div className="mini-stat">
                  <strong>{dashboard.stats.totalActive}</strong>
                  <span>活跃档案</span>
                </div>
                <div className="mini-stat">
                  <strong>{dashboard.stats.todayDueCount}</strong>
                  <span>今日待跟进</span>
                </div>
                <div className="mini-stat">
                  <strong>{dashboard.stats.overdueCount}</strong>
                  <span>逾期未处理</span>
                </div>
                <div className="mini-stat">
                  <strong>{dashboard.stats.highIntentionCount}</strong>
                  <span>高意向档案</span>
                </div>
              </div>
            ) : null}
          </div>
        </aside>

        <main className="main-panel">
          <header className="main-toolbar">
            <div className="toolbar-page">
              <div>
                <p className="toolbar-caption">英格学生档案管理</p>
                <h2 className="toolbar-title">{currentTitle}</h2>
              </div>
              <div className="toolbar-status">
                <span className={busyLabel ? 'traffic-dot warning' : 'traffic-dot success'} />
                <span>{busyLabel ?? '本地单用户模式'}</span>
              </div>
            </div>

            <div className="toolbar-actions">
              <button
                type="button"
                className="toolbar-icon-button"
                onClick={() => navigate('/profiles')}
                aria-label="打开档案库"
              >
                <ShellIcon name="add" />
              </button>
              <button
                type="button"
                className="toolbar-icon-button"
                onClick={() => navigate('/settings')}
                aria-label="打开设置"
              >
                <ShellIcon name="settings" />
              </button>
              <form className="toolbar-search" onSubmit={handleSearch}>
                <span className="toolbar-search-icon">
                  <ShellIcon name="search" />
                </span>
                <input
                  value={searchText}
                  onChange={(event) => setSearchText(event.target.value)}
                  placeholder="搜索姓名、学校、微信或标签"
                />
              </form>
            </div>
          </header>
          <Outlet />
        </main>
      </div>
    </div>
  )
}
