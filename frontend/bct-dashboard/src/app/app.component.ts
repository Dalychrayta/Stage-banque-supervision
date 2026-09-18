import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router, RouterOutlet, NavigationEnd } from '@angular/router';
import { filter } from 'rxjs';
import { SidebarComponent } from './layout/sidebar/sidebar.component';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [CommonModule, RouterOutlet, SidebarComponent],
  template: `
    <div class="app-layout" [class.no-sidebar]="isLoginPage">
      <app-sidebar *ngIf="!isLoginPage"></app-sidebar>
      <main class="main-content">
        <router-outlet></router-outlet>
      </main>
    </div>
  `,
  styles: [`
    .app-layout {
      display: grid;
      grid-template-columns: 236px 1fr;
      gap: 20px;
      padding: 20px;
      min-height: 100vh;
      box-sizing: border-box;
      background: var(--surface-canvas);
    }
    .main-content {
      min-width: 0;
    }
    .app-layout.no-sidebar {
      grid-template-columns: 1fr;
      padding: 0;
      gap: 0;
    }
  `]
})
export class AppComponent {
  isLoginPage = false;

  constructor(router: Router) {
    router.events.pipe(filter(e => e instanceof NavigationEnd)).subscribe(e => {
      this.isLoginPage = (e as NavigationEnd).urlAfterRedirects.startsWith('/login');
    });
  }
}
