import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { AdminService, UserSummary } from '../../core/services/admin.service';
import { Account } from '../../shared/models/account.model';
import { Transaction } from '../../shared/models/transaction.model';

@Component({
  selector: 'app-admin',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="admin-page">
      <div class="container">
        <div class="page-header">
          <div class="header-left">
            <h1><i class="fas fa-shield-alt"></i> Admin Dashboard</h1>
            <p>Manage users, accounts, and transactions</p>
          </div>
        </div>

        <div class="grid">
          <div class="card">
            <div class="card-header">
              <h3><i class="fas fa-users"></i> Users</h3>
            </div>
            <div class="card-body">
              <table class="table">
                <thead>
                  <tr>
                    <th>Name</th>
                    <th>Email</th>
                    <th>Role</th>
                    <th>Status</th>
                    <th>Joined</th>
                  </tr>
                </thead>
                <tbody>
                  <tr *ngFor="let u of users">
                    <td>{{ u.firstName }} {{ u.lastName }}</td>
                    <td>{{ u.email }}</td>
                    <td>{{ u.role }}</td>
                    <td>
                      <span class="pill" [class.active]="u.enabled" [class.inactive]="!u.enabled">
                        {{ u.enabled ? 'Active' : 'Disabled' }}
                      </span>
                    </td>
                    <td>{{ u.createdAt | date:'dd MMM yyyy' }}</td>
                  </tr>
                </tbody>
              </table>
            </div>
          </div>

          <div class="card">
            <div class="card-header">
              <h3><i class="fas fa-wallet"></i> Accounts</h3>
            </div>
            <div class="card-body">
              <table class="table">
                <thead>
                  <tr>
                    <th>Account</th>
                    <th>Type</th>
                    <th>Balance</th>
                    <th>Status</th>
                    <th>Action</th>
                  </tr>
                </thead>
                <tbody>
                  <tr *ngFor="let acc of accounts">
                    <td>****{{ acc.accountNumber.slice(-4) }}</td>
                    <td>{{ acc.accountType }}</td>
                    <td>{{ acc.currency }} {{ acc.balance | number:'1.2-2' }}</td>
                    <td>
                      <span class="pill" [class.active]="acc.active" [class.inactive]="!acc.active">
                        {{ acc.active ? 'Active' : 'Frozen' }}
                      </span>
                    </td>
                    <td>
                      <button class="btn btn-secondary" (click)="toggleFreeze(acc)">
                        {{ acc.active ? 'Freeze' : 'Unfreeze' }}
                      </button>
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
          </div>

          <div class="card">
            <div class="card-header">
              <h3><i class="fas fa-receipt"></i> Transactions</h3>
              <div class="pager">
                <button class="btn btn-secondary" (click)="changePage(-1)" [disabled]="txPage === 0">Prev</button>
                <span>Page {{ txPage + 1 }} of {{ txTotalPages || 1 }}</span>
                <button class="btn btn-secondary" (click)="changePage(1)" [disabled]="txPage + 1 >= txTotalPages">Next</button>
              </div>
            </div>
            <div class="card-body">
              <table class="table">
                <thead>
                  <tr>
                    <th>Date</th>
                    <th>Ref</th>
                    <th>Type</th>
                    <th>Amount</th>
                    <th>Status</th>
                  </tr>
                </thead>
                <tbody>
                  <tr *ngFor="let tx of transactions">
                    <td>{{ tx.transactionDate | date:'dd MMM yyyy, hh:mm a' }}</td>
                    <td class="mono">{{ tx.transactionReference }}</td>
                    <td>{{ tx.type }}</td>
                    <td>{{ tx.amount | number:'1.2-2' }}</td>
                    <td>{{ tx.status }}</td>
                  </tr>
                </tbody>
              </table>
            </div>
          </div>
        </div>
      </div>
    </div>
  `,
  styles: [`
    .admin-page {
      padding: 24px 0 40px;
      background: #f0f4f8;
      min-height: calc(100vh - 70px);
    }

    .page-header {
      display: flex;
      justify-content: space-between;
      align-items: flex-start;
      margin-bottom: 24px;
      background: linear-gradient(135deg, #111827 0%, #1f2937 100%);
      padding: 28px;
      border-radius: 18px;
      color: white;
    }

    .page-header h1 {
      font-size: 1.6rem;
      display: flex;
      align-items: center;
      gap: 10px;
      margin-bottom: 6px;
    }

    .grid {
      display: grid;
      gap: 20px;
    }

    .card {
      background: white;
      border-radius: 16px;
      box-shadow: 0 2px 10px rgba(0,0,0,0.06);
      overflow: hidden;
    }

    .card-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      padding: 18px 20px;
      border-bottom: 1px solid #e5e7eb;
    }

    .card-header h3 {
      color: #111827;
      font-size: 1.05rem;
      display: flex;
      align-items: center;
      gap: 8px;
    }

    .card-body {
      padding: 8px 16px 16px;
    }

    .table {
      width: 100%;
      border-collapse: collapse;
    }

    .table th, .table td {
      padding: 12px 10px;
      text-align: left;
      border-bottom: 1px solid #f3f4f6;
      font-size: 0.9rem;
      color: #374151;
      white-space: nowrap;
    }

    .table th {
      color: #6b7280;
      font-size: 0.75rem;
      text-transform: uppercase;
      letter-spacing: 0.04em;
    }

    .mono {
      font-family: monospace;
    }

    .pill {
      padding: 4px 10px;
      border-radius: 999px;
      font-size: 0.75rem;
      font-weight: 700;
    }

    .pill.active {
      background: #dcfce7;
      color: #166534;
    }

    .pill.inactive {
      background: #fee2e2;
      color: #991b1b;
    }

    .pager {
      display: flex;
      gap: 8px;
      align-items: center;
      font-size: 0.85rem;
      color: #6b7280;
    }

    @media (max-width: 900px) {
      .table {
        display: block;
        overflow-x: auto;
      }
      .card-header {
        flex-direction: column;
        align-items: flex-start;
        gap: 10px;
      }
    }
  `]
})
export class AdminComponent implements OnInit {
  users: UserSummary[] = [];
  accounts: Account[] = [];
  transactions: Transaction[] = [];
  txPage = 0;
  txTotalPages = 0;

  constructor(private adminService: AdminService) {}

  ngOnInit(): void {
    this.loadUsers();
    this.loadAccounts();
    this.loadTransactions();
  }

  loadUsers(): void {
    this.adminService.getUsers().subscribe({
      next: (data) => this.users = data,
      error: () => {}
    });
  }

  loadAccounts(): void {
    this.adminService.getAccounts().subscribe({
      next: (data) => this.accounts = data,
      error: () => {}
    });
  }

  loadTransactions(): void {
    this.adminService.getTransactions(this.txPage, 20).subscribe({
      next: (page) => {
        this.transactions = page.content;
        this.txTotalPages = page.totalPages;
        this.txPage = page.number;
      },
      error: () => {}
    });
  }

  changePage(delta: number): void {
    const next = this.txPage + delta;
    if (next < 0 || (this.txTotalPages && next >= this.txTotalPages)) return;
    this.txPage = next;
    this.loadTransactions();
  }

  toggleFreeze(account: Account): void {
    const request$ = account.active
      ? this.adminService.freezeAccount(account.id)
      : this.adminService.unfreezeAccount(account.id);

    request$.subscribe({
      next: (updated) => {
        account.active = updated.active;
      },
      error: () => {}
    });
  }
}
