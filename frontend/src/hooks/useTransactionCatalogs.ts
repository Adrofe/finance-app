import { useEffect, useState } from 'react';
import { CatalogService} from '../services/catalogService';

export type TransactionCatalogMaps = {
  statusMap: Record<number, string>;
  typeMap: Record<number, string>;
  categoryMap: Record<number, string>;
  accountMap: Record<number, string>;
  loading: boolean;
};

export function useTransactionCatalogs(token: string): TransactionCatalogMaps {
  const [statusMap, setStatusMap] = useState<Record<number, string>>({});
  const [typeMap, setTypeMap] = useState<Record<number, string>>({});
  const [categoryMap, setCategoryMap] = useState<Record<number, string>>({});
  const [accountMap, setAccountMap] = useState<Record<number, string>>({});
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    if (!token) return;
    setLoading(true);
    Promise.all([
      CatalogService.fetchStatuses(token),
      CatalogService.fetchTypes(token),
      CatalogService.fetchCategories(token),
      CatalogService.fetchAccounts(token)
    ]).then(([statuses, types, categories, accounts]) => {
      setStatusMap(Object.fromEntries(statuses.map(s => [s.id, s.code?.trim() ? s.code : '-'])));
      setTypeMap(Object.fromEntries(types.map(t => [t.id, t.name?.trim() ? t.name : '-'])));
      setCategoryMap(Object.fromEntries(categories.map(c => [c.id, c.name?.trim() ? c.name : '-'])));
      setAccountMap(Object.fromEntries(accounts.map(a => [a.id, a.name?.trim() ? a.name : '-'])));
    }).finally(() => setLoading(false));
  }, [token]);

  return { statusMap, typeMap, categoryMap, accountMap, loading };
}
