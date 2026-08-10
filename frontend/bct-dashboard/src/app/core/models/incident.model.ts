export type AnalysisStatus = 'OPEN' | 'IN_PROGRESS' | 'RESOLVED' | 'IGNORED';
export type ActionType = 'RESTART_SERVICE' | 'CLEAR_CACHE' | 'SCALE_UP' | 'FREE_DISK_SPACE' | 'KILL_PROCESS' | 'NOTIFY_TEAM' | 'RECOMMEND_ONLY';
export type ActionStatus = 'PENDING' | 'IN_PROGRESS' | 'SUCCESS' | 'FAILED' | 'SKIPPED';

export interface IncidentAnalysis {
  id: number;
  resourceId: string;
  resourceName: string;
  anomalyScore: number;
  severity: 'NORMAL' | 'WARNING' | 'CRITICAL';
  anomalousMetrics: string;
  rootCause: string;
  causeCategory: string;
  confidenceScore: number;
  correlatedLogs: string;
  recommendation: string;
  status: AnalysisStatus;
  detectedAt: string;
  analyzedAt: string;
  resolvedAt: string;
}

export interface HealingAction {
  id: number;
  resourceId: string;
  resourceName: string;
  incidentId: number;
  actionType: ActionType;
  causeCategory: string;
  description: string;
  status: ActionStatus;
  resultMessage: string;
  triggeredAt: string;
  completedAt: string;
  isAutomatic: boolean;
}

export interface HealingStats {
  total: number;
  success: number;
  failed: number;
  pending: number;
}

export interface RcaStats {
  open: number;
  resolved: number;
  total: number;
  criticalOpen: number;
  warningOpen: number;
}

export interface PagedResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}
