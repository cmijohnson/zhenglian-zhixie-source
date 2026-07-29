import { createRouter, createWebHistory } from 'vue-router'
import HomeView from './views/HomeView.vue'
import PostView from './views/PostView.vue'
import LoginView from './views/LoginView.vue'
import ComposeView from './views/ComposeView.vue'
import ManageView from './views/ManageView.vue'
import NotFoundView from './views/NotFoundView.vue'
import { auth, ensureAuth } from './lib/api'

const router = createRouter({
  history: createWebHistory(),
  scrollBehavior: () => ({ top: 0 }),
  routes: [
    { path: '/', component: HomeView },
    { path: '/posts/:id', component: PostView },
    { path: '/login', component: LoginView },
    { path: '/compose', component: ComposeView, meta: { write: true } },
    { path: '/manage', component: ManageView, meta: { moderate: true } },
    { path: '/:pathMatch(.*)*', component: NotFoundView },
  ],
})

router.beforeEach(async (to) => {
  await ensureAuth()
  if (to.path === '/login' && auth.user) return '/'
  if (to.meta.write && !auth.canWrite) return { path: '/login', query: { redirect: to.fullPath } }
  if (to.meta.moderate && !auth.canModerate) return '/'
  return true
})

export default router
