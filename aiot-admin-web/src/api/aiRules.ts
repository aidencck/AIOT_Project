import http from '@/api/http';
import type {
  RejectBody,
  RuleApproveRequest,
  RuleDraftRequest,
  RuleDraftResponse,
  RuleUpdateRequest
} from '@/types/aiRules';

export function draftRule(data: RuleDraftRequest) {
  return http.post<RuleDraftResponse, RuleDraftResponse>('/api/v1/ai/rules/draft', data);
}

export function listRules(status?: string) {
  return http.get<RuleDraftResponse[], RuleDraftResponse[]>('/api/v1/ai/rules', {
    params: status ? { status } : undefined
  });
}

export function approveRule(ruleId: string, data: RuleApproveRequest) {
  return http.post<Record<string, unknown>, Record<string, unknown>>(
    `/api/v1/ai/rules/${ruleId}/approve`,
    data
  );
}

export function rejectRule(ruleId: string, data: RejectBody) {
  return http.post<Record<string, unknown>, Record<string, unknown>>(
    `/api/v1/ai/rules/${ruleId}/reject`,
    data
  );
}

export function updateRule(ruleId: string, data: RuleUpdateRequest) {
  return http.put<RuleDraftResponse, RuleDraftResponse>(`/api/v1/ai/rules/${ruleId}`, data);
}

export function deleteRule(ruleId: string) {
  return http.delete<void, void>(`/api/v1/ai/rules/${ruleId}`);
}
