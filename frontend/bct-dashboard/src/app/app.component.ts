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
      display: flex;
      min-height: 100vh;
    }
    .main-content {
      margin-left: 240px;
      flex: 1;
      background: #f7fafc;
      min-height: 100vh;
    }
    .app-layout.no-sidebar .main-content {
      margin-left: 0;
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
