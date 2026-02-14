import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators, FormsModule } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { TransactionService } from '../../core/services/transaction.service';
import { AccountService } from '../../core/services/account.service';
import { Transaction, TransactionType } from '../../shared/models/transaction.model';
import { Account } from '../../shared/models/account.model';

@Component({
  selector: 'app-transactions',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, FormsModule],
  template: `
    <div class="transactions-page">
      <div class="container">
        <!-- Page Header -->
        <div class="page-header">
          <div class="header-left">
            <h1><i class="fas fa-receipt"></i> Transactions</h1>
            <p>View and manage your transaction history</p>
          </div>
          <button class="btn btn-primary" (click)="showTransferModal = true" [disabled]="accounts.length === 0">
            <i class="fas fa-paper-plane"></i> New Transaction
          </button>
        </div>

        <!-- Quick Actions -->
        <div class="quick-actions-bar">
          <button class="quick-btn" (click)="openTransactionModal('DEPOSIT')">
            <i class="fas fa-arrow-down"></i> Deposit
          </button>
          <button class="quick-btn" (click)="openTransactionModal('WITHDRAWAL')">
            <i class="fas fa-arrow-up"></i> Withdraw
          </button>
          <button class="quick-btn" (click)="openTransactionModal('TRANSFER')">
            <i class="fas fa-exchange-alt"></i> Transfer
          </button>
          <button class="quick-btn" (click)="openTransactionModal('PAYMENT')">
            <i class="fas fa-credit-card"></i> Pay Bills
          </button>
        </div>

        <!-- Transaction Stats -->
        <div class="stats-row" *ngIf="totalElements > 0">
          <div class="stat-card credit">
            <div class="stat-icon"><i class="fas fa-arrow-down"></i></div>
            <div class="stat-info">
              <span class="stat-label">Total Credits</span>
              <span class="stat-value">₹{{ getTotalCredits() | number:'1.2-2' }}</span>
            </div>
          </div>
          <div class="stat-card debit">
            <div class="stat-icon"><i class="fas fa-arrow-up"></i></div>
            <div class="stat-info">
              <span class="stat-label">Total Debits</span>
              <span class="stat-value">₹{{ getTotalDebits() | number:'1.2-2' }}</span>
            </div>
          </div>
          <div class="stat-card total">
            <div class="stat-icon"><i class="fas fa-chart-line"></i></div>
            <div class="stat-info">
              <span class="stat-label">Total Transactions</span>
              <span class="stat-value">{{ totalElements }}</span>
            </div>
          </div>
        </div>
        <!-- Transactions Table -->
        <div class="transactions-card" *ngIf="transactions.length; else noTransactions">
          <div class="card-header">
            <h3><i class="fas fa-history"></i> Transaction History</h3>
            <div class="filters">
              <div class="filter-group">
                <label>From</label>
                <input type="date" [(ngModel)]="startDate">
              </div>
              <div class="filter-group">
                <label>To</label>
                <input type="date" [(ngModel)]="endDate">
              </div>
              <div class="filter-group">
                <label>Type</label>
                <select [(ngModel)]="direction">
                  <option value="ALL">All</option>
                  <option value="CREDIT">Credit</option>
                  <option value="DEBIT">Debit</option>
                </select>
              </div>
              <div class="filter-group">
                <label>Sort</label>
                <select [(ngModel)]="sort">
                  <option value="date_desc">Date (Newest)</option>
                  <option value="date_asc">Date (Oldest)</option>
                  <option value="amount_desc">Amount (High-Low)</option>
                  <option value="amount_asc">Amount (Low-High)</option>
                </select>
              </div>
            <button class="btn btn-secondary" (click)="applyFilters()">Apply</button>
            <button class="btn btn-primary" (click)="downloadStatement()" [disabled]="loadingStatement">
              <i [class]="loadingStatement ? 'fas fa-spinner fa-spin' : 'fas fa-download'"></i>
              {{ loadingStatement ? 'Preparing...' : 'Download' }}
            </button>
          </div>
        </div>

          <div class="loading-row" *ngIf="loadingTransactions">
            <i class="fas fa-spinner fa-spin"></i> Loading transactions...
          </div>
          <div class="error-message" *ngIf="pageErrorMessage">
            <i class="fas fa-exclamation-circle"></i> {{ pageErrorMessage }}
          </div>

          <div class="table-wrap">
            <table class="tx-table">
              <thead>
                <tr>
                  <th>Date</th>
                  <th>Reference</th>
                  <th>Type</th>
                  <th>From</th>
                  <th>To</th>
                  <th class="amount">Amount</th>
                  <th>Status</th>
                </tr>
              </thead>
              <tbody>
                <tr *ngFor="let tx of transactions" [ngClass]="{'credit-row': isCredit(tx), 'debit-row': isDebit(tx)}">
                  <td>{{ tx.transactionDate | date:'dd MMM yyyy, hh:mm a' }}</td>
                  <td class="mono">{{ tx.transactionReference }}</td>
                  <td>{{ formatTransactionType(tx.type) }}</td>
                  <td>{{ tx.sourceAccountNumber ? '****' + tx.sourceAccountNumber.slice(-4) : '-' }}</td>
                  <td>{{ tx.destinationAccountNumber ? '****' + tx.destinationAccountNumber.slice(-4) : '-' }}</td>
                  <td class="amount" [ngClass]="{'credit': isCredit(tx), 'debit': isDebit(tx)}">
                    {{ getAmountSign(tx) }}₹{{ tx.amount | number:'1.2-2' }}
                  </td>
                  <td>
                    <span class="tx-status" [ngClass]="tx.status.toLowerCase()">
                      <i [class]="getStatusIcon(tx.status)"></i> {{ tx.status }}
                    </span>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>

          <div class="pagination">
            <button class="page-btn" (click)="changePage(-1)" [disabled]="page === 0">Prev</button>
            <span class="page-info">Page {{ page + 1 }} of {{ totalPages || 1 }}</span>
            <button class="page-btn" (click)="changePage(1)" [disabled]="page + 1 >= totalPages">Next</button>
          </div>
        </div>

        <ng-template #noTransactions>

          <div class="empty-state card">
            <div class="empty-icon">
              <i class="fas fa-receipt"></i>
            </div>
            <h2>No Transactions Yet</h2>
            <p>Start making transactions to see your history here</p>
            <div class="empty-actions">
              <button class="btn btn-primary" (click)="openTransactionModal('DEPOSIT')" [disabled]="accounts.length === 0">
                <i class="fas fa-plus"></i> Make Your First Deposit
              </button>
            </div>
            <p class="empty-note" *ngIf="accounts.length === 0">
              <i class="fas fa-info-circle"></i> You need to open an account first
            </p>
          </div>
        </ng-template>
      </div>

      <!-- Transaction Modal -->
      <div class="modal-overlay" *ngIf="showTransferModal" (click)="loading ? null : showTransferModal = false">
        <div class="modal" (click)="$event.stopPropagation()">
          <div class="modal-header">
            <div>
              <h2><i class="fas fa-paper-plane"></i> New Transaction</h2>
              <p>Send money securely with FinCore</p>
            </div>
            <button class="close-btn" (click)="showTransferModal = false" [disabled]="loading">
              <i class="fas fa-times"></i>
            </button>
          </div>
          <form [formGroup]="transactionForm" (ngSubmit)="submitTransaction()">
            <!-- Transaction Type Selector -->
            <div class="type-selector">
              <label class="type-btn" [class.active]="transactionForm.get('type')?.value === 'DEPOSIT'">
                <input type="radio" formControlName="type" value="DEPOSIT" (change)="onTypeChange()">
                <i class="fas fa-arrow-down"></i>
                <span>Deposit</span>
              </label>
              <label class="type-btn" [class.active]="transactionForm.get('type')?.value === 'WITHDRAWAL'">
                <input type="radio" formControlName="type" value="WITHDRAWAL" (change)="onTypeChange()">
                <i class="fas fa-arrow-up"></i>
                <span>Withdraw</span>
              </label>
              <label class="type-btn" [class.active]="transactionForm.get('type')?.value === 'TRANSFER'">
                <input type="radio" formControlName="type" value="TRANSFER" (change)="onTypeChange()">
                <i class="fas fa-exchange-alt"></i>
                <span>Transfer</span>
              </label>
              <label class="type-btn" [class.active]="transactionForm.get('type')?.value === 'PAYMENT'">
                <input type="radio" formControlName="type" value="PAYMENT" (change)="onTypeChange()">
                <i class="fas fa-credit-card"></i>
                <span>Payment</span>
              </label>
            </div>

            <div class="form-group">
              <label for="sourceAccountId"><i class="fas fa-wallet"></i> From Account</label>
              <select id="sourceAccountId" formControlName="sourceAccountId">
                <option value="">Select your account</option>
                <option *ngIf="loadingAccounts" value="" disabled>Loading accounts...</option>
                <option *ngFor="let acc of accounts" [value]="acc.id">
                  {{ formatAccType(acc.accountType) }} - ****{{ acc.accountNumber.slice(-4) }} (₹{{ acc.balance | number:'1.2-2' }})
                </option>
              </select>
            </div>

            <div class="form-group" *ngIf="transactionForm.get('type')?.value === 'TRANSFER'">
              <label for="destinationAccountId"><i class="fas fa-user"></i> To Account</label>
              <ng-container *ngIf="accounts.length > 1; else manualDestination">
                <select id="destinationAccountId" formControlName="destinationAccountId">
                  <option value="">Select destination account</option>
                  <option *ngFor="let acc of accounts" [value]="acc.id"
                          [disabled]="acc.id === +transactionForm.get('sourceAccountId')?.value">
                    {{ formatAccType(acc.accountType) }} - ****{{ acc.accountNumber.slice(-4) }}
                  </option>
                </select>
                <div class="field-error" *ngIf="transactionForm.get('destinationAccountId')?.touched && transactionForm.get('destinationAccountId')?.errors?.['required']">
                  Please select a destination account.
                </div>
              </ng-container>
              <ng-template #manualDestination>
                <input type="text" id="destinationAccountNumber" formControlName="destinationAccountNumber" placeholder="Enter destination account number">
                <div class="field-error" *ngIf="transactionForm.get('destinationAccountNumber')?.touched && transactionForm.get('destinationAccountNumber')?.errors?.['required']">
                  Destination account number is required.
                </div>
                <div class="field-error" *ngIf="transactionForm.get('destinationAccountNumber')?.touched && transactionForm.get('destinationAccountNumber')?.errors?.['pattern']">
                  Destination account number must be 10 digits.
                </div>
              </ng-template>
            </div>

            <div class="form-group">
              <label for="amount"><i class="fas fa-rupee-sign"></i> Amount</label>
              <div class="amount-input">
                <span class="currency">₹</span>
                <input type="number" id="amount" formControlName="amount" 
                       placeholder="0.00" min="1" step="100">
              </div>
            </div>

            <div class="form-group" *ngIf="transactionForm.get('type')?.value === 'PAYMENT'">
              <label for="billerName"><i class="fas fa-building"></i> Biller Name</label>
              <input type="text" id="billerName" formControlName="billerName" placeholder="e.g., Electricity Board">
            </div>

            <div class="form-group" *ngIf="transactionForm.get('type')?.value === 'PAYMENT'">
              <label for="billReference"><i class="fas fa-receipt"></i> Bill Reference (Optional)</label>
              <input type="text" id="billReference" formControlName="billReference" placeholder="e.g., Consumer Number">
            </div>

            <div class="form-group">
              <label for="description"><i class="fas fa-pen"></i> Remarks (Optional)</label>
              <input type="text" id="description" formControlName="description" 
                     placeholder="e.g., Rent payment, Gift, etc.">
            </div>

            <div class="error-message" *ngIf="errorMessage">
              <i class="fas fa-exclamation-circle"></i> {{ errorMessage }}
            </div>
            <div class="success-message" *ngIf="successMessage">
              <i class="fas fa-check-circle"></i> {{ successMessage }}
            </div>

            <div class="modal-actions">
              <button type="button" class="btn btn-secondary" (click)="showTransferModal = false" [disabled]="loading">Cancel</button>
              <button type="submit" class="btn btn-primary" [disabled]="loading || transactionForm.invalid">
                <i [class]="loading ? 'fas fa-spinner fa-spin' : 'fas fa-check'"></i>
                {{ loading ? 'Processing...' : 'Confirm Transaction' }}
              </button>
            </div>
          </form>
        </div>
      </div>
    </div>
  `,
  styles: [`
    .transactions-page {
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

    .quick-actions-bar {
      display: grid;
      grid-template-columns: repeat(4, 1fr);
      gap: 16px;
      margin-bottom: 24px;
    }

    .quick-btn {
      background: white;
      border-radius: 14px;
      padding: 20px;
      display: flex;
      flex-direction: column;
      align-items: center;
      gap: 10px;
      font-weight: 600;
      color: #374151;
      transition: all 0.3s;
      box-shadow: 0 2px 8px rgba(0,0,0,0.05);
    }

    .quick-btn i {
      font-size: 24px;
      color: #1a237e;
    }

    .quick-btn:hover {
      background: #1a237e;
      color: white;
      transform: translateY(-4px);
      box-shadow: 0 8px 24px rgba(26, 35, 126, 0.2);
    }

    .quick-btn:hover i {
      color: white;
    }

    .stats-row {
      display: grid;
      grid-template-columns: repeat(3, 1fr);
      gap: 20px;
      margin-bottom: 24px;
    }

    .stat-card {
      background: white;
      border-radius: 16px;
      padding: 24px;
      display: flex;
      align-items: center;
      gap: 16px;
      box-shadow: 0 2px 8px rgba(0,0,0,0.05);
    }

    .stat-card .stat-icon {
      width: 56px;
      height: 56px;
      border-radius: 14px;
      display: flex;
      align-items: center;
      justify-content: center;
      font-size: 24px;
    }

    .stat-card.credit .stat-icon { background: #e8f5e9; color: #2e7d32; }
    .stat-card.debit .stat-icon { background: #ffebee; color: #c62828; }
    .stat-card.total .stat-icon { background: #e3f2fd; color: #1565c0; }

    .stat-info {
      display: flex;
      flex-direction: column;
    }

    .stat-label {
      font-size: 0.875rem;
      color: #6b7280;
    }

    .stat-value {
      font-size: 1.5rem;
      font-weight: 700;
      color: #1a237e;
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

    .filters {
      display: flex;
      gap: 12px;
      align-items: flex-end;
      flex-wrap: wrap;
    }

    .filter-group {
      display: flex;
      flex-direction: column;
      gap: 6px;
      font-size: 0.8rem;
      color: #6b7280;
    }

    .filter-group input,
    .filter-group select {
      border: 1px solid #d1d5db;
      border-radius: 8px;
      padding: 8px 10px;
      font-size: 0.85rem;
      background: white;
      min-width: 140px;
    }

    .card-header h3 {
      display: flex;
      align-items: center;
      gap: 10px;
      color: #1a237e;
      font-size: 1.1rem;
    }

    .filter-btns {
      display: flex;
      gap: 8px;
    }

    .filter-btn {
      padding: 8px 16px;
      border-radius: 20px;
      font-size: 0.85rem;
      font-weight: 600;
      background: #f3f4f6;
      color: #6b7280;
      transition: all 0.2s;
    }

    .filter-btn.active, .filter-btn:hover {
      background: #1a237e;
      color: white;
    }

    .transactions-list {
      padding: 0;
    }

    .table-wrap {
      overflow-x: auto;
    }

    .tx-table {
      width: 100%;
      border-collapse: collapse;
    }

    .tx-table th,
    .tx-table td {
      padding: 14px 16px;
      border-bottom: 1px solid #f3f4f6;
      text-align: left;
      font-size: 0.9rem;
      color: #374151;
      white-space: nowrap;
    }

    .tx-table th {
      background: #f8fafc;
      color: #6b7280;
      font-weight: 700;
      font-size: 0.75rem;
      text-transform: uppercase;
      letter-spacing: 0.04em;
    }

    .tx-table td.amount {
      font-weight: 700;
      text-align: right;
    }

    .tx-table th.amount {
      text-align: right;
    }

    .tx-table .mono {
      font-family: monospace;
    }

    .credit-row {
      background: #f6fffb;
    }

    .debit-row {
      background: #fff7f7;
    }

    .transaction-item {
      display: flex;
      align-items: flex-start;
      padding: 20px 24px;
      border-bottom: 1px solid #f3f4f6;
      transition: background 0.2s;
    }

    .transaction-item:hover {
      background: #f8f9ff;
    }

    .transaction-item:last-child {
      border-bottom: none;
    }

    .tx-icon {
      width: 52px;
      height: 52px;
      border-radius: 14px;
      display: flex;
      align-items: center;
      justify-content: center;
      font-size: 22px;
      margin-right: 18px;
      flex-shrink: 0;
    }

    .tx-icon.deposit { background: #e8f5e9; color: #2e7d32; }
    .tx-icon.withdrawal { background: #ffebee; color: #c62828; }
    .tx-icon.transfer { background: #e3f2fd; color: #1565c0; }
    .tx-icon.payment { background: #fff3e0; color: #ef6c00; }

    .tx-details {
      flex: 1;
    }

    .tx-main {
      margin-bottom: 4px;
    }

    .tx-type {
      font-weight: 700;
      color: #1f2937;
      font-size: 1rem;
    }

    .tx-sub {
      color: #6b7280;
      font-size: 0.875rem;
      margin-bottom: 8px;
    }

    .tx-arrow {
      margin: 0 8px;
      color: #1a237e;
    }

    .tx-meta {
      display: flex;
      gap: 16px;
      font-size: 0.75rem;
      color: #9ca3af;
    }

    .tx-meta i {
      margin-right: 4px;
    }

    .tx-ref {
      font-family: monospace;
    }

    .tx-description {
      margin-top: 8px;
      color: #6b7280;
      font-size: 0.85rem;
      font-style: italic;
    }

    .tx-description i {
      margin-right: 6px;
      color: #9ca3af;
    }

    .tx-right {
      text-align: right;
      min-width: 140px;
    }

    .tx-amount {
      display: block;
      font-size: 1.35rem;
      font-weight: 700;
      margin-bottom: 8px;
    }

    .tx-amount.credit { color: #2e7d32; }
    .tx-amount.debit { color: #c62828; }

    .tx-status {
      display: inline-flex;
      align-items: center;
      gap: 6px;
      padding: 6px 12px;
      border-radius: 20px;
      font-size: 0.7rem;
      font-weight: 600;
      text-transform: uppercase;
    }

    .tx-status.completed { background: #e8f5e9; color: #2e7d32; }
    .tx-status.pending { background: #fff3e0; color: #ef6c00; }
    .tx-status.failed { background: #ffebee; color: #c62828; }

    .pagination {
      display: flex;
      justify-content: flex-end;
      align-items: center;
      gap: 12px;
      padding: 16px 24px 24px;
    }

    .page-btn {
      padding: 8px 14px;
      border-radius: 8px;
      background: #f3f4f6;
      font-weight: 600;
      color: #374151;
      transition: background 0.2s;
    }

    .page-btn:hover:not(:disabled) {
      background: #1a237e;
      color: white;
    }

    .page-btn:disabled {
      opacity: 0.5;
      cursor: not-allowed;
    }

    .page-info {
      color: #6b7280;
      font-size: 0.85rem;
    }

    .loading-row {
      padding: 16px 24px;
      color: #6b7280;
      display: flex;
      align-items: center;
      gap: 8px;
      border-bottom: 1px solid #f3f4f6;
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
    }

    .empty-state p {
      color: #6b7280;
      margin-bottom: 24px;
    }

    .empty-note {
      color: #ff6f00;
      font-size: 0.85rem;
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

    .type-selector {
      display: grid;
      grid-template-columns: repeat(4, 1fr);
      gap: 10px;
      margin-bottom: 24px;
    }

    .type-btn {
      cursor: pointer;
    }

    .type-btn input {
      display: none;
    }

    .type-btn {
      display: flex;
      flex-direction: column;
      align-items: center;
      gap: 8px;
      padding: 16px 8px;
      border: 2px solid #e5e7eb;
      border-radius: 12px;
      font-size: 0.8rem;
      font-weight: 600;
      color: #6b7280;
      transition: all 0.2s;
    }

    .type-btn i {
      font-size: 20px;
    }

    .type-btn.active {
      border-color: #1a237e;
      background: #f0f4ff;
      color: #1a237e;
    }

    .amount-input {
      display: flex;
      align-items: center;
      border: 1px solid #d1d5db;
      border-radius: 10px;
      overflow: hidden;
    }

    .amount-input .currency {
      padding: 12px 16px;
      background: #f3f4f6;
      color: #1a237e;
      font-weight: 700;
      font-size: 1.1rem;
    }

    .amount-input input {
      border: none;
      flex: 1;
      padding: 12px;
      font-size: 1.1rem;
    }

    .amount-input input:focus {
      outline: none;
    }

    .form-group label i {
      margin-right: 6px;
      color: #1a237e;
    }

    .error-message {
      background: #ffebee;
      color: #c62828;
      padding: 12px 16px;
      border-radius: 10px;
      display: flex;
      align-items: center;
      gap: 10px;
      margin-top: 16px;
    }

    .field-error {
      color: #c62828;
      font-size: 0.75rem;
      margin-top: 6px;
    }

    .success-message {
      background: #e8f5e9;
      color: #2e7d32;
      padding: 12px 16px;
      border-radius: 10px;
      display: flex;
      align-items: center;
      gap: 10px;
      margin-top: 16px;
    }

    .modal-actions {
      display: flex;
      gap: 12px;
      margin-top: 24px;
    }

    .modal-actions button {
      flex: 1;
    }

    @media (max-width: 768px) {
      .quick-actions-bar {
        grid-template-columns: repeat(2, 1fr);
      }

      .stats-row {
        grid-template-columns: 1fr;
      }

      .type-selector {
        grid-template-columns: repeat(2, 1fr);
      }

      .page-header {
        flex-direction: column;
        gap: 20px;
      }

      .card-header {
        flex-direction: column;
        gap: 16px;
        align-items: flex-start;
      }
    }
  `]
})
export class TransactionsComponent implements OnInit {
  transactions: Transaction[] = [];
  accounts: Account[] = [];
  showTransferModal = false;
  transactionForm: FormGroup;
  loading = false;
  errorMessage = '';
  successMessage = '';
  pageErrorMessage = '';
  loadingTransactions = false;
  loadingAccounts = false;
  startDate = '';
  endDate = '';
  direction: 'ALL' | 'CREDIT' | 'DEBIT' = 'ALL';
  sort: 'date_desc' | 'date_asc' | 'amount_desc' | 'amount_asc' = 'date_desc';
  page = 0;
  size = 10;
  totalPages = 0;
  totalElements = 0;
  loadingStatement = false;
  private initialTransferSourceId: number | null = null;

  constructor(
    private transactionService: TransactionService,
    private accountService: AccountService,
    private fb: FormBuilder,
    private route: ActivatedRoute
  ) {
    this.transactionForm = this.fb.group({
      type: ['', Validators.required],
      sourceAccountId: ['', Validators.required],
      destinationAccountId: [''],
      destinationAccountNumber: ['', [Validators.pattern(/^\d{10}$/)]],
      amount: ['', [Validators.required, Validators.min(1)]],
      description: [''],
      billerName: [''],
      billReference: ['']
    });
  }

  ngOnInit(): void {
    const mode = this.route.snapshot.queryParamMap.get('mode');
    const sourceAccountId = this.route.snapshot.queryParamMap.get('sourceAccountId');
    if (mode === 'TRANSFER') {
      this.initialTransferSourceId = sourceAccountId ? Number(sourceAccountId) : null;
      this.openTransactionModal('TRANSFER', this.initialTransferSourceId || undefined);
    }
    this.loadTransactions();
    this.loadAccounts();
  }

  loadTransactions(): void {
    this.loadingTransactions = true;
    this.pageErrorMessage = '';
    this.transactionService.getUserTransactions({
      page: this.page,
      size: this.size,
      startDate: this.startDate || undefined,
      endDate: this.endDate || undefined,
      direction: this.direction,
      sort: this.getSortParam()
    }).subscribe({
      next: (page) => {
        this.transactions = page.content;
        this.totalPages = page.totalPages;
        this.totalElements = page.totalElements;
        this.page = page.number;
        this.loadingTransactions = false;
      },
      error: (err) => {
        this.loadingTransactions = false;
        this.pageErrorMessage = this.getErrorMessage(err, 'Unable to load transactions.');
      }
    });
  }

  loadAccounts(): void {
    this.loadingAccounts = true;
    this.accountService.getUserAccounts().subscribe({
      next: (accounts) => {
        this.accounts = accounts;
        this.loadingAccounts = false;
        if (this.transactionForm.get('type')?.value === 'TRANSFER') {
          this.onTypeChange();
        }
      },
      error: (err) => {
        this.loadingAccounts = false;
        this.pageErrorMessage = this.getErrorMessage(err, 'Unable to load accounts.');
      }
    });
  }

  openTransactionModal(type: string, sourceAccountId?: number): void {
    this.transactionForm.patchValue({ type });
    if (sourceAccountId) {
      this.transactionForm.patchValue({ sourceAccountId: sourceAccountId.toString() });
    }
    this.showTransferModal = true;
    this.onTypeChange();
  }

  onTypeChange(): void {
    const type = this.transactionForm.get('type')?.value;
    if (type !== 'TRANSFER') {
      this.transactionForm.patchValue({ destinationAccountId: '', destinationAccountNumber: '' });
      this.transactionForm.get('destinationAccountId')?.clearValidators();
      this.transactionForm.get('destinationAccountNumber')?.clearValidators();
    } else if (this.accounts.length > 1) {
      this.transactionForm.patchValue({ destinationAccountNumber: '' });
      this.transactionForm.get('destinationAccountId')?.setValidators([Validators.required]);
      this.transactionForm.get('destinationAccountNumber')?.clearValidators();
    } else {
      this.transactionForm.patchValue({ destinationAccountId: '' });
      this.transactionForm.get('destinationAccountId')?.clearValidators();
      this.transactionForm.get('destinationAccountNumber')?.setValidators([Validators.required, Validators.pattern(/^\d{10}$/)]);
    }
    if (type === 'PAYMENT') {
      this.transactionForm.get('billerName')?.setValidators([Validators.required]);
    } else {
      this.transactionForm.get('billerName')?.clearValidators();
      this.transactionForm.patchValue({ billerName: '', billReference: '' });
    }
    this.transactionForm.get('billerName')?.updateValueAndValidity();
    this.transactionForm.get('destinationAccountId')?.updateValueAndValidity();
    this.transactionForm.get('destinationAccountNumber')?.updateValueAndValidity();
  }

  submitTransaction(): void {
    if (this.transactionForm.invalid || this.loading) return;

    this.loading = true;
    this.errorMessage = '';
    this.successMessage = '';

    const formValue = this.transactionForm.value;
    const request = {
      type: formValue.type,
      sourceAccountId: +formValue.sourceAccountId,
      destinationAccountId: formValue.destinationAccountId ? +formValue.destinationAccountId : undefined,
      destinationAccountNumber: formValue.destinationAccountNumber ? String(formValue.destinationAccountNumber).trim() : undefined,
      amount: +formValue.amount,
      description: formValue.description || undefined
    };

    const submit$ = formValue.type === 'DEPOSIT'
      ? this.transactionService.deposit(+formValue.sourceAccountId, +formValue.amount, formValue.description || undefined)
      : formValue.type === 'WITHDRAWAL'
        ? this.transactionService.withdraw(+formValue.sourceAccountId, +formValue.amount, formValue.description || undefined)
        : formValue.type === 'PAYMENT'
          ? this.transactionService.payBill(
              +formValue.sourceAccountId,
              +formValue.amount,
              formValue.billerName,
              formValue.billReference || undefined,
              formValue.description || undefined
            )
        : this.transactionService.createTransaction(request);

    submit$.subscribe({
      next: (transaction) => {
        this.transactions.unshift(transaction);
        this.successMessage = formValue.type === 'PAYMENT'
          ? 'Bill payment completed successfully!'
          : 'Transaction completed successfully!';
        this.loading = false;
        this.loadAccounts();
        this.loadTransactions();
        setTimeout(() => {
          this.showTransferModal = false;
          this.transactionForm.reset();
          this.successMessage = '';
        }, 1500);
      },
      error: (err) => {
        this.loading = false;
        this.errorMessage = this.getErrorMessage(err, 'Transaction failed. Please try again.');
      }
    });
  }

  getTotalCredits(): number {
    return this.transactions
      .filter(tx => this.isCredit(tx))
      .reduce((sum, tx) => sum + tx.amount, 0);
  }

  getTotalDebits(): number {
    return this.transactions
      .filter(tx => this.isDebit(tx))
      .reduce((sum, tx) => sum + tx.amount, 0);
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

  applyFilters(): void {
    this.page = 0;
    this.loadTransactions();
  }

  changePage(delta: number): void {
    const nextPage = this.page + delta;
    if (nextPage < 0 || (this.totalPages && nextPage >= this.totalPages)) return;
    this.page = nextPage;
    this.loadTransactions();
  }

  private getSortParam(): string {
    switch (this.sort) {
      case 'date_asc':
        return 'transactionDate,asc';
      case 'amount_desc':
        return 'amount,desc';
      case 'amount_asc':
        return 'amount,asc';
      case 'date_desc':
      default:
        return 'transactionDate,desc';
    }
  }

  downloadStatement(): void {
    if (this.loadingStatement) return;
    this.loadingStatement = true;
    this.errorMessage = '';
    this.successMessage = '';
    this.transactionService.downloadStatement(this.startDate || undefined, this.endDate || undefined).subscribe({
      next: (response) => {
        const responseBlob = response.body;
        if (!responseBlob || responseBlob.size === 0) {
          this.loadingStatement = false;
          this.errorMessage = 'Failed to download statement.';
          return;
        }
        const blob = new Blob([responseBlob], { type: 'application/pdf' });
        const url = window.URL.createObjectURL(blob);
        const link = document.createElement('a');
        const contentDisposition = response.headers.get('content-disposition') || '';
        const filenameMatch = contentDisposition.match(/filename\*?=(?:UTF-8''|\"?)([^\";]+)/i);
        const serverFilename = filenameMatch ? decodeURIComponent(filenameMatch[1].replace(/\"/g, '').trim()) : '';
        const start = this.startDate ? this.startDate : 'all';
        const end = this.endDate ? this.endDate : 'all';
        link.href = url;
        link.download = serverFilename || `bank-statement-${start}-to-${end}.pdf`;
        document.body.appendChild(link);
        link.click();
        document.body.removeChild(link);
        window.URL.revokeObjectURL(url);
        this.loadingStatement = false;
        this.successMessage = 'Statement downloaded successfully.';
      },
      error: (err) => {
        this.loadingStatement = false;
        this.errorMessage = this.getErrorMessage(err, 'Failed to download statement.');
      }
    });
  }

  isCredit(tx: Transaction): boolean {
    if (tx.type === 'DEPOSIT') return true;
    if (tx.type === 'TRANSFER') {
      return !tx.sourceAccountNumber && !!tx.destinationAccountNumber;
    }
    return false;
  }

  isDebit(tx: Transaction): boolean {
    if (tx.type === 'DEPOSIT') return false;
    if (tx.type === 'TRANSFER') {
      if (!!tx.sourceAccountNumber && !tx.destinationAccountNumber) return true;
      if (!!tx.sourceAccountNumber && !!tx.destinationAccountNumber) return true;
      return false;
    }
    return true;
  }

  getAmountSign(tx: Transaction): string {
    return this.isCredit(tx) ? '+' : '-';
  }

  formatAccType(type: string): string {
    const types: { [key: string]: string } = {
      'SAVINGS': 'Savings',
      'CHECKING': 'Current',
      'FIXED_DEPOSIT': 'FD'
    };
    return types[type] || type;
  }

  private getErrorMessage(err: any, fallback: string): string {
    if (err?.status && err.status >= 500) {
      return fallback;
    }
    const error = err?.error;
    if (error && error.errors && typeof error.errors === 'object') {
      const firstKey = Object.keys(error.errors)[0];
      if (firstKey) {
        return `${firstKey}: ${error.errors[firstKey]}`;
      }
    }
    if (error && typeof error.message === 'string' && error.message.trim()) {
      const message = error.message.trim();
      if (/jdbc|sql|SQLException/i.test(message)) {
        return fallback;
      }
      return message;
    }
    return fallback;
  }
}
