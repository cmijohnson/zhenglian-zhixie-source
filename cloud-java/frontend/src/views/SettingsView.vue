<script setup lang="ts">
import { reactive, ref } from 'vue'
import { ApiError, api, jsonBody } from '../lib/api'
import { useAuthStore } from '../stores/auth'
const auth = useAuthStore(); const success = ref(''); const error = ref('')
const form = reactive({ currentPassword: '', newPassword: '', confirm: '' })
async function change() { error.value = ''; success.value = ''; if (form.newPassword !== form.confirm) { error.value = '两次输入的新密码不一致'; return } try { await api('/api/v1/auth/password', { method: 'POST', body: jsonBody(form) }); Object.assign(form, { currentPassword: '', newPassword: '', confirm: '' }); success.value = '密码已更新' } catch (reason) { error.value = reason instanceof ApiError ? reason.message : '修改失败' } }
</script>

<template><div class="page narrow-page"><header class="page-header"><div><p class="eyebrow">ACCOUNT</p><h1>账户设置</h1><p class="muted">登录身份与安全设置。</p></div></header><section class="panel account-summary"><span class="avatar large">{{ auth.user?.displayName.slice(0, 1) }}</span><div><h2>{{ auth.user?.displayName }}</h2><p>{{ auth.user?.username }} · {{ auth.user?.roles.join(' / ') }}</p></div></section><form class="panel settings-form" @submit.prevent="change"><div><p class="eyebrow">SECURITY</p><h2>修改密码</h2><p>系统不设置最低长度或复杂度要求，密码不能为空。</p></div><label>当前密码<input v-model="form.currentPassword" type="password" autocomplete="current-password" required></label><label>新密码<input v-model="form.newPassword" type="password" autocomplete="new-password" maxlength="200" required></label><label>确认新密码<input v-model="form.confirm" type="password" autocomplete="new-password" maxlength="200" required></label><p v-if="error" class="form-error">{{ error }}</p><p v-if="success" class="form-success">{{ success }}</p><button class="primary-button">更新密码</button></form></div></template>
