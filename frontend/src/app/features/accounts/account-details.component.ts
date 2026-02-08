import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { AccountService } from '../../core/services/account.service';
import { TransactionService } from '../../core/services/transaction.service';
import { Account } from '../../shared/models/account.model';
import { Transaction } from '../../shared/models/transaction.model';

@Component({
  selector: 'app-account-details',
  standalone: true,
  imports: [CommonModule, RouterLink],
  template: `
    <div class="account-details-page">
      <div class="container">
        <div class="page-header">
          <div class="header-left">
            <a class="back-link" routerLink="/accounts">
              <i class="fas fa-arrow-left"></i> Back to Accounts
            </a>
            <h1><i class="fas fa-wallet"></i> Account Details</h1>
            <p *ngIf="account">Details for {{ formatAccountType(account.accountType) }} • {{ formatAccountNumber(account.accountNumber) }}</p>
          </div>
          <div class="header-actions">
            <span class="status-badge" [class.active]="account?.active" [class.inactive]="account && !account.active">
              <i [class]="account?.active ? 'fas fa-check-circle' : 'fas fa-times-circle'"></i>
              {{ account?.active ? 'Active' : 'Inactive' }}
            </span>
          </div>
        </div>

        <div class="summary-grid" *ngIf="account">
          <div class="summary-card balance-card">
            <div class="summary-icon"><i class="fas fa-rupee-sign"></i></div>
            <div>
              <span class="summary-label">Available Balance</span>
              <span class="summary-value">{{ currencySymbol }}{{ account.balance | number:'1.2-2' }}</span>
              <span class="summary-meta">Currency: {{ account.currency }}</span>
            </div>
          </div>
          <div class="summary-card">
            <span class="summary-label">Account Number</span>
            <span class="summary-value mono">{{ formatAccountNumber(account.accountNumber) }}</span>
          </div>
          <div class="summary-card">
            <span class="summary-label">Account Type</span>
            <span class="summary-value">{{ formatAccountType(account.accountType) }}</span>
          </div>
          <div class="summary-card">
            <span class="summary-label">Opened On</span>
            <span class="summary-value">{{ account.createdAt | date:'mediumDate' }}</span>
          </div>
        </div>

        <div class="summary-grid" *ngIf="!account && !errorMessage">
          <div class="summary-card skeleton"></div>
          <div class="summary-card skeleton"></div>
          <div class="summary-card skeleton"></div>
          <div class="summary-card skeleton"></div>
        </div>

        <div class="error-card" *ngIf="errorMessage">
          <i class="fas fa-exclamation-triangle"></i>
          <span>{{ errorMessage }}</span>
        </div>

        <div class="transactions-card">
          <div class="card-header">
            <h3><i class="fas fa-history"></i> Recent Transactions</h3>
            <a class="btn btn-secondary" routerLink="/transactions">
              View All
            </a>
          </div>

          <div class="table-wrapper" *ngIf="transactions.length; else noTransactions">
            <table class="transactions-table">
              <thead>
                <tr>
                  <th>Date</th>
                  <th>Description</th>
                  <th>Type</th>
                  <th>Amount</th>
                  <th>Status</th>
                </tr>
              </thead>
              <tbody>
                <tr *ngFor="let tx of transactions">
                  <td>{{ tx.transactionDate | date:'dd MMM yyyy, hh:mm a' }}</td>
                  <td>
                    <div class="tx-desc">
                      <span class="tx-title">{{ formatTransactionType(tx.type) }}</span>
                      <span class="tx-sub" *ngIf="tx.description">{{ tx.description }}</span>
                      <span class="tx-sub" *ngIf="!tx.description">Reference: {{ tx.transactionReference }}</span>
                    </div>
                  </td>
                  <td>
                    <span class="chip" [class]="tx.type.toLowerCase()">
                      <i [class]="getTransactionIcon(tx.type)"></i>
                      {{ tx.type }}
                    </span>
                  </td>
                  <td class="amount" [class.credit]="tx.type === 'DEPOSIT'">
                    {{ tx.type === 'DEPOSIT' ? '+' : '-' }}{{ currencySymbol }}{{ tx.amount | number:'1.2-2' }}
                  </td>
                  <td>
                    <span class="status-pill" [class]="tx.status.toLowerCase()">
                      <i [class]="getStatusIcon(tx.status)"></i>
                      {{ tx.status }}
                    </span>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>

          <ng-template #noTransactions>
            <div class="empty-state">
              <i class="fas fa-receipt"></i>
              <h4>No transactions found</h4>
              <p>This account does not have any recent activity yet.</p>
            </div>
          </ng-template>
        </div>
      </div>
    </div>
  `,
  styles: [`
    .account-details-page {
      padding: 24px 0 40px;
      background: #f0f4f8;
      min-height: calc(100vh - 70px);
    }

    .page-header {
      display: flex;
      justify-content: space-between;
      align-items: flex-start;
      margin-bottom: 24px;
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
      margin: 8px 0 6px;
    }

    .header-left p {
      opacity: 0.85;
      font-size: 0.95rem;
    }

    .back-link {
      color: #e3f2fd;
      font-size: 0.85rem;
      text-decoration: none;
      display: inline-flex;
      align-items: center;
      gap: 8px;
    }

    .status-badge {
      display: inline-flex;
      align-items: center;
      gap: 8px;
      padding: 10px 16px;
      border-radius: 999px;
      font-weight: 600;
      background: rgba(255,255,255,0.15);
      color: white;
    }

    .status-badge.active { background: rgba(46, 125, 50, 0.2); }
    .status-badge.inactive { background: rgba(198, 40, 40, 0.2); }

    .summary-grid {
      display: grid;
      grid-template-columns: repeat(4, 1fr);
      gap: 20px;
      margin-bottom: 24px;
    }

    .summary-card {
      background: white;
      border-radius: 16px;
      padding: 20px 24px;
      box-shadow: 0 2px 8px rgba(0,0,0,0.05);
      display: flex;
      flex-direction: column;
      gap: 6px;
    }

    .balance-card {
      grid-column: span 2;
      display: flex;
      flex-direction: row;
      gap: 16px;
      align-items: center;
      background: linear-gradient(135deg, #f8f9ff, #eef1ff);
    }

    .summary-icon {
      width: 56px;
      height: 56px;
      border-radius: 14px;
      display: flex;
      align-items: center;
      justify-content: center;
      font-size: 24px;
      background: #1a237e;
      color: white;
    }

    .summary-label {
      font-size: 0.8rem;
      color: #6b7280;
    }

    .summary-value {
      font-size: 1.35rem;
      font-weight: 700;
      color: #1a237e;
    }

    .summary-meta {
      font-size: 0.75rem;
      color: #9ca3af;
    }

    .mono {
      font-family: 'Courier New', monospace;
    }

    .transactions-card {
      background: white;
      border-radius: 20px;
      overflow: hidden;
      box-shadow: 0 4px 12px rgba(0,0,0,0.08);
    }

    .card-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      padding: 24px;
      border-bottom: 1px solid #e5e7eb;
    }

    .card-header h3 {
      display: flex;
      align-items: center;
      gap: 10px;
      color: #1a237e;
      font-size: 1.1rem;
    }

    .table-wrapper {
      overflow-x: auto;
    }

    .transactions-table {
      width: 100%;
      border-collapse: collapse;
      font-size: 0.9rem;
    }

    .transactions-table th,
    .transactions-table td {
      padding: 14px 18px;
      border-bottom: 1px solid #f3f4f6;
      text-align: left;
      vertical-align: top;
    }

    .transactions-table th {
      background: #f8f9ff;
      color: #6b7280;
      font-size: 0.75rem;
      text-transform: uppercase;
      letter-spacing: 0.05em;
    }

    .tx-desc {
      display: flex;
      flex-direction: column;
      gap: 4px;
    }

    .tx-title {
      font-weight: 700;
      color: #1f2937;
    }

    .tx-sub {
      color: #9ca3af;
      font-size: 0.75rem;
    }

    .chip {
      display: inline-flex;
      align-items: center;
      gap: 6px;
      padding: 6px 10px;
      border-radius: 999px;
      font-size: 0.7rem;
      font-weight: 600;
      text-transform: uppercase;
    }

    .chip.deposit { background: #e8f5e9; color: #2e7d32; }
    .chip.withdrawal { background: #ffebee; color: #c62828; }
    .chip.transfer { background: #e3f2fd; color: #1565c0; }
    .chip.payment { background: #fff3e0; color: #ef6c00; }

    .amount {
      font-weight: 700;
      color: #c62828;
    }

    .amount.credit {
      color: #2e7d32;
    }

    .status-pill {
      display: inline-flex;
      align-items: center;
      gap: 6px;
      padding: 6px 10px;
      border-radius: 999px;
      font-size: 0.7rem;
      font-weight: 600;
      text-transform: uppercase;
    }

    .status-pill.completed { background: #e8f5e9; color: #2e7d32; }
    .status-pill.pending { background: #fff3e0; color: #ef6c00; }
    .status-pill.failed { background: #ffebee; color: #c62828; }

    .empty-state {
      text-align: center;
      padding: 40px;
      color: #6b7280;
    }

    .empty-state i {
      font-size: 32px;
      color: #9ca3af;
      margin-bottom: 12px;
    }

    .error-card {
      background: #ffebee;
      color: #c62828;
      padding: 12px 16px;
      border-radius: 12px;
      display: flex;
      align-items: center;
      gap: 10px;
      margin-bottom: 20px;
    }

    .skeleton {
      min-height: 92px;
      background: linear-gradient(90deg, #f3f4f6 0%, #e5e7eb 50%, #f3f4f6 100%);
      background-size: 200% 100%;
      animation: shimmer 1.5s infinite;
    }

    @keyframes shimmer {
      0% { background-position: 200% 0; }
      100% { background-position: -200% 0; }
    }

    @media (max-width: 1024px) {
      .summary-grid {
        grid-template-columns: repeat(2, 1fr);
      }

      .balance-card {
        grid-column: span 2;
      }
    }

    @media (max-width: 768px) {
      .page-header {
        flex-direction: column;
        gap: 16px;
      }

      .summary-grid {
        grid-template-columns: 1fr;
      }

      .balance-card {
        grid-column: span 1;
      }
    }
  `]
})
export class AccountDetailsComponent implements OnInit {
  account: Account | null = null;
  transactions: Transaction[] = [];
  errorMessage = '';

  private accountId = 0;

  constructor(
    private route: ActivatedRoute,
    private accountService: AccountService,
    private transactionService: TransactionService
  ) {}

  ngOnInit(): void {
    const idParam = this.route.snapshot.paramMap.get('id');
    this.accountId = idParam ? Number(idParam) : 0;

    if (!this.accountId) {
      this.errorMessage = 'Invalid account ID provided.';
      return;
    }

    this.loadAccount();
    this.loadTransactions();
  }

  loadAccount(): void {
    this.accountService.getAccountById(this.accountId).subscribe({
      next: (account) => this.account = account,
      error: () => this.errorMessage = 'Unable to load account details. Please try again.'
    });
  }

  loadTransactions(): void {
    this.transactionService.getAccountTransactions(this.accountId, 0, 5).subscribe({
      next: (response) => {
        const content = response?.content ?? response;
        this.transactions = Array.isArray(content) ? content : [];
      },
      error: () => {
        if (!this.errorMessage) {
          this.errorMessage = 'Unable to load recent transactions.';
        }
      }
    });
  }

  formatAccountNumber(number?: string): string {
    const value = number || '';
    return value.replace(/(.{4})/g, '$1 ').trim();
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

  getStatusIcon(status: string): string {
    const icons: { [key: string]: string } = {
      'COMPLETED': 'fas fa-check-circle',
      'PENDING': 'fas fa-clock',
      'FAILED': 'fas fa-times-circle'
    };
    return icons[status] || 'fas fa-circle';
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

  get currencySymbol(): string {
    return this.getCurrencySymbol(this.account?.currency);
  }

  private getCurrencySymbol(currency?: string): string {
    if (currency === 'USD') return '$';
    if (currency === 'INR') return '₹';
    return currency ? `${currency} ` : '';
  }
}
