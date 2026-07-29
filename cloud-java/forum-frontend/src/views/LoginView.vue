<script setup lang="ts">
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { login } from '../lib/api'

const route = useRoute()
const router = useRouter()
const username = ref('')
const password = ref('')
const error = ref('')
const busy = ref(false)

async function submit() {
  error.value = ''
  busy.value = true
  try {
    await login(username.value, password.value)
    await router.push(String(route.query.redirect ?? '/'))
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '登录失败'
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <div class="login-page">
    <section class="login-story">
      <RouterLink to="/" class="brand">
        <span class="brand-mark">英</span><span><strong>英格灵感站</strong><small>YINGGE NOTES</small></span>
      </RouterLink>
      <div><p class="eyebrow">TEAM KNOWLEDGE, ALWAYS FLOWING</p><h1>让每一次经验，<br>成为团队的下一步。</h1><p>使用英格客户云员工账号登录，无需注册新的账户。</p></div>
      <small>公开内容无需登录即可浏览</small>
    </section>
    <section class="login-panel">
      <form class="login-card" @submit.prevent="submit">
        <div><p class="eyebrow">EMPLOYEE SIGN IN</p><h2>欢迎回来</h2><p class="muted">登录后可发布、评论、点赞和参与管理。</p></div>
        <label>员工账号<input v-model.trim="username" autocomplete="username" required placeholder="请输入客户云账号"></label>
        <label>密码<input v-model="password" type="password" autocomplete="current-password" required placeholder="请输入密码"></label>
        <p v-if="error" class="form-error">{{ error }}</p>
        <button class="primary-button wide" :disabled="busy" type="submit">{{ busy ? '登录中…' : '登录英格灵感站' }}</button>
        <RouterLink to="/" class="back-home">暂不登录，返回浏览公开内容</RouterLink>
      </form>
    </section>
  </div>
</template>
