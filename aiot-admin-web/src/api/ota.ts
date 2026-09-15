import http from '@/api/http';
import type { FirmwarePackageCreateReq, FirmwarePackageResp, OtaUpgradeTaskCreateReq } from '@/types/ota';

export function listFirmwarePackages(productKey?: string) {
  return http.get<FirmwarePackageResp[], FirmwarePackageResp[]>('/api/v1/ota/firmware-packages', {
    params: productKey ? { productKey } : undefined
  });
}

export function createFirmwarePackage(data: FirmwarePackageCreateReq) {
  return http.post<string, string>('/api/v1/ota/firmware-packages', data);
}

export function createOtaTask(data: OtaUpgradeTaskCreateReq) {
  return http.post<string, string>('/api/v1/ota/tasks', data);
}

export function deleteFirmwarePackage(packageId: string) {
  return http.delete<void, void>(`/api/v1/ota/firmware-packages/${packageId}`);
}

export function cancelOtaTask(taskId: string) {
  return http.post<void, void>(`/api/v1/ota/tasks/${taskId}/cancel`);
}
