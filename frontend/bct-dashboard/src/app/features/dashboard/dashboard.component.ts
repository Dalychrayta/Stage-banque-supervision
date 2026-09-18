import { Component, OnInit, OnDestroy, Inject, PLATFORM_ID } from '@angular/core';
import { CommonModule, isPlatformBrowser } from '@angular/common';
import { CardModule } from 'primeng/card';
import { ChartModule } from 'primeng/chart';
import { TableModule } from 'primeng/table';
import { HeaderComponent } from '../../layout/header/header.component';
import { ApiService } from '../../core/services/api.service';
import { ResourceStats } from '../../core/models/resource.model';
import { IncidentAnalysis } from '../../core/models/incident.model';
import { interval, Subscription } from 'rxjs';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [CommonModule, CardModule, ChartModule, TableModule, HeaderComponent],
  template: `
    <app-header title="Dashboard de Supervision"></app-header>
    <div class="dashboard-content">

      <div class="kpi-grid">
        <div class="kpi-card">
          <span class="kpi-top">
            <span class="kpi-label">Ressources</span>
            <span class="kpi-badge kpi-badge-neutral"><i class="pi pi-server" aria-hidden="true"></i></span>
          </span>
          <span class="kpi-value">{{ stats?.total ?? 0 }}</span>
        </div>
        <div class="kpi-card">
          <span class="kpi-top">
            <span class="kpi-label">Opérationnelles</span>
            <span class="kpi-badge kpi-badge-up" *ngIf="stats && stats.total">{{ stats.up / stats.total * 100 | number:'1.0-0' }} %</span>
          </span>
          <span class="kpi-value-split">
            <span class="kpi-value kpi-up">{{ stats?.up ?? 0 }}</span>
            <span class="kpi-value-suffix">/{{ stats?.total ?? 0 }}</span>
          </span>
        </div>
        <div class="kpi-card">
          <span class="kpi-top">
            <span class="kpi-label">En panne</span>
            <span class="kpi-badge kpi-badge-down"><i class="pi pi-times-circle" aria-hidden="true"></i></span>
          </span>
          <span class="kpi-value kpi-down">{{ stats?.down ?? 0 }}</span>
        </div>
        <div class="kpi-card">
          <span class="kpi-top">
            <span class="kpi-label">Incidents ouverts</span>
            <span class="kpi-badge kpi-badge-warn"><i class="pi pi-exclamation-circle" aria-hidden="true"></i></span>
          </span>
          <span class="kpi-value" [class.kpi-down]="openIncidents.length > 0">{{ openIncidents.length }}</span>
        </div>
      </div>

      <div class="charts-row">
        <p-card header="État des ressources">
          <p-chart type="doughnut" [data]="statusChartData" [options]="doughnutOptions" height="220"></p-chart>
        </p-card>
        <p-card header="Incidents par sévérité">
          <p-chart type="bar" [data]="severityChartData" [options]="barOptions" height="220"></p-chart>
        </p-card>
        <p-card header="Tendance anomalies (24h)">
          <p-chart type="line" [data]="trendChartData" [options]="lineOptions" height="220"></p-chart>
        </p-card>
      </div>

      <p-card header="Incidents récents">
        <p-table [value]="openIncidents" [rows]="5" [paginator]="openIncidents.length > 5" styleClass="p-datatable-sm incident-table">
          <ng-template pTemplate="header">
            <tr><th>Ressource</th><th>Sévérité</th><th>Cause</th><th>Recommandation</th><th>Date</th></tr>
          </ng-template>
          <ng-template pTemplate="body" let-incident>
            <tr>
              <td class="mono">{{ incident.resourceName }}</td>
              <td><span [class]="'severity-badge severity-' + incident.severity?.toLowerCase()">{{ incident.severity }}</span></td>
              <td>{{ incident.causeCategory }}</td>
              <td class="recommendation-cell">{{ incident.recommendation }}</td>
              <td class="mono time-cell">{{ incident.analyzedAt | date:'HH:mm:ss' }}</td>
            </tr>
          </ng-template>
          <ng-template pTemplate="emptymessage">
            <tr><td colspan="5" class="empty-msg"><i class="pi pi-check-circle" aria-hidden="true"></i> Aucun incident ouvert</td></tr>
          </ng-template>
        </p-table>
      </p-card>
    </div>
  `,
  styles: [`
    .dashboard-content { display: flex; flex-direction: column; gap: 16px; }

    .kpi-grid { display: grid; grid-template-columns: repeat(4, minmax(0,1fr)); gap: 16px; }
    .kpi-card {
      height: 108px;
      background: var(--surface-raised);
      border: 1px solid var(--border);
      border-radius: var(--radius-md);
      padding: 18px;
      box-sizing: border-box;
      display: flex;
      flex-direction: column;
    }
    .kpi-top { display: flex; align-items: center; gap: 10px; }
    .kpi-label { flex: none; font-size: 12px; font-weight: 500; color: var(--ink-muted); white-space: nowrap; }
    .kpi-badge {
      margin-left: auto;
      display: inline-flex; align-items: center; justify-content: center;
      padding: 4px 10px;
      border-radius: var(--radius-pill);
      font-size: 11px; font-weight: 700; white-space: nowrap;
    }
    .kpi-badge-neutral { width: 30px; height: 30px; padding: 0; border-radius: var(--radius-sm); background: var(--surface-sunken); color: var(--ink-muted); }
    .kpi-badge-up   { background: var(--status-up-bg);   color: var(--status-up); }
    .kpi-badge-warn { width: 30px; height: 30px; padding: 0; border-radius: var(--radius-sm); background: var(--status-warn-bg); color: var(--status-warn); }
    .kpi-badge-down { width: 30px; height: 30px; padding: 0; border-radius: var(--radius-sm); background: var(--status-down-bg); color: var(--status-down); }

    .kpi-value {
      margin-top: auto;
      font-family: var(--font-mono);
      font-variant-numeric: tabular-nums;
      font-size: 34px; line-height: 38px;
      font-weight: 600; letter-spacing: -0.02em;
      color: var(--ink-strong);
    }
    .kpi-value-split { margin-top: auto; display: flex; align-items: baseline; gap: 7px; }
    .kpi-value-split .kpi-value { margin-top: 0; }
    .kpi-value-suffix { font-family: var(--font-mono); font-size: 16px; font-weight: 500; color: var(--ink-muted); }
    .kpi-up   { color: var(--status-up); }
    .kpi-down { color: var(--status-down); }

    .charts-row { display: grid; grid-template-columns: repeat(3, minmax(0,1fr)); gap: 16px; }

    .severity-badge { padding: 4px 11px; border-radius: var(--radius-pill); font-size: 12px; font-weight: 600; }
    .severity-critical { background: var(--status-down-bg); color: var(--status-down); }
    .severity-warning  { background: var(--status-warn-bg); color: var(--status-warn); }
    .severity-normal   { background: var(--status-up-bg);   color: var(--status-up); }
    .recommendation-cell { font-size: 13px; color: var(--ink-muted); max-width: 300px; }
    .time-cell { color: var(--ink-muted); }
    .empty-msg { text-align: center; padding: 2rem; color: var(--status-up); }

    :host ::ng-deep .p-card .p-card-title {
      flex: none;
      white-space: nowrap;
      font-size: 16px;
      font-weight: 700;
      letter-spacing: -0.01em;
      color: var(--ink-strong);
    }
    :host ::ng-deep .p-card .p-card-body { padding: 20px; }
  `]
})
export class DashboardComponent implements OnInit, OnDestroy {
  stats: ResourceStats | null = null;
  openIncidents: IncidentAnalysis[] = [];
  private sub = new Subscription();
  statusChartData: any = {};
  severityChartData: any = {};
  trendChartData: any = {};
  // Couleur fixe (pas var(--jeton) : Chart.js dessine sur un <canvas>, hors
  // de portée du CSS) choisie pour rester lisible sur fond clair ET sombre.
  private readonly chartTickColor = '#8c8474';
  doughnutOptions = { plugins: { legend: { position: 'bottom', labels: { color: this.chartTickColor } } }, cutout: '65%' };
  barOptions = { plugins: { legend: { display: false } }, scales: { x: { ticks: { color: this.chartTickColor } }, y: { beginAtZero: true, ticks: { color: this.chartTickColor } } } };
  lineOptions = { plugins: { legend: { display: false } }, scales: { x: { ticks: { color: this.chartTickColor } }, y: { beginAtZero: true, ticks: { color: this.chartTickColor } } } };

  constructor(private api: ApiService, @Inject(PLATFORM_ID) private platformId: Object) {}

  ngOnInit(): void {
    this.loadData();
    if (isPlatformBrowser(this.platformId)) {
      this.sub.add(interval(30000).subscribe(() => this.loadData()));
    }
  }

  private loadData(): void {
    this.api.getResourceStats().subscribe({ next: s => { this.stats = s; this.buildStatusChart(s); } });
    this.api.getOpenIncidents().subscribe({ next: i => { this.openIncidents = i; this.buildSeverityChart(i); } });
    this.api.getRecentIncidents(24).subscribe({ next: recent => this.buildTrendChart(recent) });
  }

  private buildStatusChart(s: ResourceStats): void {
    this.statusChartData = { labels: ['UP','DOWN','Dégradées','Inconnues'], datasets: [{ data: [s.up,s.down,s.degraded,s.unknown], backgroundColor: ['#3b6b46','#9c1c2e','#9a4a10','#6e685a'] }] };
  }
  private buildSeverityChart(incidents: IncidentAnalysis[]): void {
    this.severityChartData = { labels: ['CRITICAL','WARNING'], datasets: [{ data: [incidents.filter(i=>i.severity==='CRITICAL').length, incidents.filter(i=>i.severity==='WARNING').length], backgroundColor: ['#9c1c2e','#9a4a10'] }] };
  }
  private buildTrendChart(incidents: IncidentAnalysis[]): void {
    // Vraies données : compte les incidents détectés dans chacune des 24 dernières heures.
    const now = Date.now();
    const counts = Array(24).fill(0);
    incidents.forEach(inc => {
      const hoursAgo = Math.floor((now - new Date(inc.analyzedAt).getTime()) / 3600000);
      if (hoursAgo >= 0 && hoursAgo < 24) counts[23 - hoursAgo]++;
    });
    const labels = Array.from({ length: 24 }, (_, i) => {
      const d = new Date(now - (23 - i) * 3600000);
      return `${d.getHours()}h`;
    });
    this.trendChartData = { labels, datasets: [{ data: counts, borderColor: '#a8842a', backgroundColor: 'rgba(168,132,42,.15)', fill: true, tension: 0.4 }] };
  }

  ngOnDestroy(): void { this.sub.unsubscribe(); }
}
