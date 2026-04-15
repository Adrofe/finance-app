import type { BankingSubTab } from '../types/banking';

type BankingSubTabsProps = {
  activeTab: BankingSubTab;
  onSelectTab: (tab: BankingSubTab) => void;
};

export function BankingSubTabs({ activeTab, onSelectTab }: BankingSubTabsProps) {
  return (
    <nav className="subtabs" aria-label="Banking sections">
      <button type="button" className={`subtab ${activeTab === 'dashboard' ? 'active' : ''}`} onClick={() => onSelectTab('dashboard')}>
        Dashboard
      </button>
      <button type="button" className={`subtab ${activeTab === 'accounts' ? 'active' : ''}`} onClick={() => onSelectTab('accounts')}>
        Accounts
      </button>
      <button type="button" className={`subtab ${activeTab === 'transactions' ? 'active' : ''}`} onClick={() => onSelectTab('transactions')}>
        Transactions
      </button>
      <button type="button" className={`subtab ${activeTab === 'tags' ? 'active' : ''}`} onClick={() => onSelectTab('tags')}>
        Tags
      </button>
      <button type="button" className={`subtab ${activeTab === 'budgets' ? 'active' : ''}`} onClick={() => onSelectTab('budgets')}>
        Budgets
      </button>
    </nav>
  );
}
