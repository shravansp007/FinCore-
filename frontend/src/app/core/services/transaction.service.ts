import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Transaction, TransactionRequest } from '../../shared/models/transaction.model';

export interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
}

@Injectable({
  providedIn: 'root'
})
export class TransactionService {
  private readonly API_URL = `${environment.apiUrl}/transactions`;

  constructor(private http: HttpClient) {}

  createTransaction(request: TransactionRequest): Observable<Transaction> {
    return this.http.post<Transaction>(this.API_URL, request);
  }

  deposit(accountId: number, amount: number, description?: string): Observable<Transaction> {
    return this.http.post<Transaction>(`${this.API_URL}/deposit`, {
      accountId,
      amount,
      description
    });
  }

  withdraw(accountId: number, amount: number, description?: string): Observable<Transaction> {
    return this.http.post<Transaction>(`${this.API_URL}/withdraw`, {
      accountId,
      amount,
      description
    });
  }

  payBill(accountId: number, amount: number, billerName: string, billReference?: string, description?: string): Observable<Transaction> {
    return this.http.post<Transaction>(`${this.API_URL}/bill-payment`, {
      accountId,
      amount,
      billerName,
      billReference,
      description
    });
  }

  getUserTransactions(params: {
    page?: number;
    size?: number;
    startDate?: string;
    endDate?: string;
    direction?: 'ALL' | 'CREDIT' | 'DEBIT';
    sort?: string;
  }): Observable<PageResponse<Transaction>> {
    let httpParams = new HttpParams();
    if (params.page !== undefined) httpParams = httpParams.set('page', params.page.toString());
    if (params.size !== undefined) httpParams = httpParams.set('size', params.size.toString());
    if (params.startDate) httpParams = httpParams.set('startDate', params.startDate);
    if (params.endDate) httpParams = httpParams.set('endDate', params.endDate);
    if (params.direction && params.direction !== 'ALL') httpParams = httpParams.set('direction', params.direction);
    if (params.sort) httpParams = httpParams.set('sort', params.sort);
    return this.http.get<PageResponse<Transaction>>(this.API_URL, { params: httpParams });
  }

  downloadStatement(startDate?: string, endDate?: string): Observable<Blob> {
    let httpParams = new HttpParams();
    if (startDate) httpParams = httpParams.set('startDate', startDate);
    if (endDate) httpParams = httpParams.set('endDate', endDate);
    return this.http.get(`${this.API_URL}/statement`, {
      params: httpParams,
      responseType: 'blob'
    });
  }

  getAccountTransactions(accountId: number, page: number = 0, size: number = 10): Observable<any> {
    const params = new HttpParams()
      .set('page', page.toString())
      .set('size', size.toString());
    return this.http.get(`${this.API_URL}/account/${accountId}`, { params });
  }

  getTransactionByReference(reference: string): Observable<Transaction> {
    return this.http.get<Transaction>(`${this.API_URL}/reference/${reference}`);
  }
}
