<script setup lang="ts">
import { ref } from 'vue';
import { ApiError } from '../../api/client';
import { loginAdmin } from '../../api/auth';
import type { AdminUserSession } from '../../api/adminSession';

const props = defineProps<{ restoring: boolean }>();
const emit = defineEmits<{ authenticated: [session: AdminUserSession] }>();

const tenantCode = ref(sessionStorage.getItem('zhyf.admin.tenant-code') || 'demo-tenant');
const username = ref('admin');
const password = ref('');
const submitting = ref(false);
const errorMessage = ref('');

async function submit() {
  if (submitting.value || props.restoring) return;
  errorMessage.value = '';
  if (!tenantCode.value.trim() || !username.value.trim() || !password.value) {
    errorMessage.value = '请完整填写租户编码、用户名和密码';
    return;
  }
  submitting.value = true;
  try {
    const session = await loginAdmin({
      tenantCode: tenantCode.value.trim(),
      username: username.value.trim(),
      password: password.value,
    });
    sessionStorage.setItem('zhyf.admin.tenant-code', session.tenantCode);
    password.value = '';
    emit('authenticated', session);
  } catch (error) {
    errorMessage.value = error instanceof ApiError ? error.message : '登录失败，请稍后重试';
  } finally {
    submitting.value = false;
  }
}
</script>

<template>
  <main class="admin-login-shell">
    <header class="admin-login-brand">
      <span class="cloud-console-brand-mark">YF</span>
      <div>
        <strong>智能药房 SaaS 平台</strong>
        <small>运营控制台</small>
      </div>
    </header>

    <form class="admin-login-form" @submit.prevent="submit">
      <div class="admin-login-heading">
        <p>开发环境</p>
        <h1>登录运营控制台</h1>
      </div>

      <label>
        <span>租户编码</span>
        <input v-model="tenantCode" name="tenantCode" autocomplete="organization" :disabled="restoring || submitting" />
      </label>

      <label>
        <span>用户名</span>
        <input v-model="username" name="username" autocomplete="username" :disabled="restoring || submitting" />
      </label>

      <label>
        <span>密码</span>
        <input
          v-model="password"
          name="password"
          type="password"
          autocomplete="current-password"
          :disabled="restoring || submitting"
        />
      </label>

      <p v-if="errorMessage" class="admin-login-error" role="alert">{{ errorMessage }}</p>

      <button class="admin-login-submit" type="submit" :disabled="restoring || submitting">
        {{ restoring ? '正在恢复会话' : submitting ? '正在登录' : '登录' }}
      </button>
    </form>
  </main>
</template>

<style scoped>
.admin-login-shell {
  display: grid;
  min-height: 100vh;
  grid-template-rows: 56px 1fr;
  color: var(--admin-text);
  background: var(--admin-bg);
}

.admin-login-brand {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 0 20px;
  border-bottom: 1px solid var(--admin-line);
  background: var(--admin-surface);
}

.admin-login-brand strong,
.admin-login-brand small {
  display: block;
}

.admin-login-brand strong {
  color: #111827;
  font-size: 15px;
}

.admin-login-brand small {
  margin-top: 1px;
  color: var(--admin-muted);
  font-size: 12px;
}

.admin-login-form {
  display: grid;
  align-content: start;
  width: min(400px, calc(100% - 32px));
  margin: 12vh auto 0;
  gap: 18px;
  padding: 28px;
  border: 1px solid var(--admin-line);
  border-radius: 8px;
  background: var(--admin-surface);
}

.admin-login-heading p,
.admin-login-heading h1 {
  margin: 0;
}

.admin-login-heading p {
  color: var(--admin-primary);
  font-size: 13px;
  font-weight: 700;
}

.admin-login-heading h1 {
  margin-top: 6px;
  font-size: 22px;
  font-weight: 700;
  letter-spacing: 0;
}

.admin-login-form label {
  min-width: 0;
}

.admin-login-form input:focus-visible,
.admin-login-submit:focus-visible {
  outline: 2px solid var(--admin-primary);
  outline-offset: 2px;
}

.admin-login-error {
  margin: 0;
  padding: 10px 12px;
  border: 1px solid #f3b7b7;
  border-radius: 6px;
  color: #8d2c2c;
  background: #fff2f2;
  font-size: 13px;
  line-height: 1.5;
  overflow-wrap: anywhere;
}

.admin-login-submit {
  min-height: 40px;
  border-radius: 6px;
  color: #ffffff;
  background: var(--admin-primary);
  font-weight: 700;
}

.admin-login-submit:hover:not(:disabled) {
  background: #0046b8;
}

@media (max-width: 640px) {
  .admin-login-brand {
    padding: 0 14px;
  }

  .admin-login-form {
    margin-top: 8vh;
    padding: 22px 18px;
  }
}
</style>
