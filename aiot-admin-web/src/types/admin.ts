export interface AdminOverview {
  homeCount: number;
  memberCount: number;
  productCount: number;
  deviceCount: number;
  otaTaskCount: number;
  todayAlarmCount: number;
  pendingWorkOrderCount: number;
  oneTimeResolveRate: number;
  aiEvalReadyCount: number;
  aiEvalPassedCount: number;
  aiPersistenceReadMode?: string;
  aiPersistenceConfiguredReadMode?: string;
  aiPersistenceMysqlCutoverReady?: boolean;
  aiPersistenceMysqlCutoverBlockReason?: string;
}

export interface AdminAiEvalReport {
  sceneType: string;
  exists: boolean;
  gatePassed: boolean;
  generatedAt?: number | null;
  content?: Record<string, unknown>;
}

export interface AdminClosureStage {
  stageKey: string;
  stageName: string;
  status: 'READY' | 'ATTENTION' | 'OPTIONAL';
  summary: string;
  actionHint: string;
}

export interface AdminDeviceRecord {
  id: string;
  deviceName?: string;
  homeId?: string;
  productKey?: string;
  status?: number;
  firmwareVersion?: string;
}

export interface DeviceCreateReq {
  deviceName: string;
  productKey: string;
  homeId: string;
  globalDeviceId?: string;
  deviceSn?: string;
  authIdentity?: string;
  roomId?: string;
  gatewayId?: string;
  firmwareVersion?: string;
}

export interface DeviceUpdateReq {
  deviceName?: string;
  globalDeviceId?: string;
  deviceSn?: string;
  authIdentity?: string;
  roomId?: string;
  gatewayId?: string;
  firmwareVersion?: string;
}

export interface PageResponse<T> {
  total: number;
  pageNo: number;
  pageSize: number;
  records: T[];
}

export interface AdminOtaTaskRecord {
  taskId: string;
  homeId?: string;
  productKey?: string;
  targetVersion?: string;
  status?: number;
  successCount?: number;
  failedCount?: number;
  totalCount?: number;
}

export interface AdminBusinessLiveFlow {
  reportExists?: boolean;
  reportPath?: string;
  verifiedAt?: number;
  scene?: string;
  success?: boolean;
  globalDeviceId?: string;
  deviceId?: string;
  authIdentity?: string;
  deviceSn?: string;
  eventId?: string;
  diagnosisId?: string;
  feedbackId?: string;
  caseId?: string;
}

export interface AdminHistoryItem {
  reportType: string;
  occurredAt: number;
  operator?: string;
  scene?: string;
  success?: boolean;
  accepted?: boolean;
  dryRun?: boolean;
  reportPath?: string;
  message?: string;
}

export interface AdminPersistenceQueryResponse {
  businessLiveFlow?: {
    recentHistory?: AdminHistoryItem[];
  };
  controlPlaneDrain?: {
    recentHistory?: AdminHistoryItem[];
  };
}

export interface HomeOption {
  id: string;
  name?: string;
  homeName?: string;
}

export interface AdminWorkbench {
  selectedHomeId: string;
  closureScore: number;
  homes: HomeOption[];
  members: Array<Record<string, unknown>>;
  products: Array<Record<string, unknown>>;
  devices: AdminDeviceRecord[];
  otaTasks: AdminOtaTaskRecord[];
  opsOverview: Record<string, unknown>;
  aiEvalReports: AdminAiEvalReport[];
  closureStages: AdminClosureStage[];
  overview: AdminOverview;
}
