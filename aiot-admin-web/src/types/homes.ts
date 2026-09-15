export interface HomeResp {
  id: string;
  name?: string;
  location?: string;
  role?: number;
}

export interface HomeMemberResp {
  userId: string;
  nickname?: string;
  phone?: string;
  role?: number;
}

export interface HomeCreateReq {
  name: string;
  location?: string;
}

export interface HomeMemberAddReq {
  userId: string;
  role: number;
}

export interface HomeMemberRoleUpdateReq {
  role: number;
}

export interface HomeUpdateReq {
  name?: string;
  location?: string;
}
