import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Notification } from '../../shared/models/notification.model';

@Injectable({
  providedIn: 'root'
})
export class NotificationService {
  private readonly API_URL = `${environment.apiUrl}/notifications`;

  constructor(private http: HttpClient) {}

  getNotifications(unreadOnly: boolean = false): Observable<Notification[]> {
    return this.http.get<Notification[]>(this.API_URL, { params: { unreadOnly } as any });
  }

  getUnreadCount(): Observable<number> {
    return this.http.get<number>(`${this.API_URL}/unread-count`);
  }

  markAsRead(id: number): Observable<void> {
    return this.http.post<void>(`${this.API_URL}/${id}/read`, {});
  }

  markAllAsRead(): Observable<void> {
    return this.http.post<void>(`${this.API_URL}/read-all`, {});
  }
}
