import { useEffect, useState } from 'react';
import { fetchAccounts } from '../services/accountsService';
import { Account } from '../types/account';

export function useAccounts(token: string) {
  const [accounts, setAccounts] = useState<Account[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!token) {
      setAccounts([]);
      setLoading(false);
      setError('No token');
      return;
    }
    setLoading(true);
    fetchAccounts(token)
      .then(setAccounts)
      .catch((err) => setError(err.message || 'Error fetching accounts'))
      .finally(() => setLoading(false));
  }, [token]);

  return { accounts, loading, error };
}
