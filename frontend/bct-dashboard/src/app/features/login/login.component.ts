import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { AuthService } from '../../core/services/auth.service';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="login-screen">
      <form class="login-card" (ngSubmit)="submit()">
        <div class="brand">
          <i class="pi pi-chart-line"></i>
          <span>BCT Supervision</span>
        </div>
        <p class="subtitle">Plateforme de supervision prédictive</p>

        <label>Utilisateur</label>
        <input type="text" name="username" [(ngModel)]="username" autocomplete="username" required />

        <label>Mot de passe</label>
        <input type="password" name="password" [(ngModel)]="password" autocomplete="current-password" required />

        <div class="error" *ngIf="error">{{ error }}</div>

        <button type="submit" [disabled]="loading">
          {{ loading ? 'Connexion...' : 'Se connecter' }}
        </button>
      </form>
    </div>
  `,
  styles: [`
    .login-screen {
      min-height: 100vh;
      display: flex;
      align-items: center;
      justify-content: center;
      background: #1a1f3a;
    }
    .login-card {
      background: #fff;
      border-radius: 14px;
      padding: 2.5rem;
      width: 340px;
      box-shadow: 0 10px 30px rgba(0,0,0,.25);
      display: flex;
      flex-direction: column;
    }
    .brand {
      display: flex;
      align-items: center;
      gap: 0.6rem;
      font-size: 1.2rem;
      font-weight: 700;
      color: #1a202c;
      i { color: #6c9bff; font-size: 1.5rem; }
    }
    .subtitle {
      color: #718096;
      font-size: 0.85rem;
      margin: 0.35rem 0 1.5rem;
    }
    label {
      font-size: 0.8rem;
      font-weight: 600;
      color: #4a5568;
      margin-bottom: 0.35rem;
    }
    input {
      padding: 0.6rem 0.75rem;
      border: 1px solid #e2e8f0;
      border-radius: 8px;
      font-size: 0.9rem;
      margin-bottom: 1.1rem;
      outline: none;
    }
    input:focus { border-color: #6c9bff; }
    .error {
      background: #fff5f5;
      color: #e53e3e;
      font-size: 0.8rem;
      padding: 0.5rem 0.75rem;
      border-radius: 8px;
      margin-bottom: 1rem;
    }
    button {
      background: #6c9bff;
      color: #fff;
      border: none;
      border-radius: 8px;
      padding: 0.7rem;
      font-size: 0.9rem;
      font-weight: 600;
      cursor: pointer;
    }
    button:disabled { opacity: 0.6; cursor: default; }
  `]
})
export class LoginComponent {
  username = '';
  password = '';
  loading = false;
  error = '';

  constructor(private auth: AuthService, private router: Router) {}

  submit(): void {
    if (!this.username || !this.password) return;
    this.loading = true;
    this.error = '';
    this.auth.login(this.username, this.password).subscribe(ok => {
      this.loading = false;
      if (ok) {
        this.router.navigate(['/dashboard']);
      } else {
        this.error = 'Identifiants incorrects.';
      }
    });
  }
}
