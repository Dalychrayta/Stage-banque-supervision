import { Component, Input, Inject, PLATFORM_ID, OnDestroy } from '@angular/core';
import { CommonModule, isPlatformBrowser } from '@angular/common';
import { Router } from '@angular/router';
import { Subscription, interval } from 'rxjs';
import { AuthService } from '../../core/services/auth.service';

@Component({
  selector: 'app-header',
  standalone: true,
  imports: [CommonModule],
  template: `
    <header class="top-header">
      <h1 class="page-title">{{ title }}</h1>
      <div class="header-right">
        <span class="live-badge">
          <span class="dot"></span> LIVE
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

  constructor(
    @Inject(PLATFORM_ID) private platformId: Object,
    private auth: AuthService,
    private router: Router
  ) {
    // Ce composant est réinstancié à chaque page (il vit dans le template de
    // chaque page, pas dans la coquille persistante de l'app) : un setInterval
    // brut, jamais arrêté, laissait un intervalle orphelin tourner pour
    // toujours à chaque navigation. L'abonnement RxJS est arrêté dans
    // ngOnDestroy, comme partout ailleurs dans l'app.
    if (isPlatformBrowser(this.platformId)) {
      this.clockSub = interval(1000).subscribe(() => this.currentTime = new Date());
    }
  }

  ngOnDestroy(): void {
    this.clockSub?.unsubscribe();
  }

  logout(): void {
    this.auth.logout();
    this.router.navigate(['/login']);
  }
}
