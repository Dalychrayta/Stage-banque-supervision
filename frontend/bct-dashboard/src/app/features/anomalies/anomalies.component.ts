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
    <app-header title="Anomalies détectées"></app-header>
    <p-toast></p-toast>
    <div class="page-content">
      <div class="stats-row" *ngIf="stats">
        <div class="stat-box open"><i class="pi pi-exclamation-circle"></i><div><span>{{ stats.open }}</span><small>Ouverts</small></div></div>
        <div class="stat-box resolved"><i class="pi pi-check-circle"></i><div><span>{{ stats.resolved }}</span><small>Résolus</small></div></div>
        <div class="stat-box total"><i class="pi pi-list"></i><div><span>{{ stats.total }}</span><small>Total</small></div></div>
        <div class="summary-item critical"><i class="pi pi-times-circle"></i><span>{{ stats.criticalOpen }} CRITICAL</span></div>
        <div class="summary-item warning"><i class="pi pi-exclamation-triangle"></i><span>{{ stats.warningOpen }} WARNING</span></div>
        <div class="refresh-info"><i class="pi pi-refresh"></i> Actualisation auto 30s</div>
      </div>

      <p-table [value]="incidents" [rows]="pageSize" [paginator]="true" dataKey="id" [expandedRowKeys]="expandedRows"
               [lazy]="true" [totalRecords]="totalRecords" (onLazyLoad)="onPageChange($event)"
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
            <td>{{ (i.confidenceScore * 100) | number:'1.0-0' }}%</td>
            <td><span [class]="'stat-dot stat-' + i.status?.toLowerCase()">{{ i.status }}</span></td>
            <td>{{ i.analyzedAt | date:'dd/MM HH:mm' }}</td>
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
    .category-tag.corrected { background: #ebf8ff; color: #2b6cb0; }
    .cause-cell { display: flex; align-items: center; gap: .25rem; flex-wrap: wrap; }
    .cat-edit { padding: 0 .25rem !important; }
    .ro-hint { color: #a0aec0; font-size: .72rem; font-style: italic; }
    .corrected-by { color: #3182ce; font-size: .68rem; }
    .cat-select { font-size: .75rem; padding: .15rem .3rem; border: 1px solid #cbd5e0; border-radius: 4px; font-family: monospace; }
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
    const by = this.auth.getUsername() ?? 'inconnu';
    this.api.correctIncidentCategory(i.id, this.editCategoryValue, by).subscribe({
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
