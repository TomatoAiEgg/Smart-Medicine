<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import {
  createAdminDictItem,
  createAdminDictType,
  listAdminDictItems,
  listAdminDictTypes,
  updateAdminDictItem,
  updateAdminDictType,
} from '../../api/order';
import type {
  AdminDictItemCommand,
  AdminDictItemPage,
  AdminDictItemRecord,
  AdminDictTypeCommand,
  AdminDictTypePage,
  AdminDictTypeRecord,
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

interface DictTypeForm {
  id: string;
  typeCode: string;
  typeName: string;
  enabled: boolean;
}

interface DictItemForm {
  id: string;
  typeId: string;
  itemCode: string;
  itemName: string;
  itemValue: string;
  sortNo: number;
  enabled: boolean;
  remark: string;
}

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
const typePage = ref<AdminDictTypePage | null>(null);
const itemPage = ref<AdminDictItemPage | null>(null);
const typePageNo = ref(1);
const typePageSize = ref(20);
const itemPageNo = ref(1);
const itemPageSize = ref(20);
const selectedTypeId = ref('');
const loadingTypes = ref(false);
const loadingItems = ref(false);
const mutatingType = ref(false);
const mutatingItem = ref(false);
const loadedTypes = ref(false);
const loadedItems = ref(false);
const typeListError = ref('');
const itemListError = ref('');
const typeFormError = ref('');
const itemFormError = ref('');
const typeRequestSequence = ref(0);
const activeTypeRequest = ref(0);
const itemRequestSequence = ref(0);
const activeItemRequest = ref(0);

const typeForm = ref<DictTypeForm>({
  id: '',
  typeCode: '',
  typeName: '',
  enabled: true,
});

const itemForm = ref<DictItemForm>({
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
const selectedType = computed(
  () => typeRows.value.find((row) => row.id === selectedTypeId.value) ?? null,
);
const editingType = computed(() => typeForm.value.id !== '');
const editingItem = computed(() => itemForm.value.id !== '');
const hasPreviousTypePage = computed(() => typePageNo.value > 1 && !loadingTypes.value);
const hasNextTypePage = computed(
  () => !loadingTypes.value && typePageNo.value * typePageSize.value < totalTypes.value,
);
const hasPreviousItemPage = computed(() => itemPageNo.value > 1 && !loadingItems.value);
const hasNextItemPage = computed(
  () => !loadingItems.value && itemPageNo.value * itemPageSize.value < totalItems.value,
);
const canExportTypes = computed(() => !loadingTypes.value && typeRows.value.length > 0);
const canExportItems = computed(() => !loadingItems.value && itemRows.value.length > 0);
const typeListState = computed<'loading' | 'error' | 'empty' | null>(() => {
  if (loadingTypes.value && !loadedTypes.value) return 'loading';
  if (typeListError.value && typePage.value === null) return 'error';
  if (loadedTypes.value && !loadingTypes.value && typeRows.value.length === 0) return 'empty';
  return null;
});
const itemListState = computed<'loading' | 'error' | 'empty' | null>(() => {
  if (loadingItems.value && !loadedItems.value) return 'loading';
  if (itemListError.value && itemPage.value === null) return 'error';
  if (loadedItems.value && !loadingItems.value && itemRows.value.length === 0) return 'empty';
  return null;
});

function normalizePageSize(value: number) {
  return boundedPositiveInteger(value, 20, 100);
}

function displayText(value: string | null | undefined) {
  if (value === null || value === undefined) return '--';
  const trimmed = String(value).trim();
  return trimmed ? trimmed : '--';
}

function resetTypeForm() {
  typeFormError.value = '';
  typeForm.value = {
    id: '',
    typeCode: '',
    typeName: '',
    enabled: true,
  };
}

function resetItemForm(typeId = selectedTypeId.value) {
  itemFormError.value = '';
  itemForm.value = {
    id: '',
    typeId,
    itemCode: '',
    itemName: '',
    itemValue: '',
    sortNo: 0,
    enabled: true,
    remark: '',
  };
}

function invalidateItemRequests() {
  const requestId = itemRequestSequence.value + 1;
  itemRequestSequence.value = requestId;
  activeItemRequest.value = requestId;
  loadingItems.value = false;
}

function downloadTypeCsv() {
  downloadCsv(
    `字典类型-第${typePageNo.value}页.csv`,
    ['类型编码', '类型名称', '状态', '创建时间', '更新时间'],
    typeRows.value.map((row) => [
      row.typeCode,
      row.typeName,
      row.enabled ? '启用' : '停用',
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
      row.itemValue ?? '',
      row.sortNo,
      row.enabled ? '启用' : '停用',
      row.remark ?? '',
      formatDate(row.createdAt),
      formatDate(row.updatedAt),
    ]),
  );
  emit('notice', 'success', `已导出本页 ${formatNumber(itemRows.value.length)} 个字典项`);
}

function editType(row: AdminDictTypeRecord) {
  typeFormError.value = '';
  typeForm.value = {
    id: row.id,
    typeCode: row.typeCode,
    typeName: row.typeName,
    enabled: row.enabled,
  };
}

function editItem(row: AdminDictItemRecord) {
  itemFormError.value = '';
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

async function refreshDictTypes() {
  const requestId = typeRequestSequence.value + 1;
  typeRequestSequence.value = requestId;
  activeTypeRequest.value = requestId;
  loadingTypes.value = true;
  typeListError.value = '';
  try {
    typePageSize.value = normalizePageSize(typePageSize.value);
    const nextPage = await listAdminDictTypes({
      keyword: typeKeyword.value,
      enabled: enabledStringParam(typeEnabledFilter.value),
      page: typePageNo.value,
      pageSize: typePageSize.value,
    });
    if (requestId !== activeTypeRequest.value) return;

    const previousSelectedTypeId = selectedTypeId.value;
    const nextSelectedTypeId = nextPage.records.some((row) => row.id === previousSelectedTypeId)
      ? previousSelectedTypeId
      : nextPage.records[0]?.id ?? '';
    const selectionChanged = nextSelectedTypeId !== previousSelectedTypeId;

    typePage.value = nextPage;
    typePageNo.value = nextPage.page;
    typePageSize.value = nextPage.pageSize;
    selectedTypeId.value = nextSelectedTypeId;
    loadedTypes.value = true;

    if (selectionChanged) {
      itemPageNo.value = 1;
      resetItemForm(nextSelectedTypeId);
    } else if (!editingItem.value && itemForm.value.typeId !== nextSelectedTypeId) {
      itemForm.value.typeId = nextSelectedTypeId;
    }

    emit('countChanged', nextPage.total);
    emit('notice', 'info', `已刷新字典类型：${formatNumber(nextPage.total)} 个`);
    await refreshDictItems();
  } catch (error) {
    if (requestId !== activeTypeRequest.value) return;
    typePage.value = null;
    itemPage.value = null;
    selectedTypeId.value = '';
    loadedTypes.value = false;
    loadedItems.value = false;
    typeListError.value = errorMessage(error);
    itemListError.value = '';
    resetItemForm('');
    itemPageNo.value = 1;
    emit('countChanged', 0);
    invalidateItemRequests();
  } finally {
    if (requestId === activeTypeRequest.value) {
      loadingTypes.value = false;
    }
  }
}

async function refreshDictItems() {
  const requestId = itemRequestSequence.value + 1;
  itemRequestSequence.value = requestId;
  activeItemRequest.value = requestId;
  loadingItems.value = true;
  itemListError.value = '';
  try {
    itemPageSize.value = normalizePageSize(itemPageSize.value);
    const nextPage = await listAdminDictItems({
      keyword: itemKeyword.value,
      typeId: selectedTypeId.value,
      enabled: enabledStringParam(itemEnabledFilter.value),
      page: itemPageNo.value,
      pageSize: itemPageSize.value,
    });
    if (requestId !== activeItemRequest.value) return;
    itemPage.value = nextPage;
    itemPageNo.value = nextPage.page;
    itemPageSize.value = nextPage.pageSize;
    loadedItems.value = true;
    if (!editingItem.value && itemForm.value.typeId !== selectedTypeId.value) {
      itemForm.value.typeId = selectedTypeId.value;
    }
  } catch (error) {
    if (requestId !== activeItemRequest.value) return;
    itemPage.value = null;
    loadedItems.value = false;
    itemListError.value = errorMessage(error);
  } finally {
    if (requestId === activeItemRequest.value) {
      loadingItems.value = false;
    }
  }
}

async function searchTypesFirstPage() {
  if (loadingTypes.value || mutatingType.value) return;
  typePageNo.value = 1;
  await refreshDictTypes();
}

async function searchItemsFirstPage() {
  if (loadingItems.value || mutatingItem.value) return;
  itemPageNo.value = 1;
  await refreshDictItems();
}

function selectType(row: AdminDictTypeRecord) {
  selectedTypeId.value = row.id;
  itemPageNo.value = 1;
  resetItemForm(row.id);
  void refreshDictItems();
}

async function saveType() {
  mutatingType.value = true;
  typeFormError.value = '';
  try {
    const command: AdminDictTypeCommand = {
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
    typeFormError.value = errorMessage(error);
  } finally {
    mutatingType.value = false;
  }
}

async function saveItem() {
  if (!selectedTypeId.value && !itemForm.value.typeId) {
    itemFormError.value = '请先选择字典类型';
    return;
  }
  mutatingItem.value = true;
  itemFormError.value = '';
  try {
    const command: AdminDictItemCommand = {
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
    resetItemForm(selectedTypeId.value);
    await refreshDictItems();
  } catch (error) {
    itemFormError.value = errorMessage(error);
  } finally {
    mutatingItem.value = false;
  }
}

async function toggleType(row: AdminDictTypeRecord) {
  mutatingType.value = true;
  typeFormError.value = '';
  try {
    await updateAdminDictType(row.id, {
      typeName: row.typeName,
      enabled: !row.enabled,
    });
    emit('notice', 'success', `${row.typeName} 已${row.enabled ? '停用' : '启用'}`);
    await refreshDictTypes();
  } catch (error) {
    typeFormError.value = errorMessage(error);
  } finally {
    mutatingType.value = false;
  }
}

async function toggleItem(row: AdminDictItemRecord) {
  mutatingItem.value = true;
  itemFormError.value = '';
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
    itemFormError.value = errorMessage(error);
  } finally {
    mutatingItem.value = false;
  }
}

async function previousTypePage() {
  if (loadingTypes.value || !hasPreviousTypePage.value) return;
  typePageNo.value -= 1;
  await refreshDictTypes();
}

async function nextTypePage() {
  if (loadingTypes.value || !hasNextTypePage.value) return;
  typePageNo.value += 1;
  await refreshDictTypes();
}

async function previousItemPage() {
  if (loadingItems.value || !hasPreviousItemPage.value) return;
  itemPageNo.value -= 1;
  await refreshDictItems();
}

async function nextItemPage() {
  if (loadingItems.value || !hasNextItemPage.value) return;
  itemPageNo.value += 1;
  await refreshDictItems();
}

watch(
  () => [props.active, props.activationKey] as const,
  ([active]) => {
    if (active && !loadedTypes.value) {
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
  <section class="dict-page">
    <div class="dict-layout">
      <AdminPanel class="dict-panel">
        <template #title>字典类型</template>
        <template #description>
          {{ loadedTypes ? `当前第 ${typePageNo} 页，共 ${formatNumber(totalTypes)} 个类型。` : '按类型名称或状态检索。' }}
        </template>

        <AdminToolbar>
          <label class="dict-field dict-field--keyword">
            <span>关键字</span>
            <input
              v-model="typeKeyword"
              class="dict-input"
              :disabled="loadingTypes || mutatingType"
              placeholder="类型名称 / 类型编码"
              @keyup.enter="searchTypesFirstPage"
            >
          </label>
          <label class="dict-field dict-field--status">
            <span>状态</span>
            <select
              v-model="typeEnabledFilter"
              class="dict-input"
              :disabled="loadingTypes || mutatingType"
              @change="searchTypesFirstPage"
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
              :disabled="loadingTypes || mutatingType"
              @click="searchTypesFirstPage"
            >
              {{ loadingTypes ? '查询中' : '查询' }}
            </t-button>
            <t-button
              theme="default"
              variant="outline"
              size="small"
              :disabled="!canExportTypes"
              @click="downloadTypeCsv"
            >
              导出当前页
            </t-button>
          </template>
        </AdminToolbar>

        <form class="dict-form" @submit.prevent="saveType">
          <p v-if="typeFormError" class="error-line" role="alert">{{ typeFormError }}</p>
          <div class="dict-form-grid">
            <label class="dict-field">
              <span>类型编码</span>
              <input
                v-model="typeForm.typeCode"
                class="dict-input"
                :disabled="editingType || loadingTypes || mutatingType"
                placeholder="DICT_TYPE"
              >
            </label>
            <label class="dict-field">
              <span>类型名称</span>
              <input
                v-model="typeForm.typeName"
                class="dict-input"
                :disabled="loadingTypes || mutatingType"
                placeholder="字典类型名称"
              >
            </label>
            <label class="dict-check">
              <input
                v-model="typeForm.enabled"
                type="checkbox"
                :disabled="loadingTypes || mutatingType"
              >
              <span>启用</span>
            </label>
          </div>
          <div class="dict-form-actions">
            <t-button
              theme="primary"
              variant="outline"
              size="small"
              type="submit"
              :disabled="loadingTypes || mutatingType"
            >
              {{ mutatingType ? '保存中' : editingType ? '保存类型' : '新增类型' }}
            </t-button>
            <t-button
              theme="default"
              variant="outline"
              size="small"
              type="button"
              :disabled="loadingTypes || mutatingType"
              @click="resetTypeForm"
            >
              清空
            </t-button>
          </div>
        </form>

        <AdminPageState
          v-if="typeListState === 'loading'"
          state="loading"
          message="正在查询字典类型。"
        />
        <AdminPageState
          v-else-if="typeListState === 'error'"
          state="error"
          :message="typeListError"
        />
        <AdminPageState
          v-else-if="typeListState === 'empty'"
          state="empty"
          message="没有相关字典类型。"
        />
        <template v-else>
          <AdminTableShell>
            <table class="dict-type-table">
              <thead>
                <tr>
                  <th>类型</th>
                  <th>状态</th>
                  <th>更新时间</th>
                  <th>操作</th>
                </tr>
              </thead>
              <tbody>
                <tr
                  v-for="row in typeRows"
                  :key="row.id"
                  :class="{ 'dict-row-selected': row.id === selectedTypeId }"
                >
                  <td>
                    <div class="primary-cell">
                      <strong>{{ row.typeName }}</strong>
                      <small>{{ row.typeCode }}</small>
                    </div>
                  </td>
                  <td>
                    <AdminStatusTag :enabled="row.enabled" />
                  </td>
                  <td>{{ formatDate(row.updatedAt) }}</td>
                  <td class="row-actions">
                    <t-button
                      theme="default"
                      variant="outline"
                      size="small"
                      :disabled="loadingTypes || mutatingType"
                      @click="selectType(row)"
                    >
                      {{ row.id === selectedTypeId ? '已选中' : '选择' }}
                    </t-button>
                    <t-button
                      theme="default"
                      variant="outline"
                      size="small"
                      :disabled="loadingTypes || mutatingType"
                      @click="editType(row)"
                    >
                      编辑
                    </t-button>
                    <t-button
                      theme="default"
                      variant="outline"
                      size="small"
                      :disabled="loadingTypes || mutatingType"
                      @click="toggleType(row)"
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
              :page="typePageNo"
              :page-size="typePageSize"
              :total="totalTypes"
              :loading="loadingTypes"
              @previous="previousTypePage"
              @next="nextTypePage"
            />
            <label class="page-size-field">
              <span>每页</span>
              <input
                v-model.number="typePageSize"
                class="dict-input dict-input--page-size"
                type="number"
                min="1"
                max="100"
                :disabled="loadingTypes || mutatingType"
                @keyup.enter="searchTypesFirstPage"
              >
            </label>
          </div>
        </template>
      </AdminPanel>

      <AdminPanel class="dict-panel">
        <template #title>字典项</template>
        <template #description>
          {{
            loadedItems
              ? `当前类型：${selectedType ? selectedType.typeName : '全部类型'}，第 ${itemPageNo} 页，共 ${formatNumber(totalItems)} 个字典项。`
              : '按关键字、状态和当前类型联动检索。'
          }}
        </template>

        <AdminToolbar>
          <label class="dict-field dict-field--keyword">
            <span>关键字</span>
            <input
              v-model="itemKeyword"
              class="dict-input"
              :disabled="loadingItems || mutatingItem"
              placeholder="项名称 / 项编码 / 项值"
              @keyup.enter="searchItemsFirstPage"
            >
          </label>
          <label class="dict-field dict-field--status">
            <span>状态</span>
            <select
              v-model="itemEnabledFilter"
              class="dict-input"
              :disabled="loadingItems || mutatingItem"
              @change="searchItemsFirstPage"
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
              :disabled="loadingItems || mutatingItem"
              @click="searchItemsFirstPage"
            >
              {{ loadingItems ? '查询中' : '查询' }}
            </t-button>
            <t-button
              theme="default"
              variant="outline"
              size="small"
              :disabled="!canExportItems"
              @click="downloadItemCsv"
            >
              导出当前页
            </t-button>
          </template>
        </AdminToolbar>

        <form class="dict-form" @submit.prevent="saveItem">
          <p v-if="itemFormError" class="error-line" role="alert">{{ itemFormError }}</p>
          <div class="dict-form-grid dict-form-grid--item">
            <label class="dict-field">
              <span>字典类型</span>
              <select
                v-model="itemForm.typeId"
                class="dict-input"
                :disabled="editingItem || loadingTypes || loadingItems || mutatingItem"
              >
                <option value="">当前选中类型</option>
                <option v-for="row in typeRows" :key="row.id" :value="row.id">
                  {{ row.typeName }}
                </option>
              </select>
            </label>
            <label class="dict-field">
              <span>项编码</span>
              <input
                v-model="itemForm.itemCode"
                class="dict-input"
                :disabled="editingItem || loadingTypes || loadingItems || mutatingItem"
                placeholder="ITEM_CODE"
              >
            </label>
            <label class="dict-field">
              <span>项名称</span>
              <input
                v-model="itemForm.itemName"
                class="dict-input"
                :disabled="loadingTypes || loadingItems || mutatingItem"
                placeholder="字典项名称"
              >
            </label>
            <label class="dict-field">
              <span>项值</span>
              <input
                v-model="itemForm.itemValue"
                class="dict-input"
                :disabled="loadingTypes || loadingItems || mutatingItem"
                placeholder="字典项值"
              >
            </label>
            <label class="dict-field">
              <span>排序</span>
              <input
                v-model.number="itemForm.sortNo"
                class="dict-input"
                type="number"
                min="0"
                :disabled="loadingTypes || loadingItems || mutatingItem"
              >
            </label>
            <label class="dict-check">
              <input
                v-model="itemForm.enabled"
                type="checkbox"
                :disabled="loadingTypes || loadingItems || mutatingItem"
              >
              <span>启用</span>
            </label>
            <label class="dict-field dict-field--full">
              <span>备注</span>
              <input
                v-model="itemForm.remark"
                class="dict-input"
                :disabled="loadingTypes || loadingItems || mutatingItem"
                placeholder="备注"
              >
            </label>
          </div>
          <div class="dict-form-actions">
            <t-button
              theme="primary"
              variant="outline"
              size="small"
              type="submit"
              :disabled="loadingTypes || loadingItems || mutatingItem"
            >
              {{ mutatingItem ? '保存中' : editingItem ? '保存字典项' : '新增字典项' }}
            </t-button>
            <t-button
              theme="default"
              variant="outline"
              size="small"
              type="button"
              :disabled="loadingTypes || loadingItems || mutatingItem"
              @click="resetItemForm(selectedTypeId)"
            >
              清空
            </t-button>
          </div>
        </form>

        <AdminPageState
          v-if="itemListState === 'loading'"
          state="loading"
          message="正在查询字典项。"
        />
        <AdminPageState
          v-else-if="itemListState === 'error'"
          state="error"
          :message="itemListError"
        />
        <AdminPageState
          v-else-if="itemListState === 'empty'"
          state="empty"
          message="没有相关字典项。"
        />
        <template v-else>
          <AdminTableShell>
            <table class="dict-item-table">
              <thead>
                <tr>
                  <th>字典项</th>
                  <th>所属类型</th>
                  <th>项值</th>
                  <th>排序</th>
                  <th>状态</th>
                  <th>更新时间</th>
                  <th>操作</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="row in itemRows" :key="row.id">
                  <td>
                    <div class="primary-cell">
                      <strong>{{ row.itemName }}</strong>
                      <small>{{ row.itemCode }}</small>
                    </div>
                  </td>
                  <td>
                    <div class="secondary-cell">
                      <strong>{{ row.typeName }}</strong>
                      <small>{{ row.typeCode }}</small>
                    </div>
                  </td>
                  <td>
                    <div class="value-cell" :title="displayText(row.itemValue)">
                      {{ displayText(row.itemValue) }}
                    </div>
                  </td>
                  <td>{{ row.sortNo }}</td>
                  <td>
                    <AdminStatusTag :enabled="row.enabled" />
                  </td>
                  <td>{{ formatDate(row.updatedAt) }}</td>
                  <td class="row-actions">
                    <t-button
                      theme="default"
                      variant="outline"
                      size="small"
                      :disabled="loadingItems || mutatingItem"
                      @click="editItem(row)"
                    >
                      编辑
                    </t-button>
                    <t-button
                      theme="default"
                      variant="outline"
                      size="small"
                      :disabled="loadingItems || mutatingItem"
                      @click="toggleItem(row)"
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
              :page="itemPageNo"
              :page-size="itemPageSize"
              :total="totalItems"
              :loading="loadingItems"
              @previous="previousItemPage"
              @next="nextItemPage"
            />
            <label class="page-size-field">
              <span>每页</span>
              <input
                v-model.number="itemPageSize"
                class="dict-input dict-input--page-size"
                type="number"
                min="1"
                max="100"
                :disabled="loadingItems || mutatingItem"
                @keyup.enter="searchItemsFirstPage"
              >
            </label>
          </div>
        </template>
      </AdminPanel>
    </div>
  </section>
</template>

<style scoped>
.dict-page {
  min-width: 0;
  overflow-x: hidden;
}

.dict-layout {
  display: grid;
  grid-template-columns: minmax(320px, 0.92fr) minmax(0, 1.28fr);
  gap: 12px;
  min-width: 0;
}

.dict-panel {
  min-width: 0;
}

.dict-form {
  display: grid;
  gap: 12px;
}

.dict-form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr)) minmax(92px, auto);
  gap: 12px;
  min-width: 0;
}

.dict-form-grid--item {
  grid-template-columns: repeat(3, minmax(0, 1fr)) minmax(92px, auto);
}

.dict-field {
  display: grid;
  gap: 6px;
  min-width: 0;
}

.dict-field--keyword {
  flex: 1 1 260px;
}

.dict-field--status {
  flex: 0 0 140px;
}

.dict-field--full {
  grid-column: 1 / -1;
}

.dict-field span,
.dict-check span,
.page-size-field span {
  color: #4b5563;
  font-size: 13px;
  line-height: 20px;
}

.dict-input {
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

.dict-input:disabled {
  color: #98a2b3;
  background: #f8fafc;
}

.dict-input--page-size {
  width: 92px;
}

.dict-check {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  min-height: 34px;
  padding-top: 24px;
}

.dict-form-actions,
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

.dict-type-table {
  min-width: 640px;
}

.dict-item-table {
  min-width: 960px;
}

.primary-cell,
.secondary-cell {
  display: grid;
  gap: 2px;
}

.primary-cell strong,
.secondary-cell strong {
  color: #111827;
  font-size: 13px;
  font-weight: 700;
  line-height: 20px;
}

.primary-cell small,
.secondary-cell small {
  color: #667085;
  font-size: 12px;
  line-height: 18px;
}

.dict-row-selected td {
  background: #f8fbff;
}

.dict-row-selected .primary-cell strong {
  color: #0052d9;
}

.value-cell {
  max-width: 260px;
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

@media (max-width: 1180px) {
  .dict-layout {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 960px) {
  .dict-form-grid,
  .dict-form-grid--item {
    grid-template-columns: 1fr;
  }

  .dict-check {
    padding-top: 0;
  }

  .page-size-field {
    margin-left: 0;
  }
}
</style>
