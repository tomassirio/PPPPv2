// The player's own game summary (GET /api/v1/game/state, kept current by their GAME_STATE events), shared by the
// party box and the Pokédex banners. null while their game has no profile.
import { api } from './api';
import { on } from './events';
import { session } from './session.svelte';

/** Shape of GameSummary (backend model). */
export type Member = { name: string; species: string; level: number; hp: number; maxHp: number; status: string | null; icon: number };
export type GameSummary = {
	game: string;
	counters: { label: string; value: number; max: number }[];
	party: Member[];
	badges: { name: string; place: string; earned: boolean }[];
	collection: { size: number; seen: number[]; owned: number[]; names: (string | null)[] };
	playTime: string | null;
};

export const game = $state<{ summary: GameSummary | null }>({ summary: null });

let watchers = 0;
let offs: (() => void)[] = [];

function refresh() {
	api<GameSummary | undefined>('GET', '/game/state')
		.then((s) => {
			game.summary = s ?? null;
		})
		.catch(() => {});
}

/** Keeps `game.summary` live while at least one component watches; returns the stop function. */
export function watchGame(): () => void {
	if (watchers++ === 0) {
		refresh();
		offs = [
			// Sent to everyone, like the scoreboard: only the player's own belongs in their party box.
			on('GAME_STATE', (e: { user: string; state: GameSummary }) => {
				if (e.user === session.me?.id) game.summary = e.state;
			})
		];
	}
	return () => {
		if (--watchers === 0) {
			offs.forEach((off) => off());
			offs = [];
		}
	};
}

// Holds live state and event subscriptions: hot-swapping it in dev would leave a fresh copy with no listeners
// while the page keeps the old one, so a change here reloads the page instead.
import.meta.hot?.decline();
