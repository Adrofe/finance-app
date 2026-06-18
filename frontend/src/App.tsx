import { useCallback, useEffect, useState } from 'react';

import { FINANCE_EVENTS, addFinanceEventListener } from './events/financeEvents';
import { BankingSubTabs } from './components/BankingSubTabs';
import { BudgetPanel } from './components/BudgetPanel';
import { BankImportPanel } from './components/BankImportPanel';
import { MerchantEditPanel } from './components/MerchantEditPanel';
import { TagsPanel } from './components/TagsPanel';
import { TaxWithholdingReport } from './components/TaxWithholdingReport';
import { InvestmentsSubTabs } from './components/InvestmentsSubTabs';
import { InvestmentCatalogTable } from './components/InvestmentCatalogTable';
import { InvestmentsDashboard } from './components/InvestmentsDashboard';
import { InvestmentsOverviewTable } from './components/InvestmentsOverviewTable';
import { InvestmentOperationsTable } from './components/InvestmentOperationsTable';
import { ExchangeRatesPanel } from './components/ExchangeRatesPanel';
import { LoginForm } from './components/LoginForm';
import { AppHeader } from './components/AppHeader';
import { TransactionsTable } from './components/TransactionsTable';
import { WealthPanel } from './components/WealthPanel';
import { KEYCLOAK_REALM } from './config/env';
import { useAuth } from './hooks/useAuth';
import { useTransactions } from './hooks/useTransactions';
import type { AppTab, BankingSubTab } from './types/banking';
import type { InvestmentsSubTab } from './types/investments';
import { AccountsTable } from './components/AccountsTable';
import { Dashboard } from './components/Dashboard';

function App() {
  const { accessToken, authLoading, authError, login, logout, showSessionWarning, secondsLeft, keepSession } = useAuth();
  const handleUnauthorized = useCallback((message: string) => {
    logout(message);
  }, [logout]);

  const { items, loading, error, refresh } = useTransactions(accessToken, handleUnauthorized);
  const [activeTab, setActiveTab] = useState<AppTab>('banking');
  const [bankingSubTab, setBankingSubTab] = useState<BankingSubTab>('dashboard');
  const [investmentsSubTab, setInvestmentsSubTab] = useState<InvestmentsSubTab>('dashboard');
  const [highlightTransactionId, setHighlightTransactionId] = useState<number | undefined>(undefined);

  useEffect(() => {
    return addFinanceEventListener(FINANCE_EVENTS.NAVIGATE_TO_TRANSACTION, (e) => {
      const id = (e as CustomEvent<{ transactionId: number }>).detail.transactionId;
      setActiveTab('banking');
      setBankingSubTab('transactions');
      setHighlightTransactionId(id);
    });
  }, []);

  if (!accessToken) {
    return <LoginForm loading={authLoading} error={authError} realm={KEYCLOAK_REALM} onSubmit={login} />;
  }

  return (
    <div className="page">
      {showSessionWarning && (
        <div className="modal-backdrop" role="dialog" aria-modal="true">
          <div className="modal" style={{ width: 420 }}>
            <div className="modal-header">
              <h4>Tu sesión caduca pronto</h4>
            </div>
            <div className="modal-body" style={{ display: 'block' }}>
              <p>Tu sesión expira en <strong>{secondsLeft}</strong> segundos.</p>
              <div style={{ display: 'flex', gap: 8, justifyContent: 'flex-end' }}>
                <button className="btn" type="button" onClick={() => logout('Sesión cerrada manualmente')}>Cerrar sesión</button>
                <button className="btn primary" type="button" onClick={() => keepSession()}>Mantener sesión</button>
              </div>
            </div>
          </div>
        </div>
      )}
      <AppHeader activeTab={activeTab} onSelectTab={setActiveTab} onLogout={() => logout('Logged out successfully.')} />

      {activeTab === 'banking' && (
        <section className="panel" aria-label="Banking tab">
          <div className="section-header">
            <h2>Banking</h2>
            <p>Review balance, accounts and transactions in one place.</p>
          </div>

          <BankingSubTabs activeTab={bankingSubTab} onSelectTab={setBankingSubTab} />

          {bankingSubTab === 'dashboard' && (
            <Dashboard
              token={accessToken}
              transactions={items}
              onUnauthorized={handleUnauthorized}
            />
          )}

          {bankingSubTab === 'accounts' && (
            <article className="sheet">
              <h3>Accounts</h3>
              <AccountsTable token={accessToken} />
            </article>
          )}

          {bankingSubTab === 'transactions' && (
            <article className="sheet">
              <div className="sheet-header">
                <h3>Transactions</h3>
                <span>{items.length} total</span>
              </div>
              {loading && items.length === 0 && <p className="state">Loading transactions...</p>}
              {error && <p className="state error">{error}</p>}
              {(items.length > 0 || (!loading && !error)) && <TransactionsTable items={items} accessToken={accessToken} onRefresh={refresh} highlightTransactionId={highlightTransactionId} onClearHighlight={() => setHighlightTransactionId(undefined)} />}
            </article>
          )}

          {bankingSubTab === 'budget' && (
            <BudgetPanel token={accessToken} onUnauthorized={handleUnauthorized} />
          )}

          {bankingSubTab === 'import' && (
            <BankImportPanel token={accessToken} onUnauthorized={handleUnauthorized} />
          )}

          {bankingSubTab === 'merchants' && (
            <MerchantEditPanel token={accessToken} onUnauthorized={handleUnauthorized} />
          )}

          {bankingSubTab === 'tags' && (
            <TagsPanel token={accessToken} onUnauthorized={handleUnauthorized} />
          )}

          {bankingSubTab === 'taxes' && (
            <TaxWithholdingReport
              token={accessToken}
              onUnauthorized={() => handleUnauthorized('Session expired. Please log in again.')}
            />
          )}

        </section>
      )}

      {activeTab === 'investments' && (
        <section className="panel" aria-label="Investments tab">
          <div className="section-header">
            <h2>Investments</h2>
            <p>Track your portfolio, operations and available assets.</p>
          </div>

          <InvestmentsSubTabs activeTab={investmentsSubTab} onSelectTab={setInvestmentsSubTab} />

          {investmentsSubTab === 'dashboard' && (
            <article className="sheet">
              <div className="sheet-header">
                <h3>Portfolio Dashboard</h3>
                <span>Resumen global, fiscal y composicion de cartera</span>
              </div>
              <InvestmentsDashboard token={accessToken} onUnauthorized={handleUnauthorized} />
            </article>
          )}

          {investmentsSubTab === 'investments' && (
            <article className="sheet">
              <div className="sheet-header">
                <h3>Investments</h3>
                <span>Resumen por instrumento y métricas de cartera</span>
              </div>
              <InvestmentsOverviewTable token={accessToken} onUnauthorized={handleUnauthorized} />
            </article>
          )}

          {investmentsSubTab === 'fifo' && (
            <article className="sheet">
              <div className="sheet-header">
                <h3>Operations</h3>
                <span>CRUD de compras y ventas con recalculo FIFO</span>
              </div>
              <InvestmentOperationsTable token={accessToken} onUnauthorized={handleUnauthorized} />
            </article>
          )}

          {investmentsSubTab === 'catalog' && (
            <InvestmentCatalogTable token={accessToken} onUnauthorized={handleUnauthorized} />
          )}

          {investmentsSubTab === 'forex' && (
            <article className="sheet">
              <div className="sheet-header">
                <h3>Exchange Rates</h3>
                <span>Histórico de divisas y mantenimiento manual</span>
              </div>
              <ExchangeRatesPanel token={accessToken} onUnauthorized={handleUnauthorized} />
            </article>
          )}
        </section>
      )}

      {activeTab === 'wealth' && (
        <WealthPanel token={accessToken} onUnauthorized={handleUnauthorized} />
      )}


    </div>
  );
}

export default App;
