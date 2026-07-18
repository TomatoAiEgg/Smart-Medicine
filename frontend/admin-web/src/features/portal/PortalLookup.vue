<script setup lang="ts">
import { ref } from 'vue';
import { ApiError } from '../../api/client';
import { createAddressSupplement, queryPortalOrder } from '../../api/portal';
import type { AddressSupplementRecord, PortalOrderRecord } from '../../api/types';
import StatusPill from '../../components/StatusPill.vue';
import { formatDate } from '../../domain/formatters';
import { statusTone } from '../../domain/status';

type NoticeTone = 'info' | 'success' | 'error';

const emit = defineEmits<{
  notice: [tone: NoticeTone, text: string];
}>();

const portalOrderNo = ref('');
const portalExternalOrderNo = ref('');
const portalPhone = ref('');
const portalLoading = ref(false);
const portalError = ref('');
const portalOrder = ref<PortalOrderRecord | null>(null);
const latestAddressSupplement = ref<AddressSupplementRecord | null>(null);
const supplementReceiverName = ref('');
const supplementReceiverPhone = ref('');
const supplementReceiverProvince = ref('');
const supplementReceiverCity = ref('');
const supplementReceiverZone = ref('');
const supplementReceiverAddress = ref('');
const supplementRemark = ref('');

function errorMessage(error: unknown) {
  if (error instanceof ApiError) {
    return error.status ? `${error.message}（HTTP ${error.status}）` : error.message;
  }
  return error instanceof Error ? error.message : '请求失败';
}

async function handlePortalQuery() {
  if (!portalPhone.value.trim()) {
    portalError.value = '请输入患者或收件人手机号';
    portalOrder.value = null;
    return;
  }
  if (!portalOrderNo.value.trim() && !portalExternalOrderNo.value.trim()) {
    portalError.value = '请输入平台订单号或外部单号';
    portalOrder.value = null;
    return;
  }

  portalLoading.value = true;
  portalError.value = '';
  try {
    portalOrder.value = await queryPortalOrder({
      orderNo: portalOrderNo.value,
      externalOrderNo: portalExternalOrderNo.value,
      phone: portalPhone.value,
    });
    latestAddressSupplement.value = null;
    supplementReceiverName.value = portalOrder.value.receiverName || '';
    supplementReceiverPhone.value = portalOrder.value.receiverPhone || portalPhone.value;
    emit('notice', 'success', `已查询到门户订单 ${portalOrder.value.orderNo}`);
  } catch (error) {
    portalOrder.value = null;
    portalError.value = errorMessage(error);
  } finally {
    portalLoading.value = false;
  }
}

async function handleAddressSupplement() {
  if (!portalOrder.value) {
    portalError.value = '请先查询门户订单';
    return;
  }
  if (!supplementReceiverName.value.trim() || !supplementReceiverPhone.value.trim() || !supplementReceiverAddress.value.trim()) {
    portalError.value = '收件人、手机号和详细地址不能为空';
    return;
  }

  portalLoading.value = true;
  portalError.value = '';
  try {
    latestAddressSupplement.value = await createAddressSupplement(portalOrder.value.orderNo, {
      phone: portalPhone.value,
      receiverName: supplementReceiverName.value.trim(),
      receiverPhone: supplementReceiverPhone.value.trim(),
      receiverProvince: supplementReceiverProvince.value.trim() || undefined,
      receiverCity: supplementReceiverCity.value.trim() || undefined,
      receiverZone: supplementReceiverZone.value.trim() || undefined,
      receiverAddress: supplementReceiverAddress.value.trim(),
      requesterName: portalOrder.value.patientName || undefined,
      requesterPhone: portalPhone.value.trim(),
      remark: supplementRemark.value.trim() || undefined,
    });
    emit('notice', 'success', `${latestAddressSupplement.value.orderNo} 地址补录申请已提交`);
  } catch (error) {
    portalError.value = errorMessage(error);
  } finally {
    portalLoading.value = false;
  }
}

defineExpose({
  handlePortalQuery,
});
</script>

<template>
  <section class="workspace">
    <div class="toolbar">
      <label>
        <span>平台订单号</span>
        <input v-model="portalOrderNo" placeholder="ZHYF..." @keyup.enter="handlePortalQuery" />
      </label>
      <label>
        <span>外部单号</span>
        <input v-model="portalExternalOrderNo" placeholder="机构单号" @keyup.enter="handlePortalQuery" />
      </label>
      <label>
        <span>手机号</span>
        <input v-model="portalPhone" placeholder="患者或收件人手机号" @keyup.enter="handlePortalQuery" />
      </label>
      <button class="primary" type="button" :disabled="portalLoading" @click="handlePortalQuery">
        {{ portalLoading ? '查询中' : '查单' }}
      </button>
    </div>

    <p v-if="portalError" class="error-line">{{ portalError }}</p>

    <div v-if="portalOrder" class="detail-grid">
      <div>
        <span>医院</span>
        <strong>{{ portalOrder.institutionName }}</strong>
      </div>
      <div>
        <span>平台订单号</span>
        <strong>{{ portalOrder.orderNo }}</strong>
      </div>
      <div>
        <span>外部单号</span>
        <strong>{{ portalOrder.externalOrderNo }}</strong>
      </div>
      <div>
        <span>订单状态</span>
        <StatusPill :value="portalOrder.orderStatus" :tone="statusTone(portalOrder.orderStatus)" />
      </div>
      <div>
        <span>患者</span>
        <strong>{{ portalOrder.patientName || '-' }}</strong>
      </div>
      <div>
        <span>收件人</span>
        <strong>{{ portalOrder.receiverName || '-' }} / {{ portalOrder.receiverPhone || '-' }}</strong>
      </div>
      <div class="wide">
        <span>收件地址</span>
        <strong>{{ portalOrder.receiverAddress || '-' }}</strong>
      </div>
      <div>
        <span>物流状态</span>
        <StatusPill :value="portalOrder.shipment?.logisticsStatus || '未发货'" :tone="statusTone(portalOrder.shipment?.logisticsStatus || '')" />
      </div>
    </div>

    <div v-if="portalOrder" class="toolbar event-toolbar">
      <label>
        <span>收件人</span>
        <input v-model="supplementReceiverName" placeholder="收件人姓名" />
      </label>
      <label>
        <span>收件手机号</span>
        <input v-model="supplementReceiverPhone" placeholder="收件手机号" />
      </label>
      <label>
        <span>省</span>
        <input v-model="supplementReceiverProvince" placeholder="广东省" />
      </label>
      <label>
        <span>市</span>
        <input v-model="supplementReceiverCity" placeholder="深圳市" />
      </label>
      <label>
        <span>区</span>
        <input v-model="supplementReceiverZone" placeholder="南山区" />
      </label>
      <label class="grow">
        <span>详细地址</span>
        <input v-model="supplementReceiverAddress" placeholder="街道、门牌号" />
      </label>
      <label class="grow">
        <span>备注</span>
        <input v-model="supplementRemark" placeholder="补录来源或说明" @keyup.enter="handleAddressSupplement" />
      </label>
      <button class="secondary" type="button" :disabled="portalLoading" @click="handleAddressSupplement">
        提交补录
      </button>
    </div>

    <div v-if="portalOrder" class="table-wrap">
      <table>
        <thead>
          <tr>
            <th>处方号</th>
            <th>状态</th>
            <th>类型</th>
            <th>医生</th>
            <th>诊断</th>
          </tr>
        </thead>
        <tbody>
          <tr v-if="portalOrder.prescriptions.length === 0">
            <td colspan="5" class="empty">暂无处方记录</td>
          </tr>
          <tr v-for="prescription in portalOrder.prescriptions" :key="prescription.prescriptionNo">
            <td>{{ prescription.prescriptionNo }}</td>
            <td><StatusPill :value="prescription.prescriptionStatus" :tone="statusTone(prescription.prescriptionStatus)" /></td>
            <td>{{ prescription.prescriptionType || '-' }}</td>
            <td>{{ prescription.doctorName || '-' }}</td>
            <td>{{ prescription.diagnosis || '-' }}</td>
          </tr>
        </tbody>
      </table>
    </div>

    <div v-if="latestAddressSupplement" class="detail-grid">
      <div>
        <span>补录状态</span>
        <StatusPill :value="latestAddressSupplement.supplementStatus" :tone="statusTone(latestAddressSupplement.supplementStatus)" />
      </div>
      <div>
        <span>提交时间</span>
        <strong>{{ formatDate(latestAddressSupplement.createdAt) }}</strong>
      </div>
      <div class="wide">
        <span>补录地址</span>
        <strong>
          {{ latestAddressSupplement.receiverProvince || '' }}{{ latestAddressSupplement.receiverCity || '' }}{{ latestAddressSupplement.receiverZone || '' }}{{ latestAddressSupplement.receiverAddress }}
        </strong>
      </div>
    </div>
  </section>
</template>
