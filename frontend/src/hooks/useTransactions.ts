import { useEffect, useState } from 'react';
import axios from 'axios';

import { fetchTransactions } from '../services/transactionsService';
import type { Transaction } from '../types/banking';

type UseTransactionsResult = {
  items: Transaction[];
  loading: boolean;
  error: string;
};

export function useTransactions(accessToken: string, onUnauthorized: (message: string) => void): UseTransactionsResult {
  const [items, setItems] = useState<Transaction[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    if (!accessToken) {
      setLoading(false);
      setItems([]);
      setError('');
      return;
    }

    setLoading(true);
    setError('');

    fetchTransactions(accessToken)
      .then((data) => setItems(data))
      .catch((err) => {
        if (axios.isAxiosError(err) && err.response?.status === 401) {
          onUnauthorized('Session expired or invalid token. Please login again.');
          return;
        }

        setError('Could not load transactions. Check that backend is running on 8081.');
      })
      .finally(() => setLoading(false));
  }, [accessToken, onUnauthorized]);

  return {
    items,
    loading,
    error
  };
}
