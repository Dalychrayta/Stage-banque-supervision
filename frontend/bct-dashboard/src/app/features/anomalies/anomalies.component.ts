import { Component, OnInit, OnDestroy, Inject, PLATFORM_ID } from '@angular/core';
import { CommonModule, isPlatformBrowser } from '@angular/common';
import { TableModule, TableLazyLoadEvent } from 'primeng/table';
import { ButtonModule } from 'primeng/button';
import { ToastModule } from 'primeng/toast';
import { ProgressBarModule } from 'primeng/progressbar';
import { ChipModule } from 'primeng/chip';
import { MessageService } from 'primeng/api';
import { FormsModule } from '@angular/forms';
import { HeaderComponent } from '../../layout/header/header.component';
import { ApiService } from '../../core/services/api.service';
import { AuthService } from '../../core/services/auth.service';
import { IncidentAnalysis, RcaStats, CAUSE_CATEGORIES } from '../../core/models/incident.model';
import { interval, Subscription } from 'rxjs';

@Component({
  selector: 'app-anomalies',
  standalone: true,
  imports: [CommonModule, FormsModule, TableModule, ButtonModule, ToastModule, ProgressBarModule, ChipModule, HeaderComponent],
  providers: [MessageService],
  template: `
    <app-header title="Anomalies"></app-header>
    <p-toast></p-toast>
    <div class="page-content">
      <div class="stats-row" *ngIf="stats">
        <div class="stat-box open"><i class="pi pi-exclamation-circle" aria-hidden="true"></i><div><span>{{ stats.open }}</span><small>Ouverts</small></div></div>
        <div class="stat-box resolved"><i class="pi pi-check-circle" aria-hidden="true"></i><div><span>{{ stats.resolved }}</span><small>Résolus</small></div></div>
        <div class="stat-box total"><i class="pi pi-list" aria-hidden="true"></i><div><span>{{ stats.total }}</span><small>Total</small></div></div>
        <div class="summary-item critical"><i class="pi pi-times-circle" aria-hidden="true"></i><span>{{ stats.criticalOpen }} CRITICAL</span></div>
        <div class="summary-item warning"><i class="pi pi-exclamation-triangle" aria-hidden="true"></i><span>{{ stats.warningOpen }} WARNING</span></div>
        <div class="refresh-info"><i class="pi pi-refresh" aria-hidden="true"></i> Actualisation auto 30s</div>
      </div>

      <p-table [value]="incidents" [rows]="pageSize" [paginator]="true" dataKey="id" [expandedRowKeys]="expandedRows"
               [lazy]="true" [totalRecords]="totalRecords" (onLazyLoad)="onPageChange($event)"
               styleClass="anomalies-table" [loading]="loading">
        <ng-template pTemplate="header">
          <tr><th style="width:2.5rem"></th><th>Ressource</th><th>Sévérité</th><th>Score</th><th>Métriques</th><th>Cause</th><th>Confiance</th><th>Statut</th><th>Date</th><th>Action</th></tr>
        </ng-template>
        <ng-template pTemplate="body" let-i let-expanded="expanded">
          <tr [class]="'row-' + i.severity?.toLowerCase()">
            <td>
              <button pButton type="button" [icon]="expanded ? 'pi pi-chevron-down' : 'pi pi-chevron-right'"
                      class="p-button-text p-button-sm" (click)="toggleRow(i)"></button>
            </td>
            <td><strong class="mono host-name">{{ i.resourceName }}</strong></td>
            <td><span [class]="'sev-badge sev-' + i.severity?.toLowerCase()">{{ i.severity }}</span></td>
            <td>
              <div class="score-bar">
                <p-progressBar [value]="getScorePercent(i.anomalyScore)" [showValue]="false" styleClass="score-pb"></p-progressBar>
                <span class="mono">{{ i.anomalyScore | number:'1.2-2' }}</span>
              </div>
            </td>
            <td>
              <div class="chips-cell">
                <p-chip *ngFor="let m of parseMetrics(i.anomalousMetrics)" [label]="m"></p-chip>
              </div>
            </td>
            <td>
              <div class="cause-cell">
                <ng-container *ngIf="editingCategoryId !== i.id; else editCat">
                  <span class="category-tag" [class.corrected]="i.correctedCategory">{{ i.effectiveCategory || i.causeCategory }}</span>
                  <button *ngIf="canOperate" pButton type="button" icon="pi pi-pencil" class="p-button-text p-button-sm cat-edit"
                          title="Corriger la cause" (click)="startEditCategory(i)"></button>
                  <small *ngIf="i.correctedBy" class="corrected-by" title="Corrigé manuellement">corrigé par {{ i.correctedBy }}</small>
                </ng-container>
                <ng-template #editCat>
                  <select [(ngModel)]="editCategoryValue" class="cat-select">
                    <option *ngFor="let c of categories" [value]="c">{{ c }}</option>
                  </select>
                  <button pButton type="button" icon="pi pi-check" severity="success" class="p-button-sm" (click)="saveCategory(i)"></button>
                  <button pButton type="button" icon="pi pi-times" class="p-button-text p-button-sm" (click)="editingCategoryId = null"></button>
                </ng-template>
              </div>
            </td>
            <td class="mono">{{ (i.confidenceScore * 100) | number:'1.0-0' }}%</td>
            <td><span [class]="'stat-dot stat-' + i.status?.toLowerCase()">{{ i.status }}</span></td>
            <td class="mono time-cell">{{ i.analyzedAt | date:'dd/MM HH:mm' }}
              <span *ngIf="i.occurrenceCount && i.occurrenceCount > 1" class="occurrence-badge"
                    title="Nombre de détections consécutives pour cette même condition, tant qu'elle reste ouverte">
                vu {{ i.occurrenceCount }}×
              </span>
            </td>
            <td>
              <button pButton label="Résoudre" icon="pi pi-check" severity="success" size="small"
                      *ngIf="i.status === 'OPEN' && canOperate" (click)="resolve(i)"></button>
              <span *ngIf="i.status === 'OPEN' && !canOperate" class="ro-hint" title="Réservé aux opérateurs">lecture seule</span>
            </td>
          </tr>
        </ng-template>
        <ng-template pTemplate="rowexpansion" let-i>
          <tr class="expanded-row">
            <td colspan="10">
              <div class="detail-grid">
                <div class="detail-section"><h4><i class="pi pi-search" aria-hidden="true"></i> Cause identifiée</h4><p>{{ i.rootCause }}</p></div>
                <div class="detail-section"><h4><i class="pi pi-lightbulb" aria-hidden="true"></i> Recommandation</h4><p>{{ i.recommendation }}</p></div>
              </div>
            </td>
          </tr>
        </ng-template>
      </p-table>
    </div>
  `,
  styles: [`
    .page-content { display: flex; flex-direction: column; gap: 16px; }
    .mono { font-family: var(--font-mono); font-variant-numeric: tabular-nums; }

    .stats-row { display: flex; align-items: center; gap: 12px; flex-wrap: wrap; }
    .stat-box {
      background: var(--surface-raised);
      border: 1px solid var(--border);
      border-radius: var(--radius-md);
      padding: 10px 16px;
      display: flex; align-items: center; gap: 10px;
    }
    .stat-box i { font-size: 1.2rem; }
    .stat-box span { display: block; font-family: var(--font-mono); font-variant-numeric: tabular-nums; font-size: 1.3rem; font-weight: 600; line-height: 1; color: var(--ink-strong); }
    .stat-box small { color: var(--ink-muted); font-size: .72rem; }
    .stat-box.open i      { color: var(--status-down); }
    .stat-box.resolved i  { color: var(--status-up); }
    .stat-box.total i     { color: var(--ink-muted); }
    .summary-item { display: flex; align-items: center; gap: .4rem; font-weight: 600; font-size: .85rem; }
    .critical { color: var(--status-down); } .warning { color: var(--status-warn); }
    .refresh-info { margin-left: auto; color: var(--ink-muted); font-size: .78rem; }

    .sev-badge { padding: 4px 11px; border-radius: var(--radius-pill); font-size: .72rem; font-weight: 700; }
    .sev-critical { background: var(--status-down-bg); color: var(--status-down); }
    .sev-warning  { background: var(--status-warn-bg); color: var(--status-warn); }
    .sev-normal   { background: var(--status-up-bg);   color: var(--status-up); }

    .score-bar { display: flex; align-items: center; gap: .5rem; font-size: .8rem; }
    .score-bar .mono { font-size: 14px; font-weight: 600; color: var(--ink-strong); }
    .chips-cell { display: flex; flex-wrap: wrap; gap: .25rem; }

    .category-tag { background: var(--surface-sunken); color: var(--ink); padding: 3px 9px; border-radius: var(--radius-sm); font-size: .72rem; font-family: var(--font-mono); }
    .category-tag.corrected { background: var(--brand-tint); color: var(--brand); }
    .cause-cell { display: flex; align-items: center; gap: .25rem; flex-wrap: wrap; }
    .cat-edit { padding: 0 .25rem !important; color: var(--ink-muted); }
    .ro-hint { color: var(--ink-muted); font-size: .72rem; font-style: italic; }
    .corrected-by { color: var(--brand); font-size: .68rem; }
    .occurrence-badge { display: block; color: var(--ink-muted); font-size: .68rem; margin-top: .15rem; }
    .cat-select { font-size: .75rem; padding: .15rem .3rem; border: 1px solid var(--border); border-radius: 4px; font-family: var(--font-mono); background: var(--surface-raised); color: var(--ink); }

    .stat-dot { font-size: .72rem; font-weight: 600; padding: 4px 10px; border-radius: var(--radius-pill); }
    .stat-open        { background: var(--status-down-bg); color: var(--status-down); }
    .stat-resolved    { background: var(--status-up-bg);   color: var(--status-up); }
    .stat-in_progress { background: var(--surface-sunken); color: var(--ink-muted); }
    .time-cell { color: var(--ink-muted); }

    .host-name { color: var(--ink-strong); font-weight: 500; }

    .row-critical { box-shadow: inset 4px 0 0 var(--status-down); }
    .row-warning  { box-shadow: inset 4px 0 0 var(--status-warn); }
    .expanded-row { background: var(--surface-sunken) !important; }
    .detail-grid { padding: 1rem; display: grid; grid-template-columns: 1fr 1fr; gap: 1.5rem; }
    .detail-section h4 { display: flex; align-items: center; gap: .5rem; color: var(--ink-strong); font-size: .9rem; margin-bottom: .5rem; font-weight: 700; }
    .detail-section p { color: var(--ink-muted); font-size: .85rem; line-height: 1.5; }

    :host ::ng-deep .anomalies-table {
      background: var(--surface-raised);
      border: 1px solid var(--border);
      border-radius: var(--radius-lg);
      overflow: hidden;
    }
    :host ::ng-deep .anomalies-table .p-datatable-thead > tr > th {
      background: var(--surface-raised) !important;
      color: var(--ink-muted) !important;
      font-size: 11px !important;
      font-weight: 700 !important;
      letter-spacing: 0.08em !important;
      text-transform: uppercase !important;
      border-color: var(--border) !important;
    }
    :host ::ng-deep .anomalies-table .p-datatable-tbody > tr > td {
      min-height: 56px;
      border-color: var(--hairline) !important;
      font-size: 13px;
      color: var(--ink);
    }
    :host ::ng-deep .anomalies-table .p-datatable-tbody > tr:hover { background: var(--surface-hover) !important; }
    :host ::ng-deep .anomalies-table .p-paginator {
      background: var(--surface-raised) !important;
      border-color: var(--hairline) !important;
      color: var(--ink-muted) !important;
    }
    :host ::ng-deep .anomalies-table .p-paginator .p-highlight {
      background: var(--brand) !important;
      color: var(--ink-inverse) !important;
    }
  `]
})
export class AnomaliesComponent implements OnInit, OnDestroy {
  incidents: IncidentAnalysis[] = [];
  stats: RcaStats | null = null;
  loading = true;
  expandedRows: Record<string, boolean> = {};
  totalRecords = 0;
  pageSize = 15;
  readonly categories = CAUSE_CATEGORIES;
  editingCategoryId: number | null = null;
  editCategoryValue = '';
  canOperate = false;
  private currentPage = 0;
  private sub = new Subscription();

  constructor(
    private api: ApiService,
    private auth: AuthService,
    private msg: MessageService,
    @Inject(PLATFORM_ID) private platformId: Object) {
    this.canOperate = this.auth.canOperate();
  }

  ngOnInit(): void {
    this.api.getRcaStats().subscribe({ next: s => this.stats = s });
    if (isPlatformBrowser(this.platformId)) {
      this.sub.add(interval(30000).subscribe(() => this.refresh()));
    }
  }

  onPageChange(event: TableLazyLoadEvent): void {
    this.pageSize = event.rows ?? this.pageSize;
    this.currentPage = Math.floor((event.first ?? 0) / this.pageSize);
    this.loadPage();
  }

  private loadPage(): void {
    this.loading = true;
    this.api.getIncidents(this.currentPage, this.pageSize).subscribe({
      next: d => { this.incidents = d.content; this.totalRecords = d.totalElements; this.loading = false; },
      error: () => this.loading = false
    });
  }

  private refresh(): void {
    this.loadPage();
    this.api.getRcaStats().subscribe({ next: s => this.stats = s });
  }

  toggleRow(i: IncidentAnalysis): void {
    if (this.expandedRows[i.id]) { delete this.expandedRows[i.id]; } else { this.expandedRows = { [i.id]: true }; }
  }

  parseMetrics(raw: string): string[] { try { return JSON.parse(raw) as string[]; } catch { return raw ? [raw] : []; } }
  getScorePercent(score: number): number { return Math.min(Math.abs(score) * 100, 100); }

  startEditCategory(i: IncidentAnalysis): void {
    this.editingCategoryId = i.id;
    this.editCategoryValue = i.effectiveCategory || i.causeCategory;
  }

  saveCategory(i: IncidentAnalysis): void {
    if (!this.editCategoryValue || this.editCategoryValue === (i.effectiveCategory || i.causeCategory)) {
      this.editingCategoryId = null;
      return;
    }
    this.api.correctIncidentCategory(i.id, this.editCategoryValue).subscribe({
      next: updated => {
        i.correctedCategory = updated.correctedCategory;
        i.correctedBy = updated.correctedBy;
        i.effectiveCategory = updated.effectiveCategory;
        this.editingCategoryId = null;
        this.msg.add({ severity: 'success', summary: 'Cause corrigée', detail: `Incident #${i.id} → ${this.editCategoryValue}` });
      },
      error: () => {
        this.editingCategoryId = null;
        this.msg.add({ severity: 'error', summary: 'Échec', detail: 'Correction non enregistrée' });
      }
    });
  }

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
