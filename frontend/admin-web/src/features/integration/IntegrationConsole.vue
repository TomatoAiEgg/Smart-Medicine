<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { ApiError } from '../../api/client';
import {
  createCommunityStatusPush,
  dispatchDueIntegrationRetryTasks,
  findHospitalOrderByPrescription,
  listIntegrationMessages,
  listIntegrationRetryTasks,
  recordAddressPush,
  recordCommunityMessage,
} from '../../api/integration';
import type { HospitalOrderRecord, IntegrationMessageRecord, IntegrationRetryTaskRecord } from '../../api/types';
import StatusPill from '../../components/StatusPill.vue';
import { formatDate } from '../../domain/formatters';
import { statusTone } from '../../domain/status';

type NoticeTone = 'info' | 'success' | 'error';

const emit = defineEmits<{
  notice: [tone: NoticeTone, text: string];
  countChanged: [count: number];
}>();

const props = defineProps<{
  active: boolean;
  activationKey: number;
}>();

const integrationLoading = ref(false);
const integrationError = ref('');
const integrationLimit = ref(50);
const integrationSourceType = ref('');
const integrationStatus = ref('');
const integrationBusinessKey = ref('');
const integrationMessages = ref<IntegrationMessageRecord[]>([]);
const integrationRetryTasks = ref<IntegrationRetryTaskRecord[]>([]);
const integrationRetryStatus = ref('');
const integrationRetryType = ref('');
const integrationDispatchLimit = ref(20);
const communityAreaCode = ref('LG');
const communityCode = ref('CH-001');
const communityExternalMessageId = ref('');
const communityMessageType = ref('ORDER_CREATED');
const communityBusinessKey = ref('');
const communityRawPayload = ref('{}');
const communityStatusOrderNo = ref('');
const communityStatus = ref('SIGNED');
const communityStatusRequestUrl = ref('');
const communityStatusPayload = ref('{}');
const addressSupplementId = ref('');
const addressHospitalCode = ref('HOSP-001');
const addressAdapterCode = ref('LGFY');
const addressOrderNo = ref('');
const addressRawPayload = ref('{}');
const addressRequestUrl = ref('');
const hospitalPrescriptionNo = ref('');
const hospitalQueryPhone = ref('');
const hospitalOrder = ref<HospitalOrderRecord | null>(null);

const integrationCount = computed(() => integrationMessages.value.length + integrationRetryTasks.value.length);

function errorMessage(error: unknown) {
  if (error instanceof ApiError) {
    return error.status ? `${error.message}（HTTP ${error.status}）` : error.message;
  }
  return error instanceof Error ? error.message : '请求失败';
}

function normalizedIntegrationLimit() {
  if (!Number.isFinite(integrationLimit.value) || integrationLimit.value <= 0) return 50;
  return Math.min(Math.trunc(integrationLimit.value), 200);
}

async function refreshIntegrationMessages() {
  integrationLoading.value = true;
  integrationError.value = '';
  const limit = normalizedIntegrationLimit();
  integrationLimit.value = limit;
  try {
    const [messages, retryTasks] = await Promise.all([
      listIntegrationMessages({
        sourceType: integrationSourceType.value,
        processStatus: integrationStatus.value,
        businessKey: integrationBusinessKey.value,
        limit,
      }),
      listIntegrationRetryTasks({
        taskType: integrationRetryType.value,
        taskStatus: integrationRetryStatus.value,
        businessKey: integrationBusinessKey.value,
        limit,
      }),
    ]);
    integrationMessages.value = messages;
    integrationRetryTasks.value = retryTasks;
    emit('notice', 'info', `已刷新集成适配：消息 ${integrationMessages.value.length} 条，重试 ${integrationRetryTasks.value.length} 条`);
  } catch (error) {
    integrationError.value = errorMessage(error);
  } finally {
    integrationLoading.value = false;
  }
}

async function handleCommunityMessage() {
  if (!communityCode.value.trim() || !communityExternalMessageId.value.trim() || !communityMessageType.value.trim()) {
    integrationError.value = '社康编码、外部消息 ID 和消息类型不能为空';
    return;
  }
  integrationLoading.value = true;
  integrationError.value = '';
  try {
    const record = await recordCommunityMessage({
      areaCode: communityAreaCode.value.trim() || undefined,
      communityCode: communityCode.value.trim(),
      externalMessageId: communityExternalMessageId.value.trim(),
      messageType: communityMessageType.value.trim(),
      businessKey: communityBusinessKey.value.trim() || undefined,
      rawPayload: communityRawPayload.value,
    });
    integrationBusinessKey.value = record.businessKey || integrationBusinessKey.value;
    emit('notice', 'success', `社康消息已记录：${record.externalMessageId}`);
    await refreshIntegrationMessages();
  } catch (error) {
    integrationError.value = errorMessage(error);
  } finally {
    integrationLoading.value = false;
  }
}

async function handleAddressPushRecord() {
  if (!addressSupplementId.value.trim() || !addressHospitalCode.value.trim() || !addressAdapterCode.value.trim() || !addressOrderNo.value.trim()) {
    integrationError.value = '补录 ID、医院编码、适配器和订单号不能为空';
    return;
  }
  integrationLoading.value = true;
  integrationError.value = '';
  try {
    const record = await recordAddressPush({
      supplementId: addressSupplementId.value.trim(),
      hospitalCode: addressHospitalCode.value.trim(),
      adapterCode: addressAdapterCode.value.trim(),
      orderNo: addressOrderNo.value.trim(),
      rawPayload: addressRawPayload.value,
      requestUrl: addressRequestUrl.value.trim() || undefined,
    });
    integrationBusinessKey.value = record.businessKey || addressOrderNo.value.trim();
    emit('notice', 'success', `地址回推记录已写入：${record.externalMessageId}`);
    await refreshIntegrationMessages();
  } catch (error) {
    integrationError.value = errorMessage(error);
  } finally {
    integrationLoading.value = false;
  }
}

async function handleCommunityStatusPush() {
  if (!communityCode.value.trim() || !communityStatusOrderNo.value.trim() || !communityStatus.value.trim() || !communityStatusRequestUrl.value.trim()) {
    integrationError.value = '社康编码、订单号、状态和目标 URL 不能为空';
    return;
  }
  integrationLoading.value = true;
  integrationError.value = '';
  try {
    const record = await createCommunityStatusPush({
      communityCode: communityCode.value.trim(),
      orderNo: communityStatusOrderNo.value.trim(),
      status: communityStatus.value.trim(),
      requestUrl: communityStatusRequestUrl.value.trim(),
      rawPayload: communityStatusPayload.value,
    });
    integrationBusinessKey.value = record.businessKey || communityStatusOrderNo.value.trim();
    emit('notice', 'success', `社康状态回写任务已创建：${record.externalMessageId}`);
    await refreshIntegrationMessages();
  } catch (error) {
    integrationError.value = errorMessage(error);
  } finally {
    integrationLoading.value = false;
  }
}

async function handleDispatchIntegrationRetryTasks() {
  integrationLoading.value = true;
  integrationError.value = '';
  try {
    const handled = await dispatchDueIntegrationRetryTasks(integrationDispatchLimit.value);
    emit('notice', 'success', `已派发到期集成重试任务 ${handled} 条`);
    await refreshIntegrationMessages();
  } catch (error) {
    integrationError.value = errorMessage(error);
  } finally {
    integrationLoading.value = false;
  }
}

async function handleHospitalPrescriptionQuery() {
  if (!hospitalPrescriptionNo.value.trim() || !hospitalQueryPhone.value.trim()) {
    integrationError.value = '处方号和手机号不能为空';
    hospitalOrder.value = null;
    return;
  }
  integrationLoading.value = true;
  integrationError.value = '';
  try {
    hospitalOrder.value = await findHospitalOrderByPrescription(
      hospitalPrescriptionNo.value.trim(),
      hospitalQueryPhone.value.trim(),
    );
    emit('notice', 'success', `已查询到处方订单 ${hospitalOrder.value.orderNo}`);
  } catch (error) {
    hospitalOrder.value = null;
    integrationError.value = errorMessage(error);
  } finally {
    integrationLoading.value = false;
  }
}

watch(integrationCount, (count) => emit('countChanged', count), { immediate: true });

watch(
  () => [props.active, props.activationKey] as const,
  ([active]) => {
    if (active && integrationMessages.value.length === 0) {
      void refreshIntegrationMessages();
    }
  },
  { immediate: true },
);

defineExpose({
  refreshIntegrationMessages,
});
</script>

<template>
  <section class="workspace">
    <div class="toolbar">
      <label>
        <span>来源类型</span>
        <input v-model="integrationSourceType" placeholder="COMMUNITY_HOSPITAL / ADDRESS_PUSH" @keyup.enter="refreshIntegrationMessages" />
      </label>
      <label>
        <span>处理状态</span>
        <input v-model="integrationStatus" placeholder="PENDING / FAILED" @keyup.enter="refreshIntegrationMessages" />
      </label>
      <label class="grow">
        <span>业务键</span>
        <input v-model="integrationBusinessKey" placeholder="订单号 / 社康业务号" @keyup.enter="refreshIntegrationMessages" />
      </label>
      <label>
        <span>任务类型</span>
        <input v-model="integrationRetryType" placeholder="ADDRESS_PUSH / COMMUNITY_STATUS_PUSH" @keyup.enter="refreshIntegrationMessages" />
      </label>
      <label>
        <span>任务状态</span>
        <input v-model="integrationRetryStatus" placeholder="PENDING / FAILED / DEAD" @keyup.enter="refreshIntegrationMessages" />
      </label>
      <label class="limit-label">
        <span>条数</span>
        <input v-model.number="integrationLimit" type="number" min="1" max="200" step="10" @keyup.enter="refreshIntegrationMessages" />
      </label>
      <label class="limit-label">
        <span>派发数</span>
        <input v-model.number="integrationDispatchLimit" type="number" min="1" max="200" step="10" />
      </label>
      <button class="primary" type="button" :disabled="integrationLoading" @click="refreshIntegrationMessages">
        {{ integrationLoading ? '刷新中' : '刷新' }}
      </button>
      <button class="secondary" type="button" :disabled="integrationLoading" @click="handleDispatchIntegrationRetryTasks">
        派发到期
      </button>
    </div>

    <div class="toolbar event-toolbar">
      <label>
        <span>区域</span>
        <input v-model="communityAreaCode" placeholder="LG" />
      </label>
      <label>
        <span>社康编码</span>
        <input v-model="communityCode" placeholder="CH-001" />
      </label>
      <label>
        <span>外部消息 ID</span>
        <input v-model="communityExternalMessageId" placeholder="MSG-001" />
      </label>
      <label>
        <span>消息类型</span>
        <input v-model="communityMessageType" placeholder="ORDER_CREATED" />
      </label>
      <label>
        <span>业务键</span>
        <input v-model="communityBusinessKey" placeholder="CH-ORDER-001" />
      </label>
      <label class="grow payload-input">
        <span>原始报文</span>
        <input v-model="communityRawPayload" placeholder="{...} / XML" @keyup.enter="handleCommunityMessage" />
      </label>
      <button class="secondary" type="button" :disabled="integrationLoading" @click="handleCommunityMessage">
        记录社康消息
      </button>
    </div>

    <div class="toolbar event-toolbar">
      <label>
        <span>补录 ID</span>
        <input v-model="addressSupplementId" placeholder="UUID" />
      </label>
      <label>
        <span>医院编码</span>
        <input v-model="addressHospitalCode" placeholder="HOSP-001" />
      </label>
      <label>
        <span>适配器</span>
        <input v-model="addressAdapterCode" placeholder="LGFY" />
      </label>
      <label>
        <span>订单号</span>
        <input v-model="addressOrderNo" placeholder="ZHYF..." />
      </label>
      <label class="grow payload-input">
        <span>回推报文</span>
        <input v-model="addressRawPayload" placeholder="{...}" @keyup.enter="handleAddressPushRecord" />
      </label>
      <label class="grow payload-input">
        <span>目标 URL</span>
        <input v-model="addressRequestUrl" placeholder="http://..." @keyup.enter="handleAddressPushRecord" />
      </label>
      <button class="secondary" type="button" :disabled="integrationLoading" @click="handleAddressPushRecord">
        记录地址回推
      </button>
    </div>

    <div class="toolbar event-toolbar">
      <label>
        <span>社康编码</span>
        <input v-model="communityCode" placeholder="CH-001" />
      </label>
      <label>
        <span>订单号</span>
        <input v-model="communityStatusOrderNo" placeholder="ZHYF..." />
      </label>
      <label>
        <span>回写状态</span>
        <input v-model="communityStatus" placeholder="SIGNED / CANCELLED" />
      </label>
      <label class="grow payload-input">
        <span>目标 URL</span>
        <input v-model="communityStatusRequestUrl" placeholder="http://..." />
      </label>
      <label class="grow payload-input">
        <span>回写报文</span>
        <input v-model="communityStatusPayload" placeholder="{...}" @keyup.enter="handleCommunityStatusPush" />
      </label>
      <button class="secondary" type="button" :disabled="integrationLoading" @click="handleCommunityStatusPush">
        创建社康回写
      </button>
    </div>

    <div class="toolbar event-toolbar">
      <label>
        <span>处方号</span>
        <input v-model="hospitalPrescriptionNo" placeholder="RX..." @keyup.enter="handleHospitalPrescriptionQuery" />
      </label>
      <label>
        <span>手机号</span>
        <input v-model="hospitalQueryPhone" placeholder="患者或收件人手机号" @keyup.enter="handleHospitalPrescriptionQuery" />
      </label>
      <button class="secondary" type="button" :disabled="integrationLoading" @click="handleHospitalPrescriptionQuery">
        处方查单
      </button>
    </div>

    <p v-if="integrationError" class="error-line">{{ integrationError }}</p>

    <div v-if="hospitalOrder" class="detail-grid">
      <div>
        <span>医院</span>
        <strong>{{ hospitalOrder.institutionName }}</strong>
      </div>
      <div>
        <span>平台订单号</span>
        <strong>{{ hospitalOrder.orderNo }}</strong>
      </div>
      <div>
        <span>外部单号</span>
        <strong>{{ hospitalOrder.externalOrderNo }}</strong>
      </div>
      <div>
        <span>订单状态</span>
        <StatusPill :value="hospitalOrder.orderStatus" :tone="statusTone(hospitalOrder.orderStatus)" />
      </div>
      <div>
        <span>处方</span>
        <strong>{{ hospitalOrder.prescriptionNo }}</strong>
        <small>{{ hospitalOrder.prescriptionStatus }}</small>
      </div>
      <div>
        <span>收件人</span>
        <strong>{{ hospitalOrder.receiverName || '-' }} / {{ hospitalOrder.receiverPhone || '-' }}</strong>
      </div>
      <div class="wide">
        <span>收件地址</span>
        <strong>{{ hospitalOrder.receiverAddress || '-' }}</strong>
      </div>
      <div>
        <span>物流</span>
        <strong>{{ hospitalOrder.logisticsCompany || '-' }} / {{ hospitalOrder.logisticsNo || '-' }}</strong>
        <small>{{ hospitalOrder.logisticsStatus || '未发货' }}</small>
      </div>
    </div>

    <div class="table-wrap integration-table">
      <table>
        <thead>
          <tr>
            <th>任务/目标</th>
            <th>业务键</th>
            <th>状态</th>
            <th>重试</th>
            <th>时间</th>
            <th>响应</th>
          </tr>
        </thead>
        <tbody>
          <tr v-if="!integrationLoading && integrationRetryTasks.length === 0">
            <td colspan="6" class="empty">暂无集成重试任务</td>
          </tr>
          <tr v-for="task in integrationRetryTasks" :key="task.taskId">
            <td>
              <strong>{{ task.taskType }} / {{ task.targetSystem }}</strong>
              <small>{{ task.requestUrl }}</small>
            </td>
            <td>
              <strong>{{ task.businessKey || '-' }}</strong>
              <small>{{ task.messageId }}</small>
            </td>
            <td><StatusPill :value="task.taskStatus" :tone="statusTone(task.taskStatus)" /></td>
            <td>
              <strong>{{ task.retryCount }}</strong>
              <small>{{ formatDate(task.nextRetryAt) }}</small>
            </td>
            <td>
              <strong>{{ formatDate(task.createdAt) }}</strong>
              <small>{{ formatDate(task.processedAt) }}</small>
            </td>
            <td><code>{{ task.responseBody || task.requestBody || '-' }}</code></td>
          </tr>
        </tbody>
      </table>
    </div>

    <div class="table-wrap integration-table">
      <table>
        <thead>
          <tr>
            <th>来源/外部消息</th>
            <th>类型/业务键</th>
            <th>状态</th>
            <th>时间</th>
            <th>载荷</th>
            <th>失败原因</th>
          </tr>
        </thead>
        <tbody>
          <tr v-if="!integrationLoading && integrationMessages.length === 0">
            <td colspan="6" class="empty">暂无集成适配消息</td>
          </tr>
          <tr v-for="message in integrationMessages" :key="message.messageId">
            <td>
              <strong>{{ message.sourceType }} / {{ message.sourceSystem }}</strong>
              <small>{{ message.externalMessageId }}</small>
            </td>
            <td>
              <strong>{{ message.messageType }}</strong>
              <small>{{ message.businessKey || '-' }}</small>
            </td>
            <td><StatusPill :value="message.processStatus" :tone="statusTone(message.processStatus)" /></td>
            <td>
              <strong>{{ formatDate(message.createdAt) }}</strong>
              <small>{{ formatDate(message.updatedAt) }}</small>
            </td>
            <td><code>{{ message.normalizedPayload || message.rawPayload || '-' }}</code></td>
            <td><code>{{ message.failureReason || '-' }}</code></td>
          </tr>
        </tbody>
      </table>
    </div>
  </section>
</template>
