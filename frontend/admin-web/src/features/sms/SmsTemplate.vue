<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { errorMessage } from '../../domain/errors';
import { createSmsTemplate, listSmsTemplates, updateSmsTemplate } from '../../api/sms';
import type { SmsTemplateRecord } from '../../api/types';
import { downloadCsv } from '../../domain/csv';
import { formatDate, formatNumber } from '../../domain/formatters';

type NoticeTone = 'info' | 'success' | 'error';

const props = defineProps<{
  active: boolean;
  activationKey: number;
}>();

const emit = defineEmits<{
  notice: [tone: NoticeTone, text: string];
  countChanged: [count: number];
}>();

const templateTypes = [
  { value: '', label: '全部' },
  { value: 'ORDER', label: '订单通知' },
  { value: 'LOGISTICS', label: '物流通知' },
  { value: 'REVIEW', label: '审核通知' },
  { value: 'CUSTOM', label: '自定义' },
] as const;

const formTemplateTypes = templateTypes.filter((option) => option.value !== '');
const placeholderHints = ['{{orderNo}}', '{{orderStatus}}', '{{patientName}}', '{{logisticsNo}}', '{{institutionName}}'];

const sampleData: Record<string, string> = {
  orderNo: 'ZHYF202607280001',
  orderStatus: '待审核',
  patientName: '张三',
  logisticsNo: 'SF123456789',
  institutionName: '演示医院',
};

const keyword = ref('');
const templateType = ref('');
const enabledFilter = ref('');
const page = ref(1);
const pageSize = ref(20);
const loading = ref(false);
const saving = ref(false);
const errorLine = ref('');
const templatePage = ref<{ records: SmsTemplateRecord[]; total: number; page: number; pageSize: number } | null>(null);

const form = ref({
  id: '',
  templateCode: '',
  templateName: '',
  templateType: 'ORDER',
  signature: '智慧药房',
  contentTemplate: '您的处方订单{{orderNo}}已创建，当前状态：{{orderStatus}}。',
  enabled: true,
});

const records = computed(() => templatePage.value?.records ?? []);
const total = computed(() => templatePage.value?.total ?? 0);
const hasPreviousPage = computed(() => page.value > 1 && !loading.value);
const hasNextPage = computed(() => !loading.value && page.value * pageSize.value < total.value);
const isEditing = computed(() => form.value.id !== '');
const previewText = computed(() => renderPreview(form.value.contentTemplate));

function rowValue(value: string | null | undefined) {
  if (value === null || value === undefined || value === '') return '-';
  return value;
}

function enabledText(value: boolean) {
  return value ? '启用' : '停用';
}

function templateTypeText(value: string) {
  return templateTypes.find((option) => option.value === value)?.label ?? value;
}

function downloadTemplateCsv() {
  downloadCsv(
    `短信模板-第${page.value}页.csv`,
    ['模板编码', '模板名称', '类型', '签名', '模板内容', '状态', '创建时间', '更新时间'],
    records.value.map((record) => [
      record.templateCode,
      record.templateName,
      templateTypeText(record.templateType),
      record.signature,
      record.contentTemplate,
      enabledText(record.enabled),
      formatDate(record.createdAt),
      formatDate(record.updatedAt),
    ]),
  );
  emit('notice', 'success', `已导出本页 ${formatNumber(records.value.length)} 个短信模板`);
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

async function refreshSmsTemplates() {
  loading.value = true;
  errorLine.value = '';
  pageSize.value = normalizePageSize();
  try {
    const nextPage = await listSmsTemplates({
      keyword: keyword.value,
      templateType: templateType.value,
      enabled: enabledFilter.value,
      page: page.value,
      pageSize: pageSize.value,
    });
    templatePage.value = nextPage;
    page.value = nextPage.page;
    pageSize.value = nextPage.pageSize;
    emit('countChanged', nextPage.total);
    emit('notice', 'info', `已刷新短信模板：${nextPage.total} 条`);
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
  await refreshSmsTemplates();
}

async function goPreviousPage() {
  if (!hasPreviousPage.value) return;
  page.value -= 1;
  await refreshSmsTemplates();
}

async function goNextPage() {
  if (!hasNextPage.value) return;
  page.value += 1;
  await refreshSmsTemplates();
}

function resetFilters() {
  keyword.value = '';
  templateType.value = '';
  enabledFilter.value = '';
  page.value = 1;
  void refreshSmsTemplates();
}

function resetForm() {
  form.value = {
    id: '',
    templateCode: '',
    templateName: '',
    templateType: 'ORDER',
    signature: '智慧药房',
    contentTemplate: '您的处方订单{{orderNo}}已创建，当前状态：{{orderStatus}}。',
    enabled: true,
  };
}

function editTemplate(record: SmsTemplateRecord) {
  form.value = {
    id: record.id,
    templateCode: record.templateCode,
    templateName: record.templateName,
    templateType: record.templateType,
    signature: record.signature ?? '',
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
      templateType: form.value.templateType,
      signature: form.value.signature.trim(),
      contentTemplate: form.value.contentTemplate,
      enabled: form.value.enabled,
    };
    const saved = isEditing.value
      ? await updateSmsTemplate(form.value.id, command)
      : await createSmsTemplate(command);
    emit('notice', 'success', `${saved.templateName} 已保存`);
    resetForm();
    await refreshSmsTemplates();
  } catch (error) {
    errorLine.value = errorMessage(error);
  } finally {
    saving.value = false;
  }
}

async function toggleTemplate(record: SmsTemplateRecord) {
  saving.value = true;
  errorLine.value = '';
  try {
    const updated = await updateSmsTemplate(record.id, {
      templateName: record.templateName,
      templateType: record.templateType,
      signature: record.signature ?? '',
      contentTemplate: record.contentTemplate,
      enabled: !record.enabled,
    });
    emit('notice', 'success', `${updated.templateName} 已${enabledText(updated.enabled)}`);
    await refreshSmsTemplates();
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
      void refreshSmsTemplates();
    }
  },
  { immediate: true },
);

defineExpose({
  refreshSmsTemplates,
});
</script>

<template>
  <section class="legacy-page sms-template-page">
    <ul class="legacy-search sms-template-search">
      <li>
        关键字：
        <input v-model="keyword" class="legacy-input input-large" @keyup.enter="searchFirstPage" />
      </li>
      <li>
        类型：
        <select v-model="templateType" class="legacy-input input-medium" @change="searchFirstPage">
          <option v-for="option in templateTypes" :key="option.value" :value="option.value">
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
        <div class="legacy-panel-title">{{ isEditing ? '编辑短信模板' : '新增短信模板' }}</div>
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
            <span>模板类型</span>
            <select v-model="form.templateType" class="legacy-input" :disabled="saving">
              <option v-for="option in formTemplateTypes" :key="option.value" :value="option.value">
                {{ option.label }}
              </option>
            </select>
          </label>
          <label>
            <span>签名</span>
            <input v-model="form.signature" class="legacy-input" :disabled="saving" />
          </label>
          <label class="enabled-field">
            <input v-model="form.enabled" type="checkbox" :disabled="saving" />
            <span>启用</span>
          </label>
          <label class="template-content-field">
            <span>模板内容</span>
            <textarea v-model="form.contentTemplate" class="legacy-input template-editor" rows="7" :disabled="saving" />
          </label>
        </div>
        <div class="placeholder-hints">
          <span v-for="hint in placeholderHints" :key="hint">{{ hint }}</span>
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
        <div class="legacy-panel-title">短信预览</div>
        <div class="sms-preview">
          <div class="sms-preview-signature" v-if="form.signature">【{{ form.signature }}】</div>
          <p>{{ previewText }}</p>
        </div>
      </section>
    </div>

    <div class="legacy-table-wrap">
      <table class="legacy-table">
        <thead>
          <tr>
            <th>模板编码</th>
            <th>模板名称</th>
            <th>类型</th>
            <th>签名</th>
            <th>内容</th>
            <th>状态</th>
            <th>更新时间</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-if="!loading && records.length === 0">
            <td colspan="8" class="empty-cell">暂无短信模板</td>
          </tr>
          <tr v-for="record in records" :key="record.id">
            <td>{{ record.templateCode }}</td>
            <td>{{ record.templateName }}</td>
            <td>{{ templateTypeText(record.templateType) }}</td>
            <td>{{ rowValue(record.signature) }}</td>
            <td class="content-cell">{{ record.contentTemplate }}</td>
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
.sms-template-search {
  align-items: center;
}

.template-layout {
  display: grid;
  grid-template-columns: minmax(0, 1.05fr) minmax(320px, 0.95fr);
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

.placeholder-hints,
.template-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 14px;
}

.placeholder-hints span {
  padding: 4px 8px;
  border: 1px solid #cbd5e1;
  border-radius: 4px;
  color: #475569;
  background: #f8fafc;
  font-family: Consolas, monospace;
  font-size: 12px;
}

.sms-preview {
  min-height: 190px;
  padding: 16px;
  border: 1px solid #cbd5e1;
  border-radius: 6px;
  background: #f8fafc;
  color: #0f172a;
  line-height: 1.7;
  word-break: break-word;
}

.sms-preview-signature {
  margin-bottom: 8px;
  color: #1d4ed8;
  font-weight: 600;
}

.sms-preview p {
  margin: 0;
  white-space: pre-wrap;
}

.content-cell {
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
