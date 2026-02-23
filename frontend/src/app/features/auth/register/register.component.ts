import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../../../core/services/auth.service';

@Component({
  selector: 'app-register',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterLink],
  template: `
    <div class="register-page">
      <div class="register-left">
        <div class="brand-section">
          <div class="logo">
            <i class="fas fa-landmark"></i>
          </div>
          <h1>FinCore</h1>
          <p class="tagline">Start Your Banking Journey Today</p>
        </div>
        
        <div class="benefits">
          <h3><i class="fas fa-gift"></i> Account Benefits</h3>
          <ul>
            <li><i class="fas fa-check-circle"></i> Zero Balance Savings Account</li>
            <li><i class="fas fa-check-circle"></i> Free Debit Card</li>
            <li><i class="fas fa-check-circle"></i> Unlimited Free Transfers</li>
            <li><i class="fas fa-check-circle"></i> 24/7 Customer Support</li>
            <li><i class="fas fa-check-circle"></i> Mobile Banking Access</li>
            <li><i class="fas fa-check-circle"></i> Secure Online Transactions</li>
          </ul>
        </div>
      </div>

      <div class="register-right">
        <div class="register-card animate-fade-in">
          <div class="register-header">
            <h2>Create Your Account</h2>
            <p>Join thousands of happy customers</p>
          </div>

          <div class="steps-indicator">
            <div class="step active">
              <span class="step-number">1</span>
              <span class="step-text">Personal Info</span>
            </div>
            <div class="step-line"></div>
            <div class="step">
              <span class="step-number">2</span>
              <span class="step-text">Verification</span>
            </div>
          </div>

          <form [formGroup]="registerForm" (ngSubmit)="onSubmit()">
            <div class="form-row">
              <div class="form-group">
                <label for="firstName">
                  <i class="fas fa-user"></i> First Name
                </label>
                <input type="text" id="firstName" formControlName="firstName" placeholder="Enter first name">
                <small class="field-error" *ngIf="isFieldInvalid('firstName')">First name is required</small>
              </div>
              <div class="form-group">
                <label for="lastName">
                  <i class="fas fa-user"></i> Last Name
                </label>
                <input type="text" id="lastName" formControlName="lastName" placeholder="Enter last name">
                <small class="field-error" *ngIf="isFieldInvalid('lastName')">Last name is required</small>
              </div>
            </div>

            <div class="form-group">
              <label for="email">
                <i class="fas fa-envelope"></i> Email Address
              </label>
              <input type="email" id="email" formControlName="email" placeholder="Enter your email">
              <small class="field-error" *ngIf="isFieldInvalid('email')">
                {{ registerForm.get('email')?.hasError('email') ? 'Enter a valid email address' : 'Email is required' }}
              </small>
            </div>

            <div class="form-group">
              <label for="phoneNumber">
                <i class="fas fa-phone"></i> Mobile Number (Optional)
              </label>
              <input type="tel" id="phoneNumber" formControlName="phoneNumber" placeholder="+91 XXXXX XXXXX">
            </div>

            <div class="form-group">
              <label for="password">
                <i class="fas fa-lock"></i> Create Password
              </label>
              <div class="password-input">
                <input 
                  [type]="showPassword ? 'text' : 'password'" 
                  id="password" 
                  formControlName="password" 
                  placeholder="Minimum 6 characters">
                <button type="button" class="toggle-password" (click)="showPassword = !showPassword">
                  <i [class]="showPassword ? 'fas fa-eye-slash' : 'fas fa-eye'"></i>
                </button>
              </div>
              <small class="field-error" *ngIf="isFieldInvalid('password')">
                {{ registerForm.get('password')?.hasError('minlength') ? 'Password must be at least 6 characters' : 'Password is required' }}
              </small>
              <div class="password-strength" *ngIf="registerForm.get('password')?.value">
                <div class="strength-bar" [style.width]="getPasswordStrength() + '%'" 
                     [style.background]="getPasswordStrengthColor()"></div>
              </div>
            </div>

            <div class="terms-checkbox">
              <input type="checkbox" id="terms" formControlName="termsAccepted">
              <label for="terms">
                I agree to the <a href="#">Terms & Conditions</a> and <a href="#">Privacy Policy</a>
              </label>
            </div>
            <small class="field-error" *ngIf="isFieldInvalid('termsAccepted')">You must accept terms to continue</small>

            <div class="alert alert-error" *ngIf="errorMessage">
              <i class="fas fa-exclamation-triangle"></i>
              {{ errorMessage }}
            </div>

            <button type="submit" class="btn btn-orange btn-block" [disabled]="loading || registerForm.invalid">
              <span *ngIf="!loading">
                <i class="fas fa-user-plus"></i> Create Account
              </span>
              <span *ngIf="loading">
                <i class="fas fa-circle-notch fa-spin"></i> Creating Account...
              </span>
            </button>
          </form>

          <div class="login-link">
            Already have an account? <a routerLink="/login">Sign In</a>
          </div>
        </div>
      </div>
    </div>
  `,
  styles: [`
    .register-page {
      min-height: 100vh;
      display: flex;
    }

    .register-left {
      flex: 0.8;
      background: linear-gradient(135deg, #1a237e 0%, #0d47a1 50%, #1565c0 100%);
      padding: 60px;
      display: flex;
      flex-direction: column;
      justify-content: center;
      color: white;
      position: relative;
      overflow: hidden;
    }

    .register-left::before {
      content: '';
      position: absolute;
      top: -50%;
      right: -50%;
      width: 200%;
      height: 200%;
      background: radial-gradient(circle, rgba(255,111,0,0.15) 0%, transparent 50%);
    }

    .brand-section {
      position: relative;
      z-index: 1;
      margin-bottom: 50px;
    }

    .logo {
      width: 70px;
      height: 70px;
      background: rgba(255,255,255,0.2);
      border-radius: 18px;
      display: flex;
      align-items: center;
      justify-content: center;
      margin-bottom: 20px;
      backdrop-filter: blur(10px);
    }

    .logo i {
      font-size: 35px;
      color: #ff6f00;
    }

    .brand-section h1 {
      font-size: 2.5rem;
      font-weight: 700;
      margin-bottom: 8px;
    }

    .tagline {
      font-size: 1.1rem;
      opacity: 0.9;
    }

    .benefits {
      position: relative;
      z-index: 1;
      background: rgba(255,255,255,0.1);
      border-radius: 20px;
      padding: 28px;
      backdrop-filter: blur(10px);
    }

    .benefits h3 {
      display: flex;
      align-items: center;
      gap: 10px;
      margin-bottom: 20px;
      font-size: 1.2rem;
    }

    .benefits h3 i {
      color: #ff6f00;
    }

    .benefits ul {
      list-style: none;
    }

    .benefits li {
      display: flex;
      align-items: center;
      gap: 12px;
      padding: 10px 0;
      font-size: 0.95rem;
    }

    .benefits li i {
      color: #4caf50;
      font-size: 16px;
    }

    .register-right {
      flex: 1.2;
      display: flex;
      align-items: center;
      justify-content: center;
      padding: 40px;
      background: #f0f4f8;
      overflow-y: auto;
    }

    .register-card {
      width: 100%;
      max-width: 520px;
      background: white;
      padding: 40px;
      border-radius: 24px;
      box-shadow: 0 20px 60px rgba(0, 0, 0, 0.1);
    }

    .register-header {
      text-align: center;
      margin-bottom: 28px;
    }

    .register-header h2 {
      font-size: 1.6rem;
      color: #1a237e;
      margin-bottom: 6px;
    }

    .register-header p {
      color: #6b7280;
      font-size: 0.95rem;
    }

    .steps-indicator {
      display: flex;
      align-items: center;
      justify-content: center;
      margin-bottom: 32px;
    }

    .step {
      display: flex;
      flex-direction: column;
      align-items: center;
      gap: 6px;
    }

    .step-number {
      width: 36px;
      height: 36px;
      border-radius: 50%;
      background: #e0e0e0;
      display: flex;
      align-items: center;
      justify-content: center;
      font-weight: 600;
      color: #6b7280;
      transition: all 0.3s;
    }

    .step.active .step-number {
      background: linear-gradient(135deg, #ff6f00, #ffa040);
      color: white;
    }

    .step-text {
      font-size: 12px;
      color: #6b7280;
    }

    .step-line {
      width: 60px;
      height: 3px;
      background: #e0e0e0;
      margin: 0 16px;
      margin-bottom: 20px;
    }

    .form-row {
      display: grid;
      grid-template-columns: 1fr 1fr;
      gap: 16px;
    }

    .form-group label {
      display: flex;
      align-items: center;
      gap: 8px;
    }

    .form-group label i {
      color: #1a237e;
      font-size: 14px;
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

    .password-strength {
      height: 4px;
      background: #e0e0e0;
      border-radius: 2px;
      margin-top: 8px;
      overflow: hidden;
    }

    .field-error {
      display: block;
      margin-top: 6px;
      color: #c62828;
      font-size: 12px;
    }

    .strength-bar {
      height: 100%;
      transition: all 0.3s;
      border-radius: 2px;
    }

    .terms-checkbox {
      display: flex;
      align-items: flex-start;
      gap: 10px;
      margin-bottom: 24px;
      font-size: 13px;
      color: #6b7280;
    }

    .terms-checkbox input {
      width: auto;
      margin-top: 3px;
    }

    .terms-checkbox a {
      color: #1a237e;
      font-weight: 500;
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

    .login-link {
      text-align: center;
      margin-top: 24px;
      color: #6b7280;
      font-size: 14px;
    }

    .login-link a {
      color: #1a237e;
      font-weight: 600;
    }

    @media (max-width: 968px) {
      .register-left {
        display: none;
      }

      .register-right {
        padding: 20px;
      }

      .register-card {
        padding: 28px 20px;
      }

      .form-row {
        grid-template-columns: 1fr;
      }
    }
  `]
})
export class RegisterComponent {
  registerForm: FormGroup;
  loading = false;
  errorMessage = '';
  showPassword = false;

  constructor(
    private fb: FormBuilder,
    private authService: AuthService,
    private router: Router
  ) {
    this.registerForm = this.fb.group({
      firstName: ['', Validators.required],
      lastName: ['', Validators.required],
      email: ['', [Validators.required, Validators.email]],
      phoneNumber: [''],
      password: ['', [Validators.required, Validators.minLength(6)]],
      termsAccepted: [false, Validators.requiredTrue]
    });
  }

  isFieldInvalid(fieldName: string): boolean {
    const field = this.registerForm.get(fieldName);
    return !!field && field.invalid && (field.touched || field.dirty);
  }

  getPasswordStrength(): number {
    const password = this.registerForm.get('password')?.value || '';
    let strength = 0;
    if (password.length >= 6) strength += 25;
    if (password.length >= 8) strength += 25;
    if (/[A-Z]/.test(password)) strength += 25;
    if (/[0-9]/.test(password)) strength += 25;
    return strength;
  }

  getPasswordStrengthColor(): string {
    const strength = this.getPasswordStrength();
    if (strength <= 25) return '#c62828';
    if (strength <= 50) return '#f9a825';
    if (strength <= 75) return '#7cb342';
    return '#2e7d32';
  }

  onSubmit(): void {
    if (this.registerForm.invalid) {
      this.registerForm.markAllAsTouched();
      this.errorMessage = 'Please complete all required fields.';
      return;
    }

    this.loading = true;
    this.errorMessage = '';
    const { termsAccepted, ...payload } = this.registerForm.value;
    this.authService.register(payload).subscribe({
      next: (res) => {
        this.authService.persistAuthResponse(res);
        this.router.navigate(['/dashboard']);
      },
      error: (err) => {
        this.loading = false;
        this.errorMessage = err.error?.message || 'Registration failed. Please try again.';
      }
    });
  }
}
