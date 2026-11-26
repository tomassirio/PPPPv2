// Daily rewards (backend DailyRewards): each day played earns that day's reward from a 90-day calendar, sent as Mystery
// Gifts. GET /me/days; DAY_PLAYED {user, day, reward} when a day counts. The check-in dialog (DailyRewards.svelte)
// opens by itself once per day per browser (localStorage pppp.daily:{user} = the day count it last showed).

import { api } from '#lib/api.ts';

export type Grant = { gift: string; name: string; quantity: number; pokemon: boolean };
export type Tier = 'common' | 'rare' | 'epic' | 'legendary';
/** tier frames it everywhere: days 1-5 of a week common, day 6 rare, day 7 epic, a Pokémon legendary. */
export type Reward = { day: number; title: string | null; tier: Tier; grants: Grant[] };
export type Days = { days: number; today: boolean; calendar: Reward[] };

export const daily = $state<{ data: Days | null; open: boolean; /** today's reward as sent (the stone rolled) */ sent: Reward | null }>({
	data: null,
	open: false,
	sent: null
});

export async function loadDays(): Promise<Days | null> {
	try {
		daily.data = await api<Days>('GET', '/me/days');
	} catch {
		// signed out or an older backend: no calendar
	}
	return daily.data;
}

const key = (user: string) => `pppp.daily:${user}`;

/** Opens the dialog when today counted and this browser has not shown this day yet. */
export function maybeShow(user: string) {
	const d = daily.data;
	if (!d?.today) return;
	try {
		if (localStorage.getItem(key(user)) === String(d.days)) return;
		localStorage.setItem(key(user), String(d.days));
	} catch {
		// no storage: show it anyway
	}
	daily.open = true;
}

/** "5× Poké Ball", "Mew (Lv 5)". */
export const grantText = (g: Grant) => (g.pokemon ? g.name : `${g.quantity}× ${g.name}`);
/** Short, for a calendar cell: "5× Poké Ball", "Celebi". */
export const grantShort = (g: Grant) => (g.pokemon ? g.name.replace(/\s*\(.*\)$/, '') : `${g.quantity}× ${g.name}`);
export const rewardText = (r: Reward) => r.grants.map(grantText).join(' + ');

// Item sprites (Gen 2 has none of its own: these are the later games', PokeAPI's sprites/items, 30x30) by gift id; the
// event Pokémon as the player's ROM draws them (253: the Egg).
const ITEM_ART = import.meta.glob('./assets/items/*.png', { eager: true, import: 'default' }) as Record<string, string>;
const MON_ART: Record<string, number> = { celebi: 251, mew: 151, 'surfing-pikachu': 25, 'odd-egg': 253 };

/** A gift id or its name ("Poké Ball", "Mew (Lv 5)") as an id: "poke-ball", "mew". */
export const giftId = (s: string) =>
	s
		.replace(/\s*\(.*\)$/, '')
		.toLowerCase()
		.normalize('NFD')
		.replace(/[̀-ͯ]/g, '')
		.replace(/\s+/g, '-');

/** The gift's picture, or null (the surprise stone). */
export function giftArt(gift: string): { src: string; mon: boolean } | null {
	const id = giftId(gift);
	const n = MON_ART[id];
	if (n) return { src: `/api/v1/game/icons/${n}.png`, mon: true };
	const src = ITEM_ART[`./assets/items/${id}.png`];
	return src ? { src, mon: false } : null;
}
