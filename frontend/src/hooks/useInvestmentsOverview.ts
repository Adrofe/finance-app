import { useCallback, useEffect, useMemo, useState } from 'react';
import { FINANCE_EVENTS } from '../events/financeEvents';
import axios from 'axios';
import type { InvestmentInstrument, InvestmentPosition, InvestmentSummary } from '../types/investments';
import { fetchInstruments } from '../services/investmentCatalogService';
import { fetchInvestmentPositions, fetchInvestmentSummary } from '../services/investmentOperationsService';

type InstrumentGroup = {
  instrumentId: number;
  instrumentSymbol: string;
  instrumentName: string;
  typeCode: string;
  typeName: string;
  currency: string;
  positions: number;
  quantity: number;
  investedAmount: number;
  currentValue: number;
  pnl: number;
  pnlPct: number;
  platforms: string[];
  countryCode: string;
  region: string;
  sector: string;
  industry: string;
};

const getPositionCurrentValue = (position: InvestmentPosition) =>
  position.currentValueCalculated ?? position.currentValueManual ?? 0;

const sortPositions = (items: InvestmentPosition[]) =>
  [...items].sort((left, right) => left.name.localeCompare(right.name));

export function useInvestmentsOverview(token: string, onUnauthorized?: (message: string) => void) {
  const [summary, setSummary] = useState<InvestmentSummary | null>(null);
  const [positions, setPositions] = useState<InvestmentPosition[]>([]);
  const [instruments, setInstruments] = useState<InvestmentInstrument[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(() => {
    if (!token) {
      setSummary(null);
      setPositions([]);
      setLoading(false);
      return;
    }

    setLoading(true);
    setError(null);

    Promise.all([fetchInvestmentSummary(token), fetchInvestmentPositions(token), fetchInstruments(token)])
      .then(([loadedSummary, loadedPositions, loadedInstruments]) => {
        setSummary(loadedSummary);
        setPositions(sortPositions(loadedPositions.filter((p) => (p.quantity ?? 0) > 0)));
        setInstruments(loadedInstruments);
      })
      .catch((err) => {
        if (axios.isAxiosError(err) && err.response?.status === 401) {
          onUnauthorized?.('Session expired or invalid token. Please login again.');
          return;
        }
        setError(err?.response?.data?.message || err.message || 'Error loading investments overview');
      })
      .finally(() => setLoading(false));
  }, [token, onUnauthorized]);

  useEffect(() => {
    load();
  }, [load]);

  useEffect(() => {
    const handleInvestmentsUpdated = () => {
      load();
    };

    window.addEventListener(FINANCE_EVENTS.INVESTMENTS_UPDATED, handleInvestmentsUpdated);
    return () => {
      window.removeEventListener(FINANCE_EVENTS.INVESTMENTS_UPDATED, handleInvestmentsUpdated);
    };
  }, [load]);

  const clearError = useCallback(() => setError(null), []);

  const byInstrument = useMemo<InstrumentGroup[]>(() => {
    const grouped = new Map<number, InstrumentGroup>();
    const instrumentsById = new Map(instruments.map((instrument) => [instrument.id, instrument]));

    for (const position of positions) {
      const current = getPositionCurrentValue(position);
      const quantity = position.quantity ?? 0;
      const instrumentId = position.instrumentId;
      const instrumentSymbol = position.instrumentSymbol || `#${instrumentId}`;
      const instrumentName = position.instrumentName || position.name;
      const platformName = position.platformName || position.platformCode || 'Sin plataforma';
      const instrumentMeta = instrumentsById.get(instrumentId);

      const currentGroup = grouped.get(instrumentId);
      if (!currentGroup) {
        grouped.set(instrumentId, {
          instrumentId,
          instrumentSymbol,
          instrumentName,
          typeCode: position.typeCode ?? '',
          typeName: position.typeName ?? '',
          currency: position.currency || 'EUR',
          positions: 1,
          quantity,
          investedAmount: position.investedAmount,
          currentValue: current,
          pnl: current - position.investedAmount,
          pnlPct: position.investedAmount > 0 ? ((current - position.investedAmount) / position.investedAmount) * 100 : 0,
          platforms: [platformName],
          countryCode: instrumentMeta?.countryCode ?? instrumentMeta?.countryName ?? '',
          region: instrumentMeta?.regionName ?? instrumentMeta?.regionCode ?? '',
          sector: instrumentMeta?.sectorName ?? instrumentMeta?.sectorCode ?? '',
          industry: instrumentMeta?.industryName ?? instrumentMeta?.industryCode ?? '',
        });
        continue;
      }

      currentGroup.positions += 1;
      currentGroup.quantity += quantity;
      currentGroup.investedAmount += position.investedAmount;
      currentGroup.currentValue += current;
      currentGroup.pnl = currentGroup.currentValue - currentGroup.investedAmount;
      currentGroup.pnlPct = currentGroup.investedAmount > 0 ? (currentGroup.pnl / currentGroup.investedAmount) * 100 : 0;

      if (!currentGroup.platforms.includes(platformName)) {
        currentGroup.platforms.push(platformName);
      }
    }

    return [...grouped.values()].sort((left, right) => right.currentValue - left.currentValue);
  }, [positions, instruments]);

  return {
    summary,
    positions,
    byInstrument,
    loading,
    error,
    clearError,
    reload: load,
  };
}
