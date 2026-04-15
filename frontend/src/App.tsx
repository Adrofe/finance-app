import { useEffect, useState } from 'react';
import axios from 'axios';

type Transaction = {
  id: number;
  bookingDate?: string;
  amount?: number;
  description?: string;
  merchantName?: string;
};

type ApiResponse<T> = {
  status: number;
  message: string;
  data: T;
};

function App() {
  const [items, setItems] = useState<Transaction[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    axios
      .get<ApiResponse<Transaction[]>>('/v1/api/transactions')
      .then((res) => setItems(res.data.data || []))
      .catch((err) => {
        if (axios.isAxiosError(err) && err.response?.status === 401) {
          setError('Backend is running, but this endpoint requires login (401 Unauthorized).');
          return;
        }

        setError('Could not load transactions. Check that backend is running on 8081.');
      })
      .finally(() => setLoading(false));
  }, []);

  return (
    <div className="page">
      <header className="header">
        <h1>Finance App</h1>
        <p>Banking transactions preview</p>
      </header>

      {loading && <p className="state">Loading transactions...</p>}
      {!loading && error && <p className="state error">{error}</p>}

      {!loading && !error && (
        <table className="table">
          <thead>
            <tr>
              <th>ID</th>
              <th>Date</th>
              <th>Merchant</th>
              <th>Description</th>
              <th>Amount</th>
            </tr>
          </thead>
          <tbody>
            {items.map((t) => (
              <tr key={t.id}>
                <td>{t.id}</td>
                <td>{t.bookingDate || '-'}</td>
                <td>{t.merchantName || '-'}</td>
                <td>{t.description || '-'}</td>
                <td>{t.amount ?? '-'}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </div>
  );
}

export default App;
