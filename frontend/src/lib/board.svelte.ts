// Scoreboard rows and the activity feed (GET /api/v1/scoreboard, /activity), kept current by SCOREBOARD_CHANGED (the
// new rows) and PROGRESS (one event). Shared by the scoreboard, the activity feed and the "Your run" card.
import { api } from './api';
import { on, onOpen } from './events';
import type { ScoreEvent, ScoreRow } from './play';

const FEED = 30;

/** names: cartridge id -> "Pokémon Gold", filled by /play from GET /me/game. */
export const board = $state<{ rows: ScoreRow[]; activity: ScoreEvent[]; names: Record<string, string> }>({
	rows: [],
	activity: [],
	names: {}
});

function load() {
	api<ScoreRow[]>('GET', '/scoreboard')
		.then((r) => (board.rows = r))
		.catch(() => {});
	api<ScoreEvent[]>('GET', `/activity?limit=${FEED}`)
		.then((a) => (board.activity = a))
		.catch(() => {});
}

let watchers = 0;
let offs: (() => void)[] = [];

/** Keeps `board` live while at least one component watches; returns the stop function. */
export function watchBoard(): () => void {
	if (watchers++ === 0) {
		offs = [
			on('SCOREBOARD_CHANGED', (r: ScoreRow[]) => (board.rows = r)),
			on('PROGRESS', (e: ScoreEvent) => (board.activity = [e, ...board.activity].slice(0, FEED))),
			onOpen(load) // events sent while the stream was down are gone: fetch again
		];
		load();
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
