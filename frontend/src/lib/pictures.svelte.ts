// Players' Google photos by id (GET /players/pictures), shared by every list that shows a player.
import { api } from './api';

export const pictures = $state<{ byUser: Record<string, string> }>({ byUser: {} });

let loadedAt = 0;

/** Loads the photos the first time, and again when someone unknown shows up, at most every 10 s. */
export async function needPicture(user: string) {
	if (user in pictures.byUser || Date.now() - loadedAt < 10_000) return;
	loadedAt = Date.now();
	try {
		pictures.byUser = await api<Record<string, string>>('GET', '/players/pictures');
	} catch {
		/* initials instead */
	}
}
