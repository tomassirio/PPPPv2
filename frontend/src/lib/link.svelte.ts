// The link cable, shared by the Link cable card, the challenge sheet, the phone's linked banner and the scoreboard's
// status lines. GET /api/v1/link is the whole picture for this player; LOBBY_CHANGED, LINK_CHALLENGE and LINK_SESSION
// say when to fetch it again. links (user -> peer) also follows other players' sessions from LINK_SESSION, so only
// sessions started while this page is open (and this player's own) are known.
import { api, ApiError } from './api';
import { on, onOpen } from './events';
import type { LinkKind } from './play';
import { session } from './session.svelte';

export type Challenge = { from: string; to: string; kind: LinkKind; expiresAt: number };
export type LinkState = {
	lobby: { userId: string; kind: LinkKind }[];
	ready: LinkKind | null;
	incoming: Challenge | null;
	outgoing: Challenge | null;
	session: { peer: string; kind: LinkKind; endsAt: number } | null;
	now: number;
};
type SessionEvent = { a: string; b: string; status: 'STARTED' | 'ENDED' };

export const link = $state<{ state: LinkState | null; links: Record<string, string>; now: number; error: string }>({
	state: null,
	links: {},
	now: Date.now(),
	error: ''
});

/** Challenges last 60 s (LinkService). */
export const CHALLENGE_MS = 60_000;
export const verb = (k: LinkKind) => (k === 'TRADE' ? 'trade' : 'battle');
/** Seconds until an epoch-ms time, by the server's clock. */
export const left = (until: number) => Math.max(0, Math.ceil((until - link.now) / 1000));
export const clock = (s: number) => `${Math.floor(s / 60)}:${String(s % 60).padStart(2, '0')}`;

let skew = 0; // server clock minus this browser's

function show(s: LinkState) {
	skew = s.now - Date.now();
	link.now = s.now;
	link.state = s;
	const me = session.me?.id;
	if (!me) return;
	const links = { ...link.links };
	const peer = s.session?.peer;
	for (const [u, p] of Object.entries(links)) if (u === me || p === me) delete links[u];
	if (peer) Object.assign(links, { [me]: peer, [peer]: me });
	link.links = links;
}

async function load() {
	try {
		show(await api<LinkState>('GET', '/link'));
	} catch {
		/* keep what we have; the next event or reconnect fetches again */
	}
}

export async function act(method: 'PUT' | 'POST' | 'DELETE', path: string, body?: unknown) {
	link.error = '';
	try {
		show(await api<LinkState>(method, path, body));
	} catch (e) {
		link.error = e instanceof ApiError ? e.detail : 'Something went wrong';
		load();
	}
}

let watchers = 0;
let offs: (() => void)[] = [];

/** Keeps `link` live while at least one component watches; returns the stop function. */
export function watchLink(): () => void {
	if (watchers++ === 0) {
		const mine = (e: { from?: string; to?: string; a?: string; b?: string }) =>
			[e.from, e.to, e.a, e.b].includes(session.me?.id) && load();
		const tick = setInterval(() => (link.now = Date.now() + skew), 1000);
		offs = [
			onOpen(load),
			on('LOBBY_CHANGED', load),
			on('LINK_CHALLENGE', mine),
			on('LINK_SESSION', (e: SessionEvent) => {
				const links = { ...link.links };
				if (e.status === 'STARTED') Object.assign(links, { [e.a]: e.b, [e.b]: e.a });
				else if (links[e.a] === e.b) {
					delete links[e.a];
					delete links[e.b];
				}
				link.links = links;
				mine(e);
			}),
			() => clearInterval(tick)
		];
		load();
	}
	return () => {
		if (--watchers === 0) {
			offs.forEach((off) => off());
			offs = [];
		}
	};
}
