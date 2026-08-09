import { Routes } from '@angular/router';
import { authGuard } from './core/guards/auth.guard';

export const routes: Routes = [
  { path: '', redirectTo: 'dashboard', pathMatch: 'full' },
  {
    path: 'login',
    loadComponent: () => import('./features/login/login.component').then(m => m.LoginComponent)
  },
  {
    path: 'dashboard',
    canActivate: [authGuard],
    loadComponent: () => import('./features/dashboard/dashboard.component').then(m => m.DashboardComponent)
  },
  {
    path: 'resources',
    canActivate: [authGuard],
    loadComponent: () => import('./features/resources/resources.component').then(m => m.ResourcesComponent)
  },
  {
    path: 'anomalies',
    canActivate: [authGuard],
    loadComponent: () => import('./features/anomalies/anomalies.component').then(m => m.AnomaliesComponent)
  },
  { path: 'rca', redirectTo: 'anomalies' },
  {
    path: 'healing',
    canActivate: [authGuard],
    loadComponent: () => import('./features/healing/healing.component').then(m => m.HealingComponent)
  },
  { path: '**', redirectTo: 'dashboard' }
];
