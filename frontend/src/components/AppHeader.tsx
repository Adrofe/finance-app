import type { AppTab } from '../types/banking';

const TABS: { id: AppTab; label: string; icon: string; description: string }[] = [
  { id: 'banking', label: 'Banking', icon: '🏦', description: 'Accounts & transactions' },
  { id: 'investments', label: 'Investments', icon: '📊', description: 'Portfolio & operations' },
];

type AppHeaderProps = {
  activeTab: AppTab;
  onSelectTab: (tab: AppTab) => void;
  onLogout: () => void;
};

export function AppHeader({ activeTab, onSelectTab, onLogout }: AppHeaderProps) {
  return (
    <header className="app-header">
      <div className="app-header-brand">
        <span className="app-header-logo">💰</span>
        <div>
          <span className="app-header-title">Finance App</span>
          <span className="app-header-subtitle">Personal finance control center</span>
        </div>
      </div>

      <nav className="app-header-tabs" aria-label="Main sections">
        {TABS.map((tab) => (
          <button
            key={tab.id}
            type="button"
            className={`app-header-tab${activeTab === tab.id ? ' active' : ''}`}
            onClick={() => onSelectTab(tab.id)}
            aria-current={activeTab === tab.id ? 'page' : undefined}
          >
            <span className="app-header-tab-icon">{tab.icon}</span>
            <span className="app-header-tab-text">
              <span className="app-header-tab-label">{tab.label}</span>
              <span className="app-header-tab-desc">{tab.description}</span>
            </span>
          </button>
        ))}
      </nav>

      <button className="app-header-logout" type="button" onClick={onLogout}>
        <span>Logout</span>
        <span className="app-header-logout-icon">→</span>
      </button>
    </header>
  );
}
