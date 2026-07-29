<script setup lang="ts">
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ApiError } from '../lib/api'
import { useAuthStore } from '../stores/auth'

const auth = useAuthStore()
const route = useRoute()
const router = useRouter()
const username = ref('')
const password = ref('')
const busy = ref(false)
const error = ref('')

async function submit() {
  busy.value = true
  error.value = ''
  try {
    await auth.login(username.value, password.value)
    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : '/dashboard'
    await router.replace(redirect)
  } catch (reason) {
    error.value = reason instanceof ApiError ? reason.message : '登录失败，请稍后重试'
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <main class="login-page">
    <section class="login-story">
      <div class="brand brand-light"><span class="brand-mark">Y</span><strong>英格客户云</strong></div>
      <div>
        <p class="eyebrow light">YINGGE TEAM CLOUD</p>
        <h1>把每一次沟通，<br>变成清晰的下一步。</h1>
        <p>客户档案、跟进提醒与团队协作集中在一处，关键业务规则始终由服务端保护。</p>
      </div>
      <div class="security-note">安全会话 · 权限隔离 · 全程审计</div>
    </section>
    <section class="login-panel">
      <form class="login-card" @submit.prevent="submit">
        <div><p class="eyebrow">欢迎回来</p><h2>登录工作台</h2><p class="muted">使用团队账户登录。</p></div>
        <label>用户名<input v-model.trim="username" autocomplete="username" required autofocus placeholder="请输入用户名"></label>
        <label>密码<input v-model="password" type="password" autocomplete="current-password" required placeholder="请输入密码"></label>
        <p v-if="error" class="form-error">{{ error }}</p>
        <button class="primary-button wide" :disabled="busy">{{ busy ? '正在验证…' : '安全登录' }}</button>
        <p class="auth-switch">还没有账户？<RouterLink to="/register">使用注册码注册</RouterLink></p>
      </form>
    </section>
  </main>
</template>
