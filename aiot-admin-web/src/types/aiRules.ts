export interface RuleDraftRequest {
  requirement: string;
  deviceId?: string;
  eventType?: string;
  actionType?: string;
  actionPayload?: string;
}

export interface RuleDraftResponse {
  ruleId: string;
  conditionEventType?: string;
  conditionDeviceId?: string;
  actionType?: string;
  actionPayload?: string;
  status?: string;
}

export interface RuleApproveRequest {
  approver: string;
  comment?: string;
}

export interface RuleUpdateRequest {
  conditionEventType?: string;
  conditionDeviceId?: string;
  actionType?: string;
  actionPayload?: string;
}

export interface RejectBody {
  rejectReason?: string;
}
