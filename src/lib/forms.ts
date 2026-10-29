import { emptyInteractionForm, emptyProfileForm } from '../constants'
import type { InteractionFormState, ProfileFormState, ProfileInput, StudentProfileRecord } from '../types'
import { fromLocalDateTimeInput, toLocalDateTimeInput } from './date'

export function toProfileInput(form: ProfileFormState): ProfileInput {
  return {
    fullName: form.fullName,
    nickname: form.nickname || null,
    phone: form.phone || null,
    wechatId: form.wechatId || null,
    school: form.school || null,
    major: form.major || null,
    grade: form.grade || null,
    city: form.city || null,
    identityType: form.identityType,
    focusDirections: form.focusDirections,
    demandSummary: form.demandSummary || null,
    intentionLevel: form.intentionLevel,
    urgencyLevel: form.urgencyLevel,
    verificationStatus: form.verificationStatus,
    sourceChannel: form.sourceChannel || null,
    currentStatus: form.currentStatus,
    ownerNotes: form.ownerNotes || null,
    nextFollowUpAt: fromLocalDateTimeInput(form.nextFollowUpAt),
    tagIds: form.tagIds,
  }
}

export function profileToForm(profile?: StudentProfileRecord | null): ProfileFormState {
  if (!profile) return emptyProfileForm()
  return {
    fullName: profile.fullName,
    nickname: profile.nickname ?? '',
    phone: profile.phone ?? '',
    wechatId: profile.wechatId ?? '',
    school: profile.school ?? '',
    major: profile.major ?? '',
    grade: profile.grade ?? '',
    city: profile.city ?? '',
    identityType: profile.identityType,
    focusDirections: profile.focusDirections.length > 0 ? profile.focusDirections : ['暂不明确'],
    demandSummary: profile.demandSummary ?? '',
    intentionLevel: profile.intentionLevel,
    urgencyLevel: profile.urgencyLevel,
    verificationStatus: profile.verificationStatus,
    sourceChannel: profile.sourceChannel ?? '',
    currentStatus: profile.currentStatus,
    ownerNotes: profile.ownerNotes ?? '',
    nextFollowUpAt: toLocalDateTimeInput(profile.nextFollowUpAt),
    tagIds: profile.tagIds,
  }
}

export function createInteractionForm(status = '跟进中'): InteractionFormState {
  return {
    ...emptyInteractionForm(status),
    contactedAt: toLocalDateTimeInput(new Date().toISOString()),
  }
}
