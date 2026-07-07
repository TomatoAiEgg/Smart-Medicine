import type { ApiResponse } from './types';

export class ApiError extends Error {
  readonly code: string;
  readonly status?: number;

  constructor(message: string, code = 'REQUEST_FAILED', status?: number) {
    super(message);
    this.name = 'ApiError';
    this.code = code;
    this.status = status;
  }
}

export async function request<T>(url: string, init?: RequestInit): Promise<T> {
  let response: Response;

  try {
    response = await fetch(url, {
      headers: {
        'Content-Type': 'application/json',
        ...init?.headers,
      },
      ...init,
    });
  } catch (error) {
    throw new ApiError(error instanceof Error ? error.message : '服务连接失败');
  }

  const contentType = response.headers.get('content-type') || '';
  const payload = contentType.includes('application/json')
    ? ((await response.json()) as ApiResponse<T>)
    : null;

  if (!response.ok) {
    throw new ApiError(payload?.message || `HTTP ${response.status}`, payload?.code, response.status);
  }

  if (!payload) {
    throw new ApiError('服务返回不是 JSON');
  }

  if (payload.code !== '0') {
    throw new ApiError(payload.message || '业务处理失败', payload.code);
  }

  return payload.data;
}
