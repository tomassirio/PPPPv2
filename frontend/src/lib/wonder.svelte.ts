// Wonder Trade's pool as the player sees it (backend WonderTrade, GET /wonder-trade): other Pibs' Pokémon waiting,
// theirs waiting, whether today's trade is used, and what is on its way to their party. Fetched again on WONDER_POOL,
// WONDER_TRADE and WONDER_ARRIVED.

import { api } from '#lib/api.ts';
import { on } from '#lib/events.ts';

export type Waiting = { species: number; name: string; nickname: string | null; level: number; shiny: boolean; since: number };
/** open: their Cable Club is (the server's own check; the UI follows the Link cable card's clubClosed). */
export type Pool = { waiting: number; mine: Waiting[]; tradedToday: boolean; incoming: number; open: boolean };

export const wonder = $state<{ pool: Pool | null }>({ pool: null });

export const loadPool = () =>
	api<Pool>('GET', '/wonder-trade').then(
		(p) => (wonder.pool = p),
		() => {}
	);

/** Keeps wonder.pool fresh while mounted; returns the cleanup. */
export function watchPool() {
	loadPool();
	const offs = ['WONDER_POOL', 'WONDER_TRADE', 'WONDER_ARRIVED'].map((t) => on(t, () => loadPool()));
	return () => offs.forEach((off) => off());
}

/** How long until the next Wonder Trade (the day turns at midnight UTC): "5 h 12 min", "12 min". */
export function untilTomorrow(now = Date.now()): string {
	const d = new Date(now);
	const left = Date.UTC(d.getUTCFullYear(), d.getUTCMonth(), d.getUTCDate() + 1) - now;
	const h = Math.floor(left / 3_600_000), m = Math.max(1, Math.ceil((left % 3_600_000) / 60_000));
	return h ? `${h} h ${m} min` : `${m} min`;
}
