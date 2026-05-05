export type InvestmentsSubTab = 'dashboard' | 'investments' | 'fifo' | 'catalog';

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

export type PriceRefreshResult = {
  updatedInstruments: number;
  recalculatedPositions: number;
  instrumentIds: number[];
  mode: string;
};
