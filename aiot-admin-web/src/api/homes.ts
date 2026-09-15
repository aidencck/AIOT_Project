import http from '@/api/http';
import type {
  HomeCreateReq,
  HomeMemberAddReq,
  HomeMemberResp,
  HomeMemberRoleUpdateReq,
  HomeResp,
  HomeUpdateReq
} from '@/types/homes';

export function getHomes() {
  return http.get<HomeResp[], HomeResp[]>('/api/v1/homes');
}

export function createHome(data: HomeCreateReq) {
  return http.post<string, string>('/api/v1/homes', data);
}

export function deleteHome(homeId: string) {
  return http.delete<void, void>(`/api/v1/homes/${homeId}`);
}

export function updateHome(homeId: string, data: HomeUpdateReq) {
  return http.put<void, void>(`/api/v1/homes/${homeId}`, data);
}

export function getHomeMembers(homeId: string) {
  return http.get<HomeMemberResp[], HomeMemberResp[]>(`/api/v1/homes/${homeId}/members`);
}

export function addHomeMember(homeId: string, data: HomeMemberAddReq) {
  return http.post<void, void>(`/api/v1/homes/${homeId}/members`, data);
}

export function removeHomeMember(homeId: string, userId: string) {
  return http.delete<void, void>(`/api/v1/homes/${homeId}/members/${userId}`);
}

export function updateHomeMemberRole(homeId: string, userId: string, data: HomeMemberRoleUpdateReq) {
  return http.put<void, void>(`/api/v1/homes/${homeId}/members/${userId}/role`, data);
}
