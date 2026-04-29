import { useEffect, useState } from 'react';
import type { Account, TransactionCategory, TransactionStatus, TransactionType, Tag } from '../services/catalogService';
import { CatalogService } from '../services/catalogService';

export type TransactionCatalogMaps = {
  statusMap: Record<number, string>;
  typeMap: Record<number, string>;
  categoryMap: Record<number, string>;
  categoryCodeMap: Record<number, string>;
  accountMap: Record<number, string>;
  accountDetailMap: Record<number, Account>;
  tagMap: Record<number, string>;
  // Raw lists for filter dropdowns
  categories: TransactionCategory[];
  statuses: TransactionStatus[];
  types: TransactionType[];
  accounts: Account[];
  tags: Tag[];
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
  // Raw lists
  const [categories, setCategories] = useState<TransactionCategory[]>([]);
  const [statuses, setStatuses] = useState<TransactionStatus[]>([]);
  const [types, setTypes] = useState<TransactionType[]>([]);
  const [accounts, setAccounts] = useState<Account[]>([]);
  const [tags, setTags] = useState<Tag[]>([]);
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
    ]).then(([fetchedStatuses, fetchedTypes, fetchedCategories, fetchedAccounts, fetchedTags]) => {
      setStatuses(fetchedStatuses);
      setTypes(fetchedTypes);
      setCategories(fetchedCategories);
      setAccounts(fetchedAccounts);
      setTags(fetchedTags);
      setStatusMap(Object.fromEntries(fetchedStatuses.map(s => [s.id, s.code?.trim() ? s.code : '-'])));
      setTypeMap(Object.fromEntries(fetchedTypes.map(t => [t.id, t.name?.trim() ? t.name : '-'])));
      setCategoryMap(Object.fromEntries(fetchedCategories.map(c => [c.id, c.name?.trim() ? c.name : '-'])));
      setCategoryCodeMap(Object.fromEntries(fetchedCategories.map(c => [c.id, c.code?.toUpperCase().trim() || ''])));
      setAccountMap(Object.fromEntries(fetchedAccounts.map(a => [a.id, a.name?.trim() ? a.name : '-'])));
      setAccountDetailMap(Object.fromEntries(fetchedAccounts.map(a => [a.id, a])));
      setTagMap(Object.fromEntries(fetchedTags.map(t => [t.id, t.name?.trim() ? t.name : String(t.id)])));
    }).finally(() => setLoading(false));
  }, [token]);

  return { statusMap, typeMap, categoryMap, categoryCodeMap, accountMap, accountDetailMap, tagMap,
           categories, statuses, types, accounts, tags, loading };
}
