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
    <app-header title="Ressources supervisées"></app-header>
    <p-toast></p-toast>
    <div class="page-content">
      <div class="page-toolbar">
        <span class="p-input-icon-left">
          <i class="pi pi-search"></i>
          <input pInputText type="text" placeholder="Rechercher..." [(ngModel)]="searchTerm" (input)="filterResources()" />
        </span>
        <p-dropdown [options]="statusOptions" [(ngModel)]="selectedStatus" placeholder="Tous les statuts"
                    (onChange)="filterResources()" [showClear]="true"></p-dropdown>
      </div>
      <p-table [value]="filtered" [rows]="10" [paginator]="true" [rowsPerPageOptions]="[10,25,50]"
               styleClass="p-datatable-gridlines" [loading]="loading">
        <ng-template pTemplate="header">
          <tr><th>Nom</th><th>Type</th><th>Environnement</th><th>IP</th><th>Statut</th><th>Dernière activité</th></tr>
        </ng-template>
        <ng-template pTemplate="body" let-r>
          <tr>
            <td><strong>{{ r.name }}</strong><br><small class="text-muted">{{ r.resourceId }}</small></td>
            <td><span class="type-badge">{{ r.type }}</span></td>
            <td>{{ r.environment }}</td>
            <td class="font-mono">{{ r.ipAddress || r.host }}</td>
            <td>
              <span [class]="'status-badge status-' + r.status?.toLowerCase()">
                <i [class]="getStatusIcon(r.status)"></i> {{ r.status }}
              </span>
            </td>
            <td>{{ r.lastSeen | date:'dd/MM/yyyy HH:mm' }}</td>
          </tr>
        </ng-template>
        <ng-template pTemplate="emptymessage">
          <tr><td colspan="6" style="text-align:center;padding:2rem">Aucune ressource trouvée</td></tr>
        </ng-template>
      </p-table>
    </div>
  `,
  styles: [`
    .page-content { padding: 1.5rem; }
    .page-toolbar { display: flex; gap: 1rem; margin-bottom: 1rem; align-items: center; }
    .status-badge { padding: .25rem .6rem; border-radius: 12px; font-size: .75rem; font-weight: 600; display: inline-flex; align-items: center; gap: .3rem; }
    .status-up          { background: #f0fff4; color: #38a169; }
    .status-down        { background: #fff5f5; color: #e53e3e; }
    .status-degraded    { background: #fffbeb; color: #d69e2e; }
    .status-unknown     { background: #f7fafc; color: #718096; }
    .status-maintenance { background: #ebf8ff; color: #3182ce; }
    .type-badge { background: #edf2f7; color: #4a5568; padding: .2rem .5rem; border-radius: 6px; font-size: .75rem; }
    .font-mono  { font-family: monospace; font-size: .85rem; }
    .text-muted { color: #a0aec0; font-size: .75rem; }
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

  filterResources(): void {
    const s = this.searchTerm.toLowerCase();
    this.filtered = this.resources.filter(r => (!s || r.name.toLowerCase().includes(s) || r.resourceId.toLowerCase().includes(s)) && (!this.selectedStatus || r.status === this.selectedStatus));
  }

  getStatusIcon(status: string): string {
    const m: Record<string,string> = { UP:'pi pi-check-circle', DOWN:'pi pi-times-circle', DEGRADED:'pi pi-exclamation-circle', UNKNOWN:'pi pi-question-circle', MAINTENANCE:'pi pi-wrench' };
    return m[status] ?? 'pi pi-circle';
  }
}
