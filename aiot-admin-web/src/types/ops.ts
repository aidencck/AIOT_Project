export interface AlarmRecord {
  alarmId: string;
  ruleId: string;
  eventId?: string;
  deviceId?: string;
  globalDeviceId?: string;
  authIdentity?: string;
  deviceSn?: string;
  eventType?: string;
  level?: string;
  status?: string;
  occurredAt?: number;
  acknowledgedAt?: number;
  acknowledgedBy?: string;
  createdAt?: number;
  updatedAt?: number;
}

export interface WorkOrderRecord {
  workOrderId: string;
  alarmId?: string;
  deviceId?: string;
  globalDeviceId?: string;
  authIdentity?: string;
  deviceSn?: string;
  priority?: string;
  status?: string;
  assignee?: string;
  respondedAt?: number;
  resolvedAt?: number;
  result?: string;
  slaMinutes?: number;
  dueAt?: number;
  slaBreached?: boolean;
  createdAt?: number;
  updatedAt?: number;
}

export interface WorkOrderResolveReq {
  operator: string;
  result: string;
}

export interface AuditRecord {
  auditId: string;
  eventType: string;
  operator: string;
  targetId: string;
  details: string;
  traceId: string;
  createdAt: number;
}

export interface DeadLetterRecord {
  recordId: string;
  payload?: string;
  [k: string]: unknown;
}

export interface DeadLetterListResp {
  count: number;
  deadLetters: DeadLetterRecord[];
}

export interface DashboardOverview {
  onlineDeviceCount?: number;
  offlineDeviceCount?: number;
  todayAlarmCount?: number;
  pendingWorkOrderCount?: number;
  oneTimeResolveRate?: number;
}

export interface ControlPlaneDrainReq {
  operator: string;
  dryRun?: boolean;
  batchSize?: number;
  stores?: string[];
}

export interface ControlPlaneDrainResp {
  accepted?: boolean;
  dryRun?: boolean;
  batchSize?: number;
  message?: string;
  [k: string]: unknown;
}
