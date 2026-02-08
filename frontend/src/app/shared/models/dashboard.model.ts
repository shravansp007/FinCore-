import { Account } from './account.model';
import { Transaction } from './transaction.model';

export interface DashboardResponse {
  welcomeName: string;
  totalBalance: number;
  monthlyCredit: number;
  monthlyDebit: number;
  accountCount: number;
  beneficiaryCount: number;
  accounts: Account[];
  recentTransactions: Transaction[];
}
