import { confirm } from '@tauri-apps/plugin-dialog'
import { useEffect, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'

import { useArchiveApp } from '../context/AppContext'
import { formatDateTime, fromLocalDateTimeInput } from '../lib/date'
import { toProfileInput } from '../lib/forms'
import type { InteractionFormState, ProfileFormState } from '../types'
import { InteractionComposer } from './InteractionComposer'
import { ProfileEditorDialog } from './ProfileEditorDialog'

function DetailMeta({
  label,
  value,
}: {
  label: string
  value: string
}) {
  return (
    <div className="detail-meta">
      <span>{label}</span>
      <strong>{value}</strong>
    </div>
  )
}

export function ProfileDetailPage() {
  const params = useParams()
  const studentProfileId = Number(params.profileId)
  const navigate = useNavigate()
  const {
    activeDetail,
    tags,
    detailLoading,
    busyLabel,
    loadProfileDetail,
    clearActiveDetail,
    updateProfile,
    archiveProfile,
    addInteraction,
    exportSingleProfileTimeline,
  } = useArchiveApp()
  const [editOpen, setEditOpen] = useState(false)
  const [visibleInteractions, setVisibleInteractions] = useState(8)

  useEffect(() => {
    if (!Number.isNaN(studentProfileId)) {
      void loadProfileDetail(studentProfileId)
    }
    return () => {
      clearActiveDetail()
    }
  }, [studentProfileId, loadProfileDetail, clearActiveDetail])

  if (detailLoading || !activeDetail || activeDetail.profile.id !== studentProfileId) {
    return <div className="page-loading">正在加载档案详情...</div>
  }

  const detail = activeDetail

  async function handleUpdate(form: ProfileFormState) {
    await updateProfile(detail.profile.id, toProfileInput(form))
    setEditOpen(false)
  }

  async function handleArchive() {
    const approved = await confirm('归档后该档案默认不出现在普通列表中，确定归档吗？', {
      title: '归档档案',
      kind: 'warning',
    })
    if (!approved) return
    await archiveProfile(detail.profile.id)
    navigate('/profiles')
  }

  async function handleInteractionSubmit(form: InteractionFormState) {
    await addInteraction({
      studentProfileId: detail.profile.id,
      contactedAt: fromLocalDateTimeInput(form.contactedAt) ?? new Date().toISOString(),
      channel: form.channel,
      summary: form.summary,
      details: form.details || null,
      outcome: form.outcome || null,
      verificationTodo: form.verificationTodo || null,
      nextFollowUpAt: fromLocalDateTimeInput(form.nextFollowUpAt),
      currentStatus: form.currentStatus || null,
      intentionLevel: form.intentionLevel || null,
      verificationStatus: form.verificationStatus || null,
      tagIds: form.tagIds,
    })
  }

  const profile = detail.profile
  const visibleTimeline = detail.interactions.slice(0, visibleInteractions)

  return (
    <div className="page-stack">
      <section className="card page-header compact workspace-banner">
        <div>
          <p className="eyebrow">档案详情</p>
          <h2>{profile.fullName}</h2>
          <p className="hero-copy">联系方式、状态、提醒和时间线都集中在这里。</p>
        </div>
        <div className="hero-actions">
          <button
            type="button"
            className="secondary-button"
            onClick={() => void exportSingleProfileTimeline(profile.id, profile.fullName)}
          >
            导出本档案
          </button>
          <button type="button" className="secondary-button" onClick={() => setEditOpen(true)}>
            编辑档案
          </button>
          <button type="button" className="danger-button" onClick={() => void handleArchive()}>
            归档
          </button>
        </div>
      </section>

      <section className="card detail-summary-strip">
        <div className="summary-metric">
          <span>当前状态</span>
          <strong>{profile.currentStatus}</strong>
        </div>
        <div className="summary-metric">
          <span>最近联系</span>
          <strong>{formatDateTime(profile.lastContactAt)}</strong>
        </div>
        <div className="summary-metric">
          <span>待处理提醒</span>
          <strong>{detail.reminders.length}</strong>
        </div>
        <div className="summary-metric">
          <span>沟通记录</span>
          <strong>{detail.interactions.length}</strong>
        </div>
      </section>

      <section className="grid detail-layout">
        <div className="card detail-card">
          <div className="section-title-row">
            <div>
              <p className="eyebrow">档案</p>
              <h3>档案信息</h3>
            </div>
            <span className="status-chip">{profile.currentStatus}</span>
          </div>

          <div className="detail-meta-grid">
            <DetailMeta label="学校" value={profile.school || '未填写'} />
            <DetailMeta label="专业" value={profile.major || '未填写'} />
            <DetailMeta label="年级" value={profile.grade || '未填写'} />
            <DetailMeta label="城市" value={profile.city || '未填写'} />
            <DetailMeta label="身份类型" value={profile.identityType} />
            <DetailMeta label="关注方向" value={profile.focusDirections.join(' / ') || '未填写'} />
            <DetailMeta label="报名意向" value={profile.intentionLevel} />
            <DetailMeta label="核实状态" value={profile.verificationStatus} />
            <DetailMeta label="最近联系" value={formatDateTime(profile.lastContactAt)} />
            <DetailMeta label="下次跟进" value={formatDateTime(profile.nextFollowUpAt)} />
          </div>

          <div className="note-block">
            <strong>需求摘要</strong>
            <p>{profile.demandSummary || '暂无当前需求摘要。'}</p>
          </div>

          <div className="note-block">
            <strong>内部备注</strong>
            <p>{profile.ownerNotes || '暂无备注。'}</p>
          </div>

          <div className="tag-pill-wrap large">
            {profile.tags.length === 0 ? (
              <span className="muted-inline">暂无标签</span>
            ) : (
              profile.tags.map((tag) => (
                <span key={tag.id} className="tag-pill" style={{ borderColor: tag.color }}>
                  {tag.name}
                </span>
              ))
            )}
          </div>
        </div>

        <div className="stack-column">
          <InteractionComposer
            key={profile.id}
            tags={tags}
            defaultStatus={profile.currentStatus}
            busy={busyLabel === '正在记录沟通'}
            onSubmit={handleInteractionSubmit}
          />

          <section className="card detail-card">
            <div className="section-title-row">
              <div>
                <p className="eyebrow">提醒</p>
                <h3>待处理提醒</h3>
              </div>
            </div>
            {detail.reminders.length === 0 ? (
              <p className="empty-copy">暂无未处理提醒。</p>
            ) : (
              <div className="timeline-list">
                {detail.reminders.map((reminder) => (
                  <div key={reminder.id} className="timeline-item">
                    <div className="timeline-head">
                      <strong>{reminder.title}</strong>
                      <span className="timeline-badge">{reminder.status}</span>
                    </div>
                    <p>{formatDateTime(reminder.remindAt)}</p>
                  </div>
                ))}
              </div>
            )}
          </section>
        </div>
      </section>

      <section className="card detail-card">
        <div className="section-title-row">
          <div>
            <p className="eyebrow">时间线</p>
            <h3>沟通时间线</h3>
          </div>
          <span className="count-pill">{detail.interactions.length}</span>
        </div>

        {detail.interactions.length === 0 ? (
          <p className="empty-copy">还没有沟通记录。</p>
        ) : (
          <>
            <div className="timeline-list">
              {visibleTimeline.map((interaction) => (
                <article key={interaction.id} className="timeline-item">
                  <div className="timeline-head">
                    <strong>{interaction.summary}</strong>
                    <span className="timeline-badge">{interaction.channel}</span>
                  </div>
                  <p className="timeline-date">{formatDateTime(interaction.contactedAt)}</p>
                  <p>{interaction.details || '无详细备注。'}</p>
                  <div className="timeline-foot">
                    <span>结果：{interaction.outcome || '未填写'}</span>
                    <span>待核实：{interaction.verificationTodo || '无'}</span>
                    <span>下次跟进：{formatDateTime(interaction.nextFollowUpAt)}</span>
                  </div>
                </article>
              ))}
            </div>
            {visibleInteractions < detail.interactions.length ? (
              <button
                type="button"
                className="secondary-button"
                onClick={() => setVisibleInteractions((count) => count + 8)}
              >
                加载更多记录
              </button>
            ) : null}
          </>
        )}
      </section>

      <ProfileEditorDialog
        key={`${profile.id}-${editOpen ? 'open' : 'closed'}`}
        open={editOpen}
        title={`编辑 ${profile.fullName} 的档案`}
        profile={profile}
        tags={tags}
        busy={busyLabel === '正在保存档案'}
        onClose={() => setEditOpen(false)}
        onSubmit={handleUpdate}
      />
    </div>
  )
}
