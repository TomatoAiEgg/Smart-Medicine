<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { errorMessage } from '../../domain/errors';
import {
  createAdminDictItem,
  createAdminDictType,
  listAdminDictItems,
  listAdminDictTypes,
  updateAdminDictItem,
  updateAdminDictType,
} from '../../api/order';
import type { AdminDictItemRecord, AdminDictTypeRecord } from '../../api/types';
import { downloadCsv } from '../../domain/csv';
import { enabledText, displayValue, formatDate, formatNumber } from '../../domain/formatters';

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

const typeKeyword = ref('');
const typeEnabledFilter = ref<EnabledFilter>('');
const itemKeyword = ref('');
const itemEnabledFilter = ref<EnabledFilter>('');
const typePage = ref<{ records: AdminDictTypeRecord[]; total: number; page: number; pageSize: number } | null>(null);
const itemPage = ref<{ records: AdminDictItemRecord[]; total: number; page: number; pageSize: number } | null>(null);
const typePageNo = ref(1);
const typePageSize = ref(20);
const itemPageNo = ref(1);
const itemPageSize = ref(20);
const selectedTypeId = ref('');
const loadingTypes = ref(false);
const loadingItems = ref(false);
const saving = ref(false);
const loaded = ref(false);
const errorLine = ref('');

const typeForm = ref({
  id: '',
  typeCode: '',
  typeName: '',
  enabled: true,
});

const itemForm = ref({
  id: '',
  typeId: '',
  itemCode: '',
  itemName: '',
  itemValue: '',
  sortNo: 0,
  enabled: true,
  remark: '',
});

const typeRows = computed(() => typePage.value?.records ?? []);
const itemRows = computed(() => itemPage.value?.records ?? []);
const totalTypes = computed(() => typePage.value?.total ?? 0);
const totalItems = computed(() => itemPage.value?.total ?? 0);
const selectedType = computed(() => typeRows.value.find((row) => row.id === selectedTypeId.value) ?? null);
const editingType = computed(() => typeForm.value.id !== '');
const editingItem = computed(() => itemForm.value.id !== '');
const hasPreviousTypePage = computed(() => typePageNo.value > 1 && !loadingTypes.value);
const hasNextTypePage = computed(() => !loadingTypes.value && typePageNo.value * typePageSize.value < totalTypes.value);
const hasPreviousItemPage = computed(() => itemPageNo.value > 1 && !loadingItems.value);
const hasNextItemPage = computed(() => !loadingItems.value && itemPageNo.value * itemPageSize.value < totalItems.value);

function queryEnabled(value: EnabledFilter) {
  return value === '' ? undefined : value;
}

function downloadTypeCsv() {
  downloadCsv(
    `字典类型-第${typePageNo.value}页.csv`,
    ['类型编码', '类型名称', '状态', '创建时间', '更新时间'],
    typeRows.value.map((row) => [
      row.typeCode,
      row.typeName,
      enabledText(row.enabled),
      formatDate(row.createdAt),
      formatDate(row.updatedAt),
    ]),
  );
  emit('notice', 'success', `已导出本页 ${formatNumber(typeRows.value.length)} 个字典类型`);
}

function downloadItemCsv() {
  downloadCsv(
    `字典项-第${itemPageNo.value}页.csv`,
    ['类型编码', '类型名称', '项编码', '项名称', '项值', '排序', '状态', '备注', '创建时间', '更新时间'],
    itemRows.value.map((row) => [
      row.typeCode,
      row.typeName,
      row.itemCode,
      row.itemName,
      row.itemValue,
      row.sortNo,
      enabledText(row.enabled),
      row.remark,
      formatDate(row.createdAt),
      formatDate(row.updatedAt),
    ]),
  );
  emit('notice', 'success', `已导出本页 ${formatNumber(itemRows.value.length)} 个字典项`);
}

function normalizePageSize(value: number) {
  if (!Number.isFinite(value) || value <= 0) return 20;
  return Math.min(Math.trunc(value), 100);
}

function resetTypeForm() {
  typeForm.value = {
    id: '',
    typeCode: '',
    typeName: '',
    enabled: true,
  };
}

function resetItemForm() {
  itemForm.value = {
    id: '',
    typeId: selectedTypeId.value,
    itemCode: '',
    itemName: '',
    itemValue: '',
    sortNo: 0,
    enabled: true,
    remark: '',
  };
}

async function refreshDictTypes() {
  loadingTypes.value = true;
  errorLine.value = '';
  typePageSize.value = normalizePageSize(typePageSize.value);
  try {
    const nextPage = await listAdminDictTypes({
      keyword: typeKeyword.value,
      enabled: queryEnabled(typeEnabledFilter.value),
      page: typePageNo.value,
      pageSize: typePageSize.value,
    });
    typePage.value = nextPage;
    typePageNo.value = nextPage.page;
    typePageSize.value = nextPage.pageSize;
    loaded.value = true;
    if (!typeRows.value.some((row) => row.id === selectedTypeId.value)) {
      selectedTypeId.value = typeRows.value[0]?.id ?? '';
    }
    emit('countChanged', nextPage.total);
    emit('notice', 'info', `已刷新字典类型：${formatNumber(nextPage.total)} 个`);
    await refreshDictItems();
  } catch (error) {
    typePage.value = null;
    itemPage.value = null;
    selectedTypeId.value = '';
    loaded.value = false;
    emit('countChanged', 0);
    errorLine.value = errorMessage(error);
  } finally {
    loadingTypes.value = false;
  }
}

async function refreshDictItems() {
  loadingItems.value = true;
  errorLine.value = '';
  itemPageSize.value = normalizePageSize(itemPageSize.value);
  try {
    const nextPage = await listAdminDictItems({
      keyword: itemKeyword.value,
      typeId: selectedTypeId.value,
      enabled: queryEnabled(itemEnabledFilter.value),
      page: itemPageNo.value,
      pageSize: itemPageSize.value,
    });
    itemPage.value = nextPage;
    itemPageNo.value = nextPage.page;
    itemPageSize.value = nextPage.pageSize;
  } catch (error) {
    itemPage.value = null;
    errorLine.value = errorMessage(error);
  } finally {
    loadingItems.value = false;
  }
}

async function searchTypesFirstPage() {
  typePageNo.value = 1;
  await refreshDictTypes();
}

async function searchItemsFirstPage() {
  itemPageNo.value = 1;
  await refreshDictItems();
}

function selectType(row: AdminDictTypeRecord) {
  selectedTypeId.value = row.id;
  itemPageNo.value = 1;
  resetItemForm();
  void refreshDictItems();
}

function editType(row: AdminDictTypeRecord) {
  typeForm.value = {
    id: row.id,
    typeCode: row.typeCode,
    typeName: row.typeName,
    enabled: row.enabled,
  };
}

function editItem(row: AdminDictItemRecord) {
  itemForm.value = {
    id: row.id,
    typeId: row.typeId,
    itemCode: row.itemCode,
    itemName: row.itemName,
    itemValue: row.itemValue ?? '',
    sortNo: row.sortNo,
    enabled: row.enabled,
    remark: row.remark ?? '',
  };
}

async function saveType() {
  saving.value = true;
  errorLine.value = '';
  try {
    const command = {
      typeCode: typeForm.value.typeCode.trim(),
      typeName: typeForm.value.typeName.trim(),
      enabled: typeForm.value.enabled,
    };
    const saved = editingType.value
      ? await updateAdminDictType(typeForm.value.id, command)
      : await createAdminDictType(command);
    selectedTypeId.value = saved.id;
    emit('notice', 'success', `${saved.typeName} 已保存`);
    resetTypeForm();
    await refreshDictTypes();
  } catch (error) {
    errorLine.value = errorMessage(error);
  } finally {
    saving.value = false;
  }
}

async function saveItem() {
  if (!selectedTypeId.value && !itemForm.value.typeId) {
    errorLine.value = '请先选择字典类型';
    return;
  }
  saving.value = true;
  errorLine.value = '';
  try {
    const command = {
      typeId: itemForm.value.typeId || selectedTypeId.value,
      itemCode: itemForm.value.itemCode.trim(),
      itemName: itemForm.value.itemName.trim(),
      itemValue: itemForm.value.itemValue.trim(),
      sortNo: Number.isFinite(itemForm.value.sortNo) ? itemForm.value.sortNo : 0,
      enabled: itemForm.value.enabled,
      remark: itemForm.value.remark.trim(),
    };
    const saved = editingItem.value
      ? await updateAdminDictItem(itemForm.value.id, command)
      : await createAdminDictItem(command);
    emit('notice', 'success', `${saved.itemName} 已保存`);
    resetItemForm();
    await refreshDictItems();
  } catch (error) {
    errorLine.value = errorMessage(error);
  } finally {
    saving.value = false;
  }
}

async function toggleType(row: AdminDictTypeRecord) {
  saving.value = true;
  errorLine.value = '';
  try {
    await updateAdminDictType(row.id, {
      typeName: row.typeName,
      enabled: !row.enabled,
    });
    emit('notice', 'success', `${row.typeName} 已${row.enabled ? '停用' : '启用'}`);
    await refreshDictTypes();
  } catch (error) {
    errorLine.value = errorMessage(error);
  } finally {
    saving.value = false;
  }
}

async function toggleItem(row: AdminDictItemRecord) {
  saving.value = true;
  errorLine.value = '';
  try {
    await updateAdminDictItem(row.id, {
      itemName: row.itemName,
      itemValue: row.itemValue ?? '',
      sortNo: row.sortNo,
      enabled: !row.enabled,
      remark: row.remark ?? '',
    });
    emit('notice', 'success', `${row.itemName} 已${row.enabled ? '停用' : '启用'}`);
    await refreshDictItems();
  } catch (error) {
    errorLine.value = errorMessage(error);
  } finally {
    saving.value = false;
  }
}

async function previousTypePage() {
  if (!hasPreviousTypePage.value) return;
  typePageNo.value -= 1;
  await refreshDictTypes();
}

async function nextTypePage() {
  if (!hasNextTypePage.value) return;
  typePageNo.value += 1;
  await refreshDictTypes();
}

async function previousItemPage() {
  if (!hasPreviousItemPage.value) return;
  itemPageNo.value -= 1;
  await refreshDictItems();
}

async function nextItemPage() {
  if (!hasNextItemPage.value) return;
  itemPageNo.value += 1;
  await refreshDictItems();
}

watch(
  () => [props.active, props.activationKey] as const,
  ([active]) => {
    if (active && !loaded.value) {
      void refreshDictTypes();
    }
  },
  { immediate: true },
);

defineExpose({
  refreshDictTypes,
});
</script>

<template>
  <section class="legacy-page dict-page">
    <div v-if="errorLine" class="legacy-alert legacy-alert-error">{{ errorLine }}</div>

    <div class="dict-layout">
      <section class="legacy-panel dict-type-panel">
        <div class="legacy-panel-title">字典类型</div>
        <ul class="legacy-search compact-search">
          <li>
            关键字：
            <input v-model="typeKeyword" class="legacy-input input-medium" @keyup.enter="searchTypesFirstPage" />
          </li>
          <li>
            状态：
            <select v-model="typeEnabledFilter" class="legacy-input input-small" @change="searchTypesFirstPage">
              <option value="">全部</option>
              <option value="true">启用</option>
              <option value="false">停用</option>
            </select>
          </li>
          <li>
            <button class="legacy-btn legacy-btn-primary" type="button" :disabled="loadingTypes" @click="searchTypesFirstPage">
              查询
            </button>
          </li>
          <li>
            <button class="legacy-btn" type="button" :disabled="loadingTypes || typeRows.length === 0" @click="downloadTypeCsv">
              导出当前页
            </button>
          </li>
        </ul>

        <div class="dict-form-grid">
          <label>
            类型编码
            <input v-model="typeForm.typeCode" class="legacy-input" :disabled="editingType || saving" />
          </label>
          <label>
            类型名称
            <input v-model="typeForm.typeName" class="legacy-input" :disabled="saving" />
          </label>
          <label class="enabled-field">
            <input v-model="typeForm.enabled" type="checkbox" :disabled="saving" />
            启用
          </label>
        </div>
        <div class="dict-actions">
          <button class="legacy-btn legacy-btn-primary" type="button" :disabled="saving" @click="saveType">
            {{ editingType ? '保存类型' : '新增类型' }}
          </button>
          <button class="legacy-btn" type="button" :disabled="saving" @click="resetTypeForm">清空</button>
        </div>

        <div class="legacy-table-wrap">
          <table class="legacy-table">
            <thead>
              <tr>
                <th>类型编码</th>
                <th>类型名称</th>
                <th>状态</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-if="!loadingTypes && typeRows.length === 0">
                <td colspan="4" class="empty-cell">暂无字典类型</td>
              </tr>
              <tr v-for="row in typeRows" :key="row.id" :class="{ selected: row.id === selectedTypeId }">
                <td>{{ row.typeCode }}</td>
                <td>{{ row.typeName }}</td>
                <td>{{ enabledText(row.enabled) }}</td>
                <td class="action-cell">
                  <button class="legacy-link-btn" type="button" @click="selectType(row)">选择</button>
                  <button class="legacy-link-btn" type="button" @click="editType(row)">编辑</button>
                  <button class="legacy-link-btn" type="button" @click="toggleType(row)">
                    {{ row.enabled ? '停用' : '启用' }}
                  </button>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
        <div class="legacy-pagination compact-pagination">
          <button class="legacy-btn" type="button" :disabled="!hasPreviousTypePage" @click="previousTypePage">上一页</button>
          <span>第 {{ typePageNo }} 页 / 共 {{ totalTypes }} 条</span>
          <button class="legacy-btn" type="button" :disabled="!hasNextTypePage" @click="nextTypePage">下一页</button>
        </div>
      </section>

      <section class="legacy-panel dict-item-panel">
        <div class="legacy-panel-title">字典项 {{ selectedType ? `- ${selectedType.typeName}` : '' }}</div>
        <ul class="legacy-search compact-search">
          <li>
            关键字：
            <input v-model="itemKeyword" class="legacy-input input-medium" @keyup.enter="searchItemsFirstPage" />
          </li>
          <li>
            状态：
            <select v-model="itemEnabledFilter" class="legacy-input input-small" @change="searchItemsFirstPage">
              <option value="">全部</option>
              <option value="true">启用</option>
              <option value="false">停用</option>
            </select>
          </li>
          <li>
            <button class="legacy-btn legacy-btn-primary" type="button" :disabled="loadingItems" @click="searchItemsFirstPage">
              查询
            </button>
          </li>
          <li>
            <button class="legacy-btn" type="button" :disabled="loadingItems || itemRows.length === 0" @click="downloadItemCsv">
              导出当前页
            </button>
          </li>
        </ul>

        <div class="dict-form-grid item-form-grid">
          <label>
            字典类型
            <select v-model="itemForm.typeId" class="legacy-input" :disabled="editingItem || saving">
              <option value="">当前选中类型</option>
              <option v-for="row in typeRows" :key="row.id" :value="row.id">
                {{ row.typeName }}
              </option>
            </select>
          </label>
          <label>
            项编码
            <input v-model="itemForm.itemCode" class="legacy-input" :disabled="editingItem || saving" />
          </label>
          <label>
            项名称
            <input v-model="itemForm.itemName" class="legacy-input" :disabled="saving" />
          </label>
          <label>
            项值
            <input v-model="itemForm.itemValue" class="legacy-input" :disabled="saving" />
          </label>
          <label>
            排序
            <input v-model.number="itemForm.sortNo" class="legacy-input" type="number" min="0" :disabled="saving" />
          </label>
          <label class="enabled-field">
            <input v-model="itemForm.enabled" type="checkbox" :disabled="saving" />
            启用
          </label>
          <label class="remark-field">
            备注
            <input v-model="itemForm.remark" class="legacy-input" :disabled="saving" />
          </label>
        </div>
        <div class="dict-actions">
          <button class="legacy-btn legacy-btn-primary" type="button" :disabled="saving" @click="saveItem">
            {{ editingItem ? '保存字典项' : '新增字典项' }}
          </button>
          <button class="legacy-btn" type="button" :disabled="saving" @click="resetItemForm">清空</button>
        </div>

        <div class="legacy-stats">
          <span>类型总数：{{ formatNumber(totalTypes) }}</span>
          <span>字典项总数：{{ formatNumber(totalItems) }}</span>
          <span>当前类型：{{ selectedType ? selectedType.typeName : '全部' }}</span>
        </div>

        <div class="legacy-table-wrap">
          <table class="legacy-table">
            <thead>
              <tr>
                <th>类型</th>
                <th>项编码</th>
                <th>项名称</th>
                <th>项值</th>
                <th>排序</th>
                <th>状态</th>
                <th>更新时间</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-if="!loadingItems && itemRows.length === 0">
                <td colspan="8" class="empty-cell">暂无字典项</td>
              </tr>
              <tr v-for="row in itemRows" :key="row.id">
                <td>{{ row.typeName }}</td>
                <td>{{ row.itemCode }}</td>
                <td>{{ row.itemName }}</td>
                <td>{{ displayValue(row.itemValue) }}</td>
                <td>{{ row.sortNo }}</td>
                <td>{{ enabledText(row.enabled) }}</td>
                <td>{{ formatDate(row.updatedAt) }}</td>
                <td class="action-cell">
                  <button class="legacy-link-btn" type="button" @click="editItem(row)">编辑</button>
                  <button class="legacy-link-btn" type="button" @click="toggleItem(row)">
                    {{ row.enabled ? '停用' : '启用' }}
                  </button>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
        <div class="legacy-pagination compact-pagination">
          <button class="legacy-btn" type="button" :disabled="!hasPreviousItemPage" @click="previousItemPage">上一页</button>
          <span>第 {{ itemPageNo }} 页 / 共 {{ totalItems }} 条</span>
          <button class="legacy-btn" type="button" :disabled="!hasNextItemPage" @click="nextItemPage">下一页</button>
          <label>
            每页
            <input v-model.number="itemPageSize" class="legacy-input input-small" type="number" min="1" max="100" @keyup.enter="searchItemsFirstPage" />
          </label>
        </div>
      </section>
    </div>
  </section>
</template>

<style scoped>
.dict-layout {
  display: grid;
  grid-template-columns: minmax(320px, 0.85fr) minmax(0, 1.35fr);
  gap: 16px;
}

.compact-search {
  align-items: center;
  margin-bottom: 12px;
}

.dict-form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
  margin-bottom: 12px;
}

.dict-form-grid label {
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

.item-form-grid {
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.remark-field {
  grid-column: 1 / -1;
}

.dict-actions {
  display: flex;
  gap: 8px;
  margin-bottom: 12px;
}

.selected {
  background: #eff6ff;
}

.action-cell {
  white-space: nowrap;
}

.empty-cell {
  padding: 22px;
  text-align: center;
  color: #64748b;
}

.compact-pagination {
  margin-top: 12px;
}

@media (max-width: 1180px) {
  .dict-layout {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 760px) {
  .dict-form-grid,
  .item-form-grid {
    grid-template-columns: 1fr;
  }
}
</style>
