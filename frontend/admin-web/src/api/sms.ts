import { request } from './client';
import type {
  SmsSendCommand,
  SmsSendResult,
  SmsTemplateCommand,
  SmsTemplatePage,
  SmsTemplateQueryParams,
  SmsTemplateRecord,
} from './types';

function buildSmsQuery(params: SmsTemplateQueryParams) {
  const query = new URLSearchParams();
  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== null && String(value).trim() !== '') {
      query.set(key, String(value).trim());
    }
  });
  return query.toString();
}

export function listSmsTemplates(params: SmsTemplateQueryParams = {}) {
  const query = buildSmsQuery(params);
  const url = query ? `/message-api/api/admin/sms/templates?${query}` : '/message-api/api/admin/sms/templates';
  return request<SmsTemplatePage>(url);
}

export function createSmsTemplate(command: SmsTemplateCommand) {
  return request<SmsTemplateRecord>('/message-api/api/admin/sms/templates', {
    method: 'POST',
    body: JSON.stringify(command),
  });
}

export function updateSmsTemplate(templateId: string, command: SmsTemplateCommand) {
  return request<SmsTemplateRecord>(`/message-api/api/admin/sms/templates/${encodeURIComponent(templateId)}`, {
    method: 'PATCH',
    body: JSON.stringify(command),
  });
}

export function sendSingleSms(command: SmsSendCommand) {
  return request<SmsSendResult>('/message-api/api/admin/sms/send-single', {
    method: 'POST',
    body: JSON.stringify(command),
  });
}
