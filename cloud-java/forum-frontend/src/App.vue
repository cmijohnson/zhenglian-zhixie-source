<script setup lang="ts">
import { useRouter } from 'vue-router'
import { auth, logout } from './lib/api'

const router = useRouter()

async function signOut() {
  await logout()
  await router.push('/')
}
</script>

<template>
  <div class="app-shell">
    <header class="mobile-header">
      <RouterLink to="/" class="brand compact">
        <span class="brand-mark">英</span>
        <span><strong>英格灵感站</strong><small>YINGGE NOTES</small></span>
      </RouterLink>
      <RouterLink v-if="auth.canWrite" to="/compose" class="mobile-compose">＋ 发布</RouterLink>
    </header>

    <aside class="sidebar">
      <RouterLink to="/" class="brand">
        <span class="brand-mark">英</span>
        <span><strong>英格灵感站</strong><small>YINGGE NOTES</small></span>
      </RouterLink>

      <nav class="main-nav">
        <p class="nav-caption">发现</p>
        <RouterLink to="/"><span>⌂</span>首页动态</RouterLink>
        <RouterLink v-if="auth.canWrite" to="/compose"><span>＋</span>发布灵感</RouterLink>
        <RouterLink v-if="auth.canModerate" to="/manage"><span>◇</span>论坛管理</RouterLink>
      </nav>

      <div class="sidebar-note">
        <span>✦</span>
        <strong>让经验持续流动</strong>
        <p>记录一次，帮助更多同事。</p>
      </div>

      <footer class="sidebar-footer">
        <template v-if="auth.user">
          <span class="avatar">{{ auth.user.displayName.slice(0, 1) }}</span>
          <span class="user-copy"><strong>{{ auth.user.displayName }}</strong><small>英格团队成员</small></span>
          <button class="text-button" type="button" @click="signOut">退出</button>
        </template>
        <RouterLink v-else to="/login" class="login-link">登录员工账号 <span>→</span></RouterLink>
      </footer>
    </aside>

    <main class="main-area">
      <RouterView />
    </main>

    <nav class="bottom-nav">
      <RouterLink to="/"><span>⌂</span><small>首页</small></RouterLink>
      <RouterLink v-if="auth.canWrite" to="/compose"><span>＋</span><small>发布</small></RouterLink>
      <RouterLink v-if="auth.canModerate" to="/manage"><span>◇</span><small>管理</small></RouterLink>
      <RouterLink v-if="!auth.user" to="/login"><span>◎</span><small>登录</small></RouterLink>
      <button v-else type="button" @click="signOut"><span>{{ auth.user.displayName.slice(0, 1) }}</span><small>退出</small></button>
    </nav>
  </div>
</template>
