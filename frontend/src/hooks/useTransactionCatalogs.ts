import { useEffect, useState } from 'react';
import type { Account } from '../services/catalogService';
import { CatalogService } from '../services/catalogService';

export type TransactionCatalogMaps = {
  statusMap: Record<number, string>;
  typeMap: Record<number, string>;
  categoryMap: Record<number, string>;
  categoryCodeMap: Record<number, string>;
  accountMap: Record<number, string>;
  accountDetailMap: Record<number, Account>;
  tagMap: Record<number, string>;
  loading: boolean;
};

export function useTransactionCatalogs(token: string): TransactionCatalogMaps {
  const [statusMap, setStatusMap] = useState<Record<number, string>>({});
  const [typeMap, setTypeMap] = useState<Record<number, string>>({});
  const [categoryMap, setCategoryMap] = useState<Record<number, string>>({});
  const [categoryCodeMap, setCategoryCodeMap] = useState<Record<number, string>>({});
  const [accountMap, setAccountMap] = useState<Record<number, string>>({});
  const [accountDetailMap, setAccountDetailMap] = useState<Record<number, Account>>({});
  const [tagMap, setTagMap] = useState<Record<number, string>>({});
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    if (!token) return;
    setLoading(true);
    Promise.all([
      CatalogService.fetchStatuses(token),
      CatalogService.fetchTypes(token),
      CatalogService.fetchCategories(token),
      CatalogService.fetchAccounts(token),
      CatalogService.fetchTags(token),
    ]).then(([statuses, types, categories, accounts, tags]) => {
      setStatusMap(Object.fromEntries(statuses.map(s => [s.id, s.code?.trim() ? s.code : '-'])));
      setTypeMap(Object.fromEntries(types.map(t => [t.id, t.name?.trim() ? t.name : '-'])));
      setCategoryMap(Object.fromEntries(categories.map(c => [c.id, c.name?.trim() ? c.name : '-'])));
      setCategoryCodeMap(Object.fromEntries(categories.map(c => [c.id, c.code?.toUpperCase().trim() || ''])));
      setAccountMap(Object.fromEntries(accounts.map(a => [a.id, a.name?.trim() ? a.name : '-'])));
      setAccountDetailMap(Object.fromEntries(accounts.map(a => [a.id, a])));
      setTagMap(Object.fromEntries(tags.map(t => [t.id, t.name?.trim() ? t.name : String(t.id)])));
    }).finally(() => setLoading(false));
  }, [token]);

  return { statusMap, typeMap, categoryMap, categoryCodeMap, accountMap, accountDetailMap, tagMap, loading };
}
