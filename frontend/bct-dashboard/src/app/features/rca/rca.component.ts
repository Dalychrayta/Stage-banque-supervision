import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { TableModule } from 'primeng/table';
import { ButtonModule } from 'primeng/button';
import { CardModule } from 'primeng/card';
import { ToastModule } from 'primeng/toast';
import { MessageService } from 'primeng/api';
import { HeaderComponent } from '../../layout/header/header.component';
import { ApiService } from '../../core/services/api.service';
import { IncidentAnalysis, RcaStats } from '../../core/models/incident.model';

@Component({
  selector: 'app-rca',
  standalone: true,
  imports: [CommonModule, TableModule, ButtonModule, CardModule, ToastModule, HeaderComponent],
  providers: [MessageService],
  template: `
    <app-header title="Analyse des Causes Racines (RCA)"></app-header>
    <p-toast></p-toast>
    <div class="page-content">
      <div class="rca-stats" *ngIf="rcaStats">
        <div class="stat-box open"><i class="pi pi-exclamation-circle"></i><div><span>{{ rcaStats.open }}</span><small>Ouverts</small></div></div>
        <div class="stat-box resolved"><i class="pi pi-check-circle"></i><div><span>{{ rcaStats.resolved }}</span><small>Résolus</small></div></div>
        <div class="stat-box total"><i class="pi pi-list"></i><div><span>{{ rcaStats.total }}</span><small>Total</small></div></div>
      </div>

      <p-table [value]="incidents" [rows]="10" [paginator]="true" styleClass="p-datatable-gridlines"
               [loading]="loading" [expandedRowKeys]="expandedRows" dataKey="id">
        <ng-template pTemplate="header">
          <tr><th style="width:3rem"></th><th>Ressource</th><th>Catégorie</th><th>Sévérité</th><th>Confiance</th><th>Statut</th><th>Date</th><th>Action</th></tr>
        </ng-template>
        <ng-template pTemplate="body" let-inc let-expanded="expanded">
          <tr>
            <td>
              <button pButton type="button" [icon]="expanded ? 'pi pi-chevron-down' : 'pi pi-chevron-right'"
                      class="p-button-text p-button-sm" (click)="toggleRow(inc)"></button>
            </td>
            <td><strong>{{ inc.resourceName }}</strong></td>
            <td><span class="category-tag">{{ inc.causeCategory }}</span></td>
            <td><span [class]="'sev sev-' + inc.severity?.toLowerCase()">{{ inc.severity }}</span></td>
            <td><span class="confidence">{{ (inc.confidenceScore * 100) | number:'1.0-0' }}%</span></td>
            <td><span [class]="'stat stat-' + inc.status?.toLowerCase()">{{ inc.status }}</span></td>
            <td>{{ inc.analyzedAt | date:'dd/MM/yyyy HH:mm' }}</td>
            <td>
              <button pButton label="Résoudre" icon="pi pi-check" class="p-button-success p-button-sm"
                      *ngIf="inc.status === 'OPEN'" (click)="resolve(inc)"></button>
            </td>
          </tr>
        </ng-template>
        <ng-template pTemplate="rowexpansion" let-inc>
          <tr class="expanded-row">
            <td colspan="8">
              <div class="rca-detail">
                <div class="detail-section"><h4><i class="pi pi-search"></i> Cause identifiée</h4><p>{{ inc.rootCause }}</p></div>
                <div class="detail-section"><h4><i class="pi pi-lightbulb"></i> Recommandation</h4><p>{{ inc.recommendation }}</p></div>
              </div>
            </td>
          </tr>
        </ng-template>
      </p-table>
    </div>
  `,
  styles: [`
    .page-content { padding: 1.5rem; }
    .rca-stats { display: flex; gap: 1rem; margin-bottom: 1.5rem; }
    .stat-box { background: #fff; border-radius: 10px; padding: 1rem 1.5rem; display: flex; align-items: center; gap: .75rem; box-shadow: 0 1px 4px rgba(0,0,0,.06); }
    .stat-box i { font-size: 1.5rem; } .stat-box span { display: block; font-size: 1.8rem; font-weight: 700; line-height: 1; } .stat-box small { color: #718096; font-size: .8rem; }
    .open i { color: #e53e3e; } .resolved i { color: #38a169; } .total i { color: #6c9bff; }
    .sev { padding: .2rem .5rem; border-radius: 10px; font-size: .75rem; font-weight: 600; }
    .sev-critical { background: #fff5f5; color: #e53e3e; } .sev-warning { background: #fffbeb; color: #d69e2e; }
    .stat { font-size: .75rem; font-weight: 600; }
    .stat-open { color: #e53e3e; } .stat-resolved { color: #38a169; } .stat-in_progress { color: #3182ce; }
    .confidence { font-weight: 600; color: #3182ce; }
    .category-tag { background: #edf2f7; color: #2d3748; padding: .2rem .5rem; border-radius: 6px; font-size: .75rem; font-family: monospace; }
    .expanded-row { background: #f7fafc !important; }
    .rca-detail { padding: 1rem; display: grid; grid-template-columns: 1fr 1fr; gap: 1.5rem; }
    .detail-section h4 { display: flex; align-items: center; gap: .5rem; color: #2d3748; font-size: .9rem; margin-bottom: .5rem; }
    .detail-section p { color: #4a5568; font-size: .85rem; line-height: 1.5; }
  `]
})
export class RcaComponent implements OnInit {
  incidents: IncidentAnalysis[] = []; rcaStats: RcaStats | null = null;
  loading = true; expandedRows: Record<string,boolean> = {};

  constructor(private api: ApiService, private msg: MessageService) {}

  ngOnInit(): void {
    this.api.getIncidents().subscribe({ next: d => { this.incidents = d; this.loading = false; }, error: () => this.loading = false });
    this.api.getRcaStats().subscribe({ next: s => this.rcaStats = s });
  }

  toggleRow(inc: IncidentAnalysis): void {
    if (this.expandedRows[inc.id]) { delete this.expandedRows[inc.id]; } else { this.expandedRows = {[inc.id]: true}; }
  }

  resolve(inc: IncidentAnalysis): void {
    this.api.resolveIncident(inc.id).subscribe({ next: () => { inc.status = 'RESOLVED'; this.msg.add({severity:'success',summary:'Résolu',detail:`Incident #${inc.id} résolu`}); this.api.getRcaStats().subscribe({next:s=>this.rcaStats=s}); } });
  }
}
