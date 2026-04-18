import { useEffect, useState, useCallback } from 'react';
import { fetchAccounts, createAccount, updateAccount } from '../services/accountsService';
import { fetchInstitutions } from '../services/institutionsService';
import { fetchAccountTypes } from '../services/accountTypesService';
import { Account } from '../types/account';
import { Institution } from '../types/institution';
import { AccountType } from '../types/accountType';

export function useAccounts(token: string) {
  const [accounts, setAccounts] = useState<Account[]>([]);
  const [institutions, setInstitutions] = useState<Institution[]>([]);
  const [accountTypes, setAccountTypes] = useState<AccountType[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(() => {
    if (!token) {
      setAccounts([]);
      setInstitutions([]);
      setAccountTypes([]);
      setLoading(false);
      setError('No token');
      return;
    }
    setLoading(true);
    // Load accounts + enums in parallel
    Promise.all([fetchAccounts(token), fetchInstitutions(token), fetchAccountTypes(token)])
      .then(([accs, insts, types]) => {
        setAccounts(accs);
        setInstitutions(insts);
        setAccountTypes(types);
      })
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

  const update = useCallback(async (id: number, account: Partial<Account>) => {
    if (!token) throw new Error('No token');
    setLoading(true);
    try {
      const updated = await updateAccount(token, id, account);
      // reload list
      await fetchAccounts(token).then(setAccounts);
      return updated;
    } catch (err: any) {
      setError(err?.message || 'Error updating account');
      throw err;
    } finally {
      setLoading(false);
    }
  }, [token]);

  const clearError = useCallback(() => setError(null), []);

  return { accounts, institutions, accountTypes, loading, error, reload: load, createAccount: create, updateAccount: update, clearError };
}
