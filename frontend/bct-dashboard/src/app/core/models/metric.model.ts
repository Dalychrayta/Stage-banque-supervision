export interface MetricSnapshot {
  id: number;
  resourceId: string;
  resourceName: string;
  resourceType: string;
  cpuUsage: number;
  memoryUsage: number;
  diskUsage: number;
  networkInMbps: number;
  networkOutMbps: number;
  responseTimeMs: number;
  errorRate: number;
  requestCount: number;
  collectedAt: string;
}

export interface LogEntry {
  id: number;
  resourceId: string;
  resourceName: string;
  level: 'DEBUG' | 'INFO' | 'WARN' | 'ERROR' | 'FATAL';
  message: string;
  source: string;
  logTimestamp: string;
  collectedAt: string;
}
