import type { DashboardPreset } from '../types/dashboard';

export const PRESET_LABELS: Record<DashboardPreset, string> = {
  'this-month': 'Este mes',
  'last-month': 'Mes anterior',
  '3-months': 'Últimos 3m',
  '6-months': 'Últimos 6m',
  'this-year': 'Este año',
  'last-year': 'Año pasado',
  'custom': 'Personalizado',
};

export const PRESETS: DashboardPreset[] = [
  'this-month', 'last-month', '3-months', '6-months', 'this-year', 'last-year', 'custom',
];

function toISO(date: Date): string {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
}

export function getPresetRange(preset: DashboardPreset, today = new Date()): { start: string; end: string } {
  const year = today.getFullYear();
  const month = today.getMonth();
  switch (preset) {
    case 'this-month': return { start: toISO(new Date(year, month, 1)), end: toISO(today) };
    case 'last-month': return { start: toISO(new Date(year, month - 1, 1)), end: toISO(new Date(year, month, 0)) };
    case '3-months': return { start: toISO(new Date(year, month - 2, 1)), end: toISO(today) };
    case '6-months': return { start: toISO(new Date(year, month - 5, 1)), end: toISO(today) };
    case 'this-year': return { start: toISO(new Date(year, 0, 1)), end: toISO(today) };
    case 'last-year': return { start: toISO(new Date(year - 1, 0, 1)), end: toISO(new Date(year - 1, 11, 31)) };
    default: return { start: '', end: '' };
  }
}