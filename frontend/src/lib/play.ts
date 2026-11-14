// Pure helpers for the /play page (unit-tested in play.test.ts).

export type Button = 'up' | 'down' | 'left' | 'right' | 'a' | 'b' | 'start' | 'select';

const KEYS: Record<string, Button> = {
	ArrowUp: 'up',
	ArrowDown: 'down',
	ArrowLeft: 'left',
	ArrowRight: 'right',
	z: 'a',
	Z: 'a',
	x: 'b',
	X: 'b',
	Enter: 'start',
	Shift: 'select',
	Backspace: 'select'
};

export type KeyLike = {
	key: string;
	repeat?: boolean;
	ctrlKey?: boolean;
	metaKey?: boolean;
	altKey?: boolean;
	target?: EventTarget | null;
};

/** True for keydowns a page shortcut must ignore: typing in a field, modifier shortcuts, held keys. */
export function notForShortcuts(e: KeyLike): boolean {
	if (e.repeat || e.ctrlKey || e.metaKey || e.altKey) return true;
	const t = e.target as HTMLElement | null;
	const tag = t?.tagName;
	return tag === 'INPUT' || tag === 'TEXTAREA' || tag === 'SELECT' || !!t?.isContentEditable;
}

/** The pad button for a keydown, or null when it should be left alone (typing, shortcuts, held keys). */
export function keyToButton(e: KeyLike): Button | null {
	if (notForShortcuts(e)) return null;
	const tag = (e.target as HTMLElement | null)?.tagName;
	// Enter on a focused button or link already activates it.
	if (e.key === 'Enter' && (tag === 'BUTTON' || tag === 'A')) return null;
	return KEYS[e.key] ?? null;
}

/** Reconnect delay: 0.5 s doubling up to 30 s. */
export const backoff = (attempt: number) => Math.min(30_000, 500 * 2 ** attempt);

/** User ids are e-mails; show the local part. */
export const shortUser = (id: string) => id.split('@')[0];

/**
 * Walking with held arrows: after `released` goes up, what to walk next. `still` = arrows still held, oldest
 * first; `walking` = the direction being walked. Returns 'keep' (releasing an arrow already switched away from),
 * another direction to walk, or null to stop.
 */
export function afterArrowRelease(still: Button[], walking: Button | null, released: Button): Button | 'keep' | null {
	if (walking && walking !== released) return 'keep';
	return still.at(-1) ?? null;
}

/** Shapes from ScoreRow / ScoreEvent (GET /scoreboard, /activity; SCOREBOARD_CHANGED, PROGRESS). */
export type ScoreRow = { userId: string; points: number; badges: number; caught: number; seen: number; game: string | null; online: boolean };
export type ScoreEvent = { user: string; type: string; detail: string; points: number; at: number };

/** "jordan earned the Boulder Badge"; who: another subject ("You"). */
export function activityText(e: ScoreEvent, who = shortUser(e.user)): string {
	const did: Record<string, string> = {
		BADGE: `earned the ${e.detail} Badge`,
		CAUGHT: `caught ${e.detail}`,
		SEEN: `saw ${e.detail}`,
		LEVEL: `reached Lv. ${e.detail}`,
		HALL_OF_FAME: 'entered the Hall of Fame'
	};
	return `${who} ${did[e.type] ?? e.type.toLowerCase()}`;
}

/** How long ago, short: "now", "2 min", "3 h", "5 d". */
export function ago(ms: number): string {
	const min = Math.floor(ms / 60_000);
	return min < 1 ? 'now' : min < 60 ? `${min} min` : min < 1440 ? `${Math.floor(min / 60)} h` : `${Math.floor(min / 1440)} d`;
}
