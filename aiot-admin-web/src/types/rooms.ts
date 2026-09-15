export interface RoomResp {
  id: string;
  homeId?: string;
  name?: string;
}

export interface RoomCreateReq {
  homeId: string;
  name: string;
}
