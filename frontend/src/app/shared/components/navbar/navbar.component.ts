import { Component, OnInit, HostListener } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { AuthService } from '../../../core/services/auth.service';
import { NotificationService } from '../../../core/services/notification.service';
import { Notification } from '../../models/notification.model';

@Component({
  selector: 'app-navbar',
  standalone: true,
  imports: [CommonModule, RouterLink, RouterLinkActive],
  template: `
    <nav class="navbar" *ngIf="authService.isLoggedIn()">
      <div class="nav-container">
        <a routerLink="/dashboard" class="nav-brand">
          <img src="assets/fin-core-logo.svg" alt="FinCore Bank" class="brand-logo">
          <span>FinCore</span>
        </a>
        
        <div class="nav-links">
          <a routerLink="/dashboard" routerLinkActive="active">
            <i class="fas fa-home"></i> Dashboard
          </a>
          <a routerLink="/accounts" routerLinkActive="active">
            <i class="fas fa-wallet"></i> Accounts
          </a>
          <a routerLink="/transactions" routerLinkActive="active">
            <i class="fas fa-exchange-alt"></i> Transactions
          </a>
          <a *ngIf="isAdmin()" routerLink="/admin" routerLinkActive="active">
            <i class="fas fa-shield-alt"></i> Admin
          </a>
        </div>

        <div class="nav-user">
          <div class="notification-wrapper" (click)="toggleNotifications($event)">
            <button class="notif-btn" aria-label="Notifications">
              <i class="fas fa-bell"></i>
              <span class="badge" *ngIf="unreadCount > 0">{{ unreadCount }}</span>
            </button>
            <div class="notif-dropdown" *ngIf="showNotifications">
              <div class="notif-header">
                <span>Notifications</span>
                <button class="mark-all" (click)="markAllRead($event)" [disabled]="unreadCount === 0">Mark all read</button>
              </div>
              <div class="notif-list" *ngIf="notifications.length; else emptyNotifs">
                <div class="notif-item" *ngFor="let n of notifications" [class.unread]="!n.read" (click)="markRead(n, $event)">
                  <div class="notif-message">{{ n.message }}</div>
                  <div class="notif-meta">
                    <span class="notif-type">{{ n.type }}</span>
                    <span class="notif-date">{{ n.createdAt | date:'dd MMM, hh:mm a' }}</span>
                  </div>
                </div>
              </div>
              <ng-template #emptyNotifs>
                <div class="notif-empty">No notifications</div>
              </ng-template>
            </div>
          </div>
          <span class="user-name">
            <i class="fas fa-user-circle"></i>
            {{ (authService.currentUser$ | async)?.firstName }}
          </span>
          <button class="btn-logout" (click)="authService.logout()">
            <i class="fas fa-sign-out-alt"></i> Logout
          </button>
        </div>
      </div>
    </nav>
  `,
  styles: [`
    .navbar {
      position: fixed;
      top: 0;
      left: 0;
      right: 0;
      height: 70px;
      background: linear-gradient(135deg, #1e1e2e, #2d2d44);
      box-shadow: 0 4px 20px rgba(0, 0, 0, 0.15);
      z-index: 1000;
    }

    .nav-container {
      max-width: 1400px;
      margin: 0 auto;
      padding: 0 24px;
      height: 100%;
      display: flex;
      align-items: center;
      justify-content: space-between;
    }

    .nav-brand {
      display: flex;
      align-items: center;
      gap: 10px;
      font-size: 1.5rem;
      font-weight: 700;
      color: white;
    }

    .brand-logo {
      width: 34px;
      height: 34px;
      display: inline-block;
    }

    .nav-links {
      display: flex;
      gap: 8px;
    }

    .nav-links a {
      padding: 10px 20px;
      color: #a1a1aa;
      border-radius: 8px;
      transition: all 0.3s;
      display: flex;
      align-items: center;
      gap: 8px;
    }

    .nav-links a:hover, .nav-links a.active {
      background: rgba(129, 140, 248, 0.1);
      color: #818cf8;
    }

    .nav-user {
      display: flex;
      align-items: center;
      gap: 16px;
    }

    .notification-wrapper {
      position: relative;
    }

    .notif-btn {
      position: relative;
      width: 40px;
      height: 40px;
      border-radius: 10px;
      background: rgba(129, 140, 248, 0.1);
      color: #c7d2fe;
      display: flex;
      align-items: center;
      justify-content: center;
      transition: all 0.2s;
    }

    .notif-btn:hover {
      background: rgba(129, 140, 248, 0.2);
      color: #e0e7ff;
    }

    .badge {
      position: absolute;
      top: -4px;
      right: -4px;
      background: #ef4444;
      color: white;
      font-size: 0.65rem;
      font-weight: 700;
      border-radius: 12px;
      padding: 2px 6px;
      min-width: 20px;
      text-align: center;
    }

    .notif-dropdown {
      position: absolute;
      right: 0;
      top: 48px;
      width: 320px;
      background: white;
      border-radius: 14px;
      box-shadow: 0 12px 32px rgba(0,0,0,0.2);
      overflow: hidden;
      z-index: 1200;
    }

    .notif-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      padding: 12px 16px;
      background: #f8fafc;
      border-bottom: 1px solid #e5e7eb;
      font-weight: 700;
      color: #111827;
    }

    .mark-all {
      font-size: 0.75rem;
      color: #1e40af;
    }

    .mark-all:disabled {
      opacity: 0.5;
      cursor: not-allowed;
    }

    .notif-list {
      max-height: 320px;
      overflow-y: auto;
    }

    .notif-item {
      padding: 12px 16px;
      border-bottom: 1px solid #f3f4f6;
      cursor: pointer;
    }

    .notif-item.unread {
      background: #eef2ff;
    }

    .notif-message {
      color: #111827;
      font-size: 0.9rem;
      margin-bottom: 6px;
    }

    .notif-meta {
      display: flex;
      justify-content: space-between;
      font-size: 0.75rem;
      color: #6b7280;
    }

    .notif-empty {
      padding: 20px;
      text-align: center;
      color: #6b7280;
      font-size: 0.85rem;
    }

    .user-name {
      color: #e4e4e7;
      display: flex;
      align-items: center;
      gap: 8px;
    }

    .btn-logout {
      padding: 10px 20px;
      background: rgba(239, 68, 68, 0.1);
      color: #ef4444;
      border-radius: 8px;
      transition: all 0.3s;
      display: flex;
      align-items: center;
      gap: 8px;
    }

    .btn-logout:hover {
      background: rgba(239, 68, 68, 0.2);
    }
  `]
})
export class NavbarComponent implements OnInit {
  notifications: Notification[] = [];
  unreadCount = 0;
  showNotifications = false;

  constructor(
    public authService: AuthService,
    private notificationService: NotificationService
  ) {}

  ngOnInit(): void {
    if (this.authService.isLoggedIn()) {
      this.refreshUnreadCount();
    }
  }

  toggleNotifications(event: MouseEvent): void {
    event.stopPropagation();
    if (!this.authService.isLoggedIn()) {
      return;
    }
    this.showNotifications = !this.showNotifications;
    if (this.showNotifications) {
      this.loadNotifications();
    }
  }

  @HostListener('document:click')
  closeNotifications(): void {
    this.showNotifications = false;
  }

  loadNotifications(): void {
    if (!this.authService.isLoggedIn()) {
      this.notifications = [];
      this.unreadCount = 0;
      return;
    }
    this.notificationService.getNotifications(false).subscribe({
      next: (items) => this.notifications = items,
      error: () => {}
    });
    this.refreshUnreadCount();
  }

  refreshUnreadCount(): void {
    if (!this.authService.isLoggedIn()) {
      this.unreadCount = 0;
      return;
    }
    this.notificationService.getUnreadCount().subscribe({
      next: (count) => this.unreadCount = count,
      error: () => {}
    });
  }

  markRead(notification: Notification, event: MouseEvent): void {
    event.stopPropagation();
    if (notification.read) return;
    this.notificationService.markAsRead(notification.id).subscribe({
      next: () => {
        notification.read = true;
        this.unreadCount = Math.max(0, this.unreadCount - 1);
      },
      error: () => {}
    });
  }

  markAllRead(event: MouseEvent): void {
    event.stopPropagation();
    if (this.unreadCount === 0) return;
    this.notificationService.markAllAsRead().subscribe({
      next: () => {
        this.notifications = this.notifications.map(n => ({ ...n, read: true }));
        this.unreadCount = 0;
      },
      error: () => {}
    });
  }

  isAdmin(): boolean {
    return this.authService.getCurrentUser()?.role === 'ADMIN';
  }
}
