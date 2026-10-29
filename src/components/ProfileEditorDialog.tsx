import type { FormEvent } from 'react'
import { useState } from 'react'

import {
  focusDirectionOptions,
  identityTypes,
  intentionLevels,
  profileStatuses,
  urgencyLevels,
  verificationStatuses,
} from '../constants'
import { profileToForm } from '../lib/forms'
import type { ProfileFormState, StudentProfileRecord, TagRecord } from '../types'

type ProfileEditorDialogProps = {
  open: boolean
  title: string
  tags: TagRecord[]
  profile?: StudentProfileRecord | null
  busy?: boolean
  onClose: () => void
  onSubmit: (form: ProfileFormState) => Promise<void>
}

function toggleValue(values: string[], value: string) {
  return values.includes(value)
    ? values.filter((item) => item !== value)
    : [...values, value]
}

function toggleTag(values: number[], value: number) {
  return values.includes(value)
    ? values.filter((item) => item !== value)
    : [...values, value]
}

export function ProfileEditorDialog({
  open,
  title,
  tags,
  profile,
  busy,
  onClose,
  onSubmit,
}: ProfileEditorDialogProps) {
  const [form, setForm] = useState(() => profileToForm(profile))

  if (!open) return null

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    await onSubmit(form)
  }

  return (
    <div className="dialog-backdrop" onClick={onClose}>
      <div className="dialog-panel profile-dialog" onClick={(event) => event.stopPropagation()}>
        <div className="dialog-header">
          <div>
            <p className="eyebrow">档案</p>
            <h2>{title}</h2>
          </div>
          <button type="button" className="ghost-button" onClick={onClose}>
            取消
          </button>
        </div>

        <form className="stack-form" onSubmit={handleSubmit}>
          <section className="form-grid two-column">
            <label>
              <span>姓名 *</span>
              <input
                value={form.fullName}
                onChange={(event) => setForm({ ...form, fullName: event.target.value })}
                placeholder="例如：张同学"
                required
              />
            </label>
            <label>
              <span>昵称</span>
              <input
                value={form.nickname}
                onChange={(event) => setForm({ ...form, nickname: event.target.value })}
                placeholder="可填微信备注名"
              />
            </label>
            <label>
              <span>手机号</span>
              <input
                value={form.phone}
                onChange={(event) => setForm({ ...form, phone: event.target.value })}
                placeholder="手机号或留空"
              />
            </label>
            <label>
              <span>微信号</span>
              <input
                value={form.wechatId}
                onChange={(event) => setForm({ ...form, wechatId: event.target.value })}
                placeholder="微信号或留空"
              />
            </label>
            <label>
              <span>学校</span>
              <input
                value={form.school}
                onChange={(event) => setForm({ ...form, school: event.target.value })}
              />
            </label>
            <label>
              <span>专业</span>
              <input
                value={form.major}
                onChange={(event) => setForm({ ...form, major: event.target.value })}
              />
            </label>
            <label>
              <span>年级 / 届别</span>
              <input
                value={form.grade}
                onChange={(event) => setForm({ ...form, grade: event.target.value })}
                placeholder="例如 2025"
              />
            </label>
            <label>
              <span>城市</span>
              <input
                value={form.city}
                onChange={(event) => setForm({ ...form, city: event.target.value })}
              />
            </label>
            <label>
              <span>身份类型</span>
              <select
                value={form.identityType}
                onChange={(event) => setForm({ ...form, identityType: event.target.value })}
              >
                {identityTypes.map((option) => (
                  <option key={option} value={option}>
                    {option}
                  </option>
                ))}
              </select>
            </label>
            <label>
              <span>来源渠道</span>
              <input
                value={form.sourceChannel}
                onChange={(event) => setForm({ ...form, sourceChannel: event.target.value })}
                placeholder="活动 / 微信 / 转介绍"
              />
            </label>
            <label>
              <span>报名意向</span>
              <select
                value={form.intentionLevel}
                onChange={(event) => setForm({ ...form, intentionLevel: event.target.value })}
              >
                {intentionLevels.map((option) => (
                  <option key={option} value={option}>
                    {option}
                  </option>
                ))}
              </select>
            </label>
            <label>
              <span>紧急程度</span>
              <select
                value={form.urgencyLevel}
                onChange={(event) => setForm({ ...form, urgencyLevel: event.target.value })}
              >
                {urgencyLevels.map((option) => (
                  <option key={option} value={option}>
                    {option}
                  </option>
                ))}
              </select>
            </label>
            <label>
              <span>核实状态</span>
              <select
                value={form.verificationStatus}
                onChange={(event) => setForm({ ...form, verificationStatus: event.target.value })}
              >
                {verificationStatuses.map((option) => (
                  <option key={option} value={option}>
                    {option}
                  </option>
                ))}
              </select>
            </label>
            <label>
              <span>当前状态</span>
              <select
                value={form.currentStatus}
                onChange={(event) => setForm({ ...form, currentStatus: event.target.value })}
              >
                {profileStatuses.map((option) => (
                  <option key={option} value={option}>
                    {option}
                  </option>
                ))}
              </select>
            </label>
            <label>
              <span>下次跟进</span>
              <input
                type="datetime-local"
                value={form.nextFollowUpAt}
                onChange={(event) => setForm({ ...form, nextFollowUpAt: event.target.value })}
              />
            </label>
          </section>

          <label className="full-width">
            <span>当前需求</span>
            <textarea
              rows={2}
              value={form.demandSummary}
              onChange={(event) => setForm({ ...form, demandSummary: event.target.value })}
              placeholder="一句话写清这个同学当前最关心的问题"
            />
          </label>

          <div className="chip-section">
            <div className="section-title-row">
              <span>关注方向</span>
              <small>可多选</small>
            </div>
            <div className="chip-wrap">
              {focusDirectionOptions.map((option) => {
                const active = form.focusDirections.includes(option)
                return (
                  <button
                    key={option}
                    type="button"
                    className={active ? 'chip active' : 'chip'}
                    onClick={() =>
                      setForm((current) => ({
                        ...current,
                        focusDirections: toggleValue(current.focusDirections, option),
                      }))
                    }
                  >
                    {option}
                  </button>
                )
              })}
            </div>
          </div>

          <div className="chip-section">
            <div className="section-title-row">
              <span>标签</span>
              <small>按需补充</small>
            </div>
            <div className="tag-grid">
              {tags.map((tag) => {
                const active = form.tagIds.includes(tag.id)
                return (
                  <button
                    key={tag.id}
                    type="button"
                    className={active ? 'tag-tile active' : 'tag-tile'}
                    style={{ borderColor: tag.color, backgroundColor: active ? `${tag.color}1f` : undefined }}
                    onClick={() =>
                      setForm((current) => ({
                        ...current,
                        tagIds: toggleTag(current.tagIds, tag.id),
                      }))
                    }
                  >
                    <span>{tag.name}</span>
                    <small>{tag.category}</small>
                  </button>
                )
              })}
            </div>
          </div>

          <label className="full-width">
            <span>备注</span>
            <textarea
              rows={3}
              value={form.ownerNotes}
              onChange={(event) => setForm({ ...form, ownerNotes: event.target.value })}
              placeholder="记录补充背景、沟通风格、需要注意的点"
            />
          </label>

          <div className="dialog-footer">
            <p className="helper-text">姓名必填，手机号和微信号至少填写一个。</p>
            <button type="submit" className="primary-button" disabled={busy}>
              {busy ? '保存中...' : '保存档案'}
            </button>
          </div>
        </form>
      </div>
    </div>
  )
}
