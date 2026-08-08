import { Routes } from '@angular/router';

export const routes: Routes = [
  { path: '', redirectTo: 'dashboard', pathMatch: 'full' },
  {
    path: 'dashboard',
    loadComponent: () => import('./features/dashboard/dashboard.component').then(m => m.DashboardComponent)
  },
  {
    path: 'resources',
    loadComponent: () => import('./features/resources/resources.component').then(m => m.ResourcesComponent)
  },
  {
    path: 'anomalies',
    loadComponent: () => import('./features/anomalies/anomalies.component').then(m => m.AnomaliesComponent)
  },
  {
    path: 'rca',
    loadComponent: () => import('./features/rca/rca.component').then(m => m.RcaComponent)
  },
  {
    path: 'healing',
    loadComponent: () => import('./features/healing/healing.component').then(m => m.HealingComponent)
  },
  { path: '**', redirectTo: 'dashboard' }
];
