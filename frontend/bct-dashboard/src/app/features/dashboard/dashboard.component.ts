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
        <div class="kpi-card kpi-total">
          <div class="kpi-icon"><i class="pi pi-server"></i></div>
          <div class="kpi-info"><span class="kpi-value">{{ stats?.total ?? 0 }}</span><span class="kpi-label">Ressources</span></div>
        </div>
        <div class="kpi-card kpi-up">
          <div class="kpi-icon"><i class="pi pi-check-circle"></i></div>
          <div class="kpi-info"><span class="kpi-value">{{ stats?.up ?? 0 }}</span><span class="kpi-label">Opérationnelles</span></div>
        </div>
        <div class="kpi-card kpi-down">
          <div class="kpi-icon"><i class="pi pi-times-circle"></i></div>
          <div class="kpi-info"><span class="kpi-value">{{ stats?.down ?? 0 }}</span><span class="kpi-label">En panne</span></div>
        </div>
        <div class="kpi-card kpi-warn">
          <div class="kpi-icon"><i class="pi pi-exclamation-circle"></i></div>
          <div class="kpi-info"><span class="kpi-value">{{ openIncidents.length }}</span><span class="kpi-label">Incidents ouverts</span></div>
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
        <p-table [value]="openIncidents" [rows]="5" [paginator]="openIncidents.length > 5" styleClass="p-datatable-sm">
          <ng-template pTemplate="header">
            <tr><th>Ressource</th><th>Sévérité</th><th>Cause</th><th>Recommandation</th><th>Date</th></tr>
          </ng-template>
          <ng-template pTemplate="body" let-incident>
            <tr>
              <td><strong>{{ incident.resourceName }}</strong></td>
              <td><span [class]="'severity-badge severity-' + incident.severity?.toLowerCase()">{{ incident.severity }}</span></td>
              <td>{{ incident.causeCategory }}</td>
              <td class="recommendation-cell">{{ incident.recommendation }}</td>
              <td>{{ incident.analyzedAt | date:'dd/MM HH:mm' }}</td>
            </tr>
          </ng-template>
          <ng-template pTemplate="emptymessage">
            <tr><td colspan="5" class="empty-msg"><i class="pi pi-check-circle"></i> Aucun incident ouvert</td></tr>
          </ng-template>
        </p-table>
      </p-card>
    </div>
  `,
  styles: [`
    .dashboard-content { padding: 1.5rem; }
    .kpi-grid { display: grid; grid-template-columns: repeat(4,1fr); gap: 1rem; margin-bottom: 1.5rem; }
    .kpi-card { background: #fff; border-radius: 12px; padding: 1.25rem; display: flex; align-items: center; gap: 1rem; box-shadow: 0 2px 8px rgba(0,0,0,.06); border-left: 4px solid; }
    .kpi-total { border-color: #6c9bff; } .kpi-total .kpi-icon i { color: #6c9bff; }
    .kpi-up    { border-color: #38a169; } .kpi-up    .kpi-icon i { color: #38a169; }
    .kpi-down  { border-color: #e53e3e; } .kpi-down  .kpi-icon i { color: #e53e3e; }
    .kpi-warn  { border-color: #d69e2e; } .kpi-warn  .kpi-icon i { color: #d69e2e; }
    .kpi-icon i { font-size: 1.8rem; }
    .kpi-info { display: flex; flex-direction: column; }
    .kpi-value { font-size: 2rem; font-weight: 700; color: #1a202c; line-height: 1; }
    .kpi-label { font-size: .8rem; color: #718096; margin-top: .25rem; }
    .charts-row { display: grid; grid-template-columns: repeat(3,1fr); gap: 1rem; margin-bottom: 1.5rem; }
    .severity-badge { padding: .2rem .6rem; border-radius: 12px; font-size: .75rem; font-weight: 600; }
    .severity-critical { background: #fff5f5; color: #e53e3e; }
    .severity-warning  { background: #fffbeb; color: #d69e2e; }
    .severity-normal   { background: #f0fff4; color: #38a169; }
    .recommendation-cell { font-size: .8rem; color: #4a5568; max-width: 300px; }
    .empty-msg { text-align: center; padding: 2rem; color: #38a169; }
  `]
})
export class DashboardComponent implements OnInit, OnDestroy {
  stats: ResourceStats | null = null;
  openIncidents: IncidentAnalysis[] = [];
  private sub = new Subscription();
  statusChartData: any = {};
  severityChartData: any = {};
  trendChartData: any = {};
  doughnutOptions = { plugins: { legend: { position: 'bottom' } }, cutout: '65%' };
  barOptions = { plugins: { legend: { display: false } }, scales: { y: { beginAtZero: true } } };
  lineOptions = { plugins: { legend: { display: false } }, scales: { y: { beginAtZero: true } } };

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
    this.api.getIncidents().subscribe({ next: all => this.buildTrendChart(all) });
  }

  private buildStatusChart(s: ResourceStats): void {
    this.statusChartData = { labels: ['UP','DOWN','Dégradées','Inconnues'], datasets: [{ data: [s.up,s.down,s.degraded,s.unknown], backgroundColor: ['#38a169','#e53e3e','#d69e2e','#718096'] }] };
  }
  private buildSeverityChart(incidents: IncidentAnalysis[]): void {
    this.severityChartData = { labels: ['CRITICAL','WARNING'], datasets: [{ data: [incidents.filter(i=>i.severity==='CRITICAL').length, incidents.filter(i=>i.severity==='WARNING').length], backgroundColor: ['#e53e3e','#d69e2e'] }] };
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
    this.trendChartData = { labels, datasets: [{ data: counts, borderColor: '#6c9bff', backgroundColor: 'rgba(108,155,255,.1)', fill: true, tension: 0.4 }] };
  }

  ngOnDestroy(): void { this.sub.unsubscribe(); }
}
