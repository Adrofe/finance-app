import type { Transaction } from '../types/banking';

type TransactionsTableProps = {
  items: Transaction[];
};

export function TransactionsTable({ items }: TransactionsTableProps) {
  return (
    <table className="table">
      <thead>
        <tr>
          <th>Reference</th>
          <th>Date</th>
          <th>Merchant</th>
          <th>Description</th>
          <th>Amount</th>
        </tr>
      </thead>
      <tbody>
        {items.map((transaction, index) => (
          <tr key={transaction.id ?? transaction.externalId ?? index}>
            <td>{transaction.externalId ?? transaction.id ?? '-'}</td>
            <td>{transaction.bookingDate || '-'}</td>
            <td>{transaction.merchantName || '-'}</td>
            <td>{transaction.description || '-'}</td>
            <td>{transaction.amount ?? '-'}</td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}
