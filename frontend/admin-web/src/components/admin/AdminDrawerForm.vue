<script setup lang="ts">
import { computed } from 'vue';

interface Props {
  open: boolean;
  title: string;
  description?: string;
  submitting?: boolean;
  saveLabel?: string;
  width?: string;
}

interface Emits {
  'update:open': [value: boolean];
  save: [];
}

const props = withDefaults(defineProps<Props>(), {
  description: undefined,
  submitting: false,
  saveLabel: '保存',
  width: '520px',
});

const emit = defineEmits<Emits>();

defineSlots<{
  default?: () => unknown;
}>();

const drawerSize = computed(() => `min(${props.width}, 100vw)`);

function handleVisibleChange(visible: boolean) {
  if (!visible && props.submitting) return;
  emit('update:open', visible);
}

function handleCancel() {
  if (props.submitting) return;
  emit('update:open', false);
}

function handleSave() {
  if (props.submitting) return;
  emit('save');
}
</script>

<template>
  <t-drawer
    drawer-class-name="admin-drawer-form"
    :visible="open"
    :size="drawerSize"
    :close-btn="!submitting"
    :close-on-overlay-click="!submitting"
    :close-on-esc-keydown="!submitting"
    :aria-label="title"
    @update:visible="handleVisibleChange"
  >
    <template #header>
      <div class="admin-drawer-form__heading">
        <strong>{{ title }}</strong>
        <p v-if="description">{{ description }}</p>
      </div>
    </template>

    <div class="admin-drawer-form__body">
      <slot />
    </div>

    <template #footer>
      <div class="admin-drawer-form__footer">
        <t-button
          theme="default"
          variant="outline"
          :disabled="submitting"
          @click="handleCancel"
        >
          取消
        </t-button>
        <t-button
          theme="primary"
          :loading="submitting"
          :disabled="submitting"
          @click="handleSave"
        >
          {{ saveLabel }}
        </t-button>
      </div>
    </template>
  </t-drawer>
</template>

<style>
.admin-drawer-form .t-drawer__header {
  min-height: 56px;
  padding: 12px 48px 12px 16px;
  border-bottom: 1px solid var(--admin-line, #d8e0ea);
}

.admin-drawer-form .t-drawer__close-btn {
  top: 16px;
  right: 16px;
}

.admin-drawer-form .t-drawer__body {
  padding: 16px;
}

.admin-drawer-form .t-drawer__footer {
  padding: 12px 16px;
  border-top: 1px solid var(--admin-line, #d8e0ea);
}

.admin-drawer-form__heading {
  min-width: 0;
}

.admin-drawer-form__heading strong,
.admin-drawer-form__heading p {
  display: block;
  margin: 0;
  letter-spacing: 0;
}

.admin-drawer-form__heading strong {
  color: var(--admin-text, #182230);
  font-size: 15px;
  font-weight: 600;
  line-height: 22px;
}

.admin-drawer-form__heading p {
  margin-top: 2px;
  color: var(--admin-muted, #667386);
  font-size: 12px;
  line-height: 18px;
}

.admin-drawer-form__body {
  min-width: 0;
}

.admin-drawer-form__footer {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 8px;
  width: 100%;
}

.admin-drawer-form__footer .t-button {
  min-width: 72px;
  height: 32px;
}

@media (max-width: 639px) {
  .admin-drawer-form .t-drawer__content-wrapper {
    max-width: 100vw;
  }

  .admin-drawer-form .t-drawer__body {
    padding: 14px 12px;
  }

  .admin-drawer-form .t-drawer__footer {
    padding: 10px 12px;
  }
}
</style>
