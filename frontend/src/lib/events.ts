// One shared WebSocket on /ws/events for every component: everything that changes for everyone while people play
// (scoreboard, chat, who is online, lobby, link sessions, progress). Each message is {"type": ..., "data": ...}.
// Types: CHAT_MESSAGE, CHAT_DELETED, CHAT_REACTION, CHAT_CLEARED, GAME_STATE, PRESENCE, PROGRESS, ACHIEVEMENT_UNLOCKED,
// SCOREBOARD_CHANGED, LOBBY_CHANGED, LINK_CHALLENGE, LINK_SESSION, PLAYER_RESET, ACCESS_REQUESTS, WATCH_REACTION.
import { backoff } from './play';

type Handler = (data: any) => void;

const handlers = new Map<string, Set<Handler>>();
const openHandlers = new Set<() => void>();
let ws: WebSocket | null = null;
let attempt = 0;

function connect() {
	const proto = location.protocol === 'https:' ? 'wss' : 'ws';
	const sock = new WebSocket(`${proto}://${location.host}/ws/events`);
	ws = sock;
	sock.onopen = () => {
		attempt = 0;
		openHandlers.forEach((h) => h());
	};
	sock.onmessage = (e) => {
		const { type, data } = JSON.parse(e.data);
		handlers.get(type)?.forEach((h) => h(data));
	};
	// Events sent while disconnected are lost; onOpen handlers re-fetch snapshots after the reconnect.
	sock.onclose = () => {
		if (ws === sock) setTimeout(connect, backoff(attempt++));
	};
}

function ensureSocket() {
	if (!ws) connect();
}

/** Subscribe to one event type; returns an unsubscribe function (use it in $effect cleanup / onDestroy). */
export function on(type: string, handler: Handler): () => void {
	if (!handlers.has(type)) handlers.set(type, new Set());
	handlers.get(type)!.add(handler);
	ensureSocket();
	return () => handlers.get(type)?.delete(handler);
}

/**
 * Runs on every (re)connect of the socket. Snapshots fetched before the socket was registered (e.g. GET
 * /presence) can be stale and events sent meanwhile missed, so re-fetch here.
 */
export function onOpen(handler: () => void): () => void {
	openHandlers.add(handler);
	ensureSocket();
	return () => openHandlers.delete(handler);
}

// Holds live state and event subscriptions: hot-swapping it in dev would leave a fresh copy with no listeners
// while the page keeps the old one, so a change here reloads the page instead.
import.meta.hot?.accept(() => location.reload());
