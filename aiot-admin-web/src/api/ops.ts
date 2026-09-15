import http from '@/api/http';
import type {
  AlarmRecord,
  AuditRecord,
  ControlPlaneDrainReq,
  ControlPlaneDrainResp,
  DashboardOverview,
  DeadLetterListResp,
  WorkOrderRecord,
  WorkOrderResolveReq
} from '@/types/ops';

export function getAlarms(params: Record<string, unknown>) {
  return http.get<AlarmRecord[], AlarmRecord[]>('/api/v1/admin/alarms', { params });
}

export function acknowledgeAlarm(alarmId: string, operator: string) {
  return http.post<void, void>(`/api/v1/admin/alarms/${alarmId}/ack`, undefined, {
    params: { operator }
  });
}

export function resolveAlarm(alarmId: string, operator: string) {
  return http.post<void, void>(`/api/v1/admin/alarms/${alarmId}/resolve`, undefined, {
    params: { operator }
  });
}

export function getWorkOrders(params: Record<string, unknown>) {
  return http.get<WorkOrderRecord[], WorkOrderRecord[]>('/api/v1/admin/work-orders', { params });
}

export function claimWorkOrder(workOrderId: string, assignee: string) {
  return http.post<void, void>(`/api/v1/admin/work-orders/${workOrderId}/claim`, undefined, {
    params: { assignee }
  });
}

export function resolveWorkOrder(workOrderId: string, data: WorkOrderResolveReq) {
  return http.post<void, void>(`/api/v1/admin/work-orders/${workOrderId}/resolve`, data);
}

export function getAudits(limit?: number) {
  return http.get<AuditRecord[], AuditRecord[]>('/api/v1/admin/audits', {
    params: limit ? { limit } : undefined
  });
}

export function getDeadLetters(count = 100) {
  return http.get<DeadLetterListResp, DeadLetterListResp>('/api/v1/ops/dlq', {
    params: { count }
  });
}

export function replayDeadLetter(recordId: string) {
  return http.post<{ recordId: string; replayedRecordId: string }, { recordId: string; replayedRecordId: string }>(
    `/api/v1/ops/dlq/${recordId}/replay`
  );
}

export function purgeDeadLetters() {
  return http.delete<{ purgedCount: number }, { purgedCount: number }>('/api/v1/ops/dlq');
}

export function checkWorkOrderSla() {
  return http.post<number, number>('/api/v1/admin/work-orders/sla/check');
}

export function getDashboardOverview() {
  return http.get<DashboardOverview, DashboardOverview>('/api/v1/admin/dashboard/overview');
}

export function drainAiControlPlane(data: ControlPlaneDrainReq) {
  return http.post<ControlPlaneDrainResp, ControlPlaneDrainResp>(
    '/api/v1/admin/ai/persistence/control-plane/drain',
    data
  );
}
