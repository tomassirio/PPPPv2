// Pure helpers for the /play page (unit-tested in play.test.ts).

export type Button = 'up' | 'down' | 'left' | 'right' | 'a' | 'b' | 'start' | 'select';

/** Which keyboard key presses each pad button: one key per button, as KeyboardEvent.key (letters lower-case). */
export type Bindings = Record<Button, string>;

export const BUTTONS: Button[] = ['up', 'down', 'left', 'right', 'a', 'b', 'start', 'select'];

/** The two ready-made layouts; players can change any key from there (Settings in the account menu). */
export const PRESETS = {
	arrows: { up: 'ArrowUp', down: 'ArrowDown', left: 'ArrowLeft', right: 'ArrowRight', a: 'z', b: 'x', start: 'Enter', select: 'Shift' },
	wasd: { up: 'w', down: 's', left: 'a', right: 'd', a: 'k', b: 'j', start: 'Enter', select: 'Shift' }
} satisfies Record<string, Bindings>;

/** Keys that can't be bound: they move focus or close dialogs. */
export const RESERVED_KEYS = ['Escape', 'Tab'];

/** KeyboardEvent.key as stored: single characters lower-cased, so Shift+W and Caps Lock still match "w". */
export const normKey = (key: string) => (key.length === 1 ? key.toLowerCase() : key);

const KEY_NAMES: Record<string, string> = { ' ': 'Space', ArrowUp: '↑', ArrowDown: '↓', ArrowLeft: '←', ArrowRight: '→' };
/** How a bound key is shown: "W", "↑", "Space", "Enter". */
export const keyName = (key: string) => KEY_NAMES[key] ?? (key.length === 1 ? key.toUpperCase() : key);

/** Binds `key` to `button`. A key belongs to one button only: whichever button had it gets `button`'s old key (a swap). */
export function rebind(bindings: Bindings, button: Button, key: string): { bindings: Bindings; swapped: Button | null } {
	const k = normKey(key);
	const other = BUTTONS.find((b) => b !== button && bindings[b] === k) ?? null;
	const next = { ...bindings, [button]: k };
	if (other) next[other] = bindings[button];
	return { bindings: next, swapped: other };
}

/** A saved value back to bindings: anything missing or broken falls back to the arrows layout. */
export function parseBindings(raw: string | null): Bindings {
	if (raw === 'wasd') return { ...PRESETS.wasd }; // stored by the old Arrows/WASD toggle
	try {
		const v = JSON.parse(raw ?? '');
		if (BUTTONS.every((b) => typeof v?.[b] === 'string' && v[b])) return Object.fromEntries(BUTTONS.map((b) => [b, normKey(v[b])])) as Bindings;
	} catch {
		/* not JSON: the default */
	}
	return { ...PRESETS.arrows };
}

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
	// Inside an open dialog (Settings rebinding keys) keys are the dialog's.
	return tag === 'INPUT' || tag === 'TEXTAREA' || tag === 'SELECT' || !!t?.isContentEditable || !!t?.closest?.('dialog');
}

/** The pad button for a keydown, or null when it should be left alone (typing, shortcuts, held keys). */
export function keyToButton(e: KeyLike, bindings: Bindings = PRESETS.arrows): Button | null {
	if (notForShortcuts(e)) return null;
	const tag = (e.target as HTMLElement | null)?.tagName;
	// Enter and Space on a focused button or link already activate it.
	if ((e.key === 'Enter' || e.key === ' ') && (tag === 'BUTTON' || tag === 'A')) return null;
	const k = normKey(e.key);
	return BUTTONS.find((b) => bindings[b] === k) ?? null;
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
	/** Party members from their last confirmed reading; species = national Pokédex number. */
	party: PartyMon[];
	// Newer fields (optional: an older backend leaves them out).
	/** "Ecruteak City · Pokémon Center": landmark, then the spot inside it. */
	location?: string | null;
	playTime?: string | null;
	/** Bitmasks, bit i = the i-th badge (Zephyr..Rising, Boulder..Earth). */
	johtoBadges?: number;
	kantoBadges?: number;
};
export type MonStatus = 'PSN' | 'PAR' | 'SLP' | 'BRN' | 'FRZ' | 'FNT';
export type PartyMon = { species: number; level: number; hpPercent?: number; status?: MonStatus | null; shiny?: boolean; egg?: boolean };
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

export const JOHTO = ['Zephyr', 'Hive', 'Plain', 'Fog', 'Storm', 'Mineral', 'Glacier', 'Rising'];
export const KANTO = ['Boulder', 'Cascade', 'Thunder', 'Rainbow', 'Soul', 'Marsh', 'Volcano', 'Earth'];

/** Set bits in a badge mask. */
export const bitCount = (mask: number) => [...(mask >>> 0).toString(2)].filter((b) => b === '1').length;

/** Johto and Kanto badge masks; an older row only has the total, so its badges fill Johto first. */
export function badgeMasks(r: Pick<ScoreRow, 'badges' | 'johtoBadges' | 'kantoBadges'>): { johto: number; kanto: number } {
	return {
		johto: r.johtoBadges ?? (1 << Math.min(8, r.badges)) - 1,
		kanto: r.kantoBadges ?? (1 << Math.max(0, r.badges - 8)) - 1
	};
}

/** "Ecruteak City · Pokémon Center" -> landmark and spot ("" when there is none). */
export function place(location: string | null | undefined): { city: string; spot: string } {
	const [city = '', ...rest] = (location ?? '').split(' · ');
	return { city, spot: rest.join(' · ') };
}

/** HP bar colour by percentage left. */
export const hpColor = (pct: number) => (pct > 50 ? 'var(--hp-ok)' : pct > 20 ? 'var(--hp-mid)' : 'var(--hp-low)');

export type Version = 'Gold' | 'Silver' | 'Crystal';
/** Which of the three versions a cartridge is, from its name ("Pokémon Gold") or id. */
export function versionOf(game: string | null, names: Record<string, string>): Version | null {
	const m = /gold|silver|crystal/i.exec(game ? (names[game] ?? game) : '');
	return m ? ((m[0][0].toUpperCase() + m[0].slice(1).toLowerCase()) as Version) : null;
}

export type BoardSort = 'points' | 'badges' | 'dex' | 'level';
/** Best Pokémon level in a party (eggs don't count). */
export const topLevel = (party: PartyMon[] | undefined) => Math.max(0, ...(party ?? []).filter((m) => !m.egg).map((m) => m.level));
const SORT_KEY: Record<BoardSort, (r: ScoreRow) => number> = {
	points: (r) => r.trainerScore,
	badges: (r) => r.badges,
	dex: (r) => r.caught,
	level: (r) => topLevel(r.party)
};
/** The full scoreboard: one version (or all), best first by the chosen column; ties keep the server's ranking. */
export function boardRows(rows: ScoreRow[], version: Version | 'All', sort: BoardSort, names: Record<string, string>): ScoreRow[] {
	const key = SORT_KEY[sort];
	return rows.filter((r) => version === 'All' || versionOf(r.game, names) === version).sort((a, b) => key(b) - key(a));
}

/** Type colours behind type labels (Gen 2's 17 types). */
export const TYPE_COLORS: Record<string, string> = {
	normal: '#c9c2a6', fire: '#f2945f', water: '#77aef2', grass: '#8fcb6a', electric: '#f4d24f', psychic: '#f293b6',
	ice: '#a3dde2', dragon: '#9a8af0', dark: '#9c8b7c', steel: '#bcc1cf', fighting: '#dc7d66', flying: '#adb7f2',
	poison: '#c193d6', ground: '#dcc07a', rock: '#c7b474', bug: '#b9cb52', ghost: '#9b88c2'
};

/** GET /players/{userId}/profile. */
export type ProfileMon = {
	species: number;
	name: string;
	nickname: string | null;
	level: number;
	hp: number | null;
	maxHp: number | null;
	status: MonStatus | null;
	item: string | null;
	moves: string[];
	shiny: boolean;
	egg: boolean;
	types: string[];
};
export type ProfileAchievement = { id: string; name: string; description: string; points: number; unlocked: boolean; progress?: number | null; goal?: number | null };
export type Profile = {
	row: ScoreRow;
	party: ProfileMon[];
	achievements: ProfileAchievement[];
	stats: { caught: number; seen: number; trades: number | null; linkBattles: number | null; playTime: string | null };
	recent: ScoreEvent[];
};
/** GET /players/{userId}/achievements (Achievement.java): the older backend's catalogue with unlock times. */
export type CatalogueEntry = { id: string; name: string; description: string; points: number; unlockedAt: number | null };

/**
 * A profile pieced together where the backend has no /profile yet: the scoreboard row (party icons, no moves), the
 * achievement catalogue, and the player's lines in the activity feed. name: species name lookup.
 */
export function profileFrom(row: ScoreRow, catalogue: CatalogueEntry[], activity: ScoreEvent[], name: (species: number) => string): Profile {
	return {
		row,
		party: (row.party ?? []).map((m) => ({
			species: m.species,
			name: m.egg ? 'Egg' : name(m.species),
			nickname: null,
			level: m.level,
			hp: null,
			maxHp: null,
			status: m.status ?? null,
			item: null,
			moves: [],
			shiny: !!m.shiny,
			egg: !!m.egg,
			types: []
		})),
		achievements: catalogue.map((a) => ({ id: a.id, name: a.name, description: a.description, points: a.points, unlocked: a.unlockedAt != null })),
		stats: { caught: row.caught, seen: row.seen, trades: null, linkBattles: null, playTime: row.playTime ?? null },
		recent: activity.filter((e) => e.user === row.userId)
	};
}

/** Locked achievement progress as a whole percentage (0-99), or null when there is no count to show. */
export function progressPct(a: Pick<ProfileAchievement, 'unlocked' | 'progress' | 'goal'>): number | null {
	if (a.unlocked || a.progress == null || !a.goal || a.goal <= 1) return null;
	return Math.min(99, Math.floor((a.progress * 100) / a.goal));
}
