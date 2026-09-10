import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import { AuthService } from '../../core/services/auth.service';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="login-screen">
      <div class="login-card">
        <div class="brand">
          <i class="pi pi-chart-line"></i>
          <span>BCT Supervision</span>
        </div>
        <p class="subtitle">Plateforme de supervision prédictive</p>
        <p class="hint">La connexion et les comptes sont gérés par Keycloak.</p>
        <button type="button" (click)="connect()">
          <i class="pi pi-sign-in"></i> Se connecter
        </button>
      </div>
    </div>
  `,
  styles: [`
    .login-screen { min-height: 100vh; display: flex; align-items: center; justify-content: center; background: #1a1f3a; }
    .login-card { background: #fff; border-radius: 14px; padding: 2.5rem; width: 340px; box-shadow: 0 10px 30px rgba(0,0,0,.25); display: flex; flex-direction: column; }
    .brand { display: flex; align-items: center; gap: .6rem; font-size: 1.2rem; font-weight: 700; color: #1a202c; }
    .brand i { color: #6c9bff; font-size: 1.5rem; }
    .subtitle { color: #718096; font-size: .85rem; margin: .35rem 0 .25rem; }
    .hint { color: #a0aec0; font-size: .78rem; margin: 0 0 1.5rem; }
    button { background: #6c9bff; color: #fff; border: none; border-radius: 8px; padding: .75rem; font-size: .9rem; font-weight: 600; cursor: pointer; display: flex; align-items: center; justify-content: center; gap: .5rem; }
  `]
})
export class LoginComponent implements OnInit {
  constructor(private auth: AuthService, private router: Router) {}

  ngOnInit(): void {
    // Si Keycloak a déjà renvoyé une session valide, on passe directement.
    if (this.auth.isAuthenticated()) {
      this.router.navigate(['/dashboard']);
    }
  }

  connect(): void {
    this.auth.login();
  }
}
