import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { TableModule } from 'primeng/table';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { DropdownModule } from 'primeng/dropdown';
import { ToastModule } from 'primeng/toast';
import { MessageService } from 'primeng/api';
import { HeaderComponent } from '../../layout/header/header.component';
import { ApiService } from '../../core/services/api.service';
import { Resource } from '../../core/models/resource.model';

@Component({
  selector: 'app-resources',
  standalone: true,
  imports: [CommonModule, FormsModule, TableModule, ButtonModule, InputTextModule, DropdownModule, ToastModule, HeaderComponent],
  providers: [MessageService],
  template: `
    <app-header title="Ressources"></app-header>
    <p-toast></p-toast>
    <div class="page-content">
      <div class="page-toolbar">
        <span class="search-field">
          <i class="pi pi-search" aria-hidden="true"></i>
          <label for="q" class="sr-only">Rechercher une ressource</label>
          <input id="q" pInputText type="text" placeholder="nom d'hôte, IP, rôle…" [(ngModel)]="searchTerm" (input)="filterResources()" />
        </span>
        <p-dropdown [options]="statusOptions" [(ngModel)]="selectedStatus" placeholder="Tous les statuts"
                    (onChange)="filterResources()" [showClear]="true" styleClass="status-filter"></p-dropdown>
        <span class="result-count"><span class="mono">{{ filtered.length }}</span> ressources</span>
      </div>
      <p-table [value]="filtered" [rows]="10" [paginator]="filtered.length > 10" [rowsPerPageOptions]="[10,25,50]"
               currentPageReportTemplate="{first} à {last} sur {totalRecords}" [showCurrentPageReport]="true"
               styleClass="resources-table" [loading]="loading">
        <ng-template pTemplate="header">
          <tr><th>Hôte</th><th>Rôle</th><th>Adresse</th><th>Env.</th><th>État</th><th>Vérifié</th></tr>
        </ng-template>
        <ng-template pTemplate="body" let-r>
          <tr>
            <td class="mono host-cell">{{ r.name }}</td>
            <td class="role-cell">{{ r.description || '—' }}</td>
            <td class="mono">{{ r.ipAddress || r.host }}</td>
            <td><span class="env-badge">{{ r.environment }}</span></td>
            <td>
              <span [class]="'status-badge status-' + r.status?.toLowerCase()">
                <i [class]="getStatusIcon(r.status)" aria-hidden="true"></i> {{ r.status }}
              </span>
            </td>
            <td class="mono time-cell">{{ r.lastSeen | date:'HH:mm:ss' }}</td>
          </tr>
        </ng-template>
        <ng-template pTemplate="emptymessage">
          <tr *ngIf="resources.length === 0"><td colspan="6" class="empty-msg empty-unconfigured">
            <i class="pi pi-exclamation-circle" aria-hidden="true"></i>
            <strong>Aucune ressource supervisée</strong>
            <span>La découverte n'a encore enregistré aucune ressource.</span>
          </td></tr>
          <tr *ngIf="resources.length > 0"><td colspan="6" class="empty-msg">
            <strong>Aucune ressource ne correspond à ces filtres</strong>
            <button type="button" class="reset-btn" (click)="resetFilters()">Réinitialiser les filtres</button>
          </td></tr>
        </ng-template>
      </p-table>
    </div>
  `,
  styles: [`
    .page-content { display: flex; flex-direction: column; gap: 16px; }
    .sr-only { position: absolute; width: 1px; height: 1px; overflow: hidden; clip-path: inset(50%); }

    .page-toolbar {
      height: 72px;
      background: var(--surface-raised);
      border: 1px solid var(--border);
      border-radius: var(--radius-lg);
      padding: 0 16px;
      box-sizing: border-box;
      display: flex;
      align-items: center;
      gap: 12px;
    }
    .search-field { position: relative; display: inline-flex; align-items: center; }
    .search-field i { position: absolute; left: 14px; color: var(--ink-muted); font-size: 14px; pointer-events: none; }
    .search-field input {
      width: 220px; height: 42px;
      padding: 0 16px 0 38px;
      border: 1px solid var(--border);
      border-radius: var(--radius-pill);
      background: var(--surface-raised);
      color: var(--ink);
      font-family: inherit;
      font-size: 13px;
    }
    .result-count { margin-left: auto; font-size: 13px; color: var(--ink-muted); white-space: nowrap; }
    .mono { font-family: var(--font-mono); font-variant-numeric: tabular-nums; }

    .host-cell { font-size: 13px; font-weight: 500; color: var(--ink-strong); }
    .role-cell { font-size: 14px; color: var(--ink); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
    .time-cell { color: var(--ink-muted); font-size: 13px; }

    .env-badge {
      display: inline-block;
      padding: 3px 10px;
      border-radius: var(--radius-pill);
      background: var(--brand-tint);
      color: var(--brand);
      font-size: 11px; font-weight: 700;
      letter-spacing: 0.04em;
      text-transform: uppercase;
    }

    .status-badge { padding: 4px 11px; border-radius: var(--radius-pill); font-size: 12px; font-weight: 600; display: inline-flex; align-items: center; gap: .3rem; white-space: nowrap; }
    .status-up          { background: var(--status-up-bg);   color: var(--status-up); }
    .status-down        { background: var(--status-down-bg); color: var(--status-down); }
    .status-degraded    { background: var(--status-warn-bg); color: var(--status-warn); }
    .status-unknown     { background: var(--status-idle-bg); color: var(--status-idle); }
    .status-maintenance { background: var(--surface-sunken); color: var(--ink-muted); }

    .empty-msg { text-align: center; padding: 2rem; height: 160px; color: var(--ink-muted); }
    .empty-msg strong { display: block; color: var(--ink); font-size: 14px; }
    .empty-msg span, .empty-msg .reset-btn { margin-top: 8px; }
    .empty-msg i { display: block; font-size: 1.4rem; margin-bottom: 8px; }
    .empty-unconfigured, .empty-unconfigured strong { color: var(--status-warn); }
    .empty-unconfigured span { display: block; color: var(--ink-muted); font-size: 13px; }
    .reset-btn {
      height: 44px; padding: 0 18px; border-radius: var(--radius-pill);
      background: var(--surface-raised); color: var(--ink); border: 1px solid var(--border);
      font: inherit; font-size: 13px; font-weight: 600; cursor: pointer;
    }
    .reset-btn:hover { border-color: var(--border-strong); }

    :host ::ng-deep .resources-table {
      background: var(--surface-raised);
      border: 1px solid var(--border);
      border-radius: var(--radius-lg);
      overflow: hidden;
    }
    :host ::ng-deep .resources-table .p-datatable-thead > tr > th {
      background: var(--surface-raised) !important;
      color: var(--ink-muted) !important;
      font-size: 11px !important;
      font-weight: 700 !important;
      letter-spacing: 0.08em !important;
      text-transform: uppercase !important;
      border-color: var(--border) !important;
    }
    :host ::ng-deep .resources-table .p-datatable-tbody > tr > td {
      height: 56px;
      border-color: var(--hairline) !important;
      font-size: 13px;
    }
    :host ::ng-deep .resources-table .p-datatable-tbody > tr > td.empty-msg { height: 160px; padding: 2rem; }
    :host ::ng-deep .resources-table .p-datatable-tbody > tr:hover { background: var(--surface-hover) !important; }
    :host ::ng-deep .resources-table .p-paginator {
      background: var(--surface-raised) !important;
      border-color: var(--hairline) !important;
      color: var(--ink-muted) !important;
    }
    :host ::ng-deep .resources-table .p-paginator .p-paginator-page,
    :host ::ng-deep .resources-table .p-paginator .p-paginator-next,
    :host ::ng-deep .resources-table .p-paginator .p-paginator-prev,
    :host ::ng-deep .resources-table .p-paginator .p-paginator-first,
    :host ::ng-deep .resources-table .p-paginator .p-paginator-last {
      border-radius: var(--radius-pill) !important;
      min-width: 34px !important;
      height: 34px !important;
      color: var(--ink) !important;
    }
    :host ::ng-deep .resources-table .p-paginator .p-highlight {
      background: var(--brand) !important;
      color: var(--ink-inverse) !important;
    }
    :host ::ng-deep .status-filter .p-dropdown {
      border-radius: var(--radius-pill) !important;
      border-color: var(--border) !important;
      height: 44px;
      display: flex;
      align-items: center;
    }
  `]
})
export class ResourcesComponent implements OnInit {
  resources: Resource[] = []; filtered: Resource[] = [];
  loading = true; searchTerm = ''; selectedStatus: string | null = null;
  statusOptions = [{label:'UP',value:'UP'},{label:'DOWN',value:'DOWN'},{label:'DEGRADED',value:'DEGRADED'},{label:'UNKNOWN',value:'UNKNOWN'}];

  constructor(private api: ApiService, private msg: MessageService) {}

  ngOnInit(): void {
    this.api.getResources().subscribe({ next: r => { this.resources = r; this.filtered = r; this.loading = false; }, error: () => this.loading = false });
  }

  resetFilters(): void {
    this.searchTerm = '';
    this.selectedStatus = null;
    this.filterResources();
  }

  filterResources(): void {
    const s = this.searchTerm.toLowerCase();
    this.filtered = this.resources.filter(r => (!s || r.name.toLowerCase().includes(s) || r.resourceId.toLowerCase().includes(s)) && (!this.selectedStatus || r.status === this.selectedStatus));
  }

  getStatusIcon(status: string): string {
    const m: Record<string,string> = { UP:'pi pi-check-circle', DOWN:'pi pi-times-circle', DEGRADED:'pi pi-exclamation-circle', UNKNOWN:'pi pi-question-circle', MAINTENANCE:'pi pi-wrench' };
    return m[status] ?? 'pi pi-circle';
  }
}
