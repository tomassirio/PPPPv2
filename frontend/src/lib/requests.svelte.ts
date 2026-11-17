// How many access requests wait for an admin: loaded once, then kept live by ACCESS_REQUESTS (which carries only
// the count, to every browser). Only admins can read the list, so only they ever start this.
import { api } from './api.js';
import { on } from './events.js';

export const pending = $state({ count: 0 });

let started = false;
export function watchRequests() {
	if (started) return;
	started = true;
	api<unknown[]>('GET', '/admin/access-requests').then(
		(list) => (pending.count = list.length),
		() => {}
	);
	on('ACCESS_REQUESTS', (d: { pending: number }) => (pending.count = d.pending));
}
