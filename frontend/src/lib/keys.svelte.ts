// The player's key bindings (one key per pad button), stored per user on the server (GET/PUT /api/v1/me/keys).
// Changed in Settings (account menu). The arrows layout applies until the server answers.
import { api } from './api';
import { PRESETS, type Bindings } from './play';

export const keys = $state<{ bindings: Bindings; error: string }>({ bindings: { ...PRESETS.arrows }, error: '' });

let loading: Promise<void> | null = null;

/** Fetches the player's bindings once per page load (call it wherever keys are used). */
export function loadKeys(): Promise<void> {
	loading ??= api<Bindings>('GET', '/me/keys')
		.then((b) => {
			keys.bindings = b;
		})
		.catch(() => {
			loading = null; // try again next time
		});
	return loading;
}

/** Shows the change at once and saves it; the old bindings come back if the server refuses. */
export async function setBindings(bindings: Bindings) {
	const before = keys.bindings;
	keys.bindings = bindings;
	keys.error = '';
	try {
		keys.bindings = await api<Bindings>('PUT', '/me/keys', bindings);
	} catch (e) {
		keys.bindings = before;
		keys.error = (e as { detail?: string }).detail ?? 'Could not save your keys. Try again.';
	}
}

// Holds live state: a hot-swapped copy would lose it, so a change here reloads the page instead.
import.meta.hot?.accept(() => location.reload());
