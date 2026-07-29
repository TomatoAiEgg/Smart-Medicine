<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { ApiError } from '../../api/client';
import { listSmsTemplates, sendSingleSms } from '../../api/sms';
import type { SmsSendResult, SmsTemplateRecord } from '../../api/types';
import { downloadCsv } from '../../domain/csv';
import { formatDate } from '../../domain/formatters';

type NoticeTone = 'info' | 'success' | 'error';

const props = defineProps<{
  active: boolean;
  activationKey: number;
}>();

const emit = defineEmits<{
  notice: [tone: NoticeTone, text: string];
}>();

const placeholderPattern = /\{\{\s*([A-Za-z0-9_]+)\s*}}/g;
const sampleValues: Record<string, string> = {
  orderNo: 'ZHYF202607280001',
  orderStatus: '待审核',
  patientName: '张三',
  logisticsNo: 'SF123456789',
  institutionName: '演示医院',
};

const loading = ref(false);
const sending = ref(false);
const errorLine = ref('');
const templates = ref<SmsTemplateRecord[]>([]);
const selectedTemplateId = ref('');
const receiverPhone = ref('');
const receiverName = ref('');
const relatedOrderNo = ref('');
const operator = ref('admin');
const variableValues = ref<Record<string, string>>({});
const sendResult = ref<SmsSendResult | null>(null);

const selectedTemplate = computed(() => templates.value.find((template) => template.id === selectedTemplateId.value) ?? null);
const placeholderKeys = computed(() => extractPlaceholders(selectedTemplate.value?.contentTemplate ?? ''));
const hasTemplates = computed(() => templates.value.length > 0);
const previewText = computed(() => renderPreview(selectedTemplate.value?.contentTemplate ?? ''));

function errorMessage(error: unknown) {
  if (error instanceof ApiError) {
    return error.status ? `${error.message}（HTTP ${error.status}）` : error.message;
  }
  return error instanceof Error ? error.message : '请求失败';
}

function extractPlaceholders(template: string) {
  const keys = new Set<string>();
  for (const match of template.matchAll(placeholderPattern)) {
    keys.add(match[1]);
  }
  return Array.from(keys);
}

function ensureVariables() {
  const nextValues: Record<string, string> = {};
  placeholderKeys.value.forEach((key) => {
    nextValues[key] = variableValues.value[key] ?? sampleValues[key] ?? '';
  });
  variableValues.value = nextValues;
}

function renderPreview(template: string) {
  return placeholderKeys.value.reduce(
    (content, key) => content.replaceAll(`{{${key}}}`, variableValues.value[key] ?? ''),
    template,
  );
}

function sendStatusText(value: string) {
  if (value === 'SIMULATED') return '已登记（未真实发送）';
  if (value === 'SUCCESS') return '发送成功';
  if (value === 'FAILED') return '发送失败';
  return value;
}

function downloadSendResultCsv() {
  const result = sendResult.value;
  if (!result) return;
  downloadCsv(
    `单发短信登记-${result.id}.csv`,
    ['模板编码', '模板名称', '接收手机号', '接收人', '关联订单', '签名', '短信内容', '状态', '服务商流水', '失败原因', '重试次数', '操作人', '登记时间', '发送时间', '更新时间'],
    [[
      result.templateCode,
      result.templateName,
      result.receiverPhone,
      result.receiverName,
      result.relatedOrderNo,
      result.signature,
      result.content,
      sendStatusText(result.sendStatus),
      result.providerMessageId,
      result.failureReason,
      result.retryCount,
      result.operator,
      formatDate(result.createdAt),
      formatDate(result.sentAt),
      formatDate(result.updatedAt),
    ]],
  );
  emit('notice', 'success', `${result.receiverPhone} 短信登记结果已导出`);
}

function resetForm() {
  receiverPhone.value = '';
  receiverName.value = '';
  relatedOrderNo.value = '';
  operator.value = 'admin';
  sendResult.value = null;
  ensureVariables();
}

function variablesForSubmit() {
  return placeholderKeys.value.reduce<Record<string, string>>((variables, key) => {
    variables[key] = variableValues.value[key] ?? '';
    return variables;
  }, {});
}

async function refreshSingleSmsSend() {
  loading.value = true;
  errorLine.value = '';
  try {
    const pageData = await listSmsTemplates({ enabled: 'true', page: 1, pageSize: 100 });
    templates.value = pageData.records;
    if (!templates.value.some((template) => template.id === selectedTemplateId.value)) {
      selectedTemplateId.value = templates.value[0]?.id ?? '';
    }
    ensureVariables();
    emit('notice', 'info', `已加载可用短信模板：${templates.value.length} 个`);
  } catch (error) {
    templates.value = [];
    selectedTemplateId.value = '';
    errorLine.value = errorMessage(error);
  } finally {
    loading.value = false;
  }
}

async function submitSingleSms() {
  if (!selectedTemplate.value) {
    errorLine.value = '请选择短信模板';
    return;
  }
  sending.value = true;
  errorLine.value = '';
  try {
    const result = await sendSingleSms({
      templateId: selectedTemplate.value.id,
      receiverPhone: receiverPhone.value.trim(),
      receiverName: receiverName.value.trim(),
      relatedOrderNo: relatedOrderNo.value.trim(),
      variables: variablesForSubmit(),
      operator: operator.value.trim(),
    });
    sendResult.value = result;
    emit('notice', 'success', `${result.receiverPhone} 已登记短信发送记录`);
  } catch (error) {
    errorLine.value = errorMessage(error);
  } finally {
    sending.value = false;
  }
}

watch(selectedTemplateId, () => {
  ensureVariables();
  sendResult.value = null;
});

watch(
  () => [props.active, props.activationKey] as const,
  ([active]) => {
    if (active) {
      void refreshSingleSmsSend();
    }
  },
  { immediate: true },
);

defineExpose({
  refreshSingleSmsSend,
});
</script>

<template>
  <section class="legacy-page single-sms-page">
    <div v-if="errorLine" class="legacy-alert legacy-alert-error">{{ errorLine }}</div>

    <div class="single-sms-layout">
      <section class="legacy-panel single-sms-form-panel">
        <div class="legacy-panel-title">单发短信登记</div>
        <div class="single-sms-form-grid">
          <label>
            <span>短信模板</span>
            <select v-model="selectedTemplateId" class="legacy-input" :disabled="loading || sending">
              <option value="">请选择</option>
              <option v-for="template in templates" :key="template.id" :value="template.id">
                {{ template.templateName }}（{{ template.templateCode }}）
              </option>
            </select>
          </label>
          <label>
            <span>接收手机号</span>
            <input v-model="receiverPhone" class="legacy-input" :disabled="sending" />
          </label>
          <label>
            <span>接收人</span>
            <input v-model="receiverName" class="legacy-input" :disabled="sending" />
          </label>
          <label>
            <span>关联订单号</span>
            <input v-model="relatedOrderNo" class="legacy-input" :disabled="sending" />
          </label>
          <label>
            <span>操作人</span>
            <input v-model="operator" class="legacy-input" :disabled="sending" />
          </label>
        </div>

        <div class="variable-section">
          <div class="legacy-panel-subtitle">模板变量</div>
          <div v-if="placeholderKeys.length === 0" class="empty-inline">当前模板没有变量</div>
          <div v-else class="variable-grid">
            <label v-for="key in placeholderKeys" :key="key">
              <span>{{ key }}</span>
              <input v-model="variableValues[key]" class="legacy-input" :disabled="sending" />
            </label>
          </div>
        </div>

        <div class="single-sms-actions">
          <button
            class="legacy-btn legacy-btn-primary"
            type="button"
            :disabled="sending || loading || !hasTemplates"
            @click="submitSingleSms"
          >
            登记发送
          </button>
          <button class="legacy-btn" type="button" :disabled="sending" @click="resetForm">
            清空
          </button>
          <button class="legacy-btn" type="button" :disabled="loading || sending" @click="refreshSingleSmsSend">
            刷新模板
          </button>
          <button class="legacy-btn" type="button" :disabled="sending || !sendResult" @click="downloadSendResultCsv">
            导出登记结果
          </button>
        </div>
      </section>

      <section class="legacy-panel single-sms-preview-panel">
        <div class="legacy-panel-title">短信预览</div>
        <div v-if="selectedTemplate" class="sms-preview">
          <div class="sms-preview-signature" v-if="selectedTemplate.signature">【{{ selectedTemplate.signature }}】</div>
          <p>{{ previewText }}</p>
        </div>
        <div v-else class="empty-inline">暂无可用模板</div>
      </section>
    </div>

    <section v-if="sendResult" class="legacy-panel send-result-panel">
      <div class="legacy-panel-title">发送登记结果</div>
      <div class="result-grid">
        <span>接收手机号：{{ sendResult.receiverPhone }}</span>
        <span>模板：{{ sendResult.templateName }}</span>
        <span>状态：{{ sendStatusText(sendResult.sendStatus) }}</span>
        <span>登记时间：{{ formatDate(sendResult.createdAt) }}</span>
        <span>操作人：{{ sendResult.operator || '-' }}</span>
        <span>关联订单：{{ sendResult.relatedOrderNo || '-' }}</span>
      </div>
      <div class="result-content">{{ sendResult.content }}</div>
    </section>
  </section>
</template>

<style scoped>
.single-sms-layout {
  display: grid;
  grid-template-columns: minmax(0, 1.1fr) minmax(320px, 0.9fr);
  gap: 16px;
  margin-bottom: 16px;
}

.single-sms-form-grid,
.variable-grid,
.result-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.single-sms-form-grid label,
.variable-grid label {
  display: grid;
  gap: 6px;
  color: #475569;
  font-size: 13px;
}

.variable-section {
  margin-top: 16px;
}

.empty-inline {
  padding: 14px;
  border: 1px dashed #cbd5e1;
  border-radius: 6px;
  color: #64748b;
  background: #f8fafc;
}

.single-sms-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 16px;
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

.sms-preview p,
.result-content {
  margin: 0;
  white-space: pre-wrap;
}

.result-grid {
  color: #475569;
  font-size: 13px;
}

.result-content {
  margin-top: 12px;
  padding: 12px;
  border: 1px solid #cbd5e1;
  border-radius: 6px;
  background: #f8fafc;
  color: #0f172a;
  line-height: 1.7;
}

@media (max-width: 980px) {
  .single-sms-layout {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 720px) {
  .single-sms-form-grid,
  .variable-grid,
  .result-grid {
    grid-template-columns: 1fr;
  }
}
</style>
