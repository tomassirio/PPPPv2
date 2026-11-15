// Players' Google photos and full names by id (GET /players/pictures, /players/names), shared by every list that
// shows a player. Names exist for players who signed in since they were kept; others show their e-mail's local part.
import { api } from './api';
import { shortUser } from './play';

export const pictures = $state<{ byUser: Record<string, string>; names: Record<string, string> }>({ byUser: {}, names: {} });

let loadedAt = 0;

/** Loads photos and names the first time, and again when someone unknown shows up, at most every 10 s. */
export async function needPicture(user: string) {
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

/** The player's full name, or the local part of their e-mail. */
export const nameOf = (user: string): string => pictures.names[user] ?? shortUser(user);

/** Two letters for an avatar: first and last name's initials, or the start of the e-mail. */
export function initialsOf(user: string): string {
	const words = (pictures.names[user] ?? '').split(/\s+/).filter(Boolean);
	return (words.length > 1 ? words[0][0] + words.at(-1)![0] : (words[0] ?? shortUser(user)).slice(0, 2)).toUpperCase();
}
