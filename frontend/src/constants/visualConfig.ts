// Visual configuration for UI presentation - Backend doesn't need to know about this

export const CATEGORY_VISUALS: Record<string, { emoji: string; color: string }> = {
  // --- Parent categories (codes from DB seed) ---
  'FOOD':   { emoji: '🍽️', color: '#F44336' },
  'PURCH':  { emoji: '🛍️', color: '#673AB7' },
  'ENT':    { emoji: '🎭', color: '#E91E63' },
  'SUBSCR': { emoji: '📺', color: '#9C27B0' },
  'HEALTH': { emoji: '🏥', color: '#4CAF50' },
  'TRANSP': { emoji: '🚗', color: '#2196F3' },
  'DWELL':  { emoji: '🏠', color: '#FF9800' },
  'INSUR':  { emoji: '🛡️', color: '#607D8B' },
  'TAXES':  { emoji: '🧾', color: '#795548' },
  'COMM':   { emoji: '💳', color: '#FF5722' },
  'EDU':    { emoji: '🎓', color: '#3F51B5' },
  'DON':    { emoji: '❤️', color: '#E91E63' },
  'INC':    { emoji: '💵', color: '#4CAF50' },
  'SAV':    { emoji: '📈', color: '#009688' },
  'INT':    { emoji: '🔄', color: '#607D8B' },
  'ADJ':    { emoji: '⚖️', color: '#9E9E9E' },
  'OTH':    { emoji: '📦', color: '#9E9E9E' },

  // --- Food subcategories ---
  'FOOD.SUP':  { emoji: '🛒', color: '#EF9A9A' },
  'FOOD.REST': { emoji: '🍴', color: '#E57373' },
  'FOOD.DEL':  { emoji: '🛵', color: '#EF5350' },
  'FOOD.OTH':  { emoji: '🍱', color: '#F44336' },

  // --- Purchases subcategories ---
  'PURCH.CLOTH': { emoji: '👕', color: '#CE93D8' },
  'PURCH.ELEC':  { emoji: '📱', color: '#BA68C8' },
  'PURCH.HOME':  { emoji: '🛋️', color: '#AB47BC' },
  'PURCH.BOOK':  { emoji: '📚', color: '#9C27B0' },
  'PURCH.GIFT':  { emoji: '🎁', color: '#8E24AA' },
  'PURCH.OTH':   { emoji: '📦', color: '#7B1FA2' },

  // --- Entertainment subcategories ---
  'ENT.MOV':  { emoji: '🎬', color: '#F48FB1' },
  'ENT.CONC': { emoji: '🎵', color: '#F06292' },
  'ENT.MUS':  { emoji: '🎸', color: '#EC407A' },
  'ENT.GAME': { emoji: '🎮', color: '#E91E63' },
  'ENT.OTH':  { emoji: '🎭', color: '#D81B60' },

  'ENT.GAME.PC':     { emoji: '💻', color: '#AD1457' },
  'ENT.GAME.MOB':    { emoji: '📲', color: '#880E4F' },
  'ENT.GAME.SWITCH': { emoji: '🕹️', color: '#C2185B' },
  'ENT.GAME.OTH':    { emoji: '🎮', color: '#D81B60' },

  // --- Subscriptions ---
  'SUBSCR.STREAM': { emoji: '📺', color: '#CE93D8' },
  'SUBSCR.SOFT':   { emoji: '💾', color: '#BA68C8' },
  'SUBSCR.OTH':    { emoji: '🔔', color: '#AB47BC' },

  // --- Health ---
  'HEALTH.MED':   { emoji: '⚕️', color: '#81C784' },
  'HEALTH.PHARM': { emoji: '💊', color: '#66BB6A' },
  'HEALTH.GYM':   { emoji: '🏋️', color: '#4CAF50' },
  'HEALTH.OTH':   { emoji: '🏥', color: '#43A047' },

  // --- Transport ---
  'TRANSP.PUBLIC': { emoji: '🚌', color: '#64B5F6' },
  'TRANSP.TAXI':   { emoji: '🚕', color: '#42A5F5' },
  'TRANSP.FUEL':   { emoji: '⛽', color: '#2196F3' },
  'TRANSP.MAINT':  { emoji: '🔧', color: '#1E88E5' },
  'TRANSP.OTH':    { emoji: '🚗', color: '#1976D2' },

  // --- Dwelling ---
  'DWELL.RENT':  { emoji: '🏠', color: '#FFCC02' },
  'DWELL.UTIL':  { emoji: '💡', color: '#FFB300' },
  'DWELL.MAINT': { emoji: '🔨', color: '#FFA000' },
  'DWELL.OTH':   { emoji: '🏡', color: '#FF8F00' },

  // --- Insurance ---
  'INSUR.LIFE': { emoji: '❤️', color: '#90A4AE' },
  'INSUR.VEH':  { emoji: '🚗', color: '#78909C' },
  'INSUR.HOME': { emoji: '🏠', color: '#607D8B' },
  'INSUR.OTH':  { emoji: '🛡️', color: '#546E7A' },

  // --- Taxes ---
  'TAXES.STATE': { emoji: '🧾', color: '#A1887F' },
  'TAXES.FED':   { emoji: '🏛️', color: '#8D6E63' },
  'TAXES.OTH':   { emoji: '💰', color: '#795548' },

  // --- Commissions ---
  'COMM.BANK':   { emoji: '🏦', color: '#FF8A65' },
  'COMM.INVEST': { emoji: '📊', color: '#FF7043' },
  'COMM.OTH':    { emoji: '💳', color: '#FF5722' },

  // --- Education ---
  'EDU.COURSE': { emoji: '📓', color: '#7986CB' },
  'EDU.BOOK':   { emoji: '📚', color: '#5C6BC0' },
  'EDU.CERT':   { emoji: '🏅', color: '#3F51B5' },
  'EDU.OTH':    { emoji: '🎓', color: '#3949AB' },

  // --- Donations ---
  'DON.CHAR': { emoji: '🤝', color: '#F48FB1' },
  'DON.OTH':  { emoji: '❤️', color: '#E91E63' },

  // --- Income ---
  'INC.SAL':    { emoji: '💼', color: '#A5D6A7' },
  'INC.BON':    { emoji: '🎯', color: '#81C784' },
  'INC.DIV':    { emoji: '📊', color: '#66BB6A' },
  'INC.RENT':   { emoji: '🏠', color: '#4CAF50' },
  'INC.INVEST': { emoji: '📈', color: '#43A047' },
  'INC.SELL':   { emoji: '🏷️', color: '#388E3C' },
  'INC.CASH':   { emoji: '💸', color: '#2E7D32' },
  'INC.OTH':    { emoji: '💵', color: '#1B5E20' },

  // --- Savings / Investments ---
  'SAV.FIXED':  { emoji: '🏦', color: '#80CBC4' },
  'SAV.STOCKS': { emoji: '📈', color: '#4DB6AC' },
  'SAV.BONDS':  { emoji: '📜', color: '#26A69A' },
  'SAV.ETFS':   { emoji: '🗂️', color: '#009688' },
  'SAV.FUNDS':  { emoji: '💰', color: '#00897B' },
  'SAV.CRYPTO': { emoji: '₿',  color: '#00796B' },
  'SAV.OTH':    { emoji: '💎', color: '#00695C' },
  
};

// Local bank logo paths (served from /public/bank-logos/)
export const INSTITUTION_LOGOS: Record<string, string> = {
  'SANTANDER':           '/bank-logos/santanderbank-com-logo.png',
  'BBVA':                '/bank-logos/bbva-es-logo.png',
  'IMAGIN':              '/bank-logos/imagin-com-logo.png',
  'ING':                 '/bank-logos/ing-com-logo.png',
  'INTERACTIVE BROKERS': '/bank-logos/interactivebrokers-com-logo.png',
  'REVOLUT':             '/bank-logos/revolut-com-logo.png',
  'MYINVESTOR':          '/bank-logos/myinvestor-es-logo.png',
  'BINANCE':             '/bank-logos/binance-com-logo.png',
  'PLUXEE':              '/bank-logos/pluxeegroup-com-logo.png',
  'DEFAULT': '🏦',
};

// Merchant logos as emoji (no local image assets for merchants)
export const MERCHANT_LOGOS: Record<string, string> = {
  // Supermarkets
  'MERCADONA': '🟢',
  'CARREFOUR':  '🛒',
  'LIDL':       '🛒',
  'ALDI':       '🛒',
  'DIA':        '🛒',
  // Restaurants / Fast food
  'MCDONALDS':   '🍔',
  'BURGER KING': '🍔',
  'KFC':         '🍗',
  'STARBUCKS':   '☕',
  // Tech & streaming
  'AMAZON':    '📦',
  'APPLE':     '🍎',
  'GOOGLE':    '🔍',
  'MICROSOFT': '🪟',
  'NETFLIX':   '🎬',
  'SPOTIFY':   '🎵',
  // Others from seed
  'OTHERS': '🏪',
  // Default
  'DEFAULT': '🏪',
};

// Helper functions
export function getCategoryVisual(code: string): { emoji: string; color: string } {
  const upperCode = code?.toUpperCase() || '';
  // Try exact match first, then parent prefix (e.g. "FOOD.SUP" → "FOOD")
  return (
    CATEGORY_VISUALS[upperCode] ||
    CATEGORY_VISUALS[upperCode.split('.')[0]] ||
    { emoji: '📦', color: '#9E9E9E' }
  );
}

/**
 * Returns a local image path (starts with '/') or a fallback emoji ('🏦').
 * Shared between AccountsTable and CreateTransactionModal.
 */
export function getInstitutionLogo(name: string): string {
  const upperName = (name || '')
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .toUpperCase()
    .trim();

  const baseUrl = (import.meta.env.BASE_URL || '/').replace(/\/$/, '');
  const asAssetPath = (value: string): string => {
    if (!value.startsWith('/')) return value;
    return `${baseUrl}${value}`;
  };

  const exact = INSTITUTION_LOGOS[upperName];
  if (exact) return asAssetPath(exact);

  if (upperName.includes('SANTANDER')) return asAssetPath(INSTITUTION_LOGOS.SANTANDER);
  if (upperName.includes('BBVA')) return asAssetPath(INSTITUTION_LOGOS.BBVA);
  if (upperName.includes('IMAGIN')) return asAssetPath(INSTITUTION_LOGOS.IMAGIN);
  if (upperName.includes('INTERACTIVE') || upperName.includes('BROKERS')) {
    return asAssetPath(INSTITUTION_LOGOS['INTERACTIVE BROKERS']);
  }
  if (upperName.includes('MYINVESTOR')) return asAssetPath(INSTITUTION_LOGOS.MYINVESTOR);
  if (upperName.includes('REVOLUT')) return asAssetPath(INSTITUTION_LOGOS.REVOLUT);
  if (upperName.includes('BINANCE')) return asAssetPath(INSTITUTION_LOGOS.BINANCE);
  if (upperName.includes('PLUXEE')) return asAssetPath(INSTITUTION_LOGOS.PLUXEE);
  if (upperName === 'ING' || upperName.includes('ING ')) return asAssetPath(INSTITUTION_LOGOS.ING);

  return INSTITUTION_LOGOS.DEFAULT;
}

export function getMerchantLogo(name: string): string {
  const upperName = name?.toUpperCase() || '';
  return MERCHANT_LOGOS[upperName] ?? MERCHANT_LOGOS.DEFAULT;
}
