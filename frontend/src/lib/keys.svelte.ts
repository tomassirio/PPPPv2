// Which keys move the player: the arrow keys (Z = A, X = B) or WASD (K = A, J = B). Remembered per browser.
import type { KeyScheme } from './play';

const KEY = 'pppp.keys';

function saved(): KeyScheme {
	try {
		return localStorage.getItem(KEY) === 'wasd' ? 'wasd' : 'arrows';
	} catch {
		return 'arrows';
	}
}

export const keys = $state<{ scheme: KeyScheme }>({ scheme: saved() });

export function setKeyScheme(scheme: KeyScheme) {
	keys.scheme = scheme;
	try {
		localStorage.setItem(KEY, scheme);
	} catch {
		/* private mode: not remembered */
	}
}
