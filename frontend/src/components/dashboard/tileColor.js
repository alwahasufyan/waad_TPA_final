// Shared Odoo-style tile color palette, used by both the inline dashboard
// category row and (previously dialog-only) SystemCategoryCard consumers, so
// the same module id always maps to the same tile color everywhere it appears.
const TILE_PALETTE = ['#3B6F91', '#2E7D52', '#6C63A8', '#C58A16', '#147D75', '#B64B43', '#2F7DA1'];

export const colorForKey = (key = '') => {
  let hash = 0;
  for (let i = 0; i < key.length; i += 1) hash = (hash * 31 + key.charCodeAt(i)) >>> 0;
  return TILE_PALETTE[hash % TILE_PALETTE.length];
};

export default colorForKey;
