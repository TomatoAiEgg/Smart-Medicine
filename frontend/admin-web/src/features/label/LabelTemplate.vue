<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { errorMessage } from '../../domain/errors';
import {
  createAdminLabelTemplate,
  listAdminInstitutions,
  listAdminLabelTemplates,
  updateAdminLabelTemplate,
} from '../../api/order';
import type {
  AdminInstitutionRecord,
  AdminLabelTemplateRecord,
} from '../../api/types';
import { downloadCsv } from '../../domain/csv';
import { displayValue, formatDate, formatNumber } from '../../domain/formatters';

type NoticeTone = 'info' | 'success' | 'error';

const props = defineProps<{
  active: boolean;
  activationKey: number;
}>();

const emit = defineEmits<{
  notice: [tone: NoticeTone, text: string];
  countChanged: [count: number];
}>();

const prescriptionTypes = [
  { value: '', label: '通用' },
  { value: 'DECOCTION', label: '代煎' },
  { value: 'SELF_DECOCTION', label: '自煎' },
  { value: 'HERBAL_PIECE', label: '饮片' },
  { value: 'CREAM', label: '膏方' },
] as const;

const scopeTypes = [
  { value: 'GLOBAL', label: '全局' },
  { value: 'INSTITUTION', label: '机构' },
] as const;

const sampleData: Record<string, string> = {
  institutionName: '演示医院',
  prescriptionNo: 'RX202607280001',
  patientName: '张三',
  doseCount: '7',
  deliveryTime: '2026-07-28 18:00',
  receiverAddress: '广东省深圳市南山区演示地址',
};

const keyword = ref('');
const institutionId = ref('');
const prescriptionType = ref('');
const enabledFilter = ref('');
const page = ref(1);
const pageSize = ref(20);
const loading = ref(false);
const saving = ref(false);
const errorLine = ref('');
const templatePage = ref<{ records: AdminLabelTemplateRecord[]; total: number; page: number; pageSize: number } | null>(null);
const institutions = ref<AdminInstitutionRecord[]>([]);

const form = ref({
  id: '',
  templateCode: '',
  templateName: '',
  scopeType: 'GLOBAL',
  institutionId: '',
  prescriptionType: '',
  labelWidthMm: 90,
  labelHeightMm: 60,
  contentTemplate: '机构：{{institutionName}}\n处方：{{prescriptionNo}}\n患者：{{patientName}}\n剂数：{{doseCount}}\n配送：{{deliveryTime}}\n地址：{{receiverAddress}}',
  enabled: true,
});

const records = computed(() => templatePage.value?.records ?? []);
const total = computed(() => templatePage.value?.total ?? 0);
const hasPreviousPage = computed(() => page.value > 1 && !loading.value);
const hasNextPage = computed(() => !loading.value && page.value * pageSize.value < total.value);
const isEditing = computed(() => form.value.id !== '');
const previewText = computed(() => renderPreview(form.value.contentTemplate));

function enabledText(value: boolean) {
  return value ? '启用' : '停用';
}

function scopeText(value: string) {
  return scopeTypes.find((option) => option.value === value)?.label ?? value;
}

function prescriptionTypeText(value: string | null | undefined) {
  return prescriptionTypes.find((option) => option.value === (value ?? ''))?.label ?? displayValue(value);
}

function downloadTemplateCsv() {
  downloadCsv(
    `标签模板-第${page.value}页.csv`,
    [
      '模板编码',
      '模板名称',
      '范围',
      '机构',
      '处方类型',
      '宽度mm',
      '高度mm',
      '模板内容',
      '状态',
      '创建时间',
      '更新时间',
    ],
    records.value.map((record) => [
      record.templateCode,
      record.templateName,
      scopeText(record.scopeType),
      record.institutionName,
      prescriptionTypeText(record.prescriptionType),
      record.labelWidthMm,
      record.labelHeightMm,
      record.contentTemplate,
      enabledText(record.enabled),
      formatDate(record.createdAt),
      formatDate(record.updatedAt),
    ]),
  );
  emit('notice', 'success', `已导出本页 ${formatNumber(records.value.length)} 个标签模板`);
}

function normalizePageSize() {
  if (!Number.isFinite(pageSize.value) || pageSize.value <= 0) return 20;
  return Math.min(Math.trunc(pageSize.value), 100);
}

function renderPreview(template: string) {
  return Object.entries(sampleData).reduce(
    (content, [key, value]) => content.replaceAll(`{{${key}}}`, value),
    template,
  );
}

async function loadInstitutions() {
  const pageData = await listAdminInstitutions({ page: 1, pageSize: 100, status: 'ENABLED' });
  institutions.value = pageData.records;
}

async function refreshLabelTemplates() {
  loading.value = true;
  errorLine.value = '';
  pageSize.value = normalizePageSize();
  try {
    const nextPage = await listAdminLabelTemplates({
      keyword: keyword.value,
      institutionId: institutionId.value,
      prescriptionType: prescriptionType.value,
      enabled: enabledFilter.value,
      page: page.value,
      pageSize: pageSize.value,
    });
    templatePage.value = nextPage;
    page.value = nextPage.page;
    pageSize.value = nextPage.pageSize;
    emit('countChanged', nextPage.total);
    emit('notice', 'info', `已刷新标签模板：${nextPage.total} 条`);
  } catch (error) {
    templatePage.value = null;
    emit('countChanged', 0);
    errorLine.value = errorMessage(error);
  } finally {
    loading.value = false;
  }
}

async function searchFirstPage() {
  page.value = 1;
  await refreshLabelTemplates();
}

async function goPreviousPage() {
  if (!hasPreviousPage.value) return;
  page.value -= 1;
  await refreshLabelTemplates();
}

async function goNextPage() {
  if (!hasNextPage.value) return;
  page.value += 1;
  await refreshLabelTemplates();
}

function resetFilters() {
  keyword.value = '';
  institutionId.value = '';
  prescriptionType.value = '';
  enabledFilter.value = '';
  page.value = 1;
  void refreshLabelTemplates();
}

function resetForm() {
  form.value = {
    id: '',
    templateCode: '',
    templateName: '',
    scopeType: 'GLOBAL',
    institutionId: '',
    prescriptionType: '',
    labelWidthMm: 90,
    labelHeightMm: 60,
    contentTemplate: '机构：{{institutionName}}\n处方：{{prescriptionNo}}\n患者：{{patientName}}\n剂数：{{doseCount}}\n配送：{{deliveryTime}}\n地址：{{receiverAddress}}',
    enabled: true,
  };
}

function editTemplate(record: AdminLabelTemplateRecord) {
  form.value = {
    id: record.id,
    templateCode: record.templateCode,
    templateName: record.templateName,
    scopeType: record.scopeType,
    institutionId: record.institutionId ?? '',
    prescriptionType: record.prescriptionType ?? '',
    labelWidthMm: record.labelWidthMm,
    labelHeightMm: record.labelHeightMm,
    contentTemplate: record.contentTemplate,
    enabled: record.enabled,
  };
}

async function saveTemplate() {
  saving.value = true;
  errorLine.value = '';
  try {
    const command = {
      templateCode: form.value.templateCode.trim(),
      templateName: form.value.templateName.trim(),
      scopeType: form.value.scopeType,
      institutionId: form.value.scopeType === 'INSTITUTION' ? form.value.institutionId : null,
      prescriptionType: form.value.prescriptionType,
      labelWidthMm: Number.isFinite(form.value.labelWidthMm) ? form.value.labelWidthMm : 90,
      labelHeightMm: Number.isFinite(form.value.labelHeightMm) ? form.value.labelHeightMm : 60,
      contentTemplate: form.value.contentTemplate,
      enabled: form.value.enabled,
    };
    const saved = isEditing.value
      ? await updateAdminLabelTemplate(form.value.id, command)
      : await createAdminLabelTemplate(command);
    emit('notice', 'success', `${saved.templateName} 已保存`);
    resetForm();
    await refreshLabelTemplates();
  } catch (error) {
    errorLine.value = errorMessage(error);
  } finally {
    saving.value = false;
  }
}

async function toggleTemplate(record: AdminLabelTemplateRecord) {
  saving.value = true;
  errorLine.value = '';
  try {
    const updated = await updateAdminLabelTemplate(record.id, {
      templateName: record.templateName,
      scopeType: record.scopeType,
      institutionId: record.institutionId,
      prescriptionType: record.prescriptionType ?? '',
      labelWidthMm: record.labelWidthMm,
      labelHeightMm: record.labelHeightMm,
      contentTemplate: record.contentTemplate,
      enabled: !record.enabled,
    });
    emit('notice', 'success', `${updated.templateName} 已${enabledText(updated.enabled)}`);
    await refreshLabelTemplates();
  } catch (error) {
    errorLine.value = errorMessage(error);
  } finally {
    saving.value = false;
  }
}

watch(
  () => [props.active, props.activationKey] as const,
  ([active]) => {
    if (active) {
      void loadInstitutions();
      void refreshLabelTemplates();
    }
  },
  { immediate: true },
);

defineExpose({
  refreshLabelTemplates,
});
</script>

<template>
  <section class="legacy-page label-template-page">
    <ul class="legacy-search label-template-search">
      <li>
        关键字：
        <input v-model="keyword" class="legacy-input input-large" @keyup.enter="searchFirstPage" />
      </li>
      <li>
        机构：
        <select v-model="institutionId" class="legacy-input input-large" @change="searchFirstPage">
          <option value="">全部</option>
          <option v-for="institution in institutions" :key="institution.id" :value="institution.id">
            {{ institution.institutionName }}
          </option>
        </select>
      </li>
      <li>
        处方类型：
        <select v-model="prescriptionType" class="legacy-input input-medium" @change="searchFirstPage">
          <option v-for="option in prescriptionTypes" :key="option.value" :value="option.value">
            {{ option.label }}
          </option>
        </select>
      </li>
      <li>
        状态：
        <select v-model="enabledFilter" class="legacy-input input-small" @change="searchFirstPage">
          <option value="">全部</option>
          <option value="true">启用</option>
          <option value="false">停用</option>
        </select>
      </li>
      <li class="legacy-search-actions">
        <button class="legacy-btn legacy-btn-primary" type="button" :disabled="loading" @click="searchFirstPage">
          查询
        </button>
        <button class="legacy-btn" type="button" :disabled="loading" @click="resetFilters">
          重置
        </button>
        <button class="legacy-btn" type="button" :disabled="loading || records.length === 0" @click="downloadTemplateCsv">
          导出当前页
        </button>
      </li>
    </ul>

    <div v-if="errorLine" class="legacy-alert legacy-alert-error">{{ errorLine }}</div>

    <div class="legacy-stats">
      <span>模板总数：{{ total }}</span>
      <span>当前页：{{ records.length }}</span>
      <span>页码：{{ page }}</span>
    </div>

    <div class="template-layout">
      <section class="legacy-panel template-form-panel">
        <div class="legacy-panel-title">{{ isEditing ? '编辑标签模板' : '新增标签模板' }}</div>
        <div class="template-form-grid">
          <label>
            <span>模板编码</span>
            <input v-model="form.templateCode" class="legacy-input" :disabled="isEditing || saving" />
          </label>
          <label>
            <span>模板名称</span>
            <input v-model="form.templateName" class="legacy-input" :disabled="saving" />
          </label>
          <label>
            <span>作用范围</span>
            <select v-model="form.scopeType" class="legacy-input" :disabled="saving">
              <option v-for="option in scopeTypes" :key="option.value" :value="option.value">
                {{ option.label }}
              </option>
            </select>
          </label>
          <label>
            <span>机构</span>
            <select v-model="form.institutionId" class="legacy-input" :disabled="saving || form.scopeType !== 'INSTITUTION'">
              <option value="">请选择</option>
              <option v-for="institution in institutions" :key="institution.id" :value="institution.id">
                {{ institution.institutionName }}
              </option>
            </select>
          </label>
          <label>
            <span>处方类型</span>
            <select v-model="form.prescriptionType" class="legacy-input" :disabled="saving">
              <option v-for="option in prescriptionTypes" :key="option.value" :value="option.value">
                {{ option.label }}
              </option>
            </select>
          </label>
          <label>
            <span>宽度 mm</span>
            <input v-model.number="form.labelWidthMm" class="legacy-input" type="number" min="1" max="300" :disabled="saving" />
          </label>
          <label>
            <span>高度 mm</span>
            <input v-model.number="form.labelHeightMm" class="legacy-input" type="number" min="1" max="300" :disabled="saving" />
          </label>
          <label class="enabled-field">
            <input v-model="form.enabled" type="checkbox" :disabled="saving" />
            <span>启用</span>
          </label>
          <label class="template-content-field">
            <span>模板内容</span>
            <textarea v-model="form.contentTemplate" class="legacy-input template-editor" rows="9" :disabled="saving" />
          </label>
        </div>
        <div class="template-actions">
          <button class="legacy-btn legacy-btn-primary" type="button" :disabled="saving" @click="saveTemplate">
            保存
          </button>
          <button class="legacy-btn" type="button" :disabled="saving" @click="resetForm">
            清空
          </button>
        </div>
      </section>

      <section class="legacy-panel template-preview-panel">
        <div class="legacy-panel-title">标签预览</div>
        <div
          class="template-preview"
          :style="{ aspectRatio: `${form.labelWidthMm} / ${form.labelHeightMm}` }"
        >
          <pre>{{ previewText }}</pre>
        </div>
      </section>
    </div>

    <div class="legacy-table-wrap">
      <table class="legacy-table">
        <thead>
          <tr>
            <th>模板编码</th>
            <th>模板名称</th>
            <th>范围</th>
            <th>机构</th>
            <th>处方类型</th>
            <th>尺寸</th>
            <th>状态</th>
            <th>更新时间</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-if="!loading && records.length === 0">
            <td colspan="9" class="empty-cell">暂无标签模板</td>
          </tr>
          <tr v-for="record in records" :key="record.id">
            <td>{{ record.templateCode }}</td>
            <td>{{ record.templateName }}</td>
            <td>{{ scopeText(record.scopeType) }}</td>
            <td>{{ displayValue(record.institutionName) }}</td>
            <td>{{ prescriptionTypeText(record.prescriptionType) }}</td>
            <td>{{ record.labelWidthMm }} x {{ record.labelHeightMm }}</td>
            <td>{{ enabledText(record.enabled) }}</td>
            <td>{{ formatDate(record.updatedAt) }}</td>
            <td class="action-cell">
              <button class="legacy-link-btn" type="button" @click="editTemplate(record)">编辑</button>
              <button class="legacy-link-btn" type="button" @click="toggleTemplate(record)">
                {{ record.enabled ? '停用' : '启用' }}
              </button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <div class="legacy-pagination">
      <button class="legacy-btn" type="button" :disabled="!hasPreviousPage" @click="goPreviousPage">
        上一页
      </button>
      <span>第 {{ page }} 页 / 共 {{ total }} 条</span>
      <button class="legacy-btn" type="button" :disabled="!hasNextPage" @click="goNextPage">
        下一页
      </button>
      <label>
        每页
        <input v-model.number="pageSize" class="legacy-input input-small" type="number" min="1" max="100" @keyup.enter="searchFirstPage" />
      </label>
    </div>
  </section>
</template>

<style scoped>
.label-template-search {
  align-items: center;
}

.template-layout {
  display: grid;
  grid-template-columns: minmax(0, 1.15fr) minmax(320px, 0.85fr);
  gap: 16px;
  margin-bottom: 16px;
}

.template-form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.template-form-grid label {
  display: grid;
  gap: 6px;
  color: #475569;
  font-size: 13px;
}

.enabled-field {
  align-content: end;
  grid-template-columns: auto 1fr;
  align-items: center;
}

.template-content-field {
  grid-column: 1 / -1;
}

.template-editor {
  font-family: Consolas, "Microsoft YaHei", monospace;
  line-height: 1.5;
}

.template-actions {
  display: flex;
  gap: 8px;
  margin-top: 14px;
}

.template-preview {
  display: grid;
  width: min(100%, 460px);
  min-height: 220px;
  place-items: center;
  margin: 0 auto;
  padding: 18px;
  border: 1px solid #0f172a;
  background: #ffffff;
}

.template-preview pre {
  width: 100%;
  margin: 0;
  color: #0f172a;
  font-family: "Microsoft YaHei", Arial, sans-serif;
  font-size: 13px;
  line-height: 1.65;
  white-space: pre-wrap;
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

@media (max-width: 980px) {
  .template-layout {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 720px) {
  .template-form-grid {
    grid-template-columns: 1fr;
  }
}
</style>
