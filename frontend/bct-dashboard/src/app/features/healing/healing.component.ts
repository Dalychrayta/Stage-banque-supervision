import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { TableModule, TableLazyLoadEvent } from 'primeng/table';
import { ButtonModule } from 'primeng/button';
import { DropdownModule } from 'primeng/dropdown';
import { ToastModule } from 'primeng/toast';
import { DialogModule } from 'primeng/dialog';
import { InputTextareaModule } from 'primeng/inputtextarea';
import { MessageService } from 'primeng/api';
import { HeaderComponent } from '../../layout/header/header.component';
import { ApiService } from '../../core/services/api.service';
import { AuthService } from '../../core/services/auth.service';
import { HealingAction, HealingStats } from '../../core/models/incident.model';
import { Resource } from '../../core/models/resource.model';

@Component({
  selector: 'app-healing',
  standalone: true,
  imports: [CommonModule, FormsModule, TableModule, ButtonModule, DropdownModule, ToastModule, DialogModule, InputTextareaModule, HeaderComponent],
  providers: [MessageService],
  template: `
    <app-header title="Auto-réparation"></app-header>
    <p-toast></p-toast>
    <div class="page-content">
      <div class="healing-stats" *ngIf="stats">
        <div class="hstat success"><i class="pi pi-check-circle"></i><div><span>{{ stats.success }}</span><small>Succès</small></div></div>
        <div class="hstat failed"><i class="pi pi-times-circle"></i><div><span>{{ stats.failed }}</span><small>Échecs</small></div></div>
        <div class="hstat pending"><i class="pi pi-clock"></i><div><span>{{ stats.pending }}</span><small>En attente</small></div></div>
        <div class="hstat skipped" title="Refusées par le délai de garde anti-battement">
          <i class="pi pi-shield"></i><div><span>{{ stats.skipped }}</span><small>Ignorées</small></div>
        </div>
        <div class="hstat total"><i class="pi pi-list"></i><div><span>{{ stats.total }}</span><small>Total</small></div></div>
        <button *ngIf="canOperate" pButton label="Action manuelle" icon="pi pi-play" class="manual-btn" (click)="showManualDialog = true"></button>
      </div>

      <p-table [value]="actions" [rows]="pageSize" [paginator]="true" [lazy]="true" [totalRecords]="totalRecords"
               (onLazyLoad)="onPageChange($event)" styleClass="healing-table" [loading]="loading">
        <ng-template pTemplate="header">
          <tr><th>Ressource</th><th>Action</th><th>Cause</th><th>Demandé par</th><th>Motif</th><th>Statut</th><th>Type</th><th>Résultat</th><th>Date</th></tr>
        </ng-template>
        <ng-template pTemplate="body" let-a>
          <tr>
            <td><strong class="mono host-name">{{ a.resourceName }}</strong></td>
            <td><span class="action-tag">{{ a.actionType }}</span></td>
            <td>{{ a.causeCategory }}</td>
            <td><span [class]="isSystemActor(a.triggeredBy) ? 'actor-system' : 'actor-user'">
                  <i [class]="isSystemActor(a.triggeredBy) ? 'pi pi-cog' : 'pi pi-user'" aria-hidden="true"></i>
                  {{ actorLabel(a.triggeredBy) }}</span></td>
            <td class="desc-cell">{{ a.triggerReason || a.description }}</td>
            <td><span [class]="'ast ast-' + a.status?.toLowerCase()"><i [class]="getIcon(a.status)" aria-hidden="true"></i> {{ a.status }}</span></td>
            <td><span [class]="isSystemActor(a.triggeredBy) ? 'auto-badge' : 'manual-badge'">{{ isSystemActor(a.triggeredBy) ? 'Auto' : 'Manuel' }}</span></td>
            <td class="result-cell">{{ a.resultMessage }}</td>
            <td class="mono time-cell">{{ a.triggeredAt | date:'dd/MM HH:mm' }}</td>
          </tr>
        </ng-template>
      </p-table>
    </div>

    <p-dialog header="Action manuelle" [(visible)]="showManualDialog" [modal]="true" [style]="{width:'420px'}">
      <div class="manual-form">
        <label>Ressource</label>
        <p-dropdown [options]="resourceOptions" [(ngModel)]="selectedResource" [filter]="true" filterBy="label"
                    placeholder="Choisir une ressource" styleClass="w-full"
                    emptyMessage="Aucune ressource trouvée"></p-dropdown>
        <label>Action</label>
        <p-dropdown [options]="actionTypeOptions" [(ngModel)]="manualActionType"
                    placeholder="Choisir une action" styleClass="w-full"></p-dropdown>
        <label>Motif <span class="required">obligatoire</span></label>
        <textarea pInputTextarea [(ngModel)]="manualReason" rows="3"
                  placeholder="Pourquoi cette action ? Ex. : mémoire saturée signalée par l'équipe réseau"></textarea>
        <small class="reason-hint">Ce motif est enregistré avec votre nom dans le journal d'audit.</small>
      </div>
      <ng-template pTemplate="footer">
        <button pButton label="Annuler" class="p-button-text" (click)="showManualDialog = false"></button>
        <button pButton label="Déclencher" icon="pi pi-play" class="p-button-danger"
                (click)="triggerManual()"
                [disabled]="!selectedResource || !manualActionType || !manualReason.trim()"></button>
      </ng-template>
    </p-dialog>
  `,
  styles: [`
    .page-content { display: flex; flex-direction: column; gap: 16px; }
    .mono { font-family: var(--font-mono); font-variant-numeric: tabular-nums; }
    .healing-stats { display: flex; gap: 12px; align-items: center; flex-wrap: wrap; }
    .hstat { background: var(--surface-raised); border: 1px solid var(--border); border-radius: var(--radius-md); padding: 10px 16px; display: flex; align-items: center; gap: 10px; }
    .hstat i { font-size: 1.2rem; }
    .hstat span { font-family: var(--font-mono); font-variant-numeric: tabular-nums; font-size: 1.4rem; font-weight: 600; display: block; line-height: 1; color: var(--ink-strong); }
    .hstat small { color: var(--ink-muted); font-size: .72rem; }
    .success i { color: var(--status-up); } .failed i { color: var(--status-down); } .pending i { color: var(--status-warn); } .total i, .skipped i { color: var(--ink-muted); }
    .manual-btn {
      margin-left: auto; height: 44px; padding: 0 18px;
      border-radius: var(--radius-pill) !important; border: 0 !important;
      background: var(--brand) !important; color: var(--ink-inverse) !important;
      font-size: 13px; font-weight: 600;
    }
    .host-name { color: var(--ink-strong); font-weight: 500; }
    .time-cell { color: var(--ink-muted); font-size: 13px; }
    .action-tag { background: var(--surface-sunken); color: var(--ink); padding: 3px 9px; border-radius: var(--radius-sm); font-size: .72rem; font-family: var(--font-mono); }
    .ast { font-size: .72rem; font-weight: 600; padding: 4px 10px; border-radius: var(--radius-pill); white-space: nowrap; }
    .ast-success { background: var(--status-up-bg); color: var(--status-up); } .ast-failed { background: var(--status-down-bg); color: var(--status-down); }
    .ast-pending { background: var(--status-warn-bg); color: var(--status-warn); } .ast-in_progress { background: var(--surface-sunken); color: var(--ink-muted); }
    .ast-skipped { background: var(--surface-sunken); color: var(--ink-muted); }
    .auto-badge   { background: var(--brand-tint); color: var(--brand); padding: 3px 10px; border-radius: var(--radius-pill); font-size: .68rem; font-weight: 700; letter-spacing: .04em; text-transform: uppercase; }
    .manual-badge { background: var(--surface-sunken); color: var(--ink); padding: 3px 10px; border-radius: var(--radius-pill); font-size: .68rem; font-weight: 700; letter-spacing: .04em; text-transform: uppercase; }
    .desc-cell, .result-cell { font-size: .8rem; color: var(--ink-muted); max-width: 200px; }
    .manual-form { display: flex; flex-direction: column; gap: .75rem; padding: .5rem 0; }
    .manual-form label { font-weight: 600; font-size: .85rem; color: var(--ink); }
    .manual-form textarea { width: 100%; font-family: inherit; font-size: .85rem; padding: .5rem; border: 1px solid var(--border-strong); border-radius: var(--radius-sm); background: var(--surface-raised); color: var(--ink); resize: vertical; }
    .required { color: var(--status-down); font-weight: 500; font-size: .75rem; }
    .reason-hint { color: var(--ink-muted); font-size: .75rem; }
    .actor-system { background: var(--surface-sunken); color: var(--ink-muted); padding: 3px 10px; border-radius: var(--radius-pill); font-size: .72rem; font-weight: 600; white-space: nowrap; }
    .actor-user   { background: var(--brand-tint); color: var(--brand); padding: 3px 10px; border-radius: var(--radius-pill); font-size: .72rem; font-weight: 600; white-space: nowrap; }

    :host ::ng-deep .healing-table { background: var(--surface-raised); border: 1px solid var(--border); border-radius: var(--radius-lg); overflow: hidden; }
    :host ::ng-deep .healing-table .p-datatable-thead > tr > th { font-size: 11px !important; letter-spacing: 0.08em !important; text-transform: uppercase !important; }
    :host ::ng-deep .healing-table .p-datatable-tbody > tr > td { font-size: 13px; }
    :host ::ng-deep .healing-table .p-paginator { background: var(--surface-raised) !important; border-color: var(--hairline) !important; color: var(--ink-muted) !important; }
    :host ::ng-deep .healing-table .p-paginator .p-highlight { background: var(--brand) !important; color: var(--ink-inverse) !important; }
  `]
})
export class HealingComponent implements OnInit {
  actions: HealingAction[] = []; stats: HealingStats | null = null;
  loading = true; showManualDialog = false;
  totalRecords = 0;
  pageSize = 15;
  private currentPage = 0;
  resourceOptions: { label: string; value: { id: string; name: string } }[] = [];
  selectedResource: { id: string; name: string } | null = null;
  manualActionType = '';
  manualReason = '';
  actionTypeOptions = [{label:'Redémarrer le service',value:'RESTART_SERVICE'},{label:'Vider le cache',value:'CLEAR_CACHE'},{label:'Libérer espace disque',value:'FREE_DISK_SPACE'},{label:'Terminer processus CPU',value:'KILL_PROCESS'},{label:"Notifier l'équipe",value:'NOTIFY_TEAM'}];

  canOperate = false;

  constructor(private api: ApiService, private msg: MessageService, private auth: AuthService) {
    this.canOperate = this.auth.canOperate();
  }

  ngOnInit(): void {
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

  onPageChange(event: TableLazyLoadEvent): void {
    this.pageSize = event.rows ?? this.pageSize;
    this.currentPage = Math.floor((event.first ?? 0) / this.pageSize);
    this.loading = true;
    this.api.getHealingActions(this.currentPage, this.pageSize).subscribe({
      next: d => { this.actions = d.content; this.totalRecords = d.totalElements; this.loading = false; },
      error: () => this.loading = false
    });
  }

  getIcon(status: string): string {
    const m: Record<string,string> = { SUCCESS:'pi pi-check', FAILED:'pi pi-times', PENDING:'pi pi-clock', IN_PROGRESS:'pi pi-spin pi-spinner', SKIPPED:'pi pi-shield' };
    return m[status] ?? 'pi pi-circle';
  }

  /** "systeme:auto-healing" -> la plateforme ; "utilisateur:operator" -> un humain. */
  isSystemActor(triggeredBy: string | undefined): boolean {
    return !triggeredBy || triggeredBy.startsWith('systeme:');
  }

  /** Affiche "operator" ou "Plateforme" plutôt que la valeur technique stockée. */
  actorLabel(triggeredBy: string | undefined): string {
    if (!triggeredBy) return 'Plateforme';
    if (triggeredBy.startsWith('utilisateur:')) return triggeredBy.substring('utilisateur:'.length);
    return 'Plateforme';
  }

  triggerManual(): void {
    if (!this.selectedResource || !this.manualReason.trim()) return;
    const { id, name } = this.selectedResource;
    this.api.triggerManualHealing(id, name, this.manualActionType, this.manualReason.trim()).subscribe({
      next: a => {
        this.actions.unshift(a);
        this.showManualDialog = false;
        this.msg.add({severity:'success',summary:'Action déclenchée',detail:`${this.manualActionType} sur ${name}`});
        this.selectedResource = null; this.manualActionType=''; this.manualReason='';
        this.api.getHealingStats().subscribe({next:s=>this.stats=s});
      },
      error: e => this.msg.add({severity:'error',summary:'Action refusée',
        detail: e?.error?.error ?? "Vous n'avez pas le droit de déclencher cette action."})
    });
  }
}
