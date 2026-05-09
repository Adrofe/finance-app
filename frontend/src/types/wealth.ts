export type WealthItemType = 'CASH' | 'FUND' | 'ETF' | 'CRYPTO' | 'STOCK' | 'BOND' | 'REAL_ESTATE' | 'OTHER';

export interface WealthSnapshotItemDTO {
  id: number;
  type: WealthItemType;
  subtype?: string;
  source?: string;
  sourceRef?: string;
  label: string;
  quantity?: number;
  unitPrice?: number;
  value: number;
  currency: string;
}

export interface WealthSnapshotDTO {
  id: number;
  snapshotDate: string;
  snapshotAt: string;
  currency: string;
  totalValue: number;
  cashValue: number;
  fundsValue: number;
  etfsValue: number;
  cryptoValue: number;
  stocksValue: number;
  bondsValue: number;
  realEstateValue: number;
  otherValue: number;
  notes?: string;
  items?: WealthSnapshotItemDTO[];
}

export interface WealthSnapshotCreateRequest {
  snapshotDate: string;
  currency: string;
  notes?: string;
  items: WealthSnapshotItemInput[];
}

export interface WealthSnapshotItemInput {
  type: WealthItemType;
  subtype?: string;
  label: string;
  quantity?: number;
  unitPrice?: number;
  value: number;
  currency?: string;
  source?: string;
  sourceRef?: string;
}
