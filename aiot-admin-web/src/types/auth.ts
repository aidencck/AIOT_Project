export interface LoginReq {
  phone: string;
  password: string;
}

export interface RegisterReq {
  phone: string;
  password: string;
  nickname: string;
}

export interface LoginResp {
  token: string;
  userId: string;
  globalUserId: string;
  nickname: string;
}
