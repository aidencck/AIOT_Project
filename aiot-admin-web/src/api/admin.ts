import http from '@/api/http';
import type {
  AdminAiEvalReport,
  AdminBusinessLiveFlow,
  AdminDeviceRecord,
  AdminOtaTaskRecord,
  AdminPersistenceQueryResponse,
  AdminWorkbench,
  DeviceCreateReq,
  DeviceUpdateReq,
  PageResponse
} from '@/types/admin';

export function getWorkbench(homeId?: string) {
  return http.get<AdminWorkbench, AdminWorkbench>('/api/v1/admin-console/workbench', {
    params: homeId ? { homeId } : undefined
  });
}

export function getDevicePage(params: Record<string, unknown>) {
  return http.get<PageResponse<AdminDeviceRecord>, PageResponse<AdminDeviceRecord>>(
    '/api/v1/admin-console/devices/page',
    { params }
  );
}

export function createDevice(data: DeviceCreateReq) {
  return http.post<AdminDeviceRecord, AdminDeviceRecord>('/api/v1/devices', data);
}

export function updateDevice(deviceId: string, data: DeviceUpdateReq) {
  return http.put<void, void>(`/api/v1/devices/${deviceId}`, data);
}

export function deleteDevice(deviceId: string) {
  return http.delete<void, void>(`/api/v1/devices/${deviceId}`);
}

export function getOtaPage(params: Record<string, unknown>) {
  return http.get<PageResponse<AdminOtaTaskRecord>, PageResponse<AdminOtaTaskRecord>>(
    '/api/v1/admin-console/ota/tasks/page',
    { params }
  );
}

export function getAiEvalReports(sceneTypes?: string[]) {
  return http.get<AdminAiEvalReport[], AdminAiEvalReport[]>(
    '/api/v1/admin-console/ai/evals/regression-gate',
    { params: sceneTypes && sceneTypes.length ? { sceneTypes } : undefined }
  );
}

export function getAiBusinessLiveFlow() {
  return http.get<AdminBusinessLiveFlow, AdminBusinessLiveFlow>(
    '/api/v1/admin-console/ai/persistence/business-live-flow'
  );
}

export function getAiPersistenceQuery() {
  return http.get<AdminPersistenceQueryResponse, AdminPersistenceQueryResponse>(
    '/api/v1/admin-console/ai/persistence/query'
  );
}
