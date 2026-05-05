import { useCallback, useEffect, useState } from 'react';
import axios from 'axios';
import type { InvestmentOperation, InvestmentOperationDraft, InvestmentPosition } from '../types/investments';
import {
  createOperation,
  deleteOperation,
  fetchInvestmentPositions,
  fetchOperations,
  updateOperation,
} from '../services/investmentOperationsService';

const sortOperations = (items: InvestmentOperation[]) =>
  [...items].sort((left, right) => {
    const dateCompare = right.operationDate.localeCompare(left.operationDate);
    if (dateCompare !== 0) return dateCompare;
    return right.id - left.id;
  });

const sortPositions = (items: InvestmentPosition[]) =>
  [...items].sort((left, right) => left.name.localeCompare(right.name));

export function useInvestmentOperations(token: string, onUnauthorized?: (message: string) => void) {
  const [operations, setOperations] = useState<InvestmentOperation[]>([]);
  const [investments, setInvestments] = useState<InvestmentPosition[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(() => {
    if (!token) {
      setOperations([]);
      setInvestments([]);
      setLoading(false);
      return;
    }

    setLoading(true);
    setError(null);

    Promise.all([fetchOperations(token), fetchInvestmentPositions(token)])
      .then(([loadedOperations, loadedInvestments]) => {
        setOperations(sortOperations(loadedOperations));
        setInvestments(sortPositions(loadedInvestments));
      })
      .catch((err) => {
        if (axios.isAxiosError(err) && err.response?.status === 401) {
          onUnauthorized?.('Session expired or invalid token. Please login again.');
          return;
        }
        setError(err?.response?.data?.message || err.message || 'Error loading operations');
      })
      .finally(() => setLoading(false));
  }, [token, onUnauthorized]);

  useEffect(() => {
    load();
  }, [load]);

  const clearError = useCallback(() => setError(null), []);

  const addOperation = useCallback(async (payload: InvestmentOperationDraft) => {
    const created = await createOperation(token, payload);
    await load();
    return created;
  }, [token, load]);

  const editOperation = useCallback(async (id: number, payload: InvestmentOperationDraft) => {
    const updated = await updateOperation(token, id, payload);
    await load();
    return updated;
  }, [token, load]);

  const removeOperation = useCallback(async (id: number) => {
    await deleteOperation(token, id);
    await load();
  }, [token, load]);

  return {
    operations,
    investments,
    loading,
    error,
    clearError,
    reload: load,
    addOperation,
    editOperation,
    removeOperation,
  };
}