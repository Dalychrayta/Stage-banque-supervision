import { Component, Input, Inject, PLATFORM_ID } from '@angular/core';
import { CommonModule, isPlatformBrowser } from '@angular/common';

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
        </span>
        <span class="time">{{ currentTime | date:'HH:mm:ss' }}</span>
      </div>
    </header>
  `,
  styles: [`
    .top-header {
      display: flex;
      align-items: center;
      justify-content: space-between;
      padding: 1rem 1.5rem;
      background: #fff;
      border-bottom: 1px solid #e2e8f0;
      position: sticky;
      top: 0;
      z-index: 50;
    }
    .page-title {
      font-size: 1.2rem;
      font-weight: 600;
      color: #1a202c;
      margin: 0;
    }
    .header-right {
      display: flex;
      align-items: center;
      gap: 1rem;
    }
    .live-badge {
      display: flex;
      align-items: center;
      gap: 0.4rem;
      background: #f0fff4;
      color: #38a169;
      padding: 0.25rem 0.75rem;
      border-radius: 20px;
      font-size: 0.8rem;
      font-weight: 600;
    }
    .dot {
      width: 8px; height: 8px;
      background: #38a169;
      border-radius: 50%;
      animation: pulse 1.5s infinite;
    }
    @keyframes pulse {
      0%, 100% { opacity: 1; }
      50% { opacity: 0.3; }
    }
    .time { color: #718096; font-size: 0.9rem; font-family: monospace; }
  `]
})
export class HeaderComponent {
  @Input() title = 'Dashboard';
  currentTime = new Date();

  constructor(@Inject(PLATFORM_ID) private platformId: Object) {
    if (isPlatformBrowser(this.platformId)) {
      setInterval(() => this.currentTime = new Date(), 1000);
    }
  }
}
