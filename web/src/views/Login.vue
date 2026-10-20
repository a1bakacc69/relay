<script setup>
import { ref, reactive } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { login, register } from '../api/auth'
import { saveAuth } from '../utils/auth'

const router = useRouter()
const route = useRoute()

// 一个页面兼登录和注册，省得做两个几乎一样的界面
const mode = ref('login')
const loading = ref(false)
const formRef = ref()

const form = reactive({
  username: '',
  password: '',
  displayName: ''
})

const rules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 3, max: 20, message: '用户名 3~20 个字符', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, max: 64, message: '密码至少 6 位', trigger: 'blur' }
  ]
}

// 切 tab 时把上一个模式留下的红字清掉，不然注册页会挂着登录页的校验结果
function switchMode() {
  formRef.value?.clearValidate()
}

async function submit() {
  try {
    await formRef.value.validate()
  } catch {
    return
  }

  loading.value = true
  try {
    if (mode.value === 'register') {
      await register({
        username: form.username,
        password: form.password,
        // 空字符串别传，后端会当成“有值但为空”处理
        displayName: form.displayName || undefined
      })
      ElMessage.success('注册成功，正在登录…')
    }

    const data = await login({
      username: form.username,
      password: form.password
    })

    saveAuth(data.token, {
      username: data.username,
      displayName: data.displayName,
      role: data.role
    })

    ElMessage.success('登录成功')
    // 被拦下来时记了原本想去的页面，登录后送回去
    router.push(route.query.redirect || '/')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="login-page">
    <div class="card">
      <div class="brand">
        <div class="logo">R</div>
        <div class="brand-text">
          <div class="name">Relay</div>
          <div class="desc">订单同步中台</div>
        </div>
      </div>

      <el-tabs v-model="mode" class="tabs" @tab-change="switchMode">
        <el-tab-pane label="登录" name="login" />
        <el-tab-pane label="注册" name="register" />
      </el-tabs>

      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-position="top"
        @submit.prevent
      >
        <el-form-item label="用户名" prop="username">
          <el-input
            v-model="form.username"
            placeholder="3~20 个字符"
            autocomplete="username"
          />
        </el-form-item>

        <el-form-item label="密码" prop="password">
          <el-input
            v-model="form.password"
            type="password"
            show-password
            placeholder="至少 6 位"
            autocomplete="current-password"
            @keyup.enter="submit"
          />
        </el-form-item>

        <el-form-item v-if="mode === 'register'" label="显示名（选填）" prop="displayName">
          <el-input v-model="form.displayName" placeholder="不填就用用户名" />
        </el-form-item>

        <el-button
          type="primary"
          class="submit"
          :loading="loading"
          @click="submit"
        >
          {{ mode === 'login' ? '登录' : '注册并登录' }}
        </el-button>
      </el-form>

      <div class="tip">
        {{ mode === 'login' ? '还没有账号？切到「注册」' : '注册后会自动登录' }}
      </div>
    </div>
  </div>
</template>

<style scoped>
.login-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #1f2d3d 0%, #2b4a6f 100%);
}

.card {
  width: 380px;
  padding: 32px 34px 26px;
  background: #fff;
  border-radius: 14px;
  box-shadow: 0 18px 50px rgba(0, 0, 0, 0.28);
}

.brand {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 22px;
}

.logo {
  width: 42px;
  height: 42px;
  border-radius: 11px;
  background: linear-gradient(135deg, #409eff, #2b6cb0);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 700;
  font-size: 21px;
}

.brand-text .name {
  font-size: 19px;
  font-weight: 700;
  color: #1f2d3d;
  line-height: 1.2;
}

.brand-text .desc {
  font-size: 12px;
  color: #909399;
  margin-top: 3px;
}

.tabs {
  margin-bottom: 6px;
}

.submit {
  width: 100%;
  margin-top: 8px;
  height: 40px;
  font-size: 14.5px;
}

.tip {
  margin-top: 16px;
  text-align: center;
  font-size: 12px;
  color: #a8abb2;
}
</style>
