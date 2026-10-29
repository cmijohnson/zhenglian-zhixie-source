import type { FormEvent } from 'react'
import { useState } from 'react'

import {
  interactionChannels,
  intentionLevels,
  profileStatuses,
  verificationStatuses,
} from '../constants'
import { createInteractionForm } from '../lib/forms'
import type { InteractionFormState, TagRecord } from '../types'

type InteractionComposerProps = {
  tags: TagRecord[]
  defaultStatus?: string
  busy?: boolean
  onSubmit: (form: InteractionFormState) => Promise<void>
}

function toggleTag(values: number[], value: number) {
  return values.includes(value)
    ? values.filter((item) => item !== value)
    : [...values, value]
}

export function InteractionComposer({
  tags,
  defaultStatus = '跟进中',
  busy,
  onSubmit,
}: InteractionComposerProps) {
  const [form, setForm] = useState(() => createInteractionForm(defaultStatus))

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    await onSubmit(form)
    setForm(createInteractionForm(defaultStatus))
  }

  return (
    <form className="card stack-form" onSubmit={handleSubmit}>
      <div className="section-title-row">
        <div>
          <p className="eyebrow">沟通记录</p>
          <h3>记录本次沟通</h3>
        </div>
        <button type="submit" className="primary-button" disabled={busy}>
          {busy ? '记录中...' : '保存记录'}
        </button>
      </div>

      <section className="form-grid two-column">
        <label>
          <span>沟通时间</span>
          <input
            type="datetime-local"
            value={form.contactedAt}
            onChange={(event) => setForm({ ...form, contactedAt: event.target.value })}
            required
          />
        </label>
        <label>
          <span>沟通方式</span>
          <select
            value={form.channel}
            onChange={(event) => setForm({ ...form, channel: event.target.value })}
          >
            {interactionChannels.map((option) => (
              <option key={option} value={option}>
                {option}
              </option>
            ))}
          </select>
        </label>
        <label className="full-width">
          <span>本次摘要</span>
          <input
            value={form.summary}
            onChange={(event) => setForm({ ...form, summary: event.target.value })}
            placeholder="一句话概括本次沟通重点"
            required
          />
        </label>
        <label className="full-width">
          <span>详细备注</span>
          <textarea
            rows={3}
            value={form.details}
            onChange={(event) => setForm({ ...form, details: event.target.value })}
            placeholder="记录问题、回答、顾虑、偏好等"
          />
        </label>
        <label>
          <span>结果判断</span>
          <input
            value={form.outcome}
            onChange={(event) => setForm({ ...form, outcome: event.target.value })}
            placeholder="如：继续了解 / 进入决策"
          />
        </label>
        <label>
          <span>待核实信息</span>
          <input
            value={form.verificationTodo}
            onChange={(event) => setForm({ ...form, verificationTodo: event.target.value })}
            placeholder="还有哪些信息没确认"
          />
        </label>
        <label>
          <span>下次跟进</span>
          <input
            type="datetime-local"
            value={form.nextFollowUpAt}
            onChange={(event) => setForm({ ...form, nextFollowUpAt: event.target.value })}
          />
        </label>
        <label>
          <span>更新状态</span>
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
          <span>更新意向</span>
          <select
            value={form.intentionLevel}
            onChange={(event) => setForm({ ...form, intentionLevel: event.target.value })}
          >
            <option value="">不变</option>
            {intentionLevels.map((option) => (
              <option key={option} value={option}>
                {option}
              </option>
            ))}
          </select>
        </label>
        <label>
          <span>更新核实状态</span>
          <select
            value={form.verificationStatus}
            onChange={(event) => setForm({ ...form, verificationStatus: event.target.value })}
          >
            <option value="">不变</option>
            {verificationStatuses.map((option) => (
              <option key={option} value={option}>
                {option}
              </option>
            ))}
          </select>
        </label>
      </section>

      <div className="chip-section">
        <div className="section-title-row">
          <span>同步标签</span>
          <small>保存后会覆盖当前档案标签</small>
        </div>
        <div className="tag-grid compact">
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
    </form>
  )
}
