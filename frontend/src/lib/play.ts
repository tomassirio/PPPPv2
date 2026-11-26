// Pure helpers for the /play page (unit-tested in play.test.ts).

export type Button = 'up' | 'down' | 'left' | 'right' | 'a' | 'b' | 'start' | 'select';

export type ScreenPlayer = { user: string; x: number; y: number };

/** The screen stream lists the local sprite first. One overworld tile spans 16 pixels. */
export function nearbyPlayers([self, ...ghosts]: ScreenPlayer[]): string[] {
	if (!self) return [];
	return ghosts.filter((g) => Math.abs(g.x - self.x) + Math.abs(g.y - self.y) <= 16).map((g) => g.user);
}

/** Which keyboard key presses each pad button: one key per button, as KeyboardEvent.key (letters lower-case). */
/** The pad buttons' keys, and fast: the key that toggles Fast forward (null or absent: none). */
export type Bindings = Record<Button, string> & { fast?: string | null };
/** Everything a key can be bound to: the pad buttons and Fast. */
export type Binding = Button | 'fast';

export const BUTTONS: Button[] = ['up', 'down', 'left', 'right', 'a', 'b', 'start', 'select'];
export const BINDINGS: Binding[] = [...BUTTONS, 'fast'];

/** The two ready-made layouts; players can change any key from there (Settings in the account menu). */
export const PRESETS = {
	arrows: { up: 'ArrowUp', down: 'ArrowDown', left: 'ArrowLeft', right: 'ArrowRight', a: 'z', b: 'x', start: 'Enter', select: 'Shift', fast: ' ' },
	wasd: { up: 'w', down: 's', left: 'a', right: 'd', a: 'k', b: 'j', start: 'Enter', select: 'Shift', fast: ' ' }
} satisfies Record<string, Bindings>;

/** Keys that can't be bound: they move focus or close dialogs. */
export const RESERVED_KEYS = ['Escape', 'Tab'];

/** KeyboardEvent.key as stored: single characters lower-cased, so Shift+W and Caps Lock still match "w". */
export const normKey = (key: string) => (key.length === 1 ? key.toLowerCase() : key);

const KEY_NAMES: Record<string, string> = { ' ': 'Space', ArrowUp: '↑', ArrowDown: '↓', ArrowLeft: '←', ArrowRight: '→' };
/** How a bound key is shown: "W", "↑", "Space", "Enter". */
export const keyName = (key: string | null | undefined) => (!key ? 'None' : (KEY_NAMES[key] ?? (key.length === 1 ? key.toUpperCase() : key)));

/** Binds `key` to `button`. A key belongs to one button only: whichever button had it gets `button`'s old key (a swap). */
export function rebind(bindings: Bindings, button: Binding, key: string): { bindings: Bindings; swapped: Binding | null } {
	const k = normKey(key);
	const other = BINDINGS.find((b) => b !== button && bindings[b] === k) ?? null;
	const next: Bindings = { ...bindings, [button]: k };
	if (other) {
		const old = bindings[button];
		if (old) next[other] = old;
		else if (other === 'fast') next.fast = null; // Fast had that key, the button takes it; Fast is left with none
	}
	return { bindings: next, swapped: other };
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
/** Whether a key press is the player's Fast forward key (same rules as keyToButton). */
export function isFastKey(e: KeyLike, bindings: Bindings): boolean {
	if (!bindings.fast || notForShortcuts(e)) return false;
	const tag = (e.target as HTMLElement | null)?.tagName;
	if ((e.key === 'Enter' || e.key === ' ') && (tag === 'BUTTON' || tag === 'A')) return false;
	return normKey(e.key) === bindings.fast;
}

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
/** score(row) = points (every catch, sighting, badge…) + achievementPoints: no cap, it only grows. trainerScore (0–1000)
 *  is the design's capped rating behind title and grade. */
/** A Pib's points: everything in their activity feed added up, no cap. */
export const score = (r: Pick<ScoreRow, 'points' | 'achievementPoints'>) => (r.points ?? 0) + (r.achievementPoints ?? 0);

export type LinkRecord = { wins: number; losses: number; trades: number; partners: number };

/** True while a player's Cable Club is still closed (no linking until Prof. Elm has the Mystery Egg). */
export const clubClosed = (r: Pick<ScoreRow, 'cableClub'> | undefined) => r?.cableClub !== undefined && r.cableClub >= 0 && r.cableClub < 3;

/** Percentage of link battles won (0–100), or null before the first battle. */
export const winRate = (l: LinkRecord | undefined) => {
	const n = (l?.wins ?? 0) + (l?.losses ?? 0);
	return n ? Math.round(((l?.wins ?? 0) * 100) / n) : null;
};

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
	/** Link-cable record: battles won/lost, Pokémon received in trades, different Pibs linked with. */
	link?: LinkRecord;
	location?: string | null;
	playTime?: string | null;
	/** Bitmasks, bit i = the i-th badge (Zephyr..Rising, Boulder..Earth). */
	johtoBadges?: number;
	kantoBadges?: number;
	/** The name the player typed in the game ("TOMAS"). */
	trainerName?: string | null;
	/** Gold/Silver/Crystal: 0-2 the steps towards the Cable Club opening, 3 open; -1 or absent: another game. */
	cableClub?: number;
};
export type MonStatus = 'PSN' | 'PAR' | 'SLP' | 'BRN' | 'FRZ' | 'FNT';
export type PartyMon = { species: number; level: number; hpPercent?: number; status?: MonStatus | null; shiny?: boolean; egg?: boolean; tier?: Tier | null };
/** A species' Power Rating tier (PowerRating.java): S box legends, M Mew and Celebi, then A (70) down to F (5). */
export type Tier = 'S' | 'M' | 'A' | 'B' | 'C' | 'D' | 'E' | 'F';
export const TIER_NAMES: Record<Tier, string> = { S: 'Legendary', M: 'Mythical', A: 'Elite', B: 'Strong', C: 'Solid', D: 'Fair', E: 'Common', F: 'Weak' };
export type ScoreEvent = { user: string; type: string; detail: string; points: number; at: number };

/**
 * "jordan earned the Boulder Badge"; who: another subject ("You"). name: how to call another Pib (a link battle's
 * opponent, whose user id is the detail; "" when unknown).
 */
export function activityText(e: ScoreEvent, who = shortUser(e.user), name: (user: string) => string = shortUser): string {
	const against = e.detail ? ` against ${name(e.detail)}` : '';
	const did: Record<string, string> = {
		BADGE: `earned the ${e.detail} Badge`,
		CAUGHT: `caught ${e.detail}`,
		SEEN: `saw ${e.detail}`,
		LEVEL: `reached Lv. ${e.detail}`,
		HALL_OF_FAME: 'entered the Hall of Fame',
		ACHIEVEMENT: `unlocked ${e.detail}`,
		TITLE: `is now ${e.detail}`,
		LINK_WIN: e.detail ? `beat ${name(e.detail)} in a link battle` : 'won a link battle',
		LINK_LOSS: `lost a link battle${against}`
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

/** "Ecruteak City · Pokémon Center" -> landmark and spot ("" when there is none); a room alone is the city. */
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

export type BoardSort = 'points' | 'link' | 'winrate' | 'badges' | 'dex' | 'level';
/** Best Pokémon level in a party (eggs don't count). */
export const topLevel = (party: PartyMon[] | undefined) => Math.max(0, ...(party ?? []).filter((m) => !m.egg).map((m) => m.level));
const SORT_KEY: Record<BoardSort, (r: ScoreRow) => number> = {
	points: (r) => score(r),
	// Link battle wins first, then trades (x1000 keeps trades a tie-break).
	link: (r) => (r.link?.wins ?? 0) * 1000 + (r.link?.trades ?? 0),
	// Share of link battles won; more battles break ties; nobody without a battle ranks above someone with one.
	winrate: (r) => {
		const n = (r.link?.wins ?? 0) + (r.link?.losses ?? 0);
		return n ? Math.round(((r.link?.wins ?? 0) * 1e6) / n) * 1000 + Math.min(n, 999) : -1;
	},
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
	/** Each move's type, lower case, in the order of moves (missing from older data). */
	moveTypes?: string[] | null;
	tier?: Tier | null;
};
export type Rarity = 'common' | 'uncommon' | 'rare' | 'epic' | 'legendary';
export const RARITIES: Rarity[] = ['common', 'uncommon', 'rare', 'epic', 'legendary'];
/** A hidden one has name and description null until unlocked. rarity/hidden: newer backend only. */
export type ProfileAchievement = {
	id: string;
	/** Story, Pokédex, Legends, Training, Items, Side, Challenge, Link (newer backends). */
	category?: string;
	name: string | null;
	description: string | null;
	points: number;
	unlocked: boolean;
	progress?: number | null;
	goal?: number | null;
	rarity?: Rarity;
	hidden?: boolean;
};
export type Profile = {
	row: ScoreRow;
	party: ProfileMon[];
	achievements: ProfileAchievement[];
	stats: { caught: number; seen: number; trades?: number | null; linkWins?: number | null; linkLosses?: number | null; partners?: string[]; playTime: string | null };
	recent: ScoreEvent[];
	/** Where they are on the Pokégear town map (x, y in its 160x144 screen), for the location's hover; null unknown. */
	pin?: { region: 'johto' | 'kanto'; x: number; y: number } | null;
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
		stats: { caught: row.caught, seen: row.seen, trades: null, linkWins: null, linkLosses: null, playTime: row.playTime ?? null },
		recent: activity.filter((e) => e.user === row.userId)
	};
}

/** Locked achievement progress as a whole percentage (0-99), or null when there is no count to show. */
export function progressPct(a: Pick<ProfileAchievement, 'unlocked' | 'progress' | 'goal'>): number | null {
	if (a.unlocked || a.progress == null || !a.goal || a.goal <= 1) return null;
	return Math.min(99, Math.floor((a.progress * 100) / a.goal));
}

/**
 * A feed entry's tier, for how loudly it arrives: an achievement by its points (as rarityOf), the rest by what they
 * are worth: sightings (2) common, level milestones (5) uncommon, catches (10) rare, badges epic, the Hall of Fame
 * legendary. A link battle won is rare; lost, and title changes (0 points), common.
 */
export function tierOf(e: Pick<ScoreEvent, 'type' | 'points'> & { detail?: string }): Rarity {
	if (e.type === 'HALL_OF_FAME') return 'legendary';
	if (e.type === 'LINK_WIN') return 'rare';
	// The badge that finishes a region: Clair's Rising Badge is always Johto's last, and Blue only gives the Earth
	// Badge once the other seven Kanto badges are in.
	if (e.type === 'BADGE') return /^(Rising|Earth)$/i.test(e.detail ?? '') ? 'legendary' : 'epic';
	if (e.type === 'TITLE') return gradeRarity(e.detail);
	if (e.type === 'LEVEL' && Number.isFinite(Number(e.detail ?? NaN))) return levelRarity(Number(e.detail));
	if (e.type === 'GIFT') return giftRarity(e.detail);
	return rarityOf(e.points);
}

/** A level milestone's tier: 10-20 common, 30-40 uncommon, 50-60 rare, 70-90 epic, 100 legendary. */
export const levelRarity = (level: number): Rarity =>
	level >= 100 ? 'legendary' : level >= 70 ? 'epic' : level >= 50 ? 'rare' : level >= 30 ? 'uncommon' : 'common';

/** A Mystery Gift's tier from its card line: an event Pokémon ("... joined your party") legendary, Rare Candy and the
 * evolution stones epic, any other item rare. */
export function giftRarity(detail: string | undefined): Rarity {
	if (/joined your party$/.test(detail ?? '')) return 'legendary';
	return /Rare Candy|(Fire|Water|Leaf|Moon|Sun) Stone|Thunderstone/.test(detail ?? '') ? 'epic' : 'rare';
}

/** A new title's tier from its grade, the "(S+)" ending its detail ("Pokémon Master (S+)"): S+ and S legendary, A
 * epic, B rare, C uncommon, D-F common. */
export function gradeRarity(detail: string | undefined): Rarity {
	const grade = /\(([A-FS]\+?)\)\s*$/.exec(detail ?? '')?.[1];
	return grade === 'S+' || grade === 'S' ? 'legendary' : grade === 'A' ? 'epic' : grade === 'B' ? 'rare' : grade === 'C' ? 'uncommon' : 'common';
}

/** An older backend has no rarity: 1-3 points common, 5 uncommon, 10 rare, 25 epic, 50 legendary. */
export const rarityOf = (points: number): Rarity =>
	points <= 3 ? 'common' : points <= 5 ? 'uncommon' : points <= 10 ? 'rare' : points <= 25 ? 'epic' : 'legendary';

export type ShownAchievement = ProfileAchievement & { name: string; description: string; rarity: Rarity; masked: boolean };
/**
 * Achievements as the grid shows them: a locked hidden one (or, without rarity from the backend, a locked legendary)
 * is "???" with the legendary frame; sorted common..legendary, catalogue order within a tier.
 */
export function shownAchievements(list: ProfileAchievement[]): ShownAchievement[] {
	return list
		.map((a) => {
			const rarity = a.rarity ?? rarityOf(a.points);
			const masked = !a.unlocked && (a.hidden ?? (!a.rarity && rarity === 'legendary'));
			return masked
				? { ...a, name: '???', description: '???', rarity: 'legendary' as const, masked, progress: null, goal: null }
				: { ...a, name: a.name ?? '???', description: a.description ?? '', rarity, masked };
		})
		.sort((x, y) => RARITIES.indexOf(x.rarity) - RARITIES.indexOf(y.rarity)); // stable: catalogue order in a tier
}
