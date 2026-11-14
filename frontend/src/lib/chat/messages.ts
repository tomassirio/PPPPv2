// Pure chat list logic: Redis stream ids ("<ms>-<seq>") order messages; ids dedupe them.
import like from '../assets/reactions/like.png';
import love from '../assets/reactions/love.png';
import haha from '../assets/reactions/haha.png';
import wow from '../assets/reactions/wow.png';
import sad from '../assets/reactions/sad.png';
import angry from '../assets/reactions/angry.png';

export type ChatMessage = {
	id: string;
	user: string;
	name: string;
	text: string;
	kind: string; // 'USER' | 'SYSTEM' (everyone) | 'ADMIN' (admins only)
	at: number;
	reactions?: Record<string, string[]>; // reaction id -> user ids
};

/** Reactions in display order: the backend's ChatService.REACTIONS, each a Pikachu face. */
export const REACTIONS = [
	{ id: 'like', label: 'Like', src: like },
	{ id: 'love', label: 'Love', src: love },
	{ id: 'haha', label: 'Haha', src: haha },
	{ id: 'wow', label: 'Wow', src: wow },
	{ id: 'sad', label: 'Sad', src: sad },
	{ id: 'angry', label: 'Angry', src: angry }
] as const;

/** The user's reaction to a message, or undefined. */
export const myReaction = (m: ChatMessage, user: string | undefined) =>
	user ? Object.keys(m.reactions ?? {}).find((r) => m.reactions![r].includes(user)) : undefined;

/** Replaces one message's reactions (CHAT_REACTION or a PUT/DELETE response). */
export const setReactions = (list: ChatMessage[], id: string, reactions: Record<string, string[]>) =>
	list.map((m) => (m.id === id ? { ...m, reactions } : m));

/** Orders stream ids numerically ("10-0" after "9-5"). */
export function compareIds(a: string, b: string): number {
	const [am, as] = a.split('-').map(Number);
	const [bm, bs] = b.split('-').map(Number);
	return am - bm || as - bs;
}

/** Merges incoming messages (any order) into the list: oldest first, no duplicate ids. */
export function merge(list: ChatMessage[], incoming: ChatMessage[]): ChatMessage[] {
	const byId = new Map(list.map((m) => [m.id, m]));
	for (const m of incoming) byId.set(m.id, m);
	return [...byId.values()].sort((a, b) => compareIds(a.id, b.id));
}

export const remove = (list: ChatMessage[], id: string) => list.filter((m) => m.id !== id);

/** Stable per-user name color: a hue from the user id's FNV-1a hash, same lightness and chroma for every user. */
export function nameColor(user: string): string {
	let h = 0x811c9dc5;
	for (const c of user) h = Math.imul(h ^ c.charCodeAt(0), 0x01000193);
	return `oklch(0.8 0.13 ${(h >>> 0) % 360})`;
}
