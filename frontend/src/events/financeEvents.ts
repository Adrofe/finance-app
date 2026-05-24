export const FINANCE_EVENTS = {
  TRANSACTIONS_UPDATED: 'finance:transactions-updated',
  INVESTMENTS_UPDATED: 'finance:investments-updated',
  NAVIGATE_TO_TRANSACTION: 'finance:navigate-to-transaction',
} as const;

export type FinanceEventName = (typeof FINANCE_EVENTS)[keyof typeof FINANCE_EVENTS];

export function dispatchFinanceEvent(name: FinanceEventName): void {
  window.dispatchEvent(new CustomEvent(name));
}

export function dispatchNavigateToTransaction(transactionId: number): void {
  window.dispatchEvent(
    new CustomEvent(FINANCE_EVENTS.NAVIGATE_TO_TRANSACTION, { detail: { transactionId } }),
  );
}

export function addFinanceEventListener(
  name: FinanceEventName,
  handler: EventListener,
): () => void {
  window.addEventListener(name, handler);
  return () => window.removeEventListener(name, handler);
}
