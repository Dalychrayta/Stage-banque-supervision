export type ResourceType = 'SERVER' | 'VIRTUAL_MACHINE' | 'APPLICATION' | 'DATABASE' | 'CONTAINER' | 'NETWORK_DEVICE';
export type ResourceStatus = 'UP' | 'DOWN' | 'DEGRADED' | 'UNKNOWN' | 'MAINTENANCE';

export interface Resource {
  id: number;
  resourceId: string;
  name: string;
  type: ResourceType;
  host: string;
  ipAddress: string;
  port: number;
  environment: string;
  description: string;
  status: ResourceStatus;
  lastSeen: string;
  createdAt: string;
  tags: string;
}

export interface ResourceStats {
  total: number;
  up: number;
  down: number;
  degraded: number;
  unknown: number;
}
