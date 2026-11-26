// Controller shells a player can pick for the pad (remembered per browser). Original = the classic Game Boy;
// the rest approximate Game Boy Color shells. Translucent "clear" shells use alpha. Button colours are hex.

export type Shell = {
	id: string;
	name: string;
	shell: string;
	dpad: string;
	ab: string;
	pill: string;
	/** A hand-tuned see-through ("atomic") plastic for the picker's Atomic switch; others get their colour at 60%. */
	atomic?: string;
};

export const SHELLS: Shell[] = [
	{ id: 'original', name: 'Original', shell: '#d8d3c4', dpad: '#2b2d42', ab: '#bc4b51', pill: '#8d8a80' },
	{ id: 'berry', name: 'Berry', shell: '#d9324f', atomic: 'rgb(217 50 79 / 0.62)', dpad: '#2a2a2e', ab: '#8e1c33', pill: '#7a1a2d' },
	{ id: 'black', name: 'Black', shell: '#2e2e32', atomic: 'rgb(46 46 52 / 0.7)', dpad: '#141416', ab: '#141416', pill: '#1c1c1f' },
	{ id: 'clear-blue', name: 'Clear Blue', shell: 'rgb(40 140 225 / 0.65)', dpad: '#163f6b', ab: '#1d5f9c', pill: '#1d5f9c' },
	{ id: 'clear-orange', name: 'Clear Orange', shell: 'rgb(255 105 30 / 0.72)', dpad: '#7a2a08', ab: '#c2410c', pill: '#a8380b' },
	{ id: 'glow-blue', name: 'Glow Blue', shell: '#b9d6e6', dpad: '#7fa6bd', ab: '#8fb5cc', pill: '#8fb5cc' },
	{ id: 'glow-green', name: 'Glow Green', shell: '#9ed6c8', dpad: '#5e9e8f', ab: '#6fb3a3', pill: '#6fb3a3' },
	{ id: 'pikachu', name: 'Pikachu Clear Orange', shell: 'rgb(255 90 20 / 0.78)', dpad: '#7a2a08', ab: '#f7c531', pill: '#a8380b' },
	{ id: 'light-blue', name: 'Light Blue', shell: '#8fd0d6', atomic: 'rgb(143 208 214 / 0.55)', dpad: '#3f8a92', ab: '#5aaab2', pill: '#5aaab2' },
	{ id: 'light-pink', name: 'Light Pink', shell: '#f1c3cc', dpad: '#b97a88', ab: '#d99aa8', pill: '#d99aa8' },
	{ id: 'light-purple', name: 'Light Purple', shell: '#cfc8f2', dpad: '#7d70c4', ab: '#a69ae0', pill: '#a69ae0' },
	{ id: 'purple', name: 'Purple', shell: '#5145b8', atomic: 'rgb(110 82 196 / 0.6)', dpad: '#221a66', ab: '#2e2580', pill: '#2e2580' },
	{ id: 'snes-gray', name: 'SNES Gray', shell: '#d4d2d6', dpad: '#4a4a4f', ab: '#6b3fa0', pill: '#8f8d94' },
	{ id: 'green', name: 'Green', shell: '#8ccf48', atomic: 'rgb(110 190 60 / 0.6)', dpad: '#3e6e1a', ab: '#5c9a2a', pill: '#5c9a2a' },
	{ id: 'neon-yellow', name: 'Neon Yellow', shell: '#c9f03a', dpad: '#6b8a12', ab: '#8fb31f', pill: '#8fb31f' },
	{ id: 'clear-red', name: 'Clear Red', shell: 'rgb(230 40 52 / 0.72)', dpad: '#6b1016', ab: '#9e1b24', pill: '#9e1b24' },
	{ id: 'pk-limited', name: 'PK Limited', shell: '#f7c531', dpad: '#1f3fa8', ab: '#1f3fa8', pill: '#1f3fa8' },
	{ id: 'teal', name: 'Teal', shell: '#1aa7a7', atomic: 'rgb(26 167 167 / 0.6)', dpad: '#0a4f4f', ab: '#0e6f6f', pill: '#0e6f6f' },
	{ id: 'white', name: 'White', shell: '#f2f2f2', atomic: 'rgb(242 242 247 / 0.45)', dpad: '#7a7a7a', ab: '#9a9a9a', pill: '#9a9a9a' },
	{ id: 'yellow', name: 'Yellow', shell: '#f6b93b', atomic: 'rgb(246 185 59 / 0.62)', dpad: '#7a5208', ab: '#c98a12', pill: '#c98a12' }
];

const KEY = 'pppp.shell', ATOMIC_KEY = 'pppp.shellAtomic';
/** Shells folded into another's atomic version: a saved one picks that colour, atomic. */
const RETIRED: Record<string, string> = { atomic: 'purple', 'clear-purple': 'purple', 'clear-black': 'black', 'clear-green': 'green', clear: 'white' };

/** The colour as worn. Atomic: its see-through plastic (hand-tuned where set, else its own colour at 60%); the
 * clear shells are see-through already and stay as they are. */
export function variant(s: Shell, atomic: boolean): Shell {
	if (!atomic || s.shell.includes('/')) return s;
	const clear = s.atomic ?? `rgb(${[1, 3, 5].map((i) => parseInt(s.shell.slice(i, i + 2), 16)).join(' ')} / 0.6)`;
	return { ...s, name: `Atomic ${s.name}`, shell: clear };
}

/** What a player who never picked a colour wears: their version's, in atomic plastic. */
export const VERSION_SHELL: Record<'Gold' | 'Silver' | 'Crystal', string> = { Gold: 'yellow', Silver: 'white', Crystal: 'light-blue' };

/** Per player (several accounts may share a browser); without a user, the browser-wide keys of old. */
const keys = (user?: string) => (user ? [`${KEY}:${user}`, `${ATOMIC_KEY}:${user}`] : [KEY, ATOMIC_KEY]);

/**
 * The player's saved colour (its base, by id), whether atomic is on, and whether they ever picked one. A pick saved
 * browser-wide (before picks were per player) goes to the first player who loads it.
 */
export function savedShell(user?: string): { base: Shell; atomic: boolean; chosen: boolean } {
	try {
		const [key, atomicKey] = keys(user);
		if (user && localStorage.getItem(key) == null && localStorage.getItem(KEY) != null) {
			localStorage.setItem(key, localStorage.getItem(KEY)!);
			localStorage.setItem(atomicKey, localStorage.getItem(ATOMIC_KEY) ?? '0');
			localStorage.removeItem(KEY);
			localStorage.removeItem(ATOMIC_KEY);
		}
		const saved = localStorage.getItem(key) ?? '';
		const retired = RETIRED[saved];
		const base = SHELLS.find((s) => s.id === (retired ?? saved));
		return { base: base ?? SHELLS[0], atomic: !!retired || localStorage.getItem(atomicKey) === '1', chosen: !!base };
	} catch {
		return { base: SHELLS[0], atomic: false, chosen: false };
	}
}

export function saveShell(id: string, atomic: boolean, user?: string) {
	try {
		const [key, atomicKey] = keys(user);
		localStorage.setItem(key, id);
		localStorage.setItem(atomicKey, atomic ? '1' : '0');
	} catch {
		/* private mode: not remembered */
	}
}

/** Dark or light text for a background: '#rrggbb' or 'rgb(r g b / a)' (alpha ignored). WCAG luminance. */
export function inkFor(color: string): string {
	const rgb = color.startsWith('#')
		? [1, 3, 5].map((i) => parseInt(color.slice(i, i + 2), 16))
		: (color.match(/[\d.]+/g) ?? ['0', '0', '0']).slice(0, 3).map(Number);
	const [r, g, b] = rgb.map((v) => {
		const c = v / 255;
		return c <= 0.03928 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4;
	});
	return 0.2126 * r + 0.7152 * g + 0.0722 * b > 0.3 ? '#1d1f1c' : '#ffffff';
}

/** CSS custom properties for the pad. */
export function shellVars(s: Shell): string {
	return [
		`--shell:${s.shell}`,
		`--guts:${s.shell.includes('/') ? 1 : 0}`, // see-through: the board inside shows (Gamepad)
		`--dpad:${s.dpad}`,
		`--dpad-ink:${inkFor(s.dpad)}`,
		`--ab:${s.ab}`,
		`--ab-ink:${inkFor(s.ab)}`,
		`--pill:${s.pill}`,
		`--pill-ink:${inkFor(s.pill)}`
	].join(';');
}
