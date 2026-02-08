import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../core/services/auth.service';
import { DashboardService } from '../../core/services/dashboard.service';
import { Account } from '../../shared/models/account.model';
import { Transaction } from '../../shared/models/transaction.model';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [CommonModule, RouterLink],
  template: `
    <div class="dashboard">
      <div class="container">
        <!-- Welcome Banner -->
        <div class="welcome-banner animate-fade-in">
          <div class="welcome-content">
            <div class="greeting">
              <span class="time-greeting">{{ getGreeting() }}</span>
              <h1>{{ (authService.currentUser$ | async)?.firstName }} {{ (authService.currentUser$ | async)?.lastName }}</h1>
              <p>Welcome to FinCore Internet Banking</p>
            </div>
            <div class="last-login">
              <i class="fas fa-clock"></i>
              <span>Last Login: {{ getCurrentDate() }}</span>
            </div>
          </div>
          <div class="welcome-illustration">
            <i class="fas fa-chart-line"></i>
          </div>
        </div>

        <!-- Quick Actions -->
        <div class="section-header">
          <h2><i class="fas fa-bolt"></i> Quick Actions</h2>
        </div>
        <div class="quick-actions animate-slide-in">
          <button class="quick-action-btn" routerLink="/transactions">
            <div class="action-icon blue">
              <i class="fas fa-exchange-alt"></i>
            </div>
            <span>Fund Transfer</span>
          </button>
          <button class="quick-action-btn" routerLink="/accounts">
            <div class="action-icon green">
              <i class="fas fa-plus-circle"></i>
            </div>
            <span>New Account</span>
          </button>
          <button class="quick-action-btn" routerLink="/transactions">
            <div class="action-icon orange">
              <i class="fas fa-money-bill-wave"></i>
            </div>
            <span>Deposit</span>
          </button>
          <button class="quick-action-btn" routerLink="/transactions">
            <div class="action-icon purple">
              <i class="fas fa-file-invoice-dollar"></i>
            </div>
            <span>Bill Payment</span>
          </button>
          <button class="quick-action-btn" routerLink="/accounts">
            <div class="action-icon teal">
              <i class="fas fa-history"></i>
            </div>
            <span>Statement</span>
          </button>
          <button class="quick-action-btn">
            <div class="action-icon red">
              <i class="fas fa-headset"></i>
            </div>
            <span>Support</span>
          </button>
        </div>

        <!-- Stats Cards -->
        <div class="stats-grid">
          <div class="stat-card total-balance">
            <div class="stat-header">
              <span class="stat-label">Total Balance</span>
              <i class="fas fa-wallet"></i>
            </div>
            <div class="stat-value">₹{{ totalBalance | number:'1.2-2' }}</div>
            <div class="stat-footer">
              <span class="positive"><i class="fas fa-arrow-up"></i> Active Accounts: {{ getActiveAccountCount() }}</span>
            </div>
          </div>

          <div class="stat-card">
            <div class="stat-header">
              <span class="stat-label">This Month's Credit</span>
              <i class="fas fa-arrow-down text-green"></i>
            </div>
            <div class="stat-value text-green">₹{{ monthlyCredit | number:'1.2-2' }}</div>
            <div class="stat-footer">
              <span>Deposits & Transfers In</span>
            </div>
          </div>

          <div class="stat-card">
            <div class="stat-header">
              <span class="stat-label">This Month's Debit</span>
              <i class="fas fa-arrow-up text-red"></i>
            </div>
            <div class="stat-value text-red">₹{{ monthlyDebit | number:'1.2-2' }}</div>
            <div class="stat-footer">
              <span>Withdrawals & Payments</span>
            </div>
          </div>
        </div>

        <!-- Main Content Grid -->
        <div class="content-grid">
          <!-- Accounts Section -->
          <div class="card accounts-section">
            <div class="card-header">
              <h3><i class="fas fa-piggy-bank"></i> My Accounts</h3>
              <a routerLink="/accounts" class="view-all-btn">View All <i class="fas fa-chevron-right"></i></a>
            </div>
            
            <div class="accounts-list" *ngIf="getActiveAccountCount(); else noAccounts">
              <div class="account-card" *ngFor="let account of accounts.slice(0, 3)" 
                   [class]="'account-' + account.accountType.toLowerCase()">
                <div class="account-icon">
                  <i [class]="getAccountIcon(account.accountType)"></i>
                </div>
                <div class="account-details">
                  <span class="account-type">{{ formatAccountType(account.accountType) }}</span>
                  <span class="account-number">A/C No: ****{{ account.accountNumber.slice(-4) }}</span>
                </div>
                <div class="account-balance">
                  <span class="balance-label">Balance</span>
                  <span class="balance-amount">₹{{ account.balance | number:'1.2-2' }}</span>
                </div>
              </div>
            </div>

            <ng-template #noAccounts>
              <div class="empty-state">
                <div class="empty-icon">
                  <i class="fas fa-university"></i>
                </div>
                <h4>No Accounts Yet</h4>
                <p>Open your first account to start banking with us</p>
                <a routerLink="/accounts" class="btn btn-primary">
                  <i class="fas fa-plus"></i> Open New Account
                </a>
              </div>
            </ng-template>
          </div>

          <!-- Recent Transactions -->
          <div class="card transactions-section">
            <div class="card-header">
              <h3><i class="fas fa-receipt"></i> Recent Transactions</h3>
              <a routerLink="/transactions" class="view-all-btn">View All <i class="fas fa-chevron-right"></i></a>
            </div>

            <div class="transactions-list" *ngIf="transactions.length; else noTransactions">
              <div class="transaction-item" *ngFor="let tx of transactions">
                <div class="tx-icon" [class]="tx.type.toLowerCase()">
                  <i [class]="getTransactionIcon(tx.type)"></i>
                </div>
                <div class="tx-details">
                  <span class="tx-type">{{ formatTransactionType(tx.type) }}</span>
                  <span class="tx-date">{{ tx.transactionDate | date:'dd MMM yyyy, hh:mm a' }}</span>
                  <span class="tx-ref">Ref: {{ tx.transactionReference }}</span>
                </div>
                <div class="tx-amount" [class]="tx.type === 'DEPOSIT' ? 'credit' : 'debit'">
                  <span>{{ tx.type === 'DEPOSIT' ? '+' : '-' }}₹{{ tx.amount | number:'1.2-2' }}</span>
                  <span class="tx-status" [class]="tx.status.toLowerCase()">{{ tx.status }}</span>
                </div>
              </div>
            </div>

            <ng-template #noTransactions>
              <div class="empty-state">
                <div class="empty-icon">
                  <i class="fas fa-exchange-alt"></i>
                </div>
                <h4>No Transactions Yet</h4>
                <p>Your transaction history will appear here</p>
                <a routerLink="/transactions" class="btn btn-primary">
                  <i class="fas fa-plus"></i> Make a Transaction
                </a>
              </div>
            </ng-template>
          </div>
        </div>

        <!-- Offers Banner -->
        <div class="offers-banner">
          <div class="offer-content">
            <i class="fas fa-gift"></i>
            <div>
              <h4>Special Offer!</h4>
              <p>Get 5% cashback on your first fund transfer. Limited time offer!</p>
            </div>
          </div>
          <button class="btn btn-orange">Learn More</button>
        </div>
      </div>
    </div>
  `,
  styles: [`
    .dashboard {
      padding: 24px 0 40px;
      background: #f0f4f8;
      min-height: calc(100vh - 70px);
    }

    .welcome-banner {
      background: linear-gradient(135deg, #1a237e 0%, #0d47a1 100%);
      border-radius: 20px;
      padding: 32px 40px;
      color: white;
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 32px;
      position: relative;
      overflow: hidden;
    }

    .welcome-banner::before {
      content: '';
      position: absolute;
      top: -100%;
      right: -50%;
      width: 100%;
      height: 300%;
      background: radial-gradient(circle, rgba(255,111,0,0.2) 0%, transparent 50%);
    }

    .welcome-content {
      position: relative;
      z-index: 1;
    }

    .time-greeting {
      font-size: 14px;
      opacity: 0.9;
      display: block;
      margin-bottom: 8px;
    }

    .welcome-content h1 {
      font-size: 1.75rem;
      font-weight: 700;
      margin-bottom: 4px;
    }

    .welcome-content p {
      opacity: 0.85;
      font-size: 0.95rem;
    }

    .last-login {
      margin-top: 16px;
      font-size: 13px;
      opacity: 0.8;
      display: flex;
      align-items: center;
      gap: 8px;
    }

    .welcome-illustration {
      font-size: 80px;
      opacity: 0.3;
      position: relative;
      z-index: 1;
    }

    .section-header {
      margin-bottom: 16px;
    }

    .section-header h2 {
      font-size: 1.1rem;
      color: #1a237e;
      display: flex;
      align-items: center;
      gap: 10px;
    }

    .quick-actions {
      display: grid;
      grid-template-columns: repeat(6, 1fr);
      gap: 16px;
      margin-bottom: 32px;
    }

    .quick-action-btn {
      background: white;
      border-radius: 16px;
      padding: 24px 16px;
      display: flex;
      flex-direction: column;
      align-items: center;
      gap: 12px;
      border: 2px solid transparent;
      transition: all 0.3s ease;
      box-shadow: 0 2px 8px rgba(0,0,0,0.05);
    }

    .quick-action-btn:hover {
      border-color: #1a237e;
      transform: translateY(-4px);
      box-shadow: 0 8px 24px rgba(26, 35, 126, 0.15);
    }

    .action-icon {
      width: 56px;
      height: 56px;
      border-radius: 14px;
      display: flex;
      align-items: center;
      justify-content: center;
      font-size: 24px;
    }

    .action-icon.blue { background: #e3f2fd; color: #1565c0; }
    .action-icon.green { background: #e8f5e9; color: #2e7d32; }
    .action-icon.orange { background: #fff3e0; color: #ef6c00; }
    .action-icon.purple { background: #ede7f6; color: #5e35b1; }
    .action-icon.teal { background: #e0f2f1; color: #00897b; }
    .action-icon.red { background: #ffebee; color: #c62828; }

    .quick-action-btn span {
      font-size: 13px;
      font-weight: 600;
      color: #374151;
    }

    .stats-grid {
      display: grid;
      grid-template-columns: repeat(3, 1fr);
      gap: 24px;
      margin-bottom: 32px;
    }

    .stat-card {
      background: white;
      border-radius: 16px;
      padding: 24px;
      box-shadow: 0 2px 8px rgba(0,0,0,0.05);
    }

    .stat-card.total-balance {
      background: linear-gradient(135deg, #1a237e, #3949ab);
      color: white;
    }

    .stat-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 12px;
    }

    .stat-label {
      font-size: 14px;
      opacity: 0.85;
    }

    .stat-header i {
      font-size: 20px;
      opacity: 0.7;
    }

    .stat-value {
      font-size: 2rem;
      font-weight: 700;
      margin-bottom: 12px;
    }

    .text-green { color: #2e7d32; }
    .text-red { color: #c62828; }

    .stat-footer {
      font-size: 13px;
      opacity: 0.75;
    }

    .stat-footer .positive {
      color: #4caf50;
    }

    .content-grid {
      display: grid;
      grid-template-columns: 1fr 1fr;
      gap: 24px;
      margin-bottom: 32px;
    }

    .card-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 24px;
      padding-bottom: 16px;
      border-bottom: 1px solid #e5e7eb;
    }

    .card-header h3 {
      font-size: 1.1rem;
      color: #1a237e;
      display: flex;
      align-items: center;
      gap: 10px;
    }

    .view-all-btn {
      color: #ff6f00;
      font-size: 14px;
      font-weight: 600;
      display: flex;
      align-items: center;
      gap: 6px;
      transition: gap 0.3s;
    }

    .view-all-btn:hover {
      gap: 10px;
    }

    .account-card {
      display: flex;
      align-items: center;
      padding: 20px;
      border-radius: 14px;
      margin-bottom: 16px;
      background: linear-gradient(135deg, #f8f9ff, #eef1ff);
      border-left: 4px solid #1a237e;
      transition: all 0.3s;
    }

    .account-card:hover {
      transform: translateX(8px);
      box-shadow: 0 4px 16px rgba(26, 35, 126, 0.1);
    }

    .account-card.account-savings { border-left-color: #2e7d32; background: linear-gradient(135deg, #f1f8e9, #e8f5e9); }
    .account-card.account-checking { border-left-color: #1565c0; background: linear-gradient(135deg, #e3f2fd, #bbdefb); }
    .account-card.account-fixed_deposit { border-left-color: #ef6c00; background: linear-gradient(135deg, #fff3e0, #ffe0b2); }

    .account-icon {
      width: 48px;
      height: 48px;
      border-radius: 12px;
      background: rgba(26, 35, 126, 0.1);
      display: flex;
      align-items: center;
      justify-content: center;
      font-size: 20px;
      color: #1a237e;
      margin-right: 16px;
    }

    .account-details {
      flex: 1;
    }

    .account-type {
      display: block;
      font-weight: 600;
      color: #1f2937;
      margin-bottom: 4px;
    }

    .account-number {
      font-size: 13px;
      color: #6b7280;
      font-family: monospace;
    }

    .account-balance {
      text-align: right;
    }

    .balance-label {
      display: block;
      font-size: 12px;
      color: #6b7280;
      margin-bottom: 4px;
    }

    .balance-amount {
      font-size: 1.25rem;
      font-weight: 700;
      color: #1a237e;
    }

    .transaction-item {
      display: flex;
      align-items: center;
      padding: 16px 0;
      border-bottom: 1px solid #f3f4f6;
    }

    .transaction-item:last-child {
      border-bottom: none;
    }

    .tx-icon {
      width: 44px;
      height: 44px;
      border-radius: 12px;
      display: flex;
      align-items: center;
      justify-content: center;
      font-size: 18px;
      margin-right: 14px;
    }

    .tx-icon.deposit { background: #e8f5e9; color: #2e7d32; }
    .tx-icon.withdrawal { background: #ffebee; color: #c62828; }
    .tx-icon.transfer { background: #e3f2fd; color: #1565c0; }
    .tx-icon.payment { background: #fff3e0; color: #ef6c00; }

    .tx-details {
      flex: 1;
    }

    .tx-type {
      display: block;
      font-weight: 600;
      color: #1f2937;
      margin-bottom: 2px;
    }

    .tx-date {
      display: block;
      font-size: 12px;
      color: #6b7280;
    }

    .tx-ref {
      display: block;
      font-size: 11px;
      color: #9ca3af;
      font-family: monospace;
    }

    .tx-amount {
      text-align: right;
    }

    .tx-amount span:first-child {
      display: block;
      font-size: 1.1rem;
      font-weight: 700;
    }

    .tx-amount.credit span:first-child { color: #2e7d32; }
    .tx-amount.debit span:first-child { color: #c62828; }

    .tx-status {
      display: inline-block;
      font-size: 10px;
      padding: 3px 8px;
      border-radius: 10px;
      font-weight: 600;
      text-transform: uppercase;
      margin-top: 4px;
    }

    .tx-status.completed { background: #e8f5e9; color: #2e7d32; }
    .tx-status.pending { background: #fff3e0; color: #ef6c00; }
    .tx-status.failed { background: #ffebee; color: #c62828; }

    .empty-state {
      text-align: center;
      padding: 40px 20px;
    }

    .empty-icon {
      width: 80px;
      height: 80px;
      border-radius: 50%;
      background: #f3f4f6;
      display: flex;
      align-items: center;
      justify-content: center;
      margin: 0 auto 20px;
    }

    .empty-icon i {
      font-size: 32px;
      color: #9ca3af;
    }

    .empty-state h4 {
      color: #374151;
      margin-bottom: 8px;
    }

    .empty-state p {
      color: #6b7280;
      font-size: 14px;
      margin-bottom: 20px;
    }

    .offers-banner {
      background: linear-gradient(135deg, #fff3e0, #ffe0b2);
      border-radius: 16px;
      padding: 24px 32px;
      display: flex;
      justify-content: space-between;
      align-items: center;
      border: 2px solid #ffcc80;
    }

    .offer-content {
      display: flex;
      align-items: center;
      gap: 20px;
    }

    .offer-content i {
      font-size: 40px;
      color: #ef6c00;
    }

    .offer-content h4 {
      color: #e65100;
      margin-bottom: 4px;
    }

    .offer-content p {
      color: #6b7280;
      font-size: 14px;
    }

    @media (max-width: 1200px) {
      .quick-actions {
        grid-template-columns: repeat(3, 1fr);
      }
    }

    @media (max-width: 968px) {
      .stats-grid {
        grid-template-columns: 1fr;
      }

      .content-grid {
        grid-template-columns: 1fr;
      }

      .quick-actions {
        grid-template-columns: repeat(2, 1fr);
      }

      .offers-banner {
        flex-direction: column;
        text-align: center;
        gap: 20px;
      }

      .offer-content {
        flex-direction: column;
      }
    }
  `]
})
export class DashboardComponent implements OnInit {
  accounts: Account[] = [];
  transactions: Transaction[] = [];
  totalBalance = 0;
  monthlyCredit = 0;
  monthlyDebit = 0;

  constructor(
    public authService: AuthService,
    private dashboardService: DashboardService
  ) {}

  ngOnInit(): void {
    this.loadDashboard();
  }

  loadDashboard(): void {
    this.dashboardService.getDashboard().subscribe({
      next: (data) => {
        this.accounts = data.accounts || [];
        this.transactions = data.recentTransactions || [];
        this.totalBalance = data.totalBalance || 0;
        this.monthlyCredit = data.monthlyCredit || 0;
        this.monthlyDebit = data.monthlyDebit || 0;
      },
      error: (err) => console.error('Error loading dashboard', err)
    });
  }

  getGreeting(): string {
    const hour = new Date().getHours();
    if (hour < 12) return '🌅 Good Morning,';
    if (hour < 17) return '☀️ Good Afternoon,';
    return '🌙 Good Evening,';
  }

  getCurrentDate(): string {
    return new Date().toLocaleString('en-IN', { 
      dateStyle: 'medium', 
      timeStyle: 'short' 
    });
  }

  getActiveAccountCount(): number {
    return this.accounts.filter(acc => acc.active).length;
  }

  getAccountIcon(type: string): string {
    const icons: { [key: string]: string } = {
      'SAVINGS': 'fas fa-piggy-bank',
      'CHECKING': 'fas fa-money-check-alt',
      'FIXED_DEPOSIT': 'fas fa-landmark'
    };
    return icons[type] || 'fas fa-university';
  }

  formatAccountType(type: string): string {
    const types: { [key: string]: string } = {
      'SAVINGS': 'Savings Account',
      'CHECKING': 'Current Account',
      'FIXED_DEPOSIT': 'Fixed Deposit'
    };
    return types[type] || type;
  }

  getTransactionIcon(type: string): string {
    const icons: { [key: string]: string } = {
      'DEPOSIT': 'fas fa-arrow-down',
      'WITHDRAWAL': 'fas fa-arrow-up',
      'TRANSFER': 'fas fa-exchange-alt',
      'PAYMENT': 'fas fa-credit-card'
    };
    return icons[type] || 'fas fa-circle';
  }

  formatTransactionType(type: string): string {
    const types: { [key: string]: string } = {
      'DEPOSIT': 'Money Deposited',
      'WITHDRAWAL': 'Cash Withdrawal',
      'TRANSFER': 'Fund Transfer',
      'PAYMENT': 'Bill Payment'
    };
    return types[type] || type;
  }
}

