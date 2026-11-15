// The player's key bindings (one key per pad button), remembered per browser. Changed in Settings (account menu).
import { parseBindings, type Bindings } from './play';

const KEY = 'pppp.keys';

function saved(): Bindings {
	try {
		return parseBindings(localStorage.getItem(KEY));
	} catch {
		return parseBindings(null);
	}
}

export const keys = $state<{ bindings: Bindings }>({ bindings: saved() });

export function setBindings(bindings: Bindings) {
	keys.bindings = bindings;
	try {
		localStorage.setItem(KEY, JSON.stringify(bindings));
	} catch {
		/* private mode: not remembered */
	}
}
