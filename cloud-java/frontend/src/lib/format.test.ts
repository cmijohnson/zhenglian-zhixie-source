import { describe, expect, it } from 'vitest'
import { dateTime, toIso } from './format'

describe('format', () => {
  it('renders server UTC timestamps in Asia/Shanghai', () => {
    expect(dateTime('2026-01-02T02:30:00Z')).toContain('01/02')
    expect(dateTime('2026-01-02T02:30:00Z')).toContain('10:30')
  })

  it('converts a browser local input into an ISO timestamp', () => {
    expect(toIso('2026-01-02T10:30')).toMatch(/^2026-01-02T/)
    expect(toIso('')).toBeUndefined()
  })
})

