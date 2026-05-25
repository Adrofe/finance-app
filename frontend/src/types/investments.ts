export type InvestmentsSubTab = 'dashboard' | 'investments' | 'fifo' | 'catalog' | 'forex';

export type InvestmentOperationType = 'BUY' | 'SELL';

export type InvestmentType = {
  id: number;
  code: string;
  name: string;
};

export type InvestmentInstrument = {
  id: number;
  typeId: number;
  code: string;
  symbol: string;
  name: string;
  market?: string;
  currency: string;
  lastPrice?: number;
  lastPriceSource?: string;
  lastPriceAt?: string;
};

export type InvestmentPlatform = {
  id: number;
  code: string;
  name: string;
};

export type InvestmentPosition = {
  id: number;
  tenantId: number;
  typeId: number;
  typeCode?: string;
  typeName?: string;
  name: string;
  instrumentId: number;
  instrumentSymbol?: string;
  instrumentName?: string;
  platformId?: number;
  platformCode?: string;
  platformName?: string;
  currency: string;
  investedAmount: number;
  currentValueManual?: number;
  currentValueCalculated?: number;
  quantity?: number;
  openedAt?: string;
  notes?: string;
  createdAt?: string;
  updatedAt?: string;
};

export type InvestmentTypeSummary = {
  typeId: number;
  typeCode?: string;
  typeName?: string;
  count: number;
  investedAmount: number;
  currentValue: number;
  pnl: number;
};

export type InvestmentSummary = {
  totalInvested: number;
  totalCurrentValue: number;
  totalPnl: number;
  totalPnlPct: number;
  positions: number;
  byType: InvestmentTypeSummary[];
};

export type OperationFifoLot = {
  buyOperationId: number;
  quantity: number;
  buyUnitPriceEur: number;
  sellUnitPriceEur: number;
  gainLossEur: number;
};

export type InvestmentOperation = {
  id: number;
  investmentId: number;
  tenantId: number;
  type: InvestmentOperationType;
  operationDate: string;
  quantity: number;
  unitPrice: number;
  fees: number;
  totalAmount: number;
  currency: string;
  linkedAccountId?: number;
  linkedTransactionId?: number;
  eurExchangeRate: number;
  totalAmountEur: number;
  notes?: string;
  createdAt?: string;
  fifoLots?: OperationFifoLot[];
};

export type InvestmentOperationDraft = {
  investmentId?: number;
  instrumentId?: number;
  platformId?: number;
  positionName?: string;
  type: InvestmentOperationType;
  operationDate: string;
  quantity: number;
  unitPrice: number;
  fees?: number;
  currency: string;
  linkedAccountId?: number;
  linkedTransactionId?: number;
  notes?: string;
};

export type InvestmentTaxByInstrument = {
  instrumentId: number;
  code?: string;
  symbol?: string;
  name?: string;
  gainLossEur: number;
};

export type InvestmentTaxByCurrency = {
  currency: string;
  gainLossEur: number;
};

export type InvestmentTaxSummary = {
  tenantId: number;
  year: number;
  totalGainLossEur: number;
  byInstrument: InvestmentTaxByInstrument[];
  byCurrency: InvestmentTaxByCurrency[];
};

export type ExchangeRateEntry = {
  id: number;
  fromCurrency: string;
  toCurrency: string;
  rate: number;
  source?: string;
  asOf: string;
};

export type ExchangeRateUpsertDraft = {
  fromCurrency: string;
  toCurrency: string;
  rate: number;
  asOf: string;
  source?: string;
};

export type PriceRefreshResult = {
  updatedInstruments: number;
  recalculatedPositions: number;
  instrumentIds: number[];
  mode: string;
};

export type PriceUpdateDraft = {
  instrumentId: number;
  price: number;
  asOf?: string;
  source?: string;
  currency?: string;
};
