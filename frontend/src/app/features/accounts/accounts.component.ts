import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { AccountService } from '../../core/services/account.service';
import { Account } from '../../shared/models/account.model';

@Component({
  selector: 'app-accounts',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  template: `
    <div class="accounts-page">
      <div class="container">
        <!-- Page Header -->
        <div class="page-header">
          <div class="header-left">
            <h1><i class="fas fa-piggy-bank"></i> My Accounts</h1>
            <p>Manage your bank accounts with FinCore</p>
          </div>
          <button class="btn btn-primary" (click)="showCreateModal = true">
            <i class="fas fa-plus"></i> Open New Account
          </button>
        </div>

        <div class="loading-row" *ngIf="loadingAccounts && !accounts.length">
          <i class="fas fa-spinner fa-spin"></i> Loading accounts...
        </div>
        <div class="error-message" *ngIf="pageErrorMessage">
          <i class="fas fa-exclamation-circle"></i> {{ pageErrorMessage }}
        </div>

        <!-- Account Stats -->
        <div class="stats-row" *ngIf="accounts.length">
          <div class="stat-item">
            <div class="stat-icon blue"><i class="fas fa-wallet"></i></div>
            <div class="stat-content">
              <span class="stat-value">{{ accounts.length }}</span>
              <span class="stat-label">Total Accounts</span>
            </div>
          </div>
          <div class="stat-item">
            <div class="stat-icon green"><i class="fas fa-rupee-sign"></i></div>
            <div class="stat-content">
              <span class="stat-value">₹{{ getTotalBalance() | number:'1.2-2' }}</span>
              <span class="stat-label">Total Balance</span>
            </div>
          </div>
          <div class="stat-item">
            <div class="stat-icon orange"><i class="fas fa-check-circle"></i></div>
            <div class="stat-content">
              <span class="stat-value">{{ getActiveAccounts() }}</span>
              <span class="stat-label">Active Accounts</span>
            </div>
          </div>
        </div>

        <!-- Accounts Grid -->
        <div class="accounts-grid" *ngIf="accounts.length; else noAccounts">
          <div class="account-card" *ngFor="let account of accounts" [class]="'type-' + account.accountType.toLowerCase()">
            <div class="card-ribbon"></div>
            <div class="account-header">
              <div class="account-type-info">
                <div class="type-icon">
                  <i [class]="getAccountIcon(account.accountType)"></i>
                </div>
                <div>
                  <span class="account-type">{{ formatAccountType(account.accountType) }}</span>
                  <span class="account-status" [class.active]="account.active">
                    <i [class]="account.active ? 'fas fa-circle' : 'far fa-circle'"></i>
                    {{ account.active ? 'Active' : 'Inactive' }}
                  </span>
                </div>
              </div>
            </div>
            <div class="account-number">
              <span class="label">Account Number</span>
              <span class="number"><i class="fas fa-credit-card"></i> {{ getMaskedNumber(account) }}</span>
            </div>
            <div class="account-balance">
              <span class="balance-label">Available Balance</span>
              <span class="balance-value">₹{{ account.balance | number:'1.2-2' }}</span>
            </div>
            <div class="account-actions">
              <button class="action-btn" (click)="openDetails(account)"><i class="fas fa-eye"></i> View Details</button>
              <button class="action-btn primary" (click)="startTransfer(account)"><i class="fas fa-exchange-alt"></i> Transfer</button>
            </div>
            <div class="account-footer">
              <span class="created-date">
                <i class="fas fa-calendar-alt"></i>
                Opened on {{ account.createdAt | date:'mediumDate' }}
              </span>
            </div>
          </div>
        </div>

        <ng-template #noAccounts>
          <div class="empty-state card">
            <div class="empty-icon">
              <i class="fas fa-university"></i>
            </div>
            <h2>No Accounts Yet</h2>
            <p>Start your banking journey with FinCore by opening your first account</p>
            <div class="account-types-preview">
              <div class="preview-item">
                <i class="fas fa-piggy-bank"></i>
                <span>Savings</span>
              </div>
              <div class="preview-item">
                <i class="fas fa-money-check-alt"></i>
                <span>Current</span>
              </div>
              <div class="preview-item">
                <i class="fas fa-landmark"></i>
                <span>Fixed Deposit</span>
              </div>
            </div>
            <button class="btn btn-primary btn-lg" (click)="showCreateModal = true">
              <i class="fas fa-plus"></i> Open Your First Account
            </button>
          </div>
        </ng-template>
      </div>

      <!-- Create Account Modal -->
      <div class="modal-overlay" *ngIf="showCreateModal" (click)="showCreateModal = false">
        <div class="modal" (click)="$event.stopPropagation()">
          <div class="modal-header">
            <div>
              <h2><i class="fas fa-plus-circle"></i> Open New Account</h2>
              <p>Choose your account type and get started</p>
            </div>
            <button class="close-btn" (click)="showCreateModal = false">
              <i class="fas fa-times"></i>
            </button>
          </div>
          <form [formGroup]="createForm" (ngSubmit)="createAccount()">
            <div class="account-type-selector">
              <label class="type-option" [class.selected]="createForm.get('accountType')?.value === 'SAVINGS'">
                <input type="radio" formControlName="accountType" value="SAVINGS">
                <div class="option-content">
                  <i class="fas fa-piggy-bank"></i>
                  <span class="option-title">Savings Account</span>
                  <span class="option-desc">Earn interest on deposits</span>
                </div>
              </label>
              <label class="type-option" [class.selected]="createForm.get('accountType')?.value === 'CHECKING'">
                <input type="radio" formControlName="accountType" value="CHECKING">
                <div class="option-content">
                  <i class="fas fa-money-check-alt"></i>
                  <span class="option-title">Current Account</span>
                  <span class="option-desc">For daily transactions</span>
                </div>
              </label>
              <label class="type-option" [class.selected]="createForm.get('accountType')?.value === 'FIXED_DEPOSIT'">
                <input type="radio" formControlName="accountType" value="FIXED_DEPOSIT">
                <div class="option-content">
                  <i class="fas fa-landmark"></i>
                  <span class="option-title">Fixed Deposit</span>
                  <span class="option-desc">Higher returns on savings</span>
                </div>
              </label>
            </div>
            <div class="form-group">
              <label for="initialDeposit"><i class="fas fa-rupee-sign"></i> Initial Deposit</label>
              <div class="input-with-prefix">
                <span class="prefix">₹</span>
                <input type="number" id="initialDeposit" formControlName="initialDeposit" 
                       placeholder="Enter amount (min ₹500)" min="0" step="100">
              </div>
              <small>Minimum deposit: ₹500 for Savings, ₹5000 for Current Account</small>
            </div>
            <div class="form-group">
              <label for="currency"><i class="fas fa-globe"></i> Currency</label>
              <select id="currency" formControlName="currency">
                <option value="INR">INR - Indian Rupee</option>
                <option value="USD">USD - US Dollar</option>
              </select>
            </div>
            <div class="error-message" *ngIf="errorMessage">
              <i class="fas fa-exclamation-circle"></i> {{ errorMessage }}
            </div>
            <div class="modal-actions">
              <button type="button" class="btn btn-secondary" (click)="showCreateModal = false">Cancel</button>
              <button type="submit" class="btn btn-primary" [disabled]="loading || createForm.invalid">
                <i [class]="loading ? 'fas fa-spinner fa-spin' : 'fas fa-check'"></i>
                {{ loading ? 'Creating...' : 'Open Account' }}
              </button>
            </div>
          </form>
        </div>
      </div>
    </div>
  `,
  styles: [`
    .accounts-page {
      padding: 24px 0 40px;
      background: #f0f4f8;
      min-height: calc(100vh - 70px);
    }

    .page-header {
      display: flex;
      justify-content: space-between;
      align-items: flex-start;
      margin-bottom: 32px;
      background: linear-gradient(135deg, #1a237e 0%, #0d47a1 100%);
      padding: 32px;
      border-radius: 20px;
      color: white;
    }

    .header-left h1 {
      font-size: 1.75rem;
      display: flex;
      align-items: center;
      gap: 12px;
      margin-bottom: 8px;
    }

    .header-left p {
      opacity: 0.85;
      font-size: 0.95rem;
    }

    .page-header .btn-primary {
      background: #ff6f00;
      border: none;
    }

    .page-header .btn-primary:hover {
      background: #e65100;
    }

    .stats-row {
      display: grid;
      grid-template-columns: repeat(3, 1fr);
      gap: 20px;
      margin-bottom: 32px;
    }

    .stat-item {
      background: white;
      border-radius: 16px;
      padding: 24px;
      display: flex;
      align-items: center;
      gap: 16px;
      box-shadow: 0 2px 8px rgba(0,0,0,0.05);
    }

    .stat-icon {
      width: 56px;
      height: 56px;
      border-radius: 14px;
      display: flex;
      align-items: center;
      justify-content: center;
      font-size: 24px;
    }

    .stat-icon.blue { background: #e3f2fd; color: #1565c0; }
    .stat-icon.green { background: #e8f5e9; color: #2e7d32; }
    .stat-icon.orange { background: #fff3e0; color: #ef6c00; }

    .stat-content {
      display: flex;
      flex-direction: column;
    }

    .stat-value {
      font-size: 1.5rem;
      font-weight: 700;
      color: #1a237e;
    }

    .stat-label {
      font-size: 0.875rem;
      color: #6b7280;
    }

    .loading-row {
      padding: 16px 20px;
      color: #6b7280;
      display: flex;
      align-items: center;
      gap: 8px;
      background: white;
      border-radius: 12px;
      box-shadow: 0 2px 6px rgba(0,0,0,0.05);
      margin-bottom: 20px;
    }

    .accounts-grid {
      display: grid;
      grid-template-columns: repeat(auto-fill, minmax(380px, 1fr));
      gap: 24px;
    }

    .account-card {
      background: white;
      border-radius: 20px;
      padding: 0;
      box-shadow: 0 4px 12px rgba(0, 0, 0, 0.08);
      transition: all 0.3s ease;
      overflow: hidden;
      position: relative;
    }

    .card-ribbon {
      height: 6px;
      background: linear-gradient(90deg, #1a237e, #3949ab);
    }

    .account-card.type-savings .card-ribbon { background: linear-gradient(90deg, #2e7d32, #43a047); }
    .account-card.type-checking .card-ribbon { background: linear-gradient(90deg, #1565c0, #1976d2); }
    .account-card.type-fixed_deposit .card-ribbon { background: linear-gradient(90deg, #ef6c00, #ff9800); }

    .account-card:hover {
      transform: translateY(-6px);
      box-shadow: 0 16px 32px rgba(26, 35, 126, 0.15);
    }

    .account-header {
      padding: 24px 24px 16px;
    }

    .account-type-info {
      display: flex;
      align-items: center;
      gap: 14px;
    }

    .type-icon {
      width: 52px;
      height: 52px;
      border-radius: 14px;
      background: linear-gradient(135deg, #1a237e, #3949ab);
      display: flex;
      align-items: center;
      justify-content: center;
      font-size: 22px;
      color: white;
    }

    .account-card.type-savings .type-icon { background: linear-gradient(135deg, #2e7d32, #43a047); }
    .account-card.type-checking .type-icon { background: linear-gradient(135deg, #1565c0, #1976d2); }
    .account-card.type-fixed_deposit .type-icon { background: linear-gradient(135deg, #ef6c00, #ff9800); }

    .account-type {
      display: block;
      font-weight: 700;
      font-size: 1.1rem;
      color: #1f2937;
    }

    .account-status {
      display: flex;
      align-items: center;
      gap: 6px;
      font-size: 0.75rem;
      color: #9ca3af;
      margin-top: 4px;
    }

    .account-status.active {
      color: #2e7d32;
    }

    .account-status i {
      font-size: 8px;
    }

    .account-number {
      padding: 0 24px 16px;
    }

    .account-number .label {
      display: block;
      font-size: 0.75rem;
      color: #9ca3af;
      margin-bottom: 4px;
    }

    .account-number .number {
      color: #374151;
      font-family: 'Courier New', monospace;
      font-size: 1.1rem;
      display: flex;
      align-items: center;
      gap: 10px;
    }

    .account-balance {
      padding: 20px 24px;
      background: linear-gradient(135deg, #f8f9ff, #eef1ff);
      margin: 0 16px;
      border-radius: 14px;
    }

    .balance-label {
      display: block;
      color: #6b7280;
      font-size: 0.8rem;
      margin-bottom: 6px;
    }

    .balance-value {
      font-size: 2rem;
      font-weight: 700;
      color: #1a237e;
    }

    .account-actions {
      padding: 16px 24px;
      display: flex;
      gap: 12px;
    }

    .action-btn {
      flex: 1;
      padding: 10px 16px;
      border-radius: 10px;
      font-size: 0.85rem;
      font-weight: 600;
      background: #f3f4f6;
      color: #374151;
      display: flex;
      align-items: center;
      justify-content: center;
      gap: 8px;
      transition: all 0.2s;
    }

    .action-btn:hover {
      background: #e5e7eb;
    }

    .action-btn.primary {
      background: #1a237e;
      color: white;
    }

    .action-btn.primary:hover {
      background: #0d47a1;
    }

    .account-footer {
      padding: 16px 24px;
      border-top: 1px solid #f3f4f6;
    }

    .created-date {
      color: #9ca3af;
      font-size: 0.8rem;
      display: flex;
      align-items: center;
      gap: 8px;
    }

    .empty-state {
      text-align: center;
      padding: 60px 40px;
      background: white;
      border-radius: 20px;
    }

    .empty-icon {
      width: 100px;
      height: 100px;
      border-radius: 50%;
      background: linear-gradient(135deg, #e3f2fd, #bbdefb);
      display: flex;
      align-items: center;
      justify-content: center;
      margin: 0 auto 24px;
    }

    .empty-icon i {
      font-size: 40px;
      color: #1a237e;
    }

    .empty-state h2 {
      color: #1a237e;
      margin-bottom: 8px;
      font-size: 1.5rem;
    }

    .empty-state p {
      color: #6b7280;
      margin-bottom: 32px;
      max-width: 400px;
      margin-left: auto;
      margin-right: auto;
    }

    .account-types-preview {
      display: flex;
      justify-content: center;
      gap: 32px;
      margin-bottom: 32px;
    }

    .preview-item {
      display: flex;
      flex-direction: column;
      align-items: center;
      gap: 8px;
    }

    .preview-item i {
      font-size: 32px;
      color: #1a237e;
    }

    .preview-item span {
      font-size: 0.85rem;
      color: #6b7280;
    }

    .btn-lg {
      padding: 14px 32px;
      font-size: 1rem;
    }

    /* Modal Styles */
    .modal-overlay {
      position: fixed;
      top: 0;
      left: 0;
      right: 0;
      bottom: 0;
      background: rgba(26, 35, 126, 0.5);
      display: flex;
      align-items: center;
      justify-content: center;
      z-index: 1000;
      backdrop-filter: blur(4px);
    }

    .modal {
      background: white;
      border-radius: 24px;
      padding: 0;
      width: 100%;
      max-width: 520px;
      max-height: 90vh;
      overflow-y: auto;
      box-shadow: 0 24px 48px rgba(0,0,0,0.2);
    }

    .modal-header {
      display: flex;
      justify-content: space-between;
      align-items: flex-start;
      padding: 28px 32px;
      background: linear-gradient(135deg, #1a237e, #0d47a1);
      color: white;
    }

    .modal-header h2 {
      display: flex;
      align-items: center;
      gap: 10px;
      margin-bottom: 4px;
    }

    .modal-header p {
      opacity: 0.85;
      font-size: 0.9rem;
    }

    .close-btn {
      background: rgba(255,255,255,0.1);
      color: white;
      font-size: 1.1rem;
      padding: 10px;
      border-radius: 10px;
      transition: background 0.2s;
    }

    .close-btn:hover {
      background: rgba(255,255,255,0.2);
    }

    .modal form {
      padding: 28px 32px;
    }

    .account-type-selector {
      display: grid;
      grid-template-columns: repeat(3, 1fr);
      gap: 12px;
      margin-bottom: 24px;
    }

    .type-option {
      cursor: pointer;
    }

    .type-option input {
      display: none;
    }

    .option-content {
      display: flex;
      flex-direction: column;
      align-items: center;
      text-align: center;
      padding: 20px 12px;
      border: 2px solid #e5e7eb;
      border-radius: 14px;
      transition: all 0.2s;
    }

    .option-content i {
      font-size: 28px;
      color: #9ca3af;
      margin-bottom: 10px;
    }

    .option-title {
      font-weight: 600;
      color: #374151;
      font-size: 0.85rem;
      margin-bottom: 4px;
    }

    .option-desc {
      font-size: 0.7rem;
      color: #9ca3af;
    }

    .type-option.selected .option-content {
      border-color: #1a237e;
      background: #f0f4ff;
    }

    .type-option.selected .option-content i {
      color: #1a237e;
    }

    .input-with-prefix {
      display: flex;
      align-items: center;
      border: 1px solid #d1d5db;
      border-radius: 10px;
      overflow: hidden;
    }

    .input-with-prefix .prefix {
      padding: 12px 16px;
      background: #f3f4f6;
      color: #6b7280;
      font-weight: 600;
    }

    .input-with-prefix input {
      border: none;
      flex: 1;
      padding: 12px;
    }

    .input-with-prefix input:focus {
      outline: none;
    }

    .form-group small {
      display: block;
      margin-top: 6px;
      font-size: 0.75rem;
      color: #9ca3af;
    }

    .form-group label i {
      margin-right: 6px;
      color: #1a237e;
    }

    .modal-actions {
      display: flex;
      gap: 12px;
      margin-top: 28px;
    }

    .modal-actions button {
      flex: 1;
    }\n
    @media (max-width: 768px) {
      .stats-row {
        grid-template-columns: 1fr;
      }

      .accounts-grid {
        grid-template-columns: 1fr;
      }

      .account-type-selector {
        grid-template-columns: 1fr;
      }

      .page-header {
        flex-direction: column;
        gap: 20px;
      }
    }
  `]
})
export class AccountsComponent implements OnInit {
  accounts: Account[] = [];
  showCreateModal = false;
  createForm: FormGroup;
  loading = false;
  errorMessage = '';
  loadingAccounts = false;
  pageErrorMessage = '';

  constructor(
    private accountService: AccountService,
    private fb: FormBuilder,
    private router: Router
  ) {
    this.createForm = this.fb.group({
      accountType: ['', Validators.required],
      initialDeposit: [500],
      currency: ['INR']
    });
  }

  ngOnInit(): void {
    this.loadAccounts();
  }

  loadAccounts(): void {
    this.loadingAccounts = true;
    this.pageErrorMessage = '';
    this.accountService.getUserAccounts().subscribe({
      next: (accounts) => {
        this.accounts = accounts;
        this.loadingAccounts = false;
      },
      error: (err) => {
        this.loadingAccounts = false;
        this.pageErrorMessage = this.getErrorMessage(err, 'Unable to load accounts.');
      }
    });
  }

  createAccount(): void {
    if (this.createForm.invalid || this.loading) return;

    this.loading = true;
    this.errorMessage = '';

    this.accountService.createAccount(this.createForm.value).subscribe({
      next: (account) => {
        this.accounts.push(account);
        this.showCreateModal = false;
        this.createForm.reset({ currency: 'INR', initialDeposit: 500 });
        this.loading = false;
      },
      error: (err) => {
        this.loading = false;
        this.errorMessage = this.getErrorMessage(err, 'Failed to create account');
      }
    });
  }

  openDetails(account: Account): void {
    this.router.navigate(['/accounts', account.id]);
  }

  startTransfer(account: Account): void {
    this.router.navigate(['/transactions'], { queryParams: { mode: 'TRANSFER', sourceAccountId: account.id } });
  }

  getTotalBalance(): number {
    return this.accounts.reduce((sum, acc) => sum + acc.balance, 0);
  }

  getActiveAccounts(): number {
    return this.accounts.filter(acc => acc.active).length;
  }

  formatAccountNumber(number: string): string {
    return number.replace(/(.{4})/g, '$1 ').trim();
  }

  getMaskedNumber(account: Account): string {
    if (account.maskedAccountNumber) {
      return account.maskedAccountNumber;
    }
    return this.formatAccountNumber(account.accountNumber || '');
  }

  formatAccountType(type: string): string {
    const types: { [key: string]: string } = {
      'SAVINGS': 'Savings Account',
      'CHECKING': 'Current Account',
      'FIXED_DEPOSIT': 'Fixed Deposit'
    };
    return types[type] || type;
  }

  getAccountIcon(type: string): string {
    const icons: { [key: string]: string } = {
      'SAVINGS': 'fas fa-piggy-bank',
      'CHECKING': 'fas fa-money-check-alt',
      'FIXED_DEPOSIT': 'fas fa-landmark'
    };
    return icons[type] || 'fas fa-university';
  }

  private getErrorMessage(err: any, fallback: string): string {
    const error = err?.error;
    if (error && error.errors && typeof error.errors === 'object') {
      const firstKey = Object.keys(error.errors)[0];
      if (firstKey) {
        return `${firstKey}: ${error.errors[firstKey]}`;
      }
    }
    if (error && typeof error.message === 'string' && error.message.trim()) {
      return error.message;
    }
    return fallback;
  }
}









