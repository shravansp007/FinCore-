export interface Transaction {
  id: number;
  transactionReference: string;
  type: TransactionType;
  amount: number;
  description: string;
  sourceAccountNumber: string;
  destinationAccountNumber: string;
  status: TransactionStatus;
  transactionDate: string;
}

export type TransactionType = 'DEPOSIT' | 'WITHDRAWAL' | 'TRANSFER' | 'PAYMENT';
export type TransactionStatus = 'PENDING' | 'COMPLETED' | 'FAILED' | 'CANCELLED';

export interface TransactionRequest {
  type: TransactionType;
  amount: number;
  sourceAccountId: number;
  destinationAccountId?: number;
  destinationAccountNumber?: string;
  description?: string;
}
