import { Component, Input, Inject, PLATFORM_ID, OnDestroy } from '@angular/core';
import { CommonModule, isPlatformBrowser } from '@angular/common';
import { Router } from '@angular/router';
import { Subscription, interval } from 'rxjs';
import { ApiService } from '../../core/services/api.service';
import { AuthService } from '../../core/services/auth.service';

@Component({
  selector: 'app-header',
  standalone: true,
  imports: [CommonModule],
  template: `
    <header class="top-header">
      <h1 class="page-title">{{ title }}</h1>
      <div class="header-right">
        <span class="live-badge" [class.stale]="stale" [attr.title]="stale ? 'Aucune donnée reçue depuis plus de deux cycles de collecte' : 'Collecte en cours'">
          <span class="dot"></span> {{ stale ? 'Reprise…' : 'LIVE' }}
          <span class="time">{{ currentTime | date:'HH:mm:ss' }}</span>
        </span>
        <button class="logout-btn" (click)="logout()" aria-label="Se déconnecter" title="Se déconnecter">
          <i class="pi pi-sign-out" aria-hidden="true"></i>
        </button>
      </div>
    </header>
  `,
  styles: [`
    .top-header {
      display: flex;
      align-items: center;
      justify-content: space-between;
      height: 64px;
      flex: none;
    }
    .page-title {
      margin: 0;
      font-size: 28px;
      line-height: 34px;
      font-weight: 700;
      letter-spacing: -0.02em;
      color: var(--ink-strong);
      white-space: nowrap;
    }
    .header-right {
      display: flex;
      align-items: center;
      gap: 10px;
    }
    .live-badge {
      display: inline-flex;
      align-items: center;
      gap: 7px;
      height: 44px;
      padding: 0 16px;
      border-radius: var(--radius-pill);
      background: var(--surface-raised);
      border: 1px solid var(--border);
      color: var(--status-up);
      font-size: 12px;
      font-weight: 600;
      white-space: nowrap;
    }
    .live-badge.stale { background: var(--status-warn-bg); color: var(--status-warn); border-color: var(--status-warn); }
    .live-badge.stale .dot { background: transparent; border: 1.5px solid var(--status-warn); }
    .dot {
      width: 7px; height: 7px;
      background: var(--status-up);
      border-radius: 50%;
      flex: none;
    }
    .time {
      font-family: var(--font-mono);
      font-variant-numeric: tabular-nums;
      color: var(--ink-muted);
      font-weight: 500;
    }
    .logout-btn {
      width: 44px; height: 44px;
      border-radius: var(--radius-pill);
      background: var(--surface-raised);
      border: 1px solid var(--border);
      color: var(--ink);
      cursor: pointer;
      display: inline-flex;
      align-items: center;
      justify-content: center;
      font-size: 1rem;
      flex: none;
    }
    .logout-btn:hover { color: var(--status-down); border-color: var(--status-down); }
  `]
})
export class HeaderComponent implements OnDestroy {
  @Input() title = 'Dashboard';
  currentTime = new Date();
  private clockSub?: Subscription;
  private pollSub?: Subscription;

  // Le collecteur sonde toutes les 30 s (collector.metrics.interval-ms) :
  // passé deux cycles sans donnée fraîche, le badge ne doit plus prétendre
  // que la collecte est en direct.
  private static readonly POLL_INTERVAL_MS = 30000;
  private static readonly STALE_AFTER_MS = 2 * HeaderComponent.POLL_INTERVAL_MS;

  // Horodatage de la dernière donnée reçue, comparé côté client : un booléen
  // envoyé par le serveur ne dirait rien quand le serveur est injoignable.
  private lastDataAt: number | null = null;
  private failed = false;

  get stale(): boolean {
    return this.failed || this.lastDataAt === null
      || this.currentTime.getTime() - this.lastDataAt > HeaderComponent.STALE_AFTER_MS;
  }

  constructor(
    @Inject(PLATFORM_ID) private platformId: Object,
    private auth: AuthService,
    private api: ApiService,
    private router: Router
  ) {
    // Ce composant est réinstancié à chaque page (il vit dans le template de
    // chaque page, pas dans la coquille persistante de l'app) : un setInterval
    // brut, jamais arrêté, laissait un intervalle orphelin tourner pour
    // toujours à chaque navigation. L'abonnement RxJS est arrêté dans
    // ngOnDestroy, comme partout ailleurs dans l'app.
    if (isPlatformBrowser(this.platformId)) {
      this.clockSub = interval(1000).subscribe(() => this.currentTime = new Date());
      this.refreshLastData();
      this.pollSub = interval(15000).subscribe(() => this.refreshLastData());
    }
  }

  ngOnDestroy(): void {
    this.clockSub?.unsubscribe();
    this.pollSub?.unsubscribe();
  }

  private refreshLastData(): void {
    this.api.getResources().subscribe({
      next: resources => {
        this.failed = false;
        const times = resources
          .map(r => this.parseServerDate(r.lastSeen))
          .filter(t => !isNaN(t));
        this.lastDataAt = times.length ? Math.max(...times) : null;
      },
      error: () => { this.failed = true; }
    });
  }

  // Les services écrivent des LocalDateTime sans fuseau, en UTC (conteneurs) :
  // sans "Z", le navigateur les lirait en heure locale et fausserait l'écart.
  private parseServerDate(value: string): number {
    if (!value) return NaN;
    return new Date(/[zZ]|[+-]\d\d:?\d\d$/.test(value) ? value : value + 'Z').getTime();
  }

  logout(): void {
    this.auth.logout();
    this.router.navigate(['/login']);
  }
}
