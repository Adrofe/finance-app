import { useCallback, useEffect, useMemo, useState } from 'react';
import { FINANCE_EVENTS } from '../events/financeEvents';
import axios from 'axios';
import type {
  InvestmentOperation,
  InvestmentPosition,
  InvestmentSummary,
  InvestmentTaxSummary,
  InvestmentTypeSummary,
} from '../types/investments';
import {
  fetchInvestmentPositions,
  fetchInvestmentSummary,
  fetchOperations,
  fetchTaxSummary,
} from '../services/investmentOperationsService';

type InstrumentComposition = {
  instrumentId: number;
  symbol: string;
  name: string;
  currentValue: number;
  investedAmount: number;
  pnl: number;
};

type RealizedSeriesPoint = {
  period: string;
  realized: number;
  cumulativeRealized: number;
};

const currentValueOf = (position: InvestmentPosition) =>
  position.currentValueCalculated ?? position.currentValueManual ?? 0;

const monthKey = (isoDate?: string) => {
  if (!isoDate || isoDate.length < 7) return 'N/A';
  return isoDate.slice(0, 7);
};

const sortPositions = (items: InvestmentPosition[]) =>
  [...items].sort((left, right) => {
    const leftValue = currentValueOf(left);
    const rightValue = currentValueOf(right);
    return rightValue - leftValue;
  });

const sortTypeSummary = (items: InvestmentTypeSummary[]) =>
  [...items].sort((left, right) => right.currentValue - left.currentValue);

const operationRealizedGain = (operation: InvestmentOperation) =>
  (operation.fifoLots ?? []).reduce((sum, lot) => sum + (lot.gainLossEur ?? 0), 0);

export function useInvestmentsDashboard(token: string, onUnauthorized?: (message: string) => void) {
  const [summary, setSummary] = useState<InvestmentSummary | null>(null);
  const [positions, setPositions] = useState<InvestmentPosition[]>([]);
  const [operations, setOperations] = useState<InvestmentOperation[]>([]);
  const [taxSummary, setTaxSummary] = useState<InvestmentTaxSummary | null>(null);
  const [selectedYear, setSelectedYear] = useState(new Date().getFullYear());
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const loadCore = useCallback(async () => {
    if (!token) {
      setSummary(null);
      setPositions([]);
      setOperations([]);
      setLoading(false);
      return;
    }

    setLoading(true);
    setError(null);

    try {
      const [loadedSummary, loadedPositions, loadedOperations] = await Promise.all([
        fetchInvestmentSummary(token),
        fetchInvestmentPositions(token),
        fetchOperations(token),
      ]);

      setSummary({
        ...loadedSummary,
        byType: sortTypeSummary(loadedSummary.byType ?? []),
      });
      setPositions(sortPositions(loadedPositions));
      setOperations(loadedOperations);
    } catch (err) {
      if (axios.isAxiosError(err) && err.response?.status === 401) {
        onUnauthorized?.('Session expired or invalid token. Please login again.');
        return;
      }
      setError((err as { message?: string; response?: { data?: { message?: string } } })?.response?.data?.message
        || (err as { message?: string })?.message
        || 'Error loading investments dashboard');
    } finally {
      setLoading(false);
    }
  }, [token, onUnauthorized]);

  const loadTaxSummary = useCallback(async () => {
    if (!token) {
      setTaxSummary(null);
      return;
    }

    try {
      const loadedTaxSummary = await fetchTaxSummary(token, selectedYear);
      setTaxSummary(loadedTaxSummary);
    } catch (err) {
      if (axios.isAxiosError(err) && err.response?.status === 401) {
        onUnauthorized?.('Session expired or invalid token. Please login again.');
        return;
      }
      setTaxSummary(null);
      setError((err as { message?: string; response?: { data?: { message?: string } } })?.response?.data?.message
        || (err as { message?: string })?.message
        || 'Error loading tax summary');
    }
  }, [token, selectedYear, onUnauthorized]);

  useEffect(() => {
    loadCore();
  }, [loadCore]);

  useEffect(() => {
    loadTaxSummary();
  }, [loadTaxSummary]);

  useEffect(() => {
    const handleInvestmentsUpdated = () => {
      loadCore();
      loadTaxSummary();
    };

    window.addEventListener(FINANCE_EVENTS.INVESTMENTS_UPDATED, handleInvestmentsUpdated);
    return () => {
      window.removeEventListener(FINANCE_EVENTS.INVESTMENTS_UPDATED, handleInvestmentsUpdated);
    };
  }, [loadCore, loadTaxSummary]);

  const clearError = useCallback(() => setError(null), []);

  const availableYears = useMemo(() => {
    const years = new Set<number>();
    const current = new Date().getFullYear();
    years.add(current);

    operations.forEach((operation) => {
      if (operation.operationDate && operation.operationDate.length >= 4) {
        years.add(Number(operation.operationDate.slice(0, 4)));
      }
    });

    const sorted = [...years].sort((left, right) => right - left);
    if (sorted.length >= 8) return sorted;

    const expanded = [...sorted];
    let year = current - 1;
    while (expanded.length < 8) {
      if (!expanded.includes(year)) expanded.push(year);
      year -= 1;
    }
    return expanded.sort((left, right) => right - left);
  }, [operations]);

  const instrumentComposition = useMemo<InstrumentComposition[]>(() => {
    const grouped = new Map<number, InstrumentComposition>();

    positions.forEach((position) => {
      const currentValue = currentValueOf(position);
      const investedAmount = position.investedAmount ?? 0;
      const existing = grouped.get(position.instrumentId);

      if (!existing) {
        grouped.set(position.instrumentId, {
          instrumentId: position.instrumentId,
          symbol: position.instrumentSymbol || `#${position.instrumentId}`,
          name: position.instrumentName || position.name,
          currentValue,
          investedAmount,
          pnl: currentValue - investedAmount,
        });
        return;
      }

      existing.currentValue += currentValue;
      existing.investedAmount += investedAmount;
      existing.pnl = existing.currentValue - existing.investedAmount;
    });

    return [...grouped.values()].sort((left, right) => right.currentValue - left.currentValue);
  }, [positions]);

  const typeComposition = useMemo(() => {
    const totalCurrent = summary?.totalCurrentValue ?? 0;
    const items = summary?.byType ?? [];

    return items.map((item) => ({
      label: item.typeName || item.typeCode || `Type ${item.typeId}`,
      currentValue: item.currentValue,
      investedAmount: item.investedAmount,
      pnl: item.pnl,
      sharePct: totalCurrent > 0 ? (item.currentValue / totalCurrent) * 100 : 0,
    }));
  }, [summary]);

  const realizedSeries = useMemo<RealizedSeriesPoint[]>(() => {
    const byMonth = new Map<string, number>();

    operations.forEach((operation) => {
      if (operation.type !== 'SELL') return;
      const gain = operationRealizedGain(operation);
      const key = monthKey(operation.operationDate);
      byMonth.set(key, (byMonth.get(key) ?? 0) + gain);
    });

    const sortedMonths = [...byMonth.entries()].sort((left, right) => left[0].localeCompare(right[0]));
    let cumulative = 0;

    return sortedMonths.map(([period, realized]) => {
      cumulative += realized;
      return {
        period,
        realized,
        cumulativeRealized: cumulative,
      };
    });
  }, [operations]);

  const realizedAllTime = useMemo(() => {
    return operations.reduce((sum, operation) => {
      if (operation.type !== 'SELL') return sum;
      return sum + operationRealizedGain(operation);
    }, 0);
  }, [operations]);

  const unrealizedCurrent = summary?.totalPnl ?? 0;
  const bestInstrument = instrumentComposition[0] ?? null;

  return {
    summary,
    positions,
    operations,
    taxSummary,
    selectedYear,
    setSelectedYear,
    availableYears,
    instrumentComposition,
    typeComposition,
    realizedSeries,
    realizedAllTime,
    unrealizedCurrent,
    bestInstrument,
    loading,
    error,
    clearError,
    reload: loadCore,
  };
}
