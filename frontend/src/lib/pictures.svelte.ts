// Players' Google photos and shown names by id (GET /players/pictures, /players/names), shared by every list that
// shows a player. A shown name is the nickname the player picked, else their Google name; players with neither
// show their e-mail's local part. NAMES_CHANGED {user, name} keeps them current. Usernames are "@" and that part.
import { api } from './api';
import { on } from './events';
import { shortUser } from './play';

export const pictures = $state<{ byUser: Record<string, string>; names: Record<string, string> }>({ byUser: {}, names: {} });

let loadedAt = 0;

let listening = false;

/** Loads photos and names the first time, and again when someone unknown shows up, at most every 10 s. */
export async function needPicture(user: string) {
	if (!listening) {
		listening = true;
		on('NAMES_CHANGED', (e: { user: string; name: string }) => (pictures.names = { ...pictures.names, [e.user]: e.name }));
	}
	if ((user in pictures.byUser && user in pictures.names) || Date.now() - loadedAt < 10_000) return;
	loadedAt = Date.now();
	try {
		const [byUser, names] = await Promise.all([
			api<Record<string, string>>('GET', '/players/pictures'),
			api<Record<string, string>>('GET', '/players/names')
		]);
		pictures.byUser = byUser;
		pictures.names = names;
	} catch {
		/* initials and e-mail names instead */
	}
}

/** What the player is shown as: their nickname, their Google name, or the local part of their e-mail. */
export const nameOf = (user: string): string => pictures.names[user] ?? shortUser(user);

/** "@" and the local part of the player's e-mail. */
export const usernameOf = (user: string): string => `@${shortUser(user)}`;

/** Two letters for an avatar: first and last name's initials, or the start of the e-mail. */
export function initialsOf(user: string): string {
	const words = (pictures.names[user] ?? '').split(/\s+/).filter(Boolean);
	return (words.length > 1 ? words[0][0] + words.at(-1)![0] : (words[0] ?? shortUser(user)).slice(0, 2)).toUpperCase();
}

// Holds live state and event subscriptions: hot-swapping it in dev would leave a fresh copy with no listeners
// while the page keeps the old one, so a change here reloads the page instead.
import.meta.hot?.accept(() => location.reload());
