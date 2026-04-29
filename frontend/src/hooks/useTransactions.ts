import { useEffect, useState, useCallback } from 'react';
import axios from 'axios';

import { fetchTransactions } from '../services/transactionsService';
import type { Transaction } from '../types/banking';

type UseTransactionsResult = {
  items: Transaction[];
  loading: boolean;
  error: string;
  refresh: () => void;
};

export function useTransactions(accessToken: string, onUnauthorized: (message: string) => void): UseTransactionsResult {
  const [items, setItems] = useState<Transaction[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [refreshTrigger, setRefreshTrigger] = useState(0);

  const refresh = useCallback(() => {
    setRefreshTrigger(prev => prev + 1);
  }, []);

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
  }, [accessToken, onUnauthorized, refreshTrigger]);

  return {
    items,
    loading,
    error,
    refresh
  };
}
