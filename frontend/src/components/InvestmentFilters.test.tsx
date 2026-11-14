import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { act, cleanup, fireEvent, render, renderHook, screen, waitFor, within } from '@testing-library/react';
import { useInvestmentCatalog } from '../hooks/useInvestmentCatalog';
import { useInvestmentsOverview } from '../hooks/useInvestmentsOverview';
import { useInvestmentOperations } from '../hooks/useInvestmentOperations';
import { useInvestmentsDashboard } from '../hooks/useInvestmentsDashboard';
import { fetchInstruments, fetchInvestmentTypes, fetchPlatforms, fetchPortfolioHistory } from '../services/investmentCatalogService';
import { fetchExposureOverview, fetchInvestmentPositions, fetchInvestmentSummary, fetchOperations, fetchTaxSummary } from '../services/investmentOperationsService';
import { CatalogService } from '../services/catalogService';
import { fetchExchangeRates } from '../services/exchangeRatesService';
import { InvestmentCatalogTable } from './InvestmentCatalogTable';
import { InvestmentsOverviewTable } from './InvestmentsOverviewTable';
import { InvestmentOperationsTable } from './InvestmentOperationsTable';
import { InvestmentsDashboard } from './InvestmentsDashboard';
import { ExchangeRatesPanel } from './ExchangeRatesPanel';
import type { ExchangeRateEntry, ExposureOverview, PortfolioHistory } from '../types/investments';

vi.mock('../hooks/useInvestmentCatalog', () => ({ useInvestmentCatalog: vi.fn() }));
vi.mock('../hooks/useInvestmentsOverview', () => ({ useInvestmentsOverview: vi.fn() }));
vi.mock('../hooks/useInvestmentOperations', () => ({ useInvestmentOperations: vi.fn() }));
vi.mock('../hooks/useInvestmentsDashboard', () => ({ useInvestmentsDashboard: vi.fn() }));
vi.mock('../services/investmentCatalogService', () => ({ fetchInstruments: vi.fn(), fetchInvestmentTypes: vi.fn(), fetchPlatforms: vi.fn(), fetchPortfolioHistory: vi.fn() }));
vi.mock('../services/investmentOperationsService', () => ({
  fetchInvestmentPositions: vi.fn(), fetchInvestmentSummary: vi.fn(), fetchOperations: vi.fn(),
  fetchExposureOverview: vi.fn(), fetchTaxSummary: vi.fn(), recalculateAllPositions: vi.fn(),
}));
vi.mock('../services/exchangeRatesService', () => ({ fetchExchangeRates: vi.fn(), deleteExchangeRate: vi.fn(), refreshExchangeRatesForDay: vi.fn(), upsertExchangeRate: vi.fn() }));

const types = [
  { id: 1, code: 'FUND', name: 'Fondo' },
  { id: 2, code: 'ETF', name: 'ETF' },
  { id: 3, code: 'CRYPTO', name: 'Cripto' },
];
const instruments = [
  { id: 1, typeId: 1, code: 'F1', symbol: 'F1', name: 'Fondo Uno', currency: 'EUR' },
  { id: 2, typeId: 2, code: 'E1', symbol: 'E1', name: 'ETF Uno', currency: 'EUR' },
  { id: 3, typeId: 3, code: 'C1', symbol: 'C1', name: 'Cripto Uno', currency: 'EUR' },
];
const groups = instruments.map((instrument, index) => ({
  instrumentId: instrument.id, instrumentSymbol: instrument.symbol, instrumentName: instrument.name,
  typeCode: types[index].code, typeName: types[index].name, currency: 'EUR', instrumentCurrency: 'EUR',
  positions: 1, quantity: 1, investedAmount: (index + 1) * 100, currentValue: (index + 1) * 110,
  pnl: (index + 1) * 10, pnlPct: 10, platforms: ['Platform'],
  countryCode: index === 0 ? 'ES' : 'US', region: '', sector: '', industry: '',
}));
const summary = { totalInvested: 600, totalCurrentValue: 660, totalPnl: 60, totalPnlPct: 10, positions: 3, byType: [] };

beforeEach(() => {
  vi.mocked(useInvestmentCatalog).mockReturnValue({
    types, instruments, platforms: [], countries: [], regions: [], sectors: [], industries: [], marketRegimes: [],
    loading: false, error: null, clearError: vi.fn(), exposuresByInstrument: {},
  } as unknown as ReturnType<typeof useInvestmentCatalog>);
  vi.mocked(useInvestmentsOverview).mockReturnValue({
    summary, positions: [], byInstrument: groups, loading: false, error: null, clearError: vi.fn(), reload: vi.fn(),
  });
  vi.mocked(fetchInstruments).mockResolvedValue(instruments);
  vi.mocked(fetchInvestmentTypes).mockResolvedValue(types);
  vi.mocked(fetchPlatforms).mockResolvedValue([]);
  vi.mocked(fetchExchangeRates).mockResolvedValue([]);
  vi.spyOn(CatalogService, 'fetchCategories').mockResolvedValue([]);
});

afterEach(() => {
  cleanup();
  vi.restoreAllMocks();
  vi.resetAllMocks();
});

describe('investment type filters', () => {
  it('filters catalog from the type counters and combines selections with text search', () => {
    render(<InvestmentCatalogTable token="test" />);
    const table = within(screen.getByRole('table'));
    const filter = within(screen.getByRole('group', { name: 'Tipo de activo' }));
    fireEvent.click(filter.getByRole('button', { name: 'Fondo, 1 activo' }));
    expect(table.getByText('Fondo Uno')).toBeTruthy();
    expect(table.queryByText('ETF Uno')).toBeNull();

    fireEvent.click(filter.getByRole('button', { name: 'ETF, 1 activo' }));
    expect(table.getByText('ETF Uno')).toBeTruthy();
    fireEvent.change(screen.getByPlaceholderText('Buscar por nombre, símbolo, código o tipo…'), { target: { value: 'ETF' } });
    expect(table.queryByText('Fondo Uno')).toBeNull();
    expect(table.getByText('ETF Uno')).toBeTruthy();
    fireEvent.click(screen.getByRole('button', { name: 'Limpiar filtros' }));
    expect(table.getByText('Fondo Uno')).toBeTruthy();
    expect(table.getByText('Cripto Uno')).toBeTruthy();
    expect(filter.getByRole('button', { name: 'Todos' }).getAttribute('aria-pressed')).toBe('true');
  });

  it('filters the positions table and totals, then clears all filters', () => {
    render(<InvestmentsOverviewTable token="test" />);
    const table = within(screen.getByRole('table'));
    const filter = within(screen.getByRole('group', { name: 'Tipo de activo' }));
    fireEvent.click(filter.getByRole('button', { name: 'Fondo' }));
    expect(table.getByText('Fondo Uno')).toBeTruthy();
    expect(table.queryByText('ETF Uno')).toBeNull();
    expect(document.querySelector('.io-kpi--invested')?.textContent).toContain('100,00');
    fireEvent.click(filter.getByRole('button', { name: 'ETF' }));
    expect(table.getByText('ETF Uno')).toBeTruthy();
    expect(document.querySelector('.io-kpi--invested')?.textContent).toContain('300,00');
    fireEvent.change(screen.getByPlaceholderText('Filtrar por símbolo, nombre, tipo o plataforma…'), { target: { value: 'ETF' } });
    expect(table.queryByText('Fondo Uno')).toBeNull();
    expect(document.querySelector('.io-kpi--invested')?.textContent).toContain('200,00');
    fireEvent.click(screen.getByRole('button', { name: 'Limpiar filtros' }));
    expect(table.getByText('Cripto Uno')).toBeTruthy();
    expect(document.querySelector('.io-kpi--invested')?.textContent).toContain('600,00');
  });

  it('allows multiple types in FIFO and resets them together', async () => {
    const positions = instruments.map((instrument, index) => ({
      id: instrument.id, tenantId: 1, typeId: instrument.typeId, typeCode: types[index].code,
      name: instrument.name, instrumentId: instrument.id, currency: 'EUR', investedAmount: 100,
    }));
    const operations = positions.map((position, index) => ({
      id: position.id, investmentId: position.id, tenantId: 1, type: index === 1 ? 'SELL' as const : 'BUY' as const,
      operationDate: index === 1 ? '2025-02-01' : '2025-01-01', quantity: 1, unitPrice: 100, fees: 0, totalAmount: 100,
      currency: 'EUR', eurExchangeRate: 1, totalAmountEur: 100,
    }));
    vi.mocked(useInvestmentOperations).mockReturnValue({
      positions, investments: positions, operations, loading: false, error: null, clearError: vi.fn(), reload: vi.fn(),
      addOperation: vi.fn(), editOperation: vi.fn(), removeOperation: vi.fn(),
    } as unknown as ReturnType<typeof useInvestmentOperations>);
    render(<InvestmentOperationsTable token="test" />);
    const filter = within(await screen.findByRole('group', { name: 'Tipo de activo' }));
    await waitFor(() => expect(filter.getByRole('button', { name: 'Fondo' })).toBeTruthy());
    const table = within(screen.getByRole('table'));
    fireEvent.click(filter.getByRole('button', { name: 'Fondo' }));
    expect(table.getByText('Fondo Uno')).toBeTruthy();
    expect(table.queryByText('ETF Uno')).toBeNull();
    fireEvent.click(filter.getByRole('button', { name: 'ETF' }));
    expect(table.getByText('ETF Uno')).toBeTruthy();
    expect(table.queryByText('Cripto Uno')).toBeNull();
    const operationFilter = within(screen.getByRole('group', { name: 'Operación' }));
    fireEvent.click(operationFilter.getByRole('button', { name: /Venta/ }));
    expect(table.queryByText('Fondo Uno')).toBeNull();
    expect(table.getByText('ETF Uno')).toBeTruthy();
    fireEvent.click(operationFilter.getByRole('button', { name: /Todas/ }));
    const dateFilter = within(screen.getByRole('group', { name: 'Fecha' }));
    fireEvent.click(dateFilter.getByRole('button', { name: /Personalizado/ }));
    fireEvent.change(screen.getByLabelText('Desde'), { target: { value: '2025-02-01' } });
    expect(table.queryByText('Fondo Uno')).toBeNull();
    expect(table.getByText('ETF Uno')).toBeTruthy();
    fireEvent.click(screen.getByRole('button', { name: /Limpiar/ }));
    expect(table.getByText('Cripto Uno')).toBeTruthy();
    expect(dateFilter.getByRole('button', { name: 'Todo' }).getAttribute('aria-pressed')).toBe('true');
  });

  it('passes dashboard type selection to the historical and exposure filter', () => {
    const setSelectedTypeCodes = vi.fn();
    vi.mocked(useInvestmentsDashboard).mockReturnValue({
      summary, taxSummary: null, exposureOverview: null, portfolioHistory: null,
      selectedTypeCodes: [], setSelectedTypeCodes, typeFilters: types,
      selectedYear: 2025, setSelectedYear: vi.fn(), availableYears: [2025],
      instrumentComposition: [], typeComposition: [], realizedSeries: [], realizedAllTime: 0,
      unrealizedCurrent: 0, bestInstrument: null, loading: false, error: null, clearError: vi.fn(),
    } as unknown as ReturnType<typeof useInvestmentsDashboard>);
    render(<InvestmentsDashboard token="test" />);
    const filter = within(screen.getByRole('group', { name: 'Histórico y exposición por tipo' }));
    fireEvent.click(filter.getByRole('button', { name: 'Fondo' }));
    expect(setSelectedTypeCodes).toHaveBeenCalledWith(['FUND']);
    fireEvent.click(filter.getByRole('button', { name: 'Todos' }));
    expect(setSelectedTypeCodes).toHaveBeenCalledWith([]);
  });

  it('resets the exchange-rate filters to their initial defaults', async () => {
    render(<ExchangeRatesPanel token="test" />);
    const origin = screen.getByLabelText('Divisa origen') as HTMLInputElement;
    const destination = screen.getByLabelText('Divisa destino') as HTMLInputElement;
    const from = screen.getByLabelText('Desde') as HTMLInputElement;
    const initialFrom = from.value;
    fireEvent.change(origin, { target: { value: 'GBP' } });
    fireEvent.change(destination, { target: { value: 'JPY' } });
    fireEvent.change(from, { target: { value: '2024-01-01' } });
    fireEvent.click(screen.getByRole('button', { name: 'Limpiar filtros' }));
    expect(origin.value).toBe('EUR');
    expect(destination.value).toBe('USD');
    expect(from.value).toBe(initialFrom);
    await waitFor(() => expect(fetchExchangeRates).toHaveBeenLastCalledWith('test', expect.objectContaining({ fromCurrency: 'EUR', toCurrency: 'USD', from: initialFrom })));
  });

  it('does not replace newer exchange-rate results with an older response', async () => {
    let resolveOlder!: (entries: ExchangeRateEntry[]) => void;
    let resolveNewer!: (entries: ExchangeRateEntry[]) => void;
    vi.mocked(fetchExchangeRates)
      .mockImplementationOnce(() => new Promise(resolve => { resolveOlder = resolve; }))
      .mockImplementationOnce(() => new Promise(resolve => { resolveNewer = resolve; }));
    render(<ExchangeRatesPanel token="test" />);
    fireEvent.change(screen.getByLabelText('Divisa origen'), { target: { value: 'GBP' } });
    await act(async () => resolveNewer([{ id: 2, fromCurrency: 'GBP', toCurrency: 'USD', rate: 1.5, source: 'TEST', asOf: '2025-01-01' }]));
    expect(document.querySelector('.fx-kpi--rate')?.textContent).toContain('1.500000');
    await act(async () => resolveOlder([{ id: 1, fromCurrency: 'EUR', toCurrency: 'USD', rate: 1.1, source: 'TEST', asOf: '2025-01-01' }]));
    expect(document.querySelector('.fx-kpi--rate')?.textContent).toContain('1.500000');
  });

  it('keeps dashboard history and exposure for the latest type selection', async () => {
    const actualHook = await vi.importActual<typeof import('../hooks/useInvestmentsDashboard')>('../hooks/useInvestmentsDashboard');
    let resolveOldExposure!: (value: ExposureOverview) => void;
    let resolveNewExposure!: (value: ExposureOverview) => void;
    let resolveOldHistory!: (value: PortfolioHistory) => void;
    let resolveNewHistory!: (value: PortfolioHistory) => void;
    vi.mocked(fetchInvestmentSummary).mockResolvedValue(summary);
    vi.mocked(fetchInvestmentPositions).mockResolvedValue([]);
    vi.mocked(fetchOperations).mockResolvedValue([]);
    vi.mocked(fetchTaxSummary).mockResolvedValue({ tenantId: 1, year: 2025, totalGainLossEur: 0, byInstrument: [], byCurrency: [] });
    vi.mocked(fetchExposureOverview)
      .mockImplementationOnce(() => new Promise(resolve => { resolveOldExposure = resolve; }))
      .mockImplementationOnce(() => new Promise(resolve => { resolveNewExposure = resolve; }));
    vi.mocked(fetchPortfolioHistory)
      .mockImplementationOnce(() => new Promise(resolve => { resolveOldHistory = resolve; }))
      .mockImplementationOnce(() => new Promise(resolve => { resolveNewHistory = resolve; }));
    const { result } = renderHook(() => actualHook.useInvestmentsDashboard('test'));
    await waitFor(() => expect(result.current.loading).toBe(false));
    act(() => result.current.setSelectedTypeCodes(['FUND']));
    await waitFor(() => expect(fetchPortfolioHistory).toHaveBeenCalledTimes(2));
    const exposure = (codes: string[]): ExposureOverview => ({ totalCurrentValue: 1, appliedTypeCodes: codes, countries: [], regions: [], sectors: [], industries: [], marketRegimes: [] });
    await act(async () => {
      resolveNewExposure(exposure(['FUND']));
      resolveNewHistory({ appliedTypeCodes: ['FUND'], points: [] });
    });
    await act(async () => {
      resolveOldExposure(exposure([]));
      resolveOldHistory({ appliedTypeCodes: [], points: [] });
    });
    expect(result.current.exposureOverview?.appliedTypeCodes).toEqual(['FUND']);
    expect(result.current.portfolioHistory?.appliedTypeCodes).toEqual(['FUND']);
  });
});