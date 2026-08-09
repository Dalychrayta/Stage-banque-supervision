import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { TableModule } from 'primeng/table';
import { ButtonModule } from 'primeng/button';
import { DropdownModule } from 'primeng/dropdown';
import { ToastModule } from 'primeng/toast';
import { DialogModule } from 'primeng/dialog';
import { MessageService } from 'primeng/api';
import { HeaderComponent } from '../../layout/header/header.component';
import { ApiService } from '../../core/services/api.service';
import { HealingAction, HealingStats } from '../../core/models/incident.model';
import { Resource } from '../../core/models/resource.model';

@Component({
  selector: 'app-healing',
  standalone: true,
  imports: [CommonModule, FormsModule, TableModule, ButtonModule, DropdownModule, ToastModule, DialogModule, HeaderComponent],
  providers: [MessageService],
  template: `
    <app-header title="Auto-Healing Engine"></app-header>
    <p-toast></p-toast>
    <div class="page-content">
      <div class="healing-stats" *ngIf="stats">
        <div class="hstat success"><i class="pi pi-check-circle"></i><div><span>{{ stats.success }}</span><small>Succès</small></div></div>
        <div class="hstat failed"><i class="pi pi-times-circle"></i><div><span>{{ stats.failed }}</span><small>Échecs</small></div></div>
        <div class="hstat pending"><i class="pi pi-clock"></i><div><span>{{ stats.pending }}</span><small>En attente</small></div></div>
        <div class="hstat total"><i class="pi pi-list"></i><div><span>{{ stats.total }}</span><small>Total</small></div></div>
        <button pButton label="Action manuelle" icon="pi pi-play" class="p-button-outlined manual-btn" (click)="showManualDialog = true"></button>
      </div>

      <p-table [value]="actions" [rows]="15" [paginator]="true" styleClass="p-datatable-gridlines p-datatable-sm" [loading]="loading">
        <ng-template pTemplate="header">
          <tr><th>Ressource</th><th>Action</th><th>Cause</th><th>Description</th><th>Statut</th><th>Type</th><th>Résultat</th><th>Date</th></tr>
        </ng-template>
        <ng-template pTemplate="body" let-a>
          <tr>
            <td><strong>{{ a.resourceName }}</strong></td>
            <td><span class="action-tag">{{ a.actionType }}</span></td>
            <td>{{ a.causeCategory }}</td>
            <td class="desc-cell">{{ a.description }}</td>
            <td><span [class]="'ast ast-' + a.status?.toLowerCase()"><i [class]="getIcon(a.status)"></i> {{ a.status }}</span></td>
            <td><span [class]="a.isAutomatic ? 'auto-badge' : 'manual-badge'">{{ a.isAutomatic ? 'Auto' : 'Manuel' }}</span></td>
            <td class="result-cell">{{ a.resultMessage }}</td>
            <td>{{ a.triggeredAt | date:'dd/MM HH:mm' }}</td>
          </tr>
        </ng-template>
      </p-table>
    </div>

    <p-dialog header="Action manuelle" [(visible)]="showManualDialog" [modal]="true" [style]="{width:'420px'}">
      <div class="manual-form">
        <label>Ressource</label>
        <p-dropdown [options]="resourceOptions" [(ngModel)]="selectedResource" [filter]="true" filterBy="label"
                    placeholder="Choisir une ressource" styleClass="w-full" [appendTo]="'body'"
                    emptyMessage="Aucune ressource trouvée"></p-dropdown>
        <label>Action</label>
        <p-dropdown [options]="actionTypeOptions" [(ngModel)]="manualActionType"
                    placeholder="Choisir une action" styleClass="w-full" [appendTo]="'body'"></p-dropdown>
      </div>
      <ng-template pTemplate="footer">
        <button pButton label="Annuler" class="p-button-text" (click)="showManualDialog = false"></button>
        <button pButton label="Déclencher" icon="pi pi-play" class="p-button-danger"
                (click)="triggerManual()" [disabled]="!selectedResource || !manualActionType"></button>
      </ng-template>
    </p-dialog>
  `,
  styles: [`
    .page-content { padding: 1.5rem; }
    .healing-stats { display: flex; gap: 1rem; align-items: center; margin-bottom: 1.5rem; flex-wrap: wrap; }
    .hstat { background: #fff; border-radius: 10px; padding: .75rem 1.25rem; display: flex; align-items: center; gap: .6rem; box-shadow: 0 1px 4px rgba(0,0,0,.06); }
    .hstat i { font-size: 1.3rem; } .hstat span { font-size: 1.6rem; font-weight: 700; display: block; line-height: 1; } .hstat small { color: #718096; font-size: .75rem; }
    .success i { color: #38a169; } .failed i { color: #e53e3e; } .pending i { color: #d69e2e; } .total i { color: #6c9bff; }
    .manual-btn { margin-left: auto; }
    .action-tag { background: #edf2f7; color: #2d3748; padding: .2rem .5rem; border-radius: 6px; font-size: .72rem; font-family: monospace; }
    .ast { font-size: .75rem; font-weight: 600; padding: .2rem .5rem; border-radius: 8px; }
    .ast-success { background: #f0fff4; color: #38a169; } .ast-failed { background: #fff5f5; color: #e53e3e; }
    .ast-pending { background: #fffbeb; color: #d69e2e; } .ast-in_progress { background: #ebf8ff; color: #3182ce; }
    .auto-badge   { background: #ebf8ff; color: #3182ce; padding: .2rem .5rem; border-radius: 8px; font-size: .72rem; font-weight: 600; }
    .manual-badge { background: #faf5ff; color: #6b46c1; padding: .2rem .5rem; border-radius: 8px; font-size: .72rem; font-weight: 600; }
    .desc-cell, .result-cell { font-size: .8rem; color: #4a5568; max-width: 200px; }
    .manual-form { display: flex; flex-direction: column; gap: .75rem; padding: .5rem 0; }
    .manual-form label { font-weight: 600; font-size: .85rem; color: #2d3748; }
  `]
})
export class HealingComponent implements OnInit {
  actions: HealingAction[] = []; stats: HealingStats | null = null;
  loading = true; showManualDialog = false;
  resourceOptions: { label: string; value: { id: string; name: string } }[] = [];
  selectedResource: { id: string; name: string } | null = null;
  manualActionType = '';
  actionTypeOptions = [{label:'Redémarrer le service',value:'RESTART_SERVICE'},{label:'Vider le cache',value:'CLEAR_CACHE'},{label:'Libérer espace disque',value:'FREE_DISK_SPACE'},{label:'Terminer processus CPU',value:'KILL_PROCESS'},{label:"Notifier l'équipe",value:'NOTIFY_TEAM'}];

  constructor(private api: ApiService, private msg: MessageService) {}

  ngOnInit(): void {
    this.api.getHealingActions().subscribe({ next: d => { this.actions = d; this.loading = false; }, error: () => this.loading = false });
    this.api.getHealingStats().subscribe({ next: s => this.stats = s });
    this.api.getResources().subscribe({
      next: (resources: Resource[]) => {
        this.resourceOptions = resources.map(r => ({
          label: `${r.name} (${r.resourceId})`,
          value: { id: r.resourceId, name: r.name }
        }));
      }
    });
  }

  getIcon(status: string): string {
    const m: Record<string,string> = { SUCCESS:'pi pi-check', FAILED:'pi pi-times', PENDING:'pi pi-clock', IN_PROGRESS:'pi pi-spin pi-spinner' };
    return m[status] ?? 'pi pi-circle';
  }

  triggerManual(): void {
    if (!this.selectedResource) return;
    const { id, name } = this.selectedResource;
    this.api.triggerManualHealing(id, name, this.manualActionType).subscribe({
      next: a => {
        this.actions.unshift(a);
        this.showManualDialog = false;
        this.msg.add({severity:'success',summary:'Action déclenchée',detail:`${this.manualActionType} sur ${name}`});
        this.selectedResource = null; this.manualActionType='';
        this.api.getHealingStats().subscribe({next:s=>this.stats=s});
      }
    });
  }
}
