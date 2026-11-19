import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { act, link, PARTING_MS, watchLink, type LinkState } from './link.svelte';
import { session } from './session.svelte';

class FakeSocket {
	static current: FakeSocket | null = null;
	onopen: (() => void) | null = null;
	onmessage: ((e: { data: string }) => void) | null = null;
	onclose: (() => void) | null = null;
	constructor() {
		FakeSocket.current = this;
	}
	sendEvent(type: string, data: unknown) {
		this.onmessage?.({ data: JSON.stringify({ type, data }) });
	}
}

const state = (over: Partial<LinkState> = {}): LinkState => ({
	lobby: [],
	ready: null,
	incoming: null,
	outgoing: null,
	session: null,
	links: [],
	now: 1000,
	...over
});

describe('link parting state', () => {
	let responses: LinkState[] = [];
	let stop: (() => void) | undefined;

	beforeEach(() => {
		vi.useFakeTimers();
		vi.stubGlobal('document', { cookie: '' });
		vi.stubGlobal('location', { protocol: 'http:', host: 'localhost:5174' });
		vi.stubGlobal('WebSocket', FakeSocket);
		responses = [];
		vi.stubGlobal(
			'fetch',
			vi.fn(() => Promise.resolve(new Response(JSON.stringify(responses.shift() ?? state()), { status: 200 })))
		);
		session.me = { id: 'me@x', name: 'Me', picture: null, roles: ['PLAYER'] };
		link.state = null;
		link.parting = null;
		link.links = {};
		link.error = '';
	});

	afterEach(() => {
		stop?.();
		stop = undefined;
		vi.runOnlyPendingTimers();
		vi.useRealTimers();
		vi.unstubAllGlobals();
	});

	it('clears parting after PARTING_MS even when LINK_SESSION ENDED reloads state right after unplugging', async () => {
		// Initial load: idle -> no parting banner.
		responses.push(state());
		stop = watchLink();
		await vi.advanceTimersByTimeAsync(0);
		expect(link.parting).toBeNull();

		// Session starts.
		responses.push(
			state({
				session: { peer: 'misty@x', kind: 'TRADE', since: 1000, wrongRoom: null, peerWrongRoom: null },
				links: [{ a: 'me@x', b: 'misty@x', kind: 'TRADE', since: 1000 }]
			})
		);
		await act('POST', '/link/challenges/accept');
		expect(link.state?.session?.peer).toBe('misty@x');
		expect(link.parting).toBeNull();

		// Unplug: DELETE /link/session answers with session: null, followed immediately by LINK_SESSION ENDED
		// and LOBBY_CHANGED events that each reload GET /link with a new LinkState object (session: null).
		responses.push(state(), state(), state());
		await act('DELETE', '/link/session');
		expect(link.state?.session).toBeNull();
		expect(link.parting).toEqual({ peer: 'misty@x', kind: 'TRADE' });

		await vi.advanceTimersByTimeAsync(50);
		FakeSocket.current!.sendEvent('LINK_SESSION', { a: 'me@x', b: 'misty@x', status: 'ENDED' });
		FakeSocket.current!.sendEvent('LOBBY_CHANGED', []);
		await vi.advanceTimersByTimeAsync(0);
		expect(link.links).toEqual({});
		expect(link.parting).toEqual({ peer: 'misty@x', kind: 'TRADE' });

		// Still visible just before PARTING_MS, gone once PARTING_MS has elapsed since unplug.
		await vi.advanceTimersByTimeAsync(PARTING_MS - 51);
		expect(link.parting).toEqual({ peer: 'misty@x', kind: 'TRADE' });

		await vi.advanceTimersByTimeAsync(1);
		expect(link.parting).toBeNull();
	});

	it('clears parting immediately if a new link session starts before PARTING_MS elapses', async () => {
		responses.push(
			state({ session: { peer: 'misty@x', kind: 'BATTLE', since: 1000, wrongRoom: null, peerWrongRoom: null } }),
			state(),
			state({ session: { peer: 'brock@x', kind: 'TRADE', since: 1500, wrongRoom: null, peerWrongRoom: null } }),
			state()
		);
		await act('POST', '/link/challenges/accept');
		await act('DELETE', '/link/session');
		expect(link.parting).toEqual({ peer: 'misty@x', kind: 'BATTLE' });

		await vi.advanceTimersByTimeAsync(500);
		await act('POST', '/link/challenges/accept');
		expect(link.parting).toBeNull();

		// Unplugging the second session gets its own full PARTING_MS window without the first timer cutting it short.
		await vi.advanceTimersByTimeAsync(1200);
		await act('DELETE', '/link/session');
		expect(link.parting).toEqual({ peer: 'brock@x', kind: 'TRADE' });

		await vi.advanceTimersByTimeAsync(PARTING_MS - 1);
		expect(link.parting).toEqual({ peer: 'brock@x', kind: 'TRADE' });

		await vi.advanceTimersByTimeAsync(1);
		expect(link.parting).toBeNull();
	});
});
