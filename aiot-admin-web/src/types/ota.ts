export interface FirmwarePackageResp {
  packageId: string;
  productKey?: string;
  version?: string;
  downloadUrl?: string;
  checksum?: string;
  releaseNotes?: string;
  status?: number;
}

export interface FirmwarePackageCreateReq {
  productKey: string;
  version: string;
  downloadUrl: string;
  checksum?: string;
  releaseNotes?: string;
}

export interface OtaUpgradeTaskCreateReq {
  homeId: string;
  productKey: string;
  packageId: string;
  deviceIds: string[];
}
