
import { useState } from 'react';
import type { Transaction } from '../types/banking';
import { useTransactionCatalogs } from '../hooks/useTransactionCatalogs';
import { CreateTransactionModal } from './CreateTransactionModal';

type TransactionsTableProps = {
  items: Transaction[];
  accessToken: string;
  onRefresh?: () => void;
};

export function TransactionsTable({ items, accessToken, onRefresh }: TransactionsTableProps) {
  const { statusMap, typeMap, categoryMap, accountMap, loading } = useTransactionCatalogs(accessToken);
  const [showCreateModal, setShowCreateModal] = useState(false);

  const handleSuccess = () => {
    if (onRefresh) onRefresh();
  };

  return (
    <>
      {showCreateModal && (
        <CreateTransactionModal
          accessToken={accessToken}
          onClose={() => setShowCreateModal(false)}
          onSuccess={handleSuccess}
        />
      )}
      <div style={{ marginBottom: 16 }}>
        <button
          className="btn primary"
          onClick={() => setShowCreateModal(true)}
        >
          + Nueva Transacción
        </button>
      </div>
      <div style={{ width: '100%', overflowX: 'auto' }}>
        <table className="table" style={{ width: '100%', minWidth: 900 }}>
        <thead>
          <tr>
            <th>Reference</th>
            <th>Source</th>
            <th>Destination</th>
            <th>Booking Date</th>
            <th>Value Date</th>
            <th style={{ textAlign: 'right' }}>Amount</th>
            <th>Description</th>
            <th>Merchant</th>
            <th>Category</th>
            <th>Status</th>
            <th>Type</th>
            <th>Tags</th>
          </tr>
        </thead>
        <tbody>
          {items.map((transaction, index) => (
            <tr key={transaction.id ?? transaction.externalId ?? index}>
              <td>{transaction.externalId ?? transaction.id ?? '-'}</td>
              <td>{
                transaction.sourceAccountId !== undefined && transaction.sourceAccountId !== null
                  ? (accountMap[Number(transaction.sourceAccountId)] ?? (!loading ? '-' : ''))
                  : '-'
              }</td>
              <td>{
                transaction.destinationAccountId !== undefined && transaction.destinationAccountId !== null
                  ? (accountMap[Number(transaction.destinationAccountId)] ?? (!loading ? '-' : ''))
                  : '-'
              }</td>
              <td>{transaction.bookingDate ? new Date(transaction.bookingDate).toLocaleDateString() : '-'}</td>
              <td>{transaction.valueDate ? new Date(transaction.valueDate).toLocaleDateString() : '-'}</td>
              <td style={{ textAlign: 'right', fontWeight: 600, color: (transaction.amount ?? 0) < 0 ? '#b00020' : '#2d8f5a' }}>
                {typeof transaction.amount === 'number' ? transaction.amount.toLocaleString('es-ES', { style: 'currency', currency: 'EUR' }) : '-'}
              </td>
              <td>{transaction.description || '-'}</td>
              <td>{transaction.merchantName || '-'}</td>
              <td>{
                transaction.categoryId !== undefined && transaction.categoryId !== null
                  ? (categoryMap[Number(transaction.categoryId)] ?? (!loading ? '-' : ''))
                  : '-'
              }</td>
              <td>{
                transaction.statusId !== undefined && transaction.statusId !== null
                  ? (statusMap[Number(transaction.statusId)] ?? (!loading ? '-' : ''))
                  : '-'
              }</td>
              <td>{
                transaction.typeId !== undefined && transaction.typeId !== null
                  ? (typeMap[Number(transaction.typeId)] ?? (!loading ? '-' : ''))
                  : '-'
              }</td>
              <td>{transaction.tagIds && transaction.tagIds.length > 0 ? transaction.tagIds.join(', ') : '-'}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
    </>
  );
}
