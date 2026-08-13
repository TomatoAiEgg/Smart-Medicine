<script setup lang="ts">
import { computed } from 'vue';

type AdminPageStateType = 'loading' | 'empty' | 'error' | 'forbidden' | 'readonly';

interface Props {
  state: AdminPageStateType;
  message: string;
  title?: string;
}

interface StatePresentation {
  title: string;
  icon: string;
}

const props = defineProps<Props>();

defineSlots<{
  action?: () => unknown;
}>();

const presentations: Record<Exclude<AdminPageStateType, 'loading'>, StatePresentation> = {
  empty: { title: '暂无数据', icon: 'file-unknown' },
  error: { title: '加载失败', icon: 'error-circle' },
  forbidden: { title: '暂无权限', icon: 'lock-on' },
  readonly: { title: '只读模式', icon: 'info-circle' },
};

const presentation = computed<StatePresentation | null>(() => {
  if (props.state === 'loading') return null;
  return presentations[props.state];
});

const resolvedTitle = computed(() => props.title ?? presentation.value?.title ?? '');
const liveRole = computed(() => (props.state === 'error' ? 'alert' : 'status'));
const liveMode = computed(() => (props.state === 'error' ? 'assertive' : 'polite'));
</script>

<template>
  <div
    v-if="state === 'loading'"
    class="admin-page-state admin-page-state--loading"
    role="status"
    aria-live="polite"
    aria-busy="true"
  >
    <span class="admin-page-state__sr-only">{{ message }}</span>
    <div class="admin-page-state__skeleton" aria-hidden="true">
      <div v-for="row in 4" :key="row" class="admin-page-state__skeleton-row">
        <span />
        <span />
        <span />
        <span />
      </div>
    </div>
  </div>

  <div
    v-else
    class="admin-page-state admin-page-state--terminal"
    :class="`admin-page-state--${state}`"
    :role="liveRole"
    :aria-live="liveMode"
  >
    <t-icon
      v-if="presentation"
      class="admin-page-state__icon"
      :name="presentation.icon"
      aria-hidden="true"
    />
    <div class="admin-page-state__copy">
      <strong>{{ resolvedTitle }}</strong>
      <p>{{ message }}</p>
      <div v-if="$slots.action" class="admin-page-state__action">
        <slot name="action" />
      </div>
    </div>
  </div>
</template>

<style scoped>
.admin-page-state {
  width: 100%;
  min-width: 0;
  border: 1px solid var(--admin-line, #e3e8f0);
  border-radius: 0 0 3px 3px;
  color: var(--admin-muted, #667085);
  background: #ffffff;
}

.admin-page-state--loading {
  min-height: 162px;
  padding: 0 10px;
}

.admin-page-state__sr-only {
  position: absolute;
  width: 1px;
  height: 1px;
  padding: 0;
  overflow: hidden;
  clip: rect(0, 0, 0, 0);
  white-space: nowrap;
  border: 0;
}

.admin-page-state__skeleton {
  display: grid;
  width: 100%;
}

.admin-page-state__skeleton-row {
  display: grid;
  grid-template-columns: 2fr 1fr 1fr 96px;
  align-items: center;
  gap: 24px;
  min-height: 40px;
  border-bottom: 1px solid var(--admin-line-soft, #edf1f6);
}

.admin-page-state__skeleton-row:last-child {
  border-bottom: 0;
}

.admin-page-state__skeleton-row span {
  display: block;
  height: 8px;
  border-radius: 2px;
  background: #e9edf2;
}

.admin-page-state__skeleton-row span:nth-child(2) {
  width: 72%;
}

.admin-page-state__skeleton-row span:nth-child(3) {
  width: 58%;
}

.admin-page-state--terminal {
  display: flex;
  align-items: flex-start;
  justify-content: center;
  gap: 10px;
  min-height: 144px;
  padding: 36px 24px;
}

.admin-page-state__icon {
  flex: 0 0 auto;
  margin-top: 1px;
  color: var(--admin-muted, #667085);
  font-size: 20px;
}

.admin-page-state__copy {
  min-width: 0;
  max-width: 520px;
}

.admin-page-state__copy strong,
.admin-page-state__copy p {
  display: block;
  margin: 0;
  letter-spacing: 0;
}

.admin-page-state__copy strong {
  color: var(--admin-text, #1f2937);
  font-size: 14px;
  font-weight: 600;
  line-height: 20px;
}

.admin-page-state__copy p {
  margin-top: 4px;
  font-size: 13px;
  line-height: 20px;
}

.admin-page-state__action {
  margin-top: 12px;
}

.admin-page-state--error .admin-page-state__icon {
  color: var(--admin-red, #b4232e);
}

.admin-page-state--forbidden .admin-page-state__icon,
.admin-page-state--readonly .admin-page-state__icon {
  color: var(--admin-amber, #a16207);
}

@media (max-width: 639px) {
  .admin-page-state__skeleton-row {
    grid-template-columns: 2fr 1fr 72px;
    gap: 12px;
  }

  .admin-page-state__skeleton-row span:nth-child(3) {
    display: none;
  }

  .admin-page-state--terminal {
    justify-content: flex-start;
    padding: 28px 16px;
  }
}
</style>
