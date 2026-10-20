<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getStoredUser, clearAuth } from '../utils/auth'

const route = useRoute()
const router = useRouter()

// 详情页在菜单里应该高亮它所属的列表页，靠 meta.parent 指回去
const activeMenu = computed(() => route.meta.parent || route.path)
const title = computed(() => route.meta.title || 'Relay')
const subtitle = computed(() => route.meta.subtitle || '')

// 登录时存下来的展示信息。没登录的话守卫不会放我们进这个布局，这里只做兜底
const user = getStoredUser()
const userDisplay = user?.displayName || user?.username || '未登录'
const roleLabel = user?.role === 'ADMIN' ? '管理员' : '普通用户'

function onCommand(command) {
  if (command === 'logout') {
    clearAuth()
    ElMessage.success('已退出登录')
    router.push('/login')
  }
}
</script>

<template>
  <el-container class="app">
    <el-aside width="224px" class="side">
      <div class="brand">
        <div class="logo">R</div>
        <div class="brand-text">
          <div class="name">Relay</div>
          <div class="desc">订单同步中台</div>
        </div>
      </div>

      <el-menu :default-active="activeMenu" router class="menu">
        <el-menu-item index="/orders">
          <el-icon><Tickets /></el-icon>
          <span>订单</span>
        </el-menu-item>
        <el-menu-item index="/products">
          <el-icon><Goods /></el-icon>
          <span>商品</span>
        </el-menu-item>
        <el-menu-item index="/mappings">
          <el-icon><Connection /></el-icon>
          <span>映射</span>
        </el-menu-item>
      </el-menu>

      <div class="side-foot">
        <div class="dot" />
        <span>Shopify · 模拟供应商</span>
      </div>
    </el-aside>

    <el-container class="right">
      <el-header class="head">
        <div class="head-text">
          <div class="title">{{ title }}</div>
          <div v-if="subtitle" class="subtitle">{{ subtitle }}</div>
        </div>

        <!-- 右上角：当前用户 + 退出。角色只显示，不参与前端判断 -->
        <el-dropdown @command="onCommand">
          <span class="user-trigger">
            <el-avatar :size="28" class="avatar">{{ userDisplay.slice(0, 1) }}</el-avatar>
            <span class="user-name">{{ userDisplay }}</span>
            <el-icon class="caret"><ArrowDown /></el-icon>
          </span>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item disabled>{{ roleLabel }}</el-dropdown-item>
              <el-dropdown-item divided command="logout">退出登录</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </el-header>

      <el-main class="main">
        <router-view v-slot="{ Component }">
          <transition name="fade" mode="out-in">
            <component :is="Component" />
          </transition>
        </router-view>
      </el-main>
    </el-container>
  </el-container>
</template>

<style scoped>
.app {
  height: 100vh;
}

/* ---------- 左侧 ---------- */
.side {
  background: #1f2d3d;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.brand {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 18px 18px 16px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.07);
}

.logo {
  width: 34px;
  height: 34px;
  border-radius: 9px;
  background: linear-gradient(135deg, #409eff, #2b6cb0);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 700;
  font-size: 18px;
  flex-shrink: 0;
}

.brand-text .name {
  color: #fff;
  font-size: 15.5px;
  font-weight: 600;
  letter-spacing: 0.4px;
  line-height: 1.2;
}

.brand-text .desc {
  color: rgba(255, 255, 255, 0.42);
  font-size: 11px;
  margin-top: 3px;
}

.menu {
  border-right: none;
  background: transparent;
  padding: 10px 10px 0;
  flex: 1;
}

.menu :deep(.el-menu-item) {
  color: rgba(255, 255, 255, 0.68);
  border-radius: 7px;
  margin-bottom: 4px;
  height: 42px;
  line-height: 42px;
}

.menu :deep(.el-menu-item:hover) {
  background: rgba(255, 255, 255, 0.08);
  color: #fff;
}

.menu :deep(.el-menu-item.is-active) {
  background: #409eff;
  color: #fff;
  font-weight: 500;
}

.side-foot {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 14px 20px;
  border-top: 1px solid rgba(255, 255, 255, 0.07);
  color: rgba(255, 255, 255, 0.35);
  font-size: 11px;
}

.dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #67c23a;
  flex-shrink: 0;
}

/* ---------- 右侧 ---------- */
.right {
  overflow: hidden;
}

.head {
  background: #fff;
  border-bottom: 1px solid #ebeef5;
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 64px;
  flex-shrink: 0;
}

.user-trigger {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  padding: 4px 8px;
  border-radius: 8px;
  outline: none;
  transition: background 0.15s ease;
}

.user-trigger:hover {
  background: #f5f7fa;
}

.avatar {
  background: #409eff;
  font-size: 13px;
  flex-shrink: 0;
}

.user-name {
  font-size: 13.5px;
  color: #303133;
  max-width: 120px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.caret {
  color: #909399;
  font-size: 12px;
}

.head-text .title {
  font-size: 17px;
  font-weight: 600;
  color: #1f2d3d;
  line-height: 1.25;
}

.head-text .subtitle {
  font-size: 12px;
  color: #909399;
  margin-top: 3px;
}

.main {
  background: #f5f7fa;
  padding: 20px 24px;
  overflow-y: auto;
}

/* 切换页面时淡一下，比硬切舒服 */
.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.15s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}
</style>
