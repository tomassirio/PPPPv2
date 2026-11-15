// The player's button presses as they happen, over one WebSocket (/ws/input): "down UP" / "up UP". A held key
// stays held on the Game Boy until its "up" arrives. The server lets go of everything when the socket closes.
import { backoff, type Button } from './play';

let ws: WebSocket | null = null;
let attempt = 0;
let users = 0;
let timer: ReturnType<typeof setTimeout> | undefined;

function connect() {
	const proto = location.protocol === 'https:' ? 'wss' : 'ws';
	const sock = new WebSocket(`${proto}://${location.host}/ws/input`);
	ws = sock;
	sock.onopen = () => (attempt = 0);
	sock.onclose = () => {
		if (ws === sock) ws = null;
		if (users > 0) timer = setTimeout(connect, backoff(attempt++));
	};
}

/** Opens the socket while at least one pad is on the page; returns the stop function. */
export function useInput(): () => void {
	if (users++ === 0 && !ws) connect();
	return () => {
		if (--users === 0) {
			clearTimeout(timer);
			ws?.close();
			ws = null;
		}
	};
}

/** Presses (down) or lets go of (up) a button. Returns false when the socket is not open, so the caller can fall back. */
export function sendKey(button: Button, down: boolean): boolean {
	if (ws?.readyState !== WebSocket.OPEN) return false;
	ws.send(`${down ? 'down' : 'up'} ${button.toUpperCase()}`);
	return true;
}
