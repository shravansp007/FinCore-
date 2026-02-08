import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../../../core/services/auth.service';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterLink],
  template: `
    <div class="login-page">
      <div class="login-left">
        <div class="brand-section">
          <div class="logo">
            <i class="fas fa-landmark"></i>
          </div>
          <h1>FinCore</h1>
          <p class="tagline">Your Trusted Banking Partner</p>
        </div>
        
        <div class="features">
          <div class="feature-item">
            <i class="fas fa-shield-alt"></i>
            <div>
              <h3>Secure Banking</h3>
              <p>256-bit encryption for all transactions</p>
            </div>
          </div>
          <div class="feature-item">
            <i class="fas fa-clock"></i>
            <div>
              <h3>24/7 Service</h3>
              <p>Bank anytime, anywhere</p>
            </div>
          </div>
          <div class="feature-item">
            <i class="fas fa-bolt"></i>
            <div>
              <h3>Instant Transfers</h3>
              <p>Real-time money transfers</p>
            </div>
          </div>
        </div>
      </div>

      <div class="login-right">
        <div class="login-card animate-fade-in">
          <div class="login-header">
            <h2>Welcome Back!</h2>
            <p>Sign in to access your account</p>
          </div>

          <form [formGroup]="loginForm" (ngSubmit)="onSubmit()">
            <div class="form-group">
              <label for="email">
                <i class="fas fa-envelope"></i> Email Address
              </label>
              <input 
                type="email" 
                id="email" 
                formControlName="email" 
                placeholder="Enter your registered email"
                [class.error]="loginForm.get('email')?.touched && loginForm.get('email')?.invalid">
              <span class="error-message" *ngIf="loginForm.get('email')?.touched && loginForm.get('email')?.errors?.['required']">
                <i class="fas fa-exclamation-circle"></i> Email is required
              </span>
            </div>

            <div class="form-group">
              <label for="password">
                <i class="fas fa-lock"></i> Password
              </label>
              <div class="password-input">
                <input 
                  [type]="showPassword ? 'text' : 'password'" 
                  id="password" 
                  formControlName="password" 
                  placeholder="Enter your password"
                  [class.error]="loginForm.get('password')?.touched && loginForm.get('password')?.invalid">
                <button type="button" class="toggle-password" (click)="showPassword = !showPassword">
                  <i [class]="showPassword ? 'fas fa-eye-slash' : 'fas fa-eye'"></i>
                </button>
              </div>
              <span class="error-message" *ngIf="loginForm.get('password')?.touched && loginForm.get('password')?.errors?.['required']">
                <i class="fas fa-exclamation-circle"></i> Password is required
              </span>
            </div>

            <div class="form-options">
              <label class="remember-me">
                <input type="checkbox"> Remember me
              </label>
              <a routerLink="/forgot-password" class="forgot-password">Forgot Password?</a>
            </div>

            <div class="alert alert-error" *ngIf="errorMessage">
              <i class="fas fa-exclamation-triangle"></i>
              {{ errorMessage }}
            </div>

            <button type="submit" class="btn btn-primary btn-block" [disabled]="loading">
              <span *ngIf="!loading">
                <i class="fas fa-sign-in-alt"></i> Sign In
              </span>
              <span *ngIf="loading" class="loading-spinner">
                <i class="fas fa-circle-notch fa-spin"></i> Signing in...
              </span>
            </button>
          </form>

          <div class="divider">
            <span>New to FinCore?</span>
          </div>

          <a routerLink="/register" class="btn btn-secondary btn-block">
            <i class="fas fa-user-plus"></i> Create New Account
          </a>

          <div class="security-notice">
            <i class="fas fa-lock"></i>
            <span>Your connection is secure and encrypted</span>
          </div>
        </div>
      </div>
    </div>
  `,
  styles: [`
    .login-page {
      min-height: 100vh;
      display: flex;
    }

    .login-left {
      flex: 1;
      background: linear-gradient(135deg, #1a237e 0%, #0d47a1 50%, #1565c0 100%);
      padding: 60px;
      display: flex;
      flex-direction: column;
      justify-content: center;
      color: white;
      position: relative;
      overflow: hidden;
    }

    .login-left::before {
      content: '';
      position: absolute;
      top: -50%;
      left: -50%;
      width: 200%;
      height: 200%;
      background: radial-gradient(circle, rgba(255,255,255,0.1) 0%, transparent 70%);
      animation: rotate 30s linear infinite;
    }

    @keyframes rotate {
      from { transform: rotate(0deg); }
      to { transform: rotate(360deg); }
    }

    .brand-section {
      position: relative;
      z-index: 1;
      margin-bottom: 60px;
    }

    .logo {
      width: 80px;
      height: 80px;
      background: rgba(255,255,255,0.2);
      border-radius: 20px;
      display: flex;
      align-items: center;
      justify-content: center;
      margin-bottom: 24px;
      backdrop-filter: blur(10px);
    }

    .logo i {
      font-size: 40px;
      color: #ff6f00;
    }

    .brand-section h1 {
      font-size: 3rem;
      font-weight: 700;
      margin-bottom: 12px;
    }

    .tagline {
      font-size: 1.25rem;
      opacity: 0.9;
    }

    .features {
      position: relative;
      z-index: 1;
    }

    .feature-item {
      display: flex;
      align-items: flex-start;
      gap: 20px;
      margin-bottom: 32px;
      padding: 20px;
      background: rgba(255,255,255,0.1);
      border-radius: 16px;
      backdrop-filter: blur(10px);
      transition: transform 0.3s ease;
    }

    .feature-item:hover {
      transform: translateX(10px);
    }

    .feature-item i {
      font-size: 28px;
      color: #ff6f00;
      margin-top: 4px;
    }

    .feature-item h3 {
      font-size: 1.1rem;
      margin-bottom: 4px;
    }

    .feature-item p {
      font-size: 0.9rem;
      opacity: 0.8;
    }

    .login-right {
      flex: 1;
      display: flex;
      align-items: center;
      justify-content: center;
      padding: 40px;
      background: #f0f4f8;
    }

    .login-card {
      width: 100%;
      max-width: 440px;
      background: white;
      padding: 48px;
      border-radius: 24px;
      box-shadow: 0 20px 60px rgba(0, 0, 0, 0.1);
    }

    .login-header {
      text-align: center;
      margin-bottom: 36px;
    }

    .login-header h2 {
      font-size: 1.75rem;
      color: #1a237e;
      margin-bottom: 8px;
    }

    .login-header p {
      color: #6b7280;
    }

    .form-group label {
      display: flex;
      align-items: center;
      gap: 8px;
    }

    .form-group label i {
      color: #1a237e;
    }

    .password-input {
      position: relative;
    }

    .password-input input {
      padding-right: 50px;
    }

    .toggle-password {
      position: absolute;
      right: 16px;
      top: 50%;
      transform: translateY(-50%);
      background: none;
      color: #6b7280;
      padding: 4px;
    }

    .toggle-password:hover {
      color: #1a237e;
    }

    input.error {
      border-color: #c62828;
    }

    .form-options {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 24px;
      font-size: 14px;
    }

    .remember-me {
      display: flex;
      align-items: center;
      gap: 8px;
      color: #6b7280;
      cursor: pointer;
    }

    .remember-me input {
      width: auto;
    }

    .forgot-password {
      color: #1a237e;
      font-weight: 500;
    }

    .forgot-password:hover {
      text-decoration: underline;
    }

    .alert {
      padding: 14px 18px;
      border-radius: 10px;
      margin-bottom: 20px;
      display: flex;
      align-items: center;
      gap: 12px;
      font-size: 14px;
    }

    .alert-error {
      background: #ffebee;
      color: #c62828;
      border: 1px solid #ffcdd2;
    }

    .btn-block {
      width: 100%;
      padding: 16px;
      font-size: 16px;
    }

    .loading-spinner {
      display: flex;
      align-items: center;
      justify-content: center;
      gap: 8px;
    }

    .divider {
      text-align: center;
      margin: 28px 0;
      position: relative;
    }

    .divider::before {
      content: '';
      position: absolute;
      left: 0;
      top: 50%;
      width: 100%;
      height: 1px;
      background: #e0e0e0;
    }

    .divider span {
      background: white;
      padding: 0 16px;
      position: relative;
      color: #6b7280;
      font-size: 14px;
    }

    .security-notice {
      display: flex;
      align-items: center;
      justify-content: center;
      gap: 8px;
      margin-top: 28px;
      padding-top: 20px;
      border-top: 1px solid #e0e0e0;
      font-size: 13px;
      color: #2e7d32;
    }

    .security-notice i {
      font-size: 14px;
    }

    @media (max-width: 968px) {
      .login-left {
        display: none;
      }
      
      .login-right {
        padding: 20px;
      }

      .login-card {
        padding: 32px 24px;
      }
    }
  `]
})
export class LoginComponent {
  loginForm: FormGroup;
  loading = false;
  errorMessage = '';
  showPassword = false;

  constructor(
    private fb: FormBuilder,
    private authService: AuthService,
    private router: Router
  ) {
    this.loginForm = this.fb.group({
      email: ['', [Validators.required, Validators.email]],
      password: ['', Validators.required]
    });
  }

  onSubmit(): void {
    if (this.loginForm.invalid) {
      this.loginForm.markAllAsTouched();
      return;
    }

    this.loading = true;
    this.errorMessage = '';

    this.authService.login(this.loginForm.value).subscribe({
      next: (res) => {
        this.authService.persistAuthResponse(res);
        this.router.navigate(['/dashboard']);
      },
      error: (err) => {
        this.loading = false;
        this.errorMessage = err.error?.message || 'Invalid email or password. Please try again.';
      }
    });
  }
}
