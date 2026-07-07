import { request } from './client';
import type {
  DecoctionEventCommand,
  DecoctionTaskEventRecord,
  DecoctionTaskRecord,
  DeviceRecord,
  DeviceWorkRecord,
  MesTaskOperationCommand,
  PrescriptionRecord,
  SimulatorOperationCommand,
} from './types';

export function listCanOperatePrescriptions(limit = 50) {
  return request<PrescriptionRecord[]>(`/decoction-api/simulator/pda/prescriptions/can-operate?limit=${limit}`);
}

export function listDecoctionDevices() {
  return request<DeviceRecord[]>('/decoction-api/simulator/pda/decoction/devices');
}

export function bindPrescription(command: SimulatorOperationCommand) {
  return request<DecoctionTaskRecord>('/decoction-api/simulator/pda/bind-prescription', {
    method: 'POST',
    body: JSON.stringify(command),
  });
}

export function listActiveMesTasks() {
  return request<DecoctionTaskRecord[]>('/decoction-api/simulator/mes/tasks/active');
}

export function startMesTask(taskNo: string, command: MesTaskOperationCommand) {
  return request<DecoctionTaskRecord>(`/decoction-api/simulator/mes/tasks/${encodeURIComponent(taskNo)}/start`, {
    method: 'POST',
    body: JSON.stringify(command),
  });
}

export function finishMesTask(taskNo: string, command: MesTaskOperationCommand) {
  return request<DecoctionTaskRecord>(`/decoction-api/simulator/mes/tasks/${encodeURIComponent(taskNo)}/finish`, {
    method: 'POST',
    body: JSON.stringify(command),
  });
}

export function cancelMesTask(taskNo: string, command: DecoctionEventCommand) {
  return request<DecoctionTaskRecord>(`/decoction-api/simulator/mes/tasks/${encodeURIComponent(taskNo)}/cancel`, {
    method: 'POST',
    body: JSON.stringify(command),
  });
}

export function terminateMesTask(taskNo: string, command: DecoctionEventCommand) {
  return request<DecoctionTaskRecord>(`/decoction-api/simulator/mes/tasks/${encodeURIComponent(taskNo)}/terminate`, {
    method: 'POST',
    body: JSON.stringify(command),
  });
}

export function recordWaterFinished(taskNo: string, command: DecoctionEventCommand) {
  return request<DecoctionTaskEventRecord>(`/decoction-api/simulator/mes/tasks/${encodeURIComponent(taskNo)}/water-finish`, {
    method: 'POST',
    body: JSON.stringify(command),
  });
}

export function recordTemperature(taskNo: string, command: DecoctionEventCommand) {
  return request<DecoctionTaskEventRecord>(`/decoction-api/simulator/mes/tasks/${encodeURIComponent(taskNo)}/temperature`, {
    method: 'POST',
    body: JSON.stringify(command),
  });
}

export function recordTaskError(taskNo: string, command: DecoctionEventCommand) {
  return request<DecoctionTaskEventRecord>(`/decoction-api/simulator/mes/tasks/${encodeURIComponent(taskNo)}/error`, {
    method: 'POST',
    body: JSON.stringify(command),
  });
}

export function listTaskEvents(taskNo: string) {
  return request<DecoctionTaskEventRecord[]>(`/decoction-api/simulator/mes/tasks/${encodeURIComponent(taskNo)}/events`);
}

export function listDeviceWorkRecords(taskNo: string) {
  return request<DeviceWorkRecord[]>(`/decoction-api/simulator/mes/tasks/${encodeURIComponent(taskNo)}/work-records`);
}
