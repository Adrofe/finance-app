import { useEffect, useState, useCallback } from 'react';
import { fetchAccounts, createAccount } from '../services/accountsService';
import { Account } from '../types/account';

export function useAccounts(token: string) {
  const [accounts, setAccounts] = useState<Account[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(() => {
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

  useEffect(() => {
    load();
  }, [load]);

  const create = useCallback(async (account: Partial<Account>) => {
    if (!token) throw new Error('No token');
    setLoading(true);
    try {
      const created = await createAccount(token, account);
      // reload list
      await fetchAccounts(token).then(setAccounts);
      return created;
    } catch (err: any) {
      setError(err?.message || 'Error creating account');
      throw err;
    } finally {
      setLoading(false);
    }
  }, [token]);

  return { accounts, loading, error, reload: load, createAccount: create };
}
