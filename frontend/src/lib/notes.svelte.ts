// Notes left on Johto's tiles (backend NoteService): holding A while free in the overworld opens the composer
// (NoteComposer.svelte) for the tile the player stands on. Notes in view come with the screen's name tags (GameScreen).

import { api } from '#lib/api.ts';

/** A note on the player's screen: its tile's top left in screen pixels; here: the player stands on it. */
export type ScreenNote = { id: string; user: string; x: number; y: number; text: string; good: number; mine: boolean; rated: boolean; here: boolean };
export type NoteWords = { phrases: string[]; words: Record<string, string[]> };

export const composer = $state<{ open: boolean; spot: { map: string; x: number; y: number } | null }>({ open: false, spot: null });

/** Opens the composer when the player may leave a note here (standing still in the overworld); quietly not otherwise. */
export async function openComposer(): Promise<boolean> {
	try {
		composer.spot = await api('GET', '/notes/spot');
		composer.open = true;
		return true;
	} catch {
		return false;
	}
}

let words: Promise<NoteWords> | null = null;
export const loadWords = () => (words ??= api<NoteWords>('GET', '/notes/words').catch((e) => ((words = null), Promise.reject(e))));
