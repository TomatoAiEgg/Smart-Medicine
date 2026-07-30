<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { errorMessage } from '../../domain/errors';
import {
  createAdminDecoctCenter,
  listAdminDecoctCenters,
  updateAdminDecoctCenter,
} from '../../api/order';
import type { AdminDecoctCenterPage, AdminDecoctCenterRecord } from '../../api/types';
import { downloadCsv } from '../../domain/csv';
import { displayValue, formatDate, formatNumber } from '../../domain/formatters';

type NoticeTone = 'info' | 'success' | 'error';
type EnabledFilter = '' | 'true' | 'false';

const props = defineProps<{
  active: boolean;
  activationKey: number;
}>();

const emit = defineEmits<{
  notice: [tone: NoticeTone, text: string];
  countChanged: [count: number];
}>();

const keyword = ref('');
const enabledFilter = ref<EnabledFilter>('');
const page = ref(1);
const pageSize = ref(20);
const centerPage = ref<AdminDecoctCenterPage | null>(null);
const loading = ref(false);
const saving = ref(false);
const loaded = ref(false);
const errorLine = ref('');
const form = ref({
  id: '',
  centerCode: '',
  centerName: '',
  contactName: '',
  contactPhone: '',
  address: '',
  enabled: true,
  remark: '',
});

const rows = computed(() => centerPage.value?.records ?? []);
const total = computed(() => centerPage.value?.total ?? 0);
const enabledCount = computed(() => rows.value.filter((row) => row.enabled).length);
const disabledCount = computed(() => rows.value.filter((row) => !row.enabled).length);
const hasPreviousPage = computed(() => page.value > 1 && !loading.value);
const hasNextPage = computed(() => !loading.value && page.value * pageSize.value < total.value);
const editing = computed(() => form.value.id !== '');

function queryEnabled() {
  return enabledFilter.value === '' ? undefined : enabledFilter.value;
}

function enabledText(value: boolean) {
  return value ? '启用' : '停用';
}

function downloadCenterCsv() {
  downloadCsv(
    `煎煮中心-第${page.value}页.csv`,
    ['中心编码', '中心名称', '联系人', '联系电话', '地址', '状态', '备注', '创建时间', '更新时间'],
    rows.value.map((row) => [
      row.centerCode,
      row.centerName,
      row.contactName,
      row.contactPhone,
      row.address,
      enabledText(row.enabled),
      row.remark,
      formatDate(row.createdAt),
      formatDate(row.updatedAt),
    ]),
  );
  emit('notice', 'success', `已导出本页 ${formatNumber(rows.value.length)} 个煎煮中心`);
}

function normalizePageSize() {
  if (!Number.isFinite(pageSize.value) || pageSize.value <= 0) return 20;
  return Math.min(Math.trunc(pageSize.value), 100);
}

function resetForm() {
  form.value = {
    id: '',
    centerCode: '',
    centerName: '',
    contactName: '',
    contactPhone: '',
    address: '',
    enabled: true,
    remark: '',
  };
}

function editCenter(row: AdminDecoctCenterRecord) {
  form.value = {
    id: row.id,
    centerCode: row.centerCode,
    centerName: row.centerName,
    contactName: row.contactName ?? '',
    contactPhone: row.contactPhone ?? '',
    address: row.address ?? '',
    enabled: row.enabled,
    remark: row.remark ?? '',
  };
}

async function refreshDecoctCenters() {
  loading.value = true;
  errorLine.value = '';
  pageSize.value = normalizePageSize();
  try {
    const nextPage = await listAdminDecoctCenters({
      keyword: keyword.value,
      enabled: queryEnabled(),
      page: page.value,
      pageSize: pageSize.value,
    });
    centerPage.value = nextPage;
    page.value = nextPage.page;
    pageSize.value = nextPage.pageSize;
    loaded.value = true;
    emit('countChanged', nextPage.total);
    emit('notice', 'info', `已刷新煎煮中心：${formatNumber(nextPage.total)} 条`);
  } catch (error) {
    centerPage.value = null;
    loaded.value = false;
    emit('countChanged', 0);
    errorLine.value = errorMessage(error);
  } finally {
    loading.value = false;
  }
}

async function searchFirstPage() {
  page.value = 1;
  await refreshDecoctCenters();
}

async function saveCenter() {
  saving.value = true;
  errorLine.value = '';
  try {
    const command = {
      centerCode: form.value.centerCode.trim(),
      centerName: form.value.centerName.trim(),
      contactName: form.value.contactName.trim(),
      contactPhone: form.value.contactPhone.trim(),
      address: form.value.address.trim(),
      enabled: form.value.enabled,
      remark: form.value.remark.trim(),
    };
    const saved = editing.value
      ? await updateAdminDecoctCenter(form.value.id, command)
      : await createAdminDecoctCenter(command);
    emit('notice', 'success', `${saved.centerName} 已保存`);
    resetForm();
    await refreshDecoctCenters();
  } catch (error) {
    errorLine.value = errorMessage(error);
  } finally {
    saving.value = false;
  }
}

async function toggleCenter(row: AdminDecoctCenterRecord) {
  saving.value = true;
  errorLine.value = '';
  try {
    await updateAdminDecoctCenter(row.id, {
      centerName: row.centerName,
      contactName: row.contactName ?? '',
      contactPhone: row.contactPhone ?? '',
      address: row.address ?? '',
      enabled: !row.enabled,
      remark: row.remark ?? '',
    });
    emit('notice', 'success', `${row.centerName} 已${row.enabled ? '停用' : '启用'}`);
    await refreshDecoctCenters();
  } catch (error) {
    errorLine.value = errorMessage(error);
  } finally {
    saving.value = false;
  }
}

async function previousPage() {
  if (!hasPreviousPage.value) return;
  page.value -= 1;
  await refreshDecoctCenters();
}

async function nextPage() {
  if (!hasNextPage.value) return;
  page.value += 1;
  await refreshDecoctCenters();
}

watch(
  () => [props.active, props.activationKey] as const,
  ([active]) => {
    if (active && !loaded.value) {
      void refreshDecoctCenters();
    }
  },
  { immediate: true },
);

defineExpose({
  refreshDecoctCenters,
});
</script>

<template>
  <section class="legacy-page decoct-center-page">
    <ul class="legacy-search decoct-center-search">
      <li>
        关键字：
        <input v-model="keyword" class="legacy-input input-large" @keyup.enter="searchFirstPage" />
      </li>
      <li>
        状态：
        <select v-model="enabledFilter" class="legacy-input input-small" @change="searchFirstPage">
          <option value="">全部</option>
          <option value="true">启用</option>
          <option value="false">停用</option>
        </select>
      </li>
      <li>
        <button class="legacy-btn legacy-btn-primary" type="button" :disabled="loading" @click="searchFirstPage">
          查询
        </button>
      </li>
      <li>
        <button class="legacy-btn" type="button" :disabled="loading || rows.length === 0" @click="downloadCenterCsv">
          导出当前页
        </button>
      </li>
    </ul>

    <div v-if="errorLine" class="legacy-alert legacy-alert-error">{{ errorLine }}</div>

    <ul class="legacy-stats decoct-center-stats">
      <li>
        <strong>{{ formatNumber(total) }}</strong>
        <span>中心总数</span>
      </li>
      <li>
        <strong>{{ formatNumber(enabledCount) }}</strong>
        <span>本页启用</span>
      </li>
      <li>
        <strong>{{ formatNumber(disabledCount) }}</strong>
        <span>本页停用</span>
      </li>
    </ul>

    <section class="legacy-panel decoct-center-form-panel">
      <div class="legacy-panel-title">{{ editing ? '编辑煎煮中心' : '新增煎煮中心' }}</div>
      <div class="decoct-center-form-grid">
        <label>
          中心编码
          <input v-model="form.centerCode" class="legacy-input" :disabled="editing || saving" />
        </label>
        <label>
          中心名称
          <input v-model="form.centerName" class="legacy-input" :disabled="saving" />
        </label>
        <label class="enabled-field">
          <input v-model="form.enabled" type="checkbox" :disabled="saving" />
          启用
        </label>
        <label>
          联系人
          <input v-model="form.contactName" class="legacy-input" :disabled="saving" />
        </label>
        <label>
          联系电话
          <input v-model="form.contactPhone" class="legacy-input" :disabled="saving" />
        </label>
        <label>
          地址
          <input v-model="form.address" class="legacy-input" :disabled="saving" />
        </label>
        <label class="remark-field">
          备注
          <input v-model="form.remark" class="legacy-input" :disabled="saving" />
        </label>
      </div>
      <div class="decoct-center-actions">
        <button class="legacy-btn legacy-btn-primary" type="button" :disabled="saving" @click="saveCenter">
          {{ editing ? '保存中心' : '新增中心' }}
        </button>
        <button class="legacy-btn" type="button" :disabled="saving" @click="resetForm">清空</button>
      </div>
    </section>

    <div class="legacy-table-wrap">
      <table class="legacy-table">
        <thead>
          <tr>
            <th>中心编码</th>
            <th>中心名称</th>
            <th>联系人</th>
            <th>联系电话</th>
            <th>地址</th>
            <th>状态</th>
            <th>更新时间</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-if="!loading && rows.length === 0">
            <td colspan="8" class="empty-cell">暂无煎煮中心</td>
          </tr>
          <tr v-for="row in rows" :key="row.id">
            <td>{{ row.centerCode }}</td>
            <td>{{ row.centerName }}</td>
            <td>{{ displayValue(row.contactName) }}</td>
            <td>{{ displayValue(row.contactPhone) }}</td>
            <td class="address-cell">{{ displayValue(row.address) }}</td>
            <td>{{ enabledText(row.enabled) }}</td>
            <td>{{ formatDate(row.updatedAt) }}</td>
            <td class="action-cell">
              <button class="legacy-link-btn" type="button" @click="editCenter(row)">编辑</button>
              <button class="legacy-link-btn" type="button" :disabled="saving" @click="toggleCenter(row)">
                {{ row.enabled ? '停用' : '启用' }}
              </button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <div class="legacy-pagination">
      <button class="legacy-btn" type="button" :disabled="!hasPreviousPage" @click="previousPage">上一页</button>
      <span>第 {{ page }} 页 / 共 {{ formatNumber(total) }} 条</span>
      <button class="legacy-btn" type="button" :disabled="!hasNextPage" @click="nextPage">下一页</button>
      <label>
        每页
        <input
          v-model.number="pageSize"
          class="legacy-input input-small"
          type="number"
          min="1"
          max="100"
          @keyup.enter="searchFirstPage"
        />
      </label>
    </div>
  </section>
</template>

<style scoped>
.decoct-center-search {
  align-items: center;
}

.decoct-center-stats {
  margin-bottom: 16px;
}

.decoct-center-form-panel {
  margin-bottom: 16px;
}

.decoct-center-form-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}

.decoct-center-form-grid label {
  display: grid;
  gap: 6px;
  color: #475569;
  font-size: 13px;
}

.enabled-field {
  grid-template-columns: auto 1fr;
  align-items: center;
  align-content: end;
}

.remark-field {
  grid-column: 1 / -1;
}

.decoct-center-actions {
  display: flex;
  gap: 8px;
  margin-top: 14px;
}

.address-cell {
  max-width: 360px;
  white-space: normal;
  word-break: break-word;
}

.action-cell {
  white-space: nowrap;
}

.empty-cell {
  padding: 22px;
  text-align: center;
  color: #64748b;
}

@media (max-width: 920px) {
  .decoct-center-form-grid {
    grid-template-columns: 1fr;
  }
}
</style>
