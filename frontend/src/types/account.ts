// Account type for the accounts section (matches backend AccountDTO, simplified)

export interface Account {
  id: number;
  name: string;
  iban?: string;
  institutionId?: number;
  institutionName?: string;
  accountTypeId: number;
  accountTypeName?: string;
  currency: string;
  lastBalanceReal?: number;
  lastBalanceAvailable?: number;
  isActive?: boolean | null;
}

export const isAccountActive = (account: { isActive?: boolean | null }) => account.isActive !== false;
