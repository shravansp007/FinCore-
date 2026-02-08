export interface Notification {
  id: number;
  message: string;
  type: 'TRANSFER' | 'PAYMENT';
  read: boolean;
  createdAt: string;
}
