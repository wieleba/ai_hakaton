import type { PresenceState } from '../types/presence';

/**
 * Colored presence dot shared between the Contacts sidebar and the room members
 * panel: green ● for online, yellow ◐ for AFK, gray ○ for offline.
 */
export function dotFor(state: PresenceState): {
  symbol: string;
  className: string;
  label: string;
} {
  if (state === 'ONLINE') return { symbol: '●', className: 'text-green-500 mr-1', label: 'online' };
  if (state === 'AFK') return { symbol: '◐', className: 'text-yellow-500 mr-1', label: 'AFK' };
  return { symbol: '○', className: 'text-gray-400 mr-1', label: 'offline' };
}
