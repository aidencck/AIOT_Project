export interface ProductResp {
  id: string;
  productKey?: string;
  deviceModelKey?: string;
  name?: string;
  description?: string;
  nodeType?: number;
  thingModelJson?: string;
  deviceModelJson?: string;
}

export interface ProductReq {
  productKey?: string;
  name: string;
  description?: string;
  nodeType: number;
  thingModelJson?: string;
  deviceModelJson?: string;
}

export interface ProductThingModelUpdateReq {
  thingModelJson: string;
}
