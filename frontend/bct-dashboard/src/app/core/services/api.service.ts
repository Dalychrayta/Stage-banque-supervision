import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Resource, ResourceStats } from '../models/resource.model';
import { MetricSnapshot, LogEntry } from '../models/metric.model';
import { IncidentAnalysis, HealingAction, HealingStats, RcaStats } from '../models/incident.model';
import { environment } from '../../../environments/environment';

const API_BASE = environment.apiBaseUrl;

@Injectable({ providedIn: 'root' })
export class ApiService {

  constructor(private http: HttpClient) {}

  // --- RESOURCES (discovery-service) ---
  getResources(): Observable<Resource[]> {
    return this.http.get<Resource[]>(`${API_BASE}/discovery/resources`);
  }

  getResourceStats(): Observable<ResourceStats> {
    return this.http.get<ResourceStats>(`${API_BASE}/discovery/resources/stats`);
  }

  updateResourceStatus(resourceId: string, status: string): Observable<Resource> {
    return this.http.patch<Resource>(`${API_BASE}/discovery/resources/${resourceId}/status`, null, {
      params: new HttpParams().set('status', status)
    });
  }

  // --- METRICS (collector-service) ---
  getLatestMetrics(resourceId: string, limit = 20): Observable<MetricSnapshot[]> {
    return this.http.get<MetricSnapshot[]>(`${API_BASE}/collector/metrics/${resourceId}/latest`, {
      params: new HttpParams().set('limit', limit)
    });
  }

  getRecentLogs(resourceId: string): Observable<LogEntry[]> {
    return this.http.get<LogEntry[]>(`${API_BASE}/collector/logs/${resourceId}`);
  }

  getRecentErrors(minutesBack = 60): Observable<LogEntry[]> {
    return this.http.get<LogEntry[]>(`${API_BASE}/collector/logs/errors/recent`, {
      params: new HttpParams().set('minutesBack', minutesBack)
    });
  }

  getMonitoredResources(): Observable<string[]> {
    return this.http.get<string[]>(`${API_BASE}/collector/resources`);
  }

  // --- RCA (rca-service) ---
  getIncidents(): Observable<IncidentAnalysis[]> {
    return this.http.get<IncidentAnalysis[]>(`${API_BASE}/rca`);
  }

  getOpenIncidents(): Observable<IncidentAnalysis[]> {
    return this.http.get<IncidentAnalysis[]>(`${API_BASE}/rca/open`);
  }

  getIncidentsByResource(resourceId: string): Observable<IncidentAnalysis[]> {
    return this.http.get<IncidentAnalysis[]>(`${API_BASE}/rca/resource/${resourceId}`);
  }

  resolveIncident(id: number): Observable<IncidentAnalysis> {
    return this.http.patch<IncidentAnalysis>(`${API_BASE}/rca/${id}/resolve`, {});
  }

  getRcaStats(): Observable<RcaStats> {
    return this.http.get<RcaStats>(`${API_BASE}/rca/stats`);
  }

  // --- HEALING (auto-healing-service) ---
  getHealingActions(): Observable<HealingAction[]> {
    return this.http.get<HealingAction[]>(`${API_BASE}/healing`);
  }

  getHealingStats(): Observable<HealingStats> {
    return this.http.get<HealingStats>(`${API_BASE}/healing/stats`);
  }

  triggerManualHealing(resourceId: string, resourceName: string, actionType: string): Observable<HealingAction> {
    return this.http.post<HealingAction>(`${API_BASE}/healing/trigger`, null, {
      params: new HttpParams()
        .set('resourceId', resourceId)
        .set('resourceName', resourceName)
        .set('actionType', actionType)
    });
  }
}
