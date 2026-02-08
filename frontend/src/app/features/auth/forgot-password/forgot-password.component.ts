import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../../../core/services/auth.service';

@Component({
  selector: 'app-forgot-password',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterLink],
  template: `
    <div class="forgot-page">
      <div class="card">
        <h2>Reset Password</h2>
        <p class="subtitle">Enter your email to get a reset token</p>

        <form [formGroup]="requestForm" (ngSubmit)="requestReset()" *ngIf="step === 'request'">
          <div class="form-group">
            <label>Email</label>
            <input type="email" formControlName="email" placeholder="you@example.com">
          </div>
          <div class="alert alert-error" *ngIf="errorMessage">{{ errorMessage }}</div>
          <div class="alert alert-success" *ngIf="infoMessage">{{ infoMessage }}</div>
          <button class="btn" type="submit" [disabled]="loading || requestForm.invalid">
            {{ loading ? 'Sending...' : 'Send Reset Token' }}
          </button>
          <div class="link-row">
            <a routerLink="/login">Back to login</a>
          </div>
        </form>

        <form [formGroup]="resetForm" (ngSubmit)="resetPassword()" *ngIf="step === 'reset'">
          <div class="dev-token" *ngIf="devToken">
            <strong>Dev token:</strong> {{ devToken }}
          </div>
          <div class="form-group">
            <label>Reset Token</label>
            <input type="text" formControlName="token" placeholder="Paste token">
          </div>
          <div class="form-group">
            <label>New Password</label>
            <input type="password" formControlName="newPassword" placeholder="New password">
          </div>
          <div class="alert alert-error" *ngIf="errorMessage">{{ errorMessage }}</div>
          <div class="alert alert-success" *ngIf="infoMessage">{{ infoMessage }}</div>
          <button class="btn" type="submit" [disabled]="loading || resetForm.invalid">
            {{ loading ? 'Resetting...' : 'Reset Password' }}
          </button>
          <div class="link-row">
            <a routerLink="/login">Back to login</a>
          </div>
        </form>
      </div>
    </div>
  `,
  styles: [`
    .forgot-page {
      min-height: 100vh;
      display: flex;
      align-items: center;
      justify-content: center;
      background: #f0f4f8;
      padding: 24px;
    }
    .card {
      width: 100%;
      max-width: 420px;
      background: #fff;
      border-radius: 16px;
      padding: 28px;
      box-shadow: 0 12px 30px rgba(0,0,0,0.08);
    }
    h2 { margin-bottom: 8px; color: #1a237e; }
    .subtitle { color: #6b7280; margin-bottom: 20px; }
    .form-group { margin-bottom: 14px; }
    input {
      width: 100%;
      padding: 12px 14px;
      border: 1px solid #d1d5db;
      border-radius: 10px;
    }
    .btn {
      width: 100%;
      padding: 12px;
      background: #1a237e;
      color: white;
      border-radius: 10px;
      font-weight: 600;
    }
    .alert { padding: 10px 12px; border-radius: 8px; margin-bottom: 12px; }
    .alert-error { background: #ffebee; color: #c62828; }
    .alert-success { background: #e8f5e9; color: #2e7d32; }
    .link-row { margin-top: 12px; text-align: center; }
    .dev-token {
      background: #fff3e0;
      color: #e65100;
      border: 1px dashed #ffb74d;
      padding: 10px 12px;
      border-radius: 8px;
      margin-bottom: 12px;
      font-size: 13px;
    }
  `]
})
export class ForgotPasswordComponent {
  requestForm: FormGroup;
  resetForm: FormGroup;
  loading = false;
  errorMessage = '';
  infoMessage = '';
  step: 'request' | 'reset' = 'request';
  devToken: string | null = null;

  constructor(
    private fb: FormBuilder,
    private authService: AuthService,
    private router: Router
  ) {
    this.requestForm = this.fb.group({
      email: ['', [Validators.required, Validators.email]]
    });

    this.resetForm = this.fb.group({
      token: ['', Validators.required],
      newPassword: ['', [Validators.required, Validators.minLength(6)]]
    });
  }

  requestReset(): void {
    if (this.requestForm.invalid) return;
    this.loading = true;
    this.errorMessage = '';
    this.infoMessage = '';

    this.authService.forgotPassword(this.requestForm.value).subscribe({
      next: (res) => {
        this.loading = false;
        this.infoMessage = res.message || 'Reset token sent';
        if (res.resetToken) {
          this.devToken = res.resetToken;
          this.resetForm.patchValue({ token: res.resetToken });
        }
        this.step = 'reset';
      },
      error: (err) => {
        this.loading = false;
        this.errorMessage = err.error?.message || 'Failed to send reset token';
      }
    });
  }

  resetPassword(): void {
    if (this.resetForm.invalid) return;
    this.loading = true;
    this.errorMessage = '';
    this.infoMessage = '';

    this.authService.resetPassword(this.resetForm.value).subscribe({
      next: (res) => {
        this.loading = false;
        this.infoMessage = res.message || 'Password reset successful';
        setTimeout(() => this.router.navigate(['/login']), 800);
      },
      error: (err) => {
        this.loading = false;
        this.errorMessage = err.error?.message || 'Password reset failed';
      }
    });
  }
}
