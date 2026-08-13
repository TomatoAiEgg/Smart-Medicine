<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, useId, watch } from 'vue';

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

const focusableSelector = [
  'button:not([disabled])',
  '[href]',
  'input:not([disabled])',
  'select:not([disabled])',
  'textarea:not([disabled])',
  '[contenteditable="true"]',
  '[tabindex]:not([tabindex="-1"])',
].join(',');

const drawerId = useId();
const titleId = `${drawerId}-title`;
const descriptionId = `${drawerId}-description`;
const drawerSize = computed(() => `min(${props.width}, 100vw)`);
const closeLabel = computed(() => `关闭${props.title}`);

let focusFrame: number | undefined;
let previouslyFocusedElement: HTMLElement | null = null;

function getDrawerElement() {
  if (typeof document === 'undefined') return null;
  return document.querySelector<HTMLElement>(
    `[data-admin-drawer-id="${drawerId}"]`,
  );
}

function getFocusableElements(drawer: HTMLElement) {
  return Array.from(drawer.querySelectorAll<HTMLElement>(focusableSelector)).filter(
    (element) =>
      element.getClientRects().length > 0 &&
      element.getAttribute('aria-hidden') !== 'true' &&
      !element.closest('[inert]'),
  );
}

function cancelScheduledFocus() {
  if (focusFrame === undefined || typeof window === 'undefined') return;
  window.cancelAnimationFrame(focusFrame);
  focusFrame = undefined;
}

function scheduleFocus(task: () => void) {
  if (typeof window === 'undefined') return;
  cancelScheduledFocus();
  void nextTick(() => {
    focusFrame = window.requestAnimationFrame(() => {
      focusFrame = undefined;
      task();
    });
  });
}

function focusDrawer() {
  const drawer = getDrawerElement();
  if (!drawer) return;
  const focusableElements = getFocusableElements(drawer);
  const autofocusElement = drawer.querySelector<HTMLElement>('[autofocus]');
  const target =
    (autofocusElement && focusableElements.includes(autofocusElement)
      ? autofocusElement
      : null) ??
    focusableElements[0] ??
    drawer;
  target.focus({ preventScroll: true });
}

function restoreFocus() {
  const target = previouslyFocusedElement;
  previouslyFocusedElement = null;
  if (target?.isConnected) {
    target.focus({ preventScroll: true });
  }
}

function requestClose() {
  if (props.submitting) return;
  emit('update:open', false);
}

function handleVisibleChange(visible: boolean) {
  if (!visible) {
    requestClose();
    return;
  }
  emit('update:open', true);
}

function handleCancel() {
  requestClose();
}

function handleSave() {
  if (props.submitting) return;
  emit('save');
}

function handleKeydown(event: KeyboardEvent) {
  if (event.key !== 'Tab' || !props.open) return;

  const drawer = getDrawerElement();
  if (!drawer) return;

  const focusableElements = getFocusableElements(drawer);
  if (focusableElements.length === 0) {
    event.preventDefault();
    drawer.focus({ preventScroll: true });
    return;
  }

  const first = focusableElements[0];
  const last = focusableElements[focusableElements.length - 1];
  const activeElement = document.activeElement;

  if (event.shiftKey && (activeElement === first || !drawer.contains(activeElement))) {
    event.preventDefault();
    last.focus({ preventScroll: true });
  } else if (!event.shiftKey && (activeElement === last || !drawer.contains(activeElement))) {
    event.preventDefault();
    first.focus({ preventScroll: true });
  }
}

watch(
  () => props.open,
  (open, wasOpen) => {
    if (open) {
      if (
        !wasOpen &&
        typeof document !== 'undefined' &&
        document.activeElement instanceof HTMLElement
      ) {
        previouslyFocusedElement = document.activeElement;
      }
      scheduleFocus(focusDrawer);
    } else if (wasOpen) {
      scheduleFocus(restoreFocus);
    }
  },
  { immediate: true, flush: 'post' },
);

onBeforeUnmount(() => {
  cancelScheduledFocus();
  restoreFocus();
});
</script>

<template>
  <t-drawer
    drawer-class-name="admin-drawer-form"
    :visible="open"
    :size="drawerSize"
    :close-btn="false"
    :close-on-overlay-click="!submitting"
    :close-on-esc-keydown="!submitting"
    role="dialog"
    aria-modal="true"
    :aria-labelledby="titleId"
    :aria-describedby="description ? descriptionId : undefined"
    :data-admin-drawer-id="drawerId"
    @keydown.capture="handleKeydown"
    @update:visible="handleVisibleChange"
  >
    <template #header>
      <div class="admin-drawer-form__header">
        <div class="admin-drawer-form__heading">
          <h2 :id="titleId">{{ title }}</h2>
          <p v-if="description" :id="descriptionId">{{ description }}</p>
        </div>
        <button
          class="admin-drawer-form__close"
          type="button"
          :aria-label="closeLabel"
          :title="closeLabel"
          :disabled="submitting"
          @click="requestClose"
        >
          <t-icon name="close" aria-hidden="true" />
        </button>
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
  padding: 12px 16px;
  border-bottom: 1px solid var(--admin-border, #dcdcdc);
}

.admin-drawer-form .t-drawer__body {
  padding: 16px;
}

.admin-drawer-form .t-drawer__footer {
  padding: 12px 16px;
  border-top: 1px solid var(--admin-border, #dcdcdc);
}

.admin-drawer-form__header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  width: 100%;
}

.admin-drawer-form__heading {
  min-width: 0;
}

.admin-drawer-form__heading h2,
.admin-drawer-form__heading p {
  display: block;
  margin: 0;
  letter-spacing: 0;
}

.admin-drawer-form__heading h2 {
  color: var(--admin-text, #181818);
  font-size: 15px;
  font-weight: 600;
  line-height: 22px;
}

.admin-drawer-form__heading p {
  margin-top: 2px;
  color: var(--admin-text-secondary, #5e5e5e);
  font-size: 12px;
  line-height: 18px;
}

.admin-drawer-form__close {
  display: inline-flex;
  flex: 0 0 32px;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  padding: 0;
  border: 0;
  border-radius: 2px;
  color: var(--admin-text-secondary, #5e5e5e);
  background: transparent;
  cursor: pointer;
}

.admin-drawer-form__close:hover:not(:disabled) {
  color: var(--admin-text, #181818);
  background: var(--admin-surface-subtle, #f2f3f5);
}

.admin-drawer-form__close:disabled {
  color: var(--admin-text-placeholder, #8b8b8b);
  cursor: not-allowed;
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
