import { useCallback, useEffect, useState } from 'react';
import axios from 'axios';
import { WealthDashboard } from './WealthDashboard';
import { WealthHistory } from './WealthHistory';
import type { WealthSnapshotCreateRequest, WealthSnapshotDTO } from '../types/wealth';
import {
  deleteWealthSnapshot,
  fetchWealthSnapshots,
  refreshWealthSnapshot,
  upsertWealthSnapshot,
} from '../services/wealthService';

type WealthSubTab = 'dashboard' | 'history';

const SUBTABS: { id: WealthSubTab; label: string; icon: string }[] = [
  { id: 'dashboard', label: 'Dashboard', icon: '📊' },
  { id: 'history', label: 'Historial', icon: '📋' },
];

type Props = {
  token: string;
  onUnauthorized?: (msg: string) => void;
};

export function WealthPanel({ token, onUnauthorized }: Props) {
  const [subTab, setSubTab] = useState<WealthSubTab>('dashboard');
  const [snapshots, setSnapshots] = useState<WealthSnapshotDTO[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [refreshing, setRefreshing] = useState(false);

  const load = useCallback(async () => {
    try {
      setLoading(true);
      setError(null);
      const data = await fetchWealthSnapshots(token);
      setSnapshots(data);
    } catch (err: unknown) {
      if (axios.isAxiosError(err) && err.response?.status === 401) {
        onUnauthorized?.('Sesión expirada');
      } else {
        setError('Error cargando datos de patrimonio');
      }
    } finally {
      setLoading(false);
    }
  }, [token, onUnauthorized]);

  useEffect(() => { load(); }, [load]);

  const handleRefresh = async () => {
    try {
      setRefreshing(true);
      setError(null);
      await refreshWealthSnapshot(token);
      await load();
    } catch (err: unknown) {
      if (axios.isAxiosError(err) && err.response?.status === 401) {
        onUnauthorized?.('Sesión expirada');
      } else {
        setError('Error actualizando desde banca e inversiones');
      }
    } finally {
      setRefreshing(false);
    }
  };

  const handleDelete = async (id: number) => {
    await deleteWealthSnapshot(token, id);
    await load();
  };

  const handleUpsert = async (payload: WealthSnapshotCreateRequest) => {
    await upsertWealthSnapshot(token, payload);
    await load();
  };

  return (
    <section className="panel" aria-label="Wealth tab">
      <div className="section-header">
        <h2>💎 Wealth</h2>
        <p>Seguimiento de patrimonio neto — evolución y composición de todos tus activos.</p>
      </div>

      <nav className="inv-subtabs">
        {SUBTABS.map((tab) => (
          <button
            key={tab.id}
            type="button"
            className={`inv-subtab${subTab === tab.id ? ' active' : ''}`}
            onClick={() => setSubTab(tab.id)}
          >
            <span className="inv-subtab-icon">{tab.icon}</span>
            {tab.label}
          </button>
        ))}
      </nav>

      {loading && <p className="state">Cargando datos de patrimonio...</p>}
      {!loading && error && <p className="state error">{error}</p>}

      {!loading && !error && subTab === 'dashboard' && (
        <WealthDashboard
          snapshots={snapshots}
          onRefresh={handleRefresh}
          refreshing={refreshing}
        />
      )}

      {!loading && !error && subTab === 'history' && (
        <WealthHistory
          snapshots={snapshots}
          onDelete={handleDelete}
          onUpsert={handleUpsert}
        />
      )}
    </section>
  );
}
