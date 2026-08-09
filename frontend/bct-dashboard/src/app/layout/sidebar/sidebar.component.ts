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
        <i class="pi pi-chart-line"></i>
        <span>BCT Supervision</span>
      </div>
      <nav class="sidebar-nav">
        <a routerLink="/dashboard" routerLinkActive="active" class="nav-item">
          <i class="pi pi-home"></i><span>Dashboard</span>
        </a>
        <a routerLink="/resources" routerLinkActive="active" class="nav-item">
          <i class="pi pi-server"></i><span>Ressources</span>
        </a>
        <a routerLink="/anomalies" routerLinkActive="active" class="nav-item">
          <i class="pi pi-exclamation-triangle"></i><span>Anomalies</span>
        </a>
        <a routerLink="/healing" routerLinkActive="active" class="nav-item">
          <i class="pi pi-wrench"></i><span>Auto-Healing</span>
        </a>
      </nav>
      <div class="sidebar-footer">
        <span>Banque Centrale de Tunisie</span>
      </div>
    </aside>
  `,
  styles: [`
    .sidebar {
      width: 240px;
      min-height: 100vh;
      background: #1a1f3a;
      display: flex;
      flex-direction: column;
      position: fixed;
      left: 0; top: 0;
      z-index: 100;
    }
    .sidebar-brand {
      padding: 1.5rem;
      display: flex;
      align-items: center;
      gap: 0.75rem;
      color: #fff;
      font-size: 1.1rem;
      font-weight: 700;
      border-bottom: 1px solid #2d3561;
      i { font-size: 1.4rem; color: #6c9bff; }
    }
    .sidebar-nav {
      flex: 1;
      padding: 1rem 0;
    }
    .nav-item {
      display: flex;
      align-items: center;
      gap: 0.75rem;
      padding: 0.85rem 1.5rem;
      color: #a0aec0;
      text-decoration: none;
      transition: all 0.2s;
      font-size: 0.9rem;
      i { width: 18px; }
      &:hover { background: #2d3561; color: #fff; }
      &.active { background: #2d3561; color: #6c9bff; border-left: 3px solid #6c9bff; }
    }
    .sidebar-footer {
      padding: 1rem 1.5rem;
      color: #4a5568;
      font-size: 0.75rem;
      border-top: 1px solid #2d3561;
    }
  `]
})
export class SidebarComponent {}
