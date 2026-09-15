import http from '@/api/http';
import type { RoomCreateReq, RoomResp } from '@/types/rooms';

export function createRoom(data: RoomCreateReq) {
  return http.post<string, string>('/api/v1/rooms', data);
}

export function listRooms(homeId: string) {
  return http.get<RoomResp[], RoomResp[]>('/api/v1/rooms', {
    params: { homeId }
  });
}

export function renameRoom(roomId: string, homeId: string, name: string) {
  return http.put<void, void>(
    `/api/v1/rooms/${roomId}`,
    { name },
    { params: { homeId } }
  );
}

export function deleteRoom(roomId: string, homeId: string) {
  return http.delete<void, void>(`/api/v1/rooms/${roomId}`, {
    params: { homeId }
  });
}
