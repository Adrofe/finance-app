import type { Transaction } from '../types/banking';

type TransactionsTableProps = {
  items: Transaction[];
};

export function TransactionsTable({ items }: TransactionsTableProps) {
  return (
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
              <td>{transaction.sourceAccountId ?? '-'}</td>
              <td>{transaction.destinationAccountId ?? '-'}</td>
              <td>{transaction.bookingDate ? new Date(transaction.bookingDate).toLocaleDateString() : '-'}</td>
              <td>{transaction.valueDate ? new Date(transaction.valueDate).toLocaleDateString() : '-'}</td>
              <td style={{ textAlign: 'right', fontWeight: 600, color: (transaction.amount ?? 0) < 0 ? '#b00020' : '#2d8f5a' }}>
                {typeof transaction.amount === 'number' ? transaction.amount.toLocaleString('es-ES', { style: 'currency', currency: 'EUR' }) : '-'}
              </td>
              <td>{transaction.description || '-'}</td>
              <td>{transaction.merchantName || '-'}</td>
              <td>{transaction.categoryId ?? '-'}</td>
              <td>{transaction.statusId ?? '-'}</td>
              <td>{transaction.typeId ?? '-'}</td>
              <td>{transaction.tagIds && transaction.tagIds.length > 0 ? transaction.tagIds.join(', ') : '-'}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
