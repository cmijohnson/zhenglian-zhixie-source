import { HashRouter, Route, Routes } from 'react-router-dom'

import { AppShell } from './components/AppShell'
import { DashboardPage } from './components/DashboardPage'
import { ProfileDetailPage } from './components/ProfileDetailPage'
import { ProfilesPage } from './components/ProfilesPage'
import { SettingsPage } from './components/SettingsPage'
import { ArchiveAppProvider, useArchiveApp } from './context/AppContext'
import './App.css'

function RoutedApp() {
  const { bootstrapping } = useArchiveApp()

  if (bootstrapping) {
    return (
      <div className="boot-screen">
        <div className="boot-card">
          <p className="eyebrow">英格学生档案工具</p>
          <h1>正在读取本地数据</h1>
          <p>档案、标签和提醒设置正在加载。</p>
        </div>
      </div>
    )
  }

  return (
    <HashRouter>
      <Routes>
        <Route element={<AppShell />}>
          <Route path="/" element={<DashboardPage />} />
          <Route path="/profiles" element={<ProfilesPage />} />
          <Route path="/profiles/:profileId" element={<ProfileDetailPage />} />
          <Route path="/settings" element={<SettingsPage />} />
        </Route>
      </Routes>
    </HashRouter>
  )
}

export default function App() {
  return (
    <ArchiveAppProvider>
      <RoutedApp />
    </ArchiveAppProvider>
  )
}
