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
    <section class="admin-login-visual" aria-label="智能药房运营控制台">
      <div class="admin-login-brand">
        <span class="admin-login-brand-mark">YF</span>
        <div>
          <strong>智能药房 SaaS 平台</strong>
          <small>处方履约 · 煎煮作业 · 物流回调</small>
        </div>
      </div>

      <div class="admin-login-visual-copy">
        <p>开发测试环境</p>
        <h1>面向药房履约链路的运营控制台</h1>
        <span>订单、审方、调剂、复核、煎煮、物流和补偿任务统一在后台闭环。</span>
      </div>

      <dl class="admin-login-capabilities">
        <div>
          <dt>多租户</dt>
          <dd>按租户与机构范围进入后台</dd>
        </div>
        <div>
          <dt>可追踪</dt>
          <dd>订单状态、任务和回调留痕</dd>
        </div>
        <div>
          <dt>可补偿</dt>
          <dd>失败消息和回调集中处理</dd>
        </div>
      </dl>
    </section>

    <form class="admin-login-form" @submit.prevent="submit">
      <div class="admin-login-heading">
        <p>管理员登录</p>
        <h1>登录运营控制台</h1>
        <span>会话令牌只保存在当前浏览器标签页，退出或失效后需重新登录。</span>
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

      <p class="admin-login-footnote">权限、菜单和数据范围以后端会话为准。</p>
    </form>
  </main>
</template>

<style scoped>
.admin-login-shell {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 430px;
  gap: 44px;
  align-items: center;
  min-height: 100vh;
  padding: 48px clamp(32px, 7vw, 96px);
  color: var(--admin-text);
  background:
    linear-gradient(135deg, rgba(20, 39, 61, 0.96), rgba(31, 53, 79, 0.92)),
    repeating-linear-gradient(90deg, rgba(255, 255, 255, 0.035) 0 1px, transparent 1px 82px),
    #1b3142;
}

.admin-login-brand {
  display: flex;
  align-items: center;
  gap: 12px;
  color: #ffffff;
}

.admin-login-brand strong,
.admin-login-brand small {
  display: block;
}

.admin-login-brand strong {
  color: #ffffff;
  font-size: 18px;
}

.admin-login-brand small {
  margin-top: 3px;
  color: rgba(255, 255, 255, 0.66);
  font-size: 13px;
}

.admin-login-brand-mark {
  display: grid;
  width: 42px;
  height: 42px;
  place-items: center;
  border: 1px solid rgba(255, 255, 255, 0.22);
  border-radius: 10px;
  color: #ffffff;
  background: #0052d9;
  font-weight: 800;
}

.admin-login-visual {
  display: grid;
  gap: 46px;
  min-height: 560px;
  align-content: space-between;
}

.admin-login-visual-copy {
  max-width: 720px;
}

.admin-login-visual-copy p,
.admin-login-visual-copy h1,
.admin-login-visual-copy span {
  margin: 0;
}

.admin-login-visual-copy p {
  color: #9fc3ff;
  font-size: 14px;
  font-weight: 700;
}

.admin-login-visual-copy h1 {
  margin-top: 12px;
  max-width: 680px;
  color: #ffffff;
  font-size: clamp(36px, 5vw, 58px);
  font-weight: 760;
  line-height: 1.12;
  letter-spacing: 0;
}

.admin-login-visual-copy span {
  display: block;
  margin-top: 18px;
  max-width: 580px;
  color: rgba(255, 255, 255, 0.74);
  font-size: 16px;
  line-height: 1.8;
}

.admin-login-capabilities {
  display: grid;
  max-width: 720px;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
  margin: 0;
}

.admin-login-capabilities div {
  min-width: 0;
  padding: 14px 16px;
  border: 1px solid rgba(255, 255, 255, 0.16);
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.07);
}

.admin-login-capabilities dt {
  color: #ffffff;
  font-size: 14px;
  font-weight: 750;
}

.admin-login-capabilities dd {
  margin: 5px 0 0;
  color: rgba(255, 255, 255, 0.66);
  font-size: 12px;
  line-height: 1.6;
}

.admin-login-form {
  display: grid;
  align-content: start;
  width: 100%;
  gap: 17px;
  padding: 32px;
  border: 1px solid rgba(255, 255, 255, 0.7);
  border-radius: 8px;
  background: var(--admin-surface);
  box-shadow: 0 24px 70px rgba(8, 20, 36, 0.28);
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

.admin-login-heading span,
.admin-login-footnote {
  display: block;
  margin-top: 8px;
  color: var(--admin-muted);
  font-size: 13px;
  line-height: 1.6;
}

.admin-login-form label {
  min-width: 0;
}

.admin-login-form label span {
  display: block;
  margin-bottom: 6px;
  font-size: 13px;
  font-weight: 650;
}

.admin-login-form input {
  width: 100%;
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

@media (max-width: 900px) {
  .admin-login-shell {
    grid-template-columns: 1fr;
    gap: 28px;
    align-content: start;
    padding: 28px 18px;
  }

  .admin-login-visual {
    min-height: 0;
    gap: 24px;
  }

  .admin-login-visual-copy h1 {
    font-size: 30px;
  }

  .admin-login-capabilities {
    grid-template-columns: 1fr;
    gap: 8px;
  }

  .admin-login-form {
    max-width: 430px;
    margin: 0 auto;
    padding: 24px 20px;
  }
}

@media (max-width: 520px) {
  .admin-login-visual-copy,
  .admin-login-capabilities {
    display: none;
  }
}
</style>
