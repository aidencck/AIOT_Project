import http from '@/api/http';
import type { LoginReq, LoginResp, RegisterReq } from '@/types/auth';

export function login(data: LoginReq) {
  return http.post<LoginResp, LoginResp>('/api/v1/users/login', data);
}

export function register(data: RegisterReq) {
  return http.post<void, void>('/api/v1/users/register', data);
}
