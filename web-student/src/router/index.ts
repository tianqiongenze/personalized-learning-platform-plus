import { createRouter, createWebHistory } from 'vue-router'

// 六大一级模块(多端信息架构对齐 docs/MULTI_CLIENT_DESIGN_SPEC.md §1.3)
const routes = [
  { path: '/', name: 'home', component: () => import('../pages/Home.vue'), meta: { title: '学情首页' } },
  { path: '/courses', name: 'courses', component: () => import('../pages/Courses.vue'), meta: { title: '课程学习' } },
  { path: '/path', name: 'path', component: () => import('../pages/LearningPath.vue'), meta: { title: '学习路径' } },
  { path: '/review', name: 'review', component: () => import('../pages/Review.vue'), meta: { title: '复习卡片' } },
  { path: '/resources', name: 'resources', component: () => import('../pages/Resources.vue'), meta: { title: '资源中心' } },
  { path: '/profile', name: 'profile', component: () => import('../pages/Profile.vue'), meta: { title: '我的画像' } },
]

const router = createRouter({ history: createWebHistory(), routes })
router.afterEach(to => { document.title = `${to.meta.title} · 千人千面` })
export default router
