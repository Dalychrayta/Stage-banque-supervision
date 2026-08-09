import { Component, OnInit, OnDestroy, Inject, PLATFORM_ID } from '@angular/core';
import { CommonModule, isPlatformBrowser } from '@angular/common';
import { TableModule } from 'primeng/table';
import { ButtonModule } from 'primeng/button';
import { ToastModule } from 'primeng/toast';
import { ProgressBarModule } from 'primeng/progressbar';
import { ChipModule } from 'primeng/chip';
import { MessageService } from 'primeng/api';
import { HeaderComponent } from '../../layout/header/header.component';
import { ApiService } from '../../core/services/api.service';
import { IncidentAnalysis, RcaStats } from '../../core/models/incident.model';
import { interval, Subscription } from 'rxjs';

@Component({
  selector: 'app-anomalies',
  standalone: true,
  imports: [CommonModule, TableModule, ButtonModule, ToastModule, ProgressBarModule, ChipModule, HeaderComponent],
  providers: [MessageService],
  template: `
    <app-header title="Anomalies détectées"></app-header>
    <p-toast></p-toast>
    <div class="page-content">
      <div class="stats-row" *ngIf="stats">
        <div class="stat-box open"><i class="pi pi-exclamation-circle"></i><div><span>{{ stats.open }}</span><small>Ouverts</small></div></div>
        <div class="stat-box resolved"><i class="pi pi-check-circle"></i><div><span>{{ stats.resolved }}</span><small>Résolus</small></div></div>
        <div class="stat-box total"><i class="pi pi-list"></i><div><span>{{ stats.total }}</span><small>Total</small></div></div>
        <div class="summary-item critical"><i class="pi pi-times-circle"></i><span>{{ criticalCount }} CRITICAL</span></div>
        <div class="summary-item warning"><i class="pi pi-exclamation-triangle"></i><span>{{ warningCount }} WARNING</span></div>
        <div class="refresh-info"><i class="pi pi-refresh"></i> Actualisation auto 30s</div>
      </div>

      <p-table [value]="incidents" [rows]="15" [paginator]="true" dataKey="id" [expandedRowKeys]="expandedRows"
               styleClass="p-datatable-gridlines p-datatable-sm" [loading]="loading">
        <ng-template pTemplate="header">
          <tr><th style="width:2.5rem"></th><th>Ressource</th><th>Sévérité</th><th>Score</th><th>Métriques</th><th>Cause</th><th>Confiance</th><th>Statut</th><th>Date</th><th>Action</th></tr>
        </ng-template>
        <ng-template pTemplate="body" let-i let-expanded="expanded">
          <tr [class]="'row-' + i.severity?.toLowerCase()">
            <td>
              <button pButton type="button" [icon]="expanded ? 'pi pi-chevron-down' : 'pi pi-chevron-right'"
                      class="p-button-text p-button-sm" (click)="toggleRow(i)"></button>
            </td>
            <td><strong>{{ i.resourceName }}</strong></td>
            <td><span [class]="'sev-badge sev-' + i.severity?.toLowerCase()">{{ i.severity }}</span></td>
            <td>
              <div class="score-bar">
                <p-progressBar [value]="getScorePercent(i.anomalyScore)" [showValue]="false" styleClass="score-pb"></p-progressBar>
                <span>{{ i.anomalyScore | number:'1.2-2' }}</span>
              </div>
            </td>
            <td>
              <div class="chips-cell">
                <p-chip *ngFor="let m of parseMetrics(i.anomalousMetrics)" [label]="m"></p-chip>
              </div>
            </td>
            <td><span class="category-tag">{{ i.causeCategory }}</span></td>
            <td>{{ (i.confidenceScore * 100) | number:'1.0-0' }}%</td>
            <td><span [class]="'stat-dot stat-' + i.status?.toLowerCase()">{{ i.status }}</span></td>
            <td>{{ i.analyzedAt | date:'dd/MM HH:mm' }}</td>
            <td>
              <button pButton label="Résoudre" icon="pi pi-check" severity="success" size="small"
                      *ngIf="i.status === 'OPEN'" (click)="resolve(i)"></button>
            </td>
          </tr>
        </ng-template>
        <ng-template pTemplate="rowexpansion" let-i>
          <tr class="expanded-row">
            <td colspan="10">
              <div class="detail-grid">
                <div class="detail-section"><h4><i class="pi pi-search"></i> Cause identifiée</h4><p>{{ i.rootCause }}</p></div>
                <div class="detail-section"><h4><i class="pi pi-lightbulb"></i> Recommandation</h4><p>{{ i.recommendation }}</p></div>
              </div>
            </td>
          </tr>
        </ng-template>
      </p-table>
    </div>
  `,
  styles: [`
    .page-content { padding: 1.5rem; }
    .stats-row { display: flex; align-items: center; gap: 1rem; margin-bottom: 1rem; flex-wrap: wrap; }
    .stat-box { background: #fff; border-radius: 10px; padding: .6rem 1.1rem; display: flex; align-items: center; gap: .6rem; box-shadow: 0 1px 4px rgba(0,0,0,.06); }
    .stat-box i { font-size: 1.3rem; } .stat-box span { display: block; font-size: 1.4rem; font-weight: 700; line-height: 1; } .stat-box small { color: #718096; font-size: .75rem; }
    .stat-box.open i { color: #e53e3e; } .stat-box.resolved i { color: #38a169; } .stat-box.total i { color: #6c9bff; }
    .summary-item { display: flex; align-items: center; gap: .4rem; font-weight: 600; font-size: .9rem; }
    .critical { color: #e53e3e; } .warning { color: #d69e2e; }
    .refresh-info { margin-left: auto; color: #a0aec0; font-size: .8rem; }
    .sev-badge { padding: .2rem .6rem; border-radius: 12px; font-size: .75rem; font-weight: 700; }
    .sev-critical { background: #fff5f5; color: #e53e3e; }
    .sev-warning  { background: #fffbeb; color: #d69e2e; }
    .sev-normal   { background: #f0fff4; color: #38a169; }
    .score-bar { display: flex; align-items: center; gap: .5rem; font-size: .8rem; }
    .chips-cell { display: flex; flex-wrap: wrap; gap: .25rem; }
    .category-tag { background: #edf2f7; color: #2d3748; padding: .2rem .5rem; border-radius: 6px; font-size: .75rem; font-family: monospace; }
    .stat-dot { font-size: .75rem; font-weight: 600; padding: .2rem .5rem; border-radius: 8px; }
    .stat-open        { background: #fff5f5; color: #e53e3e; }
    .stat-resolved    { background: #f0fff4; color: #38a169; }
    .stat-in_progress { background: #ebf8ff; color: #3182ce; }
    .row-critical { background: #fffafa !important; }
    .row-warning  { background: #fffff8 !important; }
    .expanded-row { background: #f7fafc !important; }
    .detail-grid { padding: 1rem; display: grid; grid-template-columns: 1fr 1fr; gap: 1.5rem; }
    .detail-section h4 { display: flex; align-items: center; gap: .5rem; color: #2d3748; font-size: .9rem; margin-bottom: .5rem; }
    .detail-section p { color: #4a5568; font-size: .85rem; line-height: 1.5; }
  `]
})
export class AnomaliesComponent implements OnInit, OnDestroy {
  incidents: IncidentAnalysis[] = [];
  stats: RcaStats | null = null;
  loading = true;
  expandedRows: Record<string, boolean> = {};
  private sub = new Subscription();
  get criticalCount() { return this.incidents.filter(i => i.severity === 'CRITICAL' && i.status === 'OPEN').length; }
  get warningCount()  { return this.incidents.filter(i => i.severity === 'WARNING' && i.status === 'OPEN').length; }

  constructor(private api: ApiService, private msg: MessageService, @Inject(PLATFORM_ID) private platformId: Object) {}

  ngOnInit(): void {
    this.load();
    if (isPlatformBrowser(this.platformId)) {
      this.sub.add(interval(30000).subscribe(() => this.load()));
    }
  }

  load(): void {
    this.api.getIncidents().subscribe({ next: d => { this.incidents = d; this.loading = false; }, error: () => this.loading = false });
    this.api.getRcaStats().subscribe({ next: s => this.stats = s });
  }

  toggleRow(i: IncidentAnalysis): void {
    if (this.expandedRows[i.id]) { delete this.expandedRows[i.id]; } else { this.expandedRows = { [i.id]: true }; }
  }

  parseMetrics(raw: string): string[] { try { return JSON.parse(raw) as string[]; } catch { return raw ? [raw] : []; } }
  getScorePercent(score: number): number { return Math.min(Math.abs(score) * 100, 100); }

  resolve(incident: IncidentAnalysis): void {
    this.api.resolveIncident(incident.id).subscribe({
      next: () => {
        incident.status = 'RESOLVED';
        this.msg.add({ severity: 'success', summary: 'Résolu', detail: `Incident #${incident.id} résolu` });
        this.api.getRcaStats().subscribe({ next: s => this.stats = s });
      }
    });
  }

  ngOnDestroy(): void { this.sub.unsubscribe(); }
}
