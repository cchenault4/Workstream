import { describe, it, expect } from 'vitest'
import { formatDate, formatDateTime } from '../../utils/format'

const ISO = '2025-03-15T14:30:00.000Z'

describe('formatDate', () => {
  it('returns a human-readable date string', () => {
    const result = formatDate(ISO)
    expect(result).toContain('2025')
    expect(result).toMatch(/Mar|March/)
  })

  it('does not include time components', () => {
    const result = formatDate(ISO)
    expect(result).not.toMatch(/\d{1,2}:\d{2}/)
  })
})

describe('formatDateTime', () => {
  it('returns a human-readable date-time string', () => {
    const result = formatDateTime(ISO)
    expect(result).toMatch(/Mar|March/)
  })

  it('includes a time component', () => {
    const result = formatDateTime(ISO)
    expect(result).toMatch(/\d{1,2}:\d{2}/)
  })
})
