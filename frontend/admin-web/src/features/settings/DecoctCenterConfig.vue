<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import {
  createAdminDecoctCenter,
  listAdminDecoctCenters,
  updateAdminDecoctCenter,
} from '../../api/order';
import type {
  AdminDecoctCenterCommand,
  AdminDecoctCenterPage,
  AdminDecoctCenterRecord,
} from '../../api/types';
import AdminPageState from '../../components/admin/AdminPageState.vue';
import AdminPagination from '../../components/admin/AdminPagination.vue';
import AdminPanel from '../../components/admin/AdminPanel.vue';
import AdminStatusTag from '../../components/admin/AdminStatusTag.vue';
import AdminTableShell from '../../components/admin/AdminTableShell.vue';
import AdminToolbar from '../../components/admin/AdminToolbar.vue';
import { downloadCsv } from '../../domain/csv';
import { errorMessage } from '../../domain/errors';
import {
  boundedPositiveInteger,
  enabledStringParam,
  formatDate,
  formatNumber,
} from '../../domain/formatters';

type NoticeTone = 'info' | 'success' | 'error';
type EnabledFilter = '' | 'true' | 'false';

interface DecoctCenterForm {
  id: string;
  centerCode: string;
  centerName: string;
  contactName: string;
  contactPhone: string;
  address: string;
  enabled: boolean;
  remark: string;
}

interface CenterStat {
  label: string;
  value: string;
}

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
const mutating = ref(false);
const loaded = ref(false);
const listError = ref('');
const actionError = ref('');
const refreshRequestSequence = ref(0);
const activeRefreshRequest = ref(0);

const form = ref<DecoctCenterForm>({
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
const editing = computed(() => form.value.id !== '');
const canExport = computed(() => !loading.value && rows.value.length > 0);
const hasPreviousPage = computed(() => page.value > 1 && !loading.value);
const hasNextPage = computed(() => !loading.value && page.value * pageSize.value < total.value);
const listState = computed<'loading' | 'error' | 'empty' | null>(() => {
  if (loading.value && !loaded.value) return 'loading';
  if (listError.value && centerPage.value === null) return 'error';
  if (loaded.value && !loading.value && rows.value.length === 0) return 'empty';
  return null;
});
const stats = computed<CenterStat[]>(() => [
  { label: '中心总数', value: formatNumber(total.value) },
  { label: '本页启用', value: formatNumber(enabledCount.value) },
  { label: '本页停用', value: formatNumber(disabledCount.value) },
]);

function normalizePageSize() {
  return boundedPositiveInteger(pageSize.value, 20, 100);
}

function displayText(value: string | null | undefined) {
  if (value === null || value === undefined) return '--';
  const trimmed = String(value).trim();
  return trimmed ? trimmed : '--';
}

function resetForm() {
  actionError.value = '';
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
  actionError.value = '';
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

function downloadCenterCsv() {
  downloadCsv(
    `煎煮中心-第${page.value}页.csv`,
    ['中心编码', '中心名称', '联系人', '联系电话', '地址', '状态', '备注', '创建时间', '更新时间'],
    rows.value.map((row) => [
      row.centerCode,
      row.centerName,
      row.contactName ?? '',
      row.contactPhone ?? '',
      row.address ?? '',
      row.enabled ? '启用' : '停用',
      row.remark ?? '',
      formatDate(row.createdAt),
      formatDate(row.updatedAt),
    ]),
  );
  emit('notice', 'success', `已导出本页 ${formatNumber(rows.value.length)} 个煎煮中心`);
}

async function refreshDecoctCenters() {
  const requestId = refreshRequestSequence.value + 1;
  refreshRequestSequence.value = requestId;
  activeRefreshRequest.value = requestId;
  loading.value = true;
  listError.value = '';
  try {
    pageSize.value = normalizePageSize();
    const nextPage = await listAdminDecoctCenters({
      keyword: keyword.value,
      enabled: enabledStringParam(enabledFilter.value),
      page: page.value,
      pageSize: pageSize.value,
    });
    if (requestId !== activeRefreshRequest.value) return;
    centerPage.value = nextPage;
    page.value = nextPage.page;
    pageSize.value = nextPage.pageSize;
    loaded.value = true;
    emit('countChanged', nextPage.total);
    emit('notice', 'info', `已刷新煎煮中心：${formatNumber(nextPage.total)} 条`);
  } catch (error) {
    if (requestId !== activeRefreshRequest.value) return;
    centerPage.value = null;
    loaded.value = false;
    listError.value = errorMessage(error);
    emit('countChanged', 0);
  } finally {
    if (requestId === activeRefreshRequest.value) {
      loading.value = false;
    }
  }
}

async function searchFirstPage() {
  if (loading.value || mutating.value) return;
  page.value = 1;
  await refreshDecoctCenters();
}

async function saveCenter() {
  if (loading.value || mutating.value) return;
  mutating.value = true;
  actionError.value = '';
  try {
    const command: AdminDecoctCenterCommand = {
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
    actionError.value = errorMessage(error);
  } finally {
    mutating.value = false;
  }
}

async function toggleCenter(row: AdminDecoctCenterRecord) {
  if (loading.value || mutating.value) return;
  mutating.value = true;
  actionError.value = '';
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
    actionError.value = errorMessage(error);
  } finally {
    mutating.value = false;
  }
}

async function previousPage() {
  if (loading.value || !hasPreviousPage.value) return;
  page.value -= 1;
  await refreshDecoctCenters();
}

async function nextPage() {
  if (loading.value || !hasNextPage.value) return;
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
  <section class="decoct-center-page">
    <AdminToolbar>
      <label class="center-field center-field--keyword">
        <span>关键字</span>
        <input
          v-model="keyword"
          class="center-input"
          :disabled="loading || mutating"
          placeholder="中心名称 / 中心编码 / 联系人"
          @keyup.enter="searchFirstPage"
        >
      </label>
      <label class="center-field center-field--status">
        <span>状态</span>
        <select
          v-model="enabledFilter"
          class="center-input"
          :disabled="loading || mutating"
          @change="searchFirstPage"
        >
          <option value="">全部</option>
          <option value="true">启用</option>
          <option value="false">停用</option>
        </select>
      </label>
      <template #actions>
        <t-button
          theme="primary"
          variant="outline"
          size="small"
          :disabled="loading || mutating"
          @click="searchFirstPage"
        >
          {{ loading ? '查询中' : '查询' }}
        </t-button>
        <t-button
          theme="default"
          variant="outline"
          size="small"
          :disabled="!canExport"
          @click="downloadCenterCsv"
        >
          导出当前页
        </t-button>
      </template>
    </AdminToolbar>

    <div class="center-stats" aria-label="煎煮中心统计">
      <article v-for="stat in stats" :key="stat.label" class="center-stat">
        <strong>{{ stat.value }}</strong>
        <span>{{ stat.label }}</span>
      </article>
    </div>

    <AdminPanel class="center-edit-panel">
      <template #title>{{ editing ? '编辑煎煮中心' : '新增煎煮中心' }}</template>
      <template #description>维护中心名称、编码、联系人、地址与业务状态。</template>

      <form class="center-form" @submit.prevent="saveCenter">
        <p v-if="actionError" class="error-line" role="alert">{{ actionError }}</p>
        <div class="center-form-grid">
          <label class="center-field">
            <span>中心编码</span>
            <input
              v-model="form.centerCode"
              class="center-input"
              :disabled="editing || loading || mutating"
              required
              placeholder="CENTER_CODE"
            >
          </label>
          <label class="center-field">
            <span>中心名称</span>
            <input
              v-model="form.centerName"
              class="center-input"
              :disabled="loading || mutating"
              required
              placeholder="煎煮中心名称"
            >
          </label>
          <label class="center-check">
            <input
              v-model="form.enabled"
              type="checkbox"
              :disabled="loading || mutating"
            >
            <span>启用</span>
          </label>
          <label class="center-field">
            <span>联系人</span>
            <input
              v-model="form.contactName"
              class="center-input"
              :disabled="loading || mutating"
              placeholder="联系人"
            >
          </label>
          <label class="center-field">
            <span>联系电话</span>
            <input
              v-model="form.contactPhone"
              class="center-input"
              :disabled="loading || mutating"
              placeholder="联系电话"
            >
          </label>
          <label class="center-field">
            <span>地址</span>
            <input
              v-model="form.address"
              class="center-input"
              :disabled="loading || mutating"
              placeholder="中心地址"
            >
          </label>
          <label class="center-field center-field--full">
            <span>备注</span>
            <input
              v-model="form.remark"
              class="center-input"
              :disabled="loading || mutating"
              placeholder="备注"
            >
          </label>
        </div>
        <div class="center-form-actions">
          <t-button
            theme="primary"
            variant="outline"
            size="small"
            type="submit"
            :disabled="loading || mutating"
          >
            {{ mutating ? '保存中' : editing ? '保存中心' : '新增中心' }}
          </t-button>
          <t-button
            theme="default"
            variant="outline"
            size="small"
            type="button"
            :disabled="loading || mutating"
            @click="resetForm"
          >
            清空
          </t-button>
        </div>
      </form>
    </AdminPanel>

    <AdminPanel class="center-list-panel">
      <template #title>中心列表</template>
      <template #description>
        {{ loaded ? `当前第 ${page} 页，共 ${formatNumber(total)} 条记录。` : '按条件检索煎煮中心。' }}
      </template>

      <AdminPageState
        v-if="listState === 'loading'"
        state="loading"
        message="正在查询煎煮中心。"
      />
      <AdminPageState
        v-else-if="listState === 'error'"
        state="error"
        :message="listError"
      />
      <AdminPageState
        v-else-if="listState === 'empty'"
        state="empty"
        message="没有相关煎煮中心。"
      />
      <template v-else>
        <AdminTableShell>
          <table class="center-table">
            <thead>
              <tr>
                <th>中心</th>
                <th>业务状态</th>
                <th>联系人</th>
                <th>联系电话</th>
                <th>地址</th>
                <th>更新时间</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="row in rows" :key="row.id">
                <td>
                  <div class="primary-cell">
                    <strong>{{ row.centerName }}</strong>
                    <small>{{ row.centerCode }}</small>
                  </div>
                </td>
                <td>
                  <AdminStatusTag :enabled="row.enabled" />
                </td>
                <td>{{ displayText(row.contactName) }}</td>
                <td>{{ displayText(row.contactPhone) }}</td>
                <td>
                  <div class="address-cell" :title="displayText(row.address)">
                    {{ displayText(row.address) }}
                  </div>
                </td>
                <td>{{ formatDate(row.updatedAt) }}</td>
                <td class="row-actions">
                  <t-button
                    theme="default"
                    variant="outline"
                    size="small"
                    :disabled="loading || mutating"
                    @click="editCenter(row)"
                  >
                    编辑
                  </t-button>
                  <t-button
                    theme="default"
                    variant="outline"
                    size="small"
                    :disabled="loading || mutating"
                    @click="toggleCenter(row)"
                  >
                    {{ row.enabled ? '停用' : '启用' }}
                  </t-button>
                </td>
              </tr>
            </tbody>
          </table>
        </AdminTableShell>

        <div class="pagination-row">
          <AdminPagination
            :page="page"
            :page-size="pageSize"
            :total="total"
            :loading="loading"
            @previous="previousPage"
            @next="nextPage"
          />
          <label class="page-size-field">
            <span>每页</span>
            <input
              v-model.number="pageSize"
              class="center-input center-input--page-size"
              type="number"
              min="1"
              max="100"
              :disabled="loading || mutating"
              @keyup.enter="searchFirstPage"
            >
          </label>
        </div>
      </template>
    </AdminPanel>
  </section>
</template>

<style scoped>
.decoct-center-page {
  display: grid;
  gap: 12px;
  min-width: 0;
  overflow-x: hidden;
}

.center-stats {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(148px, 1fr));
  gap: 12px;
  min-width: 0;
}

.center-stat {
  display: grid;
  gap: 4px;
  min-height: 88px;
  padding: 14px;
  border: 1px solid #e3e8f0;
  border-radius: 6px;
  background: #ffffff;
}

.center-stat strong {
  color: #111827;
  font-size: 22px;
  font-weight: 700;
  line-height: 28px;
  font-variant-numeric: tabular-nums;
}

.center-stat span {
  color: #667085;
  font-size: 12px;
  line-height: 18px;
}

.center-form {
  display: grid;
  gap: 12px;
}

.center-form-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr)) minmax(92px, auto);
  gap: 12px;
  min-width: 0;
}

.center-field {
  display: grid;
  gap: 6px;
  min-width: 0;
}

.center-field--keyword {
  flex: 1 1 300px;
}

.center-field--status {
  flex: 0 0 150px;
}

.center-field--full {
  grid-column: 1 / -1;
}

.center-field span,
.center-check span,
.page-size-field span {
  color: #4b5563;
  font-size: 13px;
  line-height: 20px;
}

.center-input {
  width: 100%;
  min-height: 34px;
  padding: 0 10px;
  border: 1px solid #d7deea;
  border-radius: 6px;
  color: #1f2937;
  background: #ffffff;
  font-size: 13px;
  line-height: 20px;
}

.center-input:disabled {
  color: #98a2b3;
  background: #f8fafc;
}

.center-input--page-size {
  width: 92px;
}

.center-check {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  min-height: 34px;
  padding-top: 24px;
}

.center-form-actions,
.pagination-row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 10px 12px;
}

.page-size-field {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  margin-left: auto;
}

.center-table {
  min-width: 980px;
}

.primary-cell {
  display: grid;
  gap: 2px;
}

.primary-cell strong {
  color: #111827;
  font-size: 13px;
  font-weight: 700;
  line-height: 20px;
}

.primary-cell small {
  color: #667085;
  font-size: 12px;
  line-height: 18px;
}

.address-cell {
  max-width: 320px;
  overflow: hidden;
  color: #374151;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.row-actions {
  white-space: nowrap;
}

.row-actions :deep(.t-button) {
  margin-right: 8px;
}

.row-actions :deep(.t-button:last-child) {
  margin-right: 0;
}

.error-line {
  margin: 0;
  color: #b42318;
  font-size: 13px;
  line-height: 20px;
}

@media (max-width: 980px) {
  .center-form-grid {
    grid-template-columns: 1fr;
  }

  .center-check {
    padding-top: 0;
  }

  .page-size-field {
    margin-left: 0;
  }
}
</style>
