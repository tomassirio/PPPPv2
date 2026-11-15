// Pure helpers for the /play page (unit-tested in play.test.ts).

export type Button = 'up' | 'down' | 'left' | 'right' | 'a' | 'b' | 'start' | 'select';

/** arrows: arrow keys, Z = A, X = B. wasd: W A S D, K = A, J = B (the arrow keys still move too). */
export type KeyScheme = 'arrows' | 'wasd';

const COMMON: Record<string, Button> = {
	ArrowUp: 'up',
	ArrowDown: 'down',
	ArrowLeft: 'left',
	ArrowRight: 'right',
	Enter: 'start',
	Shift: 'select',
	Backspace: 'select'
};
const KEYS: Record<KeyScheme, Record<string, Button>> = {
	arrows: { ...COMMON, z: 'a', Z: 'a', x: 'b', X: 'b' },
	wasd: { ...COMMON, w: 'up', W: 'up', a: 'left', A: 'left', s: 'down', S: 'down', d: 'right', D: 'right', k: 'a', K: 'a', j: 'b', J: 'b' }
};

/** The keys shown on the pad's buttons (aria-keyshortcuts) and in the keyboard card. */
export const KEY_LABELS: Record<KeyScheme, Record<Button, string>> = {
	arrows: { up: 'ArrowUp', down: 'ArrowDown', left: 'ArrowLeft', right: 'ArrowRight', a: 'Z', b: 'X', start: 'Enter', select: 'Shift' },
	wasd: { up: 'W', down: 'S', left: 'A', right: 'D', a: 'K', b: 'J', start: 'Enter', select: 'Shift' }
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
export function keyToButton(e: KeyLike, scheme: KeyScheme = 'arrows'): Button | null {
	if (notForShortcuts(e)) return null;
	const tag = (e.target as HTMLElement | null)?.tagName;
	// Enter on a focused button or link already activates it.
	if (e.key === 'Enter' && (tag === 'BUTTON' || tag === 'A')) return null;
	return KEYS[scheme][e.key] ?? null;
}

/** Reconnect delay: 0.5 s doubling up to 30 s. */
export const backoff = (attempt: number) => Math.min(30_000, 500 * 2 ** attempt);

/** User ids are e-mails; show the local part. */
export const shortUser = (id: string) => id.split('@')[0];

/** Shapes from ScoreRow / ScoreEvent (GET /scoreboard, /activity; SCOREBOARD_CHANGED, PROGRESS). */
/** Ranked by trainerScore (0–1000), achievementPoints breaking ties; points is the older per-step tally. */
export type ScoreRow = {
	userId: string;
	points: number;
	badges: number;
	caught: number;
	seen: number;
	game: string | null;
	online: boolean;
	trainerScore: number;
	title: string;
	grade: string;
	achievementPoints: number;
	achievementsUnlocked: number;
	/** Hatched party members from their last confirmed reading; species = national Pokédex number. */
	party: { species: number; level: number }[];
};
export type ScoreEvent = { user: string; type: string; detail: string; points: number; at: number };

/** "jordan earned the Boulder Badge"; who: another subject ("You"). */
export function activityText(e: ScoreEvent, who = shortUser(e.user)): string {
	const did: Record<string, string> = {
		BADGE: `earned the ${e.detail} Badge`,
		CAUGHT: `caught ${e.detail}`,
		SEEN: `saw ${e.detail}`,
		LEVEL: `reached Lv. ${e.detail}`,
		HALL_OF_FAME: 'entered the Hall of Fame',
		ACHIEVEMENT: `unlocked ${e.detail}`,
		TITLE: `is now ${e.detail}`
	};
	return `${who} ${did[e.type] ?? e.type.toLowerCase()}`;
}

/** How long ago, short: "now", "2 min", "3 h", "5 d". */
export function ago(ms: number): string {
	const min = Math.floor(ms / 60_000);
	return min < 1 ? 'now' : min < 60 ? `${min} min` : min < 1440 ? `${Math.floor(min / 60)} h` : `${Math.floor(min / 1440)} d`;
}

export type LinkKind = 'TRADE' | 'BATTLE';
export type Status = { kind: 'linked' | 'trade' | 'battle' | 'playing' | 'offline'; label: string };

/**
 * A scoreboard row's status line. links: user -> peer for the link sessions known in this browser; lobby: who is
 * ready (GET /link, LOBBY_CHANGED); online: their console is running.
 */
export function playerStatus(
	user: string,
	online: boolean,
	me: string | undefined,
	lobby: { userId: string; kind: LinkKind }[],
	links: Record<string, string>
): Status {
	const peer = links[user];
	if (peer) return { kind: 'linked', label: `Linked with ${peer === me ? 'you' : shortUser(peer)}` };
	const ready = lobby.find((p) => p.userId === user)?.kind;
	if (ready) return ready === 'TRADE' ? { kind: 'trade', label: 'Ready to trade' } : { kind: 'battle', label: 'Ready to battle' };
	return online ? { kind: 'playing', label: 'Playing' } : { kind: 'offline', label: 'Offline' };
}

/** A stable hue (0-359) for a user id: the colour behind their initials. */
export const hue = (id: string) => [...id].reduce((h, c) => (h * 31 + c.charCodeAt(0)) >>> 0, 7) % 360;

/** "1st", "2nd", "3rd", "11th". */
export function ordinal(n: number): string {
	const t = n % 100;
	return n + (t >= 11 && t <= 13 ? 'th' : ['th', 'st', 'nd', 'rd'][n % 10] ?? 'th');
}
