export interface Account {
  id: number;
  accountNumber: string;
  maskedAccountNumber?: string;
  accountType: AccountType;
  balance: number;
  currency: string;
  active: boolean;
  createdAt: string;
}

export type AccountType = 'SAVINGS' | 'CHECKING' | 'FIXED_DEPOSIT';

export interface CreateAccountRequest {
  accountType: AccountType;
  initialDeposit?: number;
  currency?: string;
}
