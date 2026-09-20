import { Component } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-sidebar',
  standalone: true,
  imports: [CommonModule, RouterLink, RouterLinkActive],
  template: `
    <aside class="sidebar">
      <div class="sidebar-brand">
        <span class="brand-mark" aria-hidden="true">
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M3 12h4l3 7 4-14 3 7h4"></path></svg>
        </span>
        <span class="brand-name">Supervision</span>
      </div>
      <nav class="sidebar-nav">
        <a routerLink="/dashboard" routerLinkActive="active" class="nav-item">
          <svg class="nav-icon" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><rect x="3" y="3" width="7.5" height="8.5" rx="2"></rect><rect x="13.5" y="3" width="7.5" height="5.5" rx="2"></rect><rect x="3" y="14.5" width="7.5" height="6.5" rx="2"></rect><rect x="13.5" y="11.5" width="7.5" height="9.5" rx="2"></rect></svg>
          <span>Dashboard</span>
        </a>
        <a routerLink="/resources" routerLinkActive="active" class="nav-item">
          <svg class="nav-icon" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><rect x="3" y="4.5" width="18" height="6" rx="2"></rect><rect x="3" y="13.5" width="18" height="6" rx="2"></rect></svg>
          <span>Ressources</span>
        </a>
        <a routerLink="/anomalies" routerLinkActive="active" class="nav-item">
          <svg class="nav-icon" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M12 4 2.5 20h19L12 4Z"></path><path d="M12 10v4"></path><path d="M12 17.5v.5"></path></svg>
          <span>Anomalies</span>
        </a>
        <a routerLink="/healing" routerLinkActive="active" class="nav-item">
          <svg class="nav-icon" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M14.5 6.5a4 4 0 1 0 3 6.9L21 17l-2 2-3.6-3.5a4 4 0 0 1-6.9-3"></path><path d="M9 9 4 4"></path></svg>
          <span>Auto-réparation</span>
        </a>
      </nav>
      <div class="sidebar-footer">
        <img class="bct-logo" src="bct-logo-light.png" alt="Banque Centrale de Tunisie" width="170" />
      </div>
    </aside>
  `,
  styles: [`
    .sidebar {
      background: var(--nav-surface);
      border-radius: var(--radius-lg);
      min-height: 100%;
      padding: 22px 16px;
      display: flex;
      flex-direction: column;
      box-sizing: border-box;
    }
    .sidebar-brand {
      display: flex;
      align-items: center;
      gap: 11px;
      padding: 0 6px 22px;
    }
    .brand-mark {
      width: 34px; height: 34px;
      border-radius: 11px;
      background: var(--brand);
      color: var(--ink-inverse);
      display: inline-flex;
      align-items: center;
      justify-content: center;
      flex: none;
    }
    .brand-name {
      font-size: 15px;
      font-weight: 700;
      letter-spacing: -0.01em;
      color: var(--nav-ink);
      white-space: nowrap;
    }
    .sidebar-nav {
      display: flex;
      flex-direction: column;
      gap: 4px;
    }
    .nav-item {
      display: flex;
      align-items: center;
      gap: 11px;
      height: 44px;
      padding: 0 14px;
      border-radius: var(--radius-md);
      color: var(--nav-ink-muted);
      text-decoration: none;
      font-size: 14px;
      font-weight: 500;
      white-space: nowrap;
      .nav-icon { flex: none; }
      &:hover { color: var(--nav-ink); }
      &.active {
        background: var(--nav-active-bg);
        color: var(--nav-active-ink);
        font-weight: 600;
      }
    }
    .bct-logo { display: block; width: 100%; max-width: 170px; height: auto; opacity: .92; }
    .sidebar-footer {
      margin-top: auto;
      padding: 16px 6px 0;
      font-size: 11px;
      line-height: 16px;
      color: var(--nav-ink-muted);
    }
  `]
})
export class SidebarComponent {}
