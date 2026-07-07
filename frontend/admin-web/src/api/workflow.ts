import { request } from './client';
import type { OrderReviewCommand, OrderReviewResult, WorkflowTaskSnapshot } from './types';

export function listReviewTasks() {
  return request<WorkflowTaskSnapshot[]>('/workflow-api/api/admin/workflow/review-tasks');
}

export function approveReviewTask(taskId: string, command: OrderReviewCommand) {
  return request<OrderReviewResult>(`/workflow-api/api/admin/workflow/review-tasks/${taskId}/approve`, {
    method: 'PATCH',
    body: JSON.stringify(command),
  });
}

export function rejectReviewTask(taskId: string, command: OrderReviewCommand) {
  return request<OrderReviewResult>(`/workflow-api/api/admin/workflow/review-tasks/${taskId}/reject`, {
    method: 'PATCH',
    body: JSON.stringify(command),
  });
}

export function listRecheckTasks() {
  return request<WorkflowTaskSnapshot[]>('/workflow-api/api/admin/workflow/recheck-tasks');
}

export function completeRecheckTask(taskId: string, command: OrderReviewCommand) {
  return request<OrderReviewResult>(`/workflow-api/api/admin/workflow/recheck-tasks/${taskId}/complete`, {
    method: 'PATCH',
    body: JSON.stringify(command),
  });
}
