import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Account, CreateAccountRequest } from '../../shared/models/account.model';

@Injectable({
  providedIn: 'root'
})
export class AccountService {
  private readonly API_URL = `${environment.apiUrl}/accounts`;

  constructor(private http: HttpClient) {}

  createAccount(request: CreateAccountRequest): Observable<Account> {
    return this.http.post<Account>(this.API_URL, request);
  }

  getUserAccounts(): Observable<Account[]> {
    return this.http.get<Account[]>(this.API_URL);
  }

  getAccountById(id: number): Observable<Account> {
    return this.http.get<Account>(`${this.API_URL}/${id}`);
  }

  getAccountByNumber(accountNumber: string): Observable<Account> {
    return this.http.get<Account>(`${this.API_URL}/number/${accountNumber}`);
  }
}
