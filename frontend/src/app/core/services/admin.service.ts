import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Account } from '../../shared/models/account.model';
import { Transaction } from '../../shared/models/transaction.model';

export interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
}

export interface UserSummary {
  id: number;
  firstName: string;
  lastName: string;
  email: string;
  phoneNumber?: string;
  role: string;
  enabled: boolean;
  createdAt: string;
}

@Injectable({
  providedIn: 'root'
})
export class AdminService {
  private readonly API_URL = `${environment.apiUrl}/admin`;

  constructor(private http: HttpClient) {}

  getUsers(): Observable<UserSummary[]> {
    return this.http.get<UserSummary[]>(`${this.API_URL}/users`);
  }

  getAccounts(): Observable<Account[]> {
    return this.http.get<Account[]>(`${this.API_URL}/accounts`);
  }

  getTransactions(page: number = 0, size: number = 20): Observable<PageResponse<Transaction>> {
    const params = new HttpParams().set('page', page).set('size', size);
    return this.http.get<PageResponse<Transaction>>(`${this.API_URL}/transactions`, { params });
  }

  freezeAccount(accountId: number): Observable<Account> {
    return this.http.post<Account>(`${this.API_URL}/accounts/${accountId}/freeze`, {});
  }

  unfreezeAccount(accountId: number): Observable<Account> {
    return this.http.post<Account>(`${this.API_URL}/accounts/${accountId}/unfreeze`, {});
  }
}
