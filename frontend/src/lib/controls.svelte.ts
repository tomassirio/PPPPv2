// Phones, the Play panel: the controller right under the Game Boy screen (default) or at the bottom edge, where thumbs
// rest (the triangle in the controller's corner), and the strips that go with it (PlayStrips): up to three, in the
// player's order (Settings). Remembered per browser.
const LOW = 'pppp.controlsLow';
const STRIPS = 'pppp.strips';

export const STRIP_IDS = ['location', 'party', 'latest', 'badges', 'dex', 'rank'] as const;
export type StripId = (typeof STRIP_IDS)[number];
export const STRIP_NAMES: Record<StripId, string> = {
	location: 'Location',
	party: 'Party',
	latest: 'Latest activity',
	badges: 'Badges',
	dex: 'Pokédex',
	rank: 'Rank'
};
export const MAX_STRIPS = 3;
const DEFAULT: StripId[] = ['location', 'party', 'latest'];

function read(key: string) {
	try {
		return localStorage.getItem(key);
	} catch {
		return null;
	}
}

function write(key: string, value: string) {
	try {
		localStorage.setItem(key, value);
	} catch {
		/* private mode: not remembered */
	}
}

function savedStrips(): StripId[] {
	try {
		const list = JSON.parse(read(STRIPS) ?? 'null');
		if (Array.isArray(list)) return list.filter((s): s is StripId => STRIP_IDS.includes(s)).slice(0, MAX_STRIPS);
	} catch {
		/* not JSON: the default */
	}
	return DEFAULT;
}

/**
 * room: the height (px) left for strips under the controller on this screen, measured by /play's Play panel (null
 * until it has been shown). It decides how many can be picked: about one per STRIP_SPACE, at most MAX_STRIPS.
 */
export const controls = $state({ low: read(LOW) === 'on', strips: savedStrips(), room: null as number | null });

/** A strip's height (PlayStrips: every strip is 48px) plus the gap above it. */
const STRIP_SPACE = 56;
export function stripsThatFit(): number {
	if (controls.room === null) return MAX_STRIPS;
	return Math.max(0, Math.min(MAX_STRIPS, Math.floor((controls.room + 8) / STRIP_SPACE)));
}

export function setControlsLow(low: boolean) {
	controls.low = low;
	write(LOW, low ? 'on' : 'off');
}

export function setStrips(strips: StripId[]) {
	controls.strips = strips.slice(0, MAX_STRIPS);
	write(STRIPS, JSON.stringify(controls.strips));
}
