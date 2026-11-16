// Cartridges as the backend lists them (Cartridge.java) and the box art we ship for the games we know.
import crystal from '#lib/assets/covers/crystal.jpg';
import gold from '#lib/assets/covers/gold.jpg';
import silver from '#lib/assets/covers/silver.jpg';
import crystalTitle from '#lib/assets/titles/crystal.webp';
import goldTitle from '#lib/assets/titles/gold.webp';
import silverTitle from '#lib/assets/titles/silver.webp';
import hoOhCry from '#lib/assets/cries/ho-oh.mp3';
import lugiaCry from '#lib/assets/cries/lugia.mp3';
import suicuneCry from '#lib/assets/cries/suicune.mp3';

/** title: the ROM header's ("PM_CRYSTAL"); name: what players read ("Pokémon Crystal"). */
export type Cartridge = { id: string; title: string; name: string; size: number; cgb: boolean };

const COVERS: Record<string, string> = { POKEMON_GLD: gold, POKEMON_SLV: silver, PM_CRYSTAL: crystal };
/** Each game's title screen as an animated loop, recorded from the ROM with no input. */
const TITLES: Record<string, string> = { POKEMON_GLD: goldTitle, POKEMON_SLV: silverTitle, PM_CRYSTAL: crystalTitle };
/** The cover legendary's cry (Ho-Oh, Lugia, Suicune), recorded from each ROM's own sound engine. */
const CRIES: Record<string, string> = { POKEMON_GLD: hoOhCry, POKEMON_SLV: lugiaCry, PM_CRYSTAL: suicuneCry };
/** Picker order: Crystal between Gold and Silver; games we don't know go last. */
const ORDER = ['POKEMON_GLD', 'PM_CRYSTAL', 'POKEMON_SLV'];
/** Each version's colour, from its cartridge plastic. */
const COLORS: Record<string, string> = { POKEMON_GLD: '#d4a72c', POKEMON_SLV: '#a3adb7', PM_CRYSTAL: '#5aaed6' };

/** Box art for the cartridge, or null for a game we have none for. */
export const cover = (c: Pick<Cartridge, 'title'>): string | null => COVERS[c.title] ?? null;

/** The version's colour, or null for a game we have none for. */
export const color = (c: Pick<Cartridge, 'title'>): string | null => COLORS[c.title] ?? null;

/** The title-screen loop, or null for a game we have none for. */
export const titleScreen = (c: Pick<Cartridge, 'title'>): string | null => TITLES[c.title] ?? null;

/** The cover legendary's cry, or null. */
export const cry = (c: Pick<Cartridge, 'title'>): string | null => CRIES[c.title] ?? null;

/** Gold, Crystal, Silver, then the rest as listed. */
export const byPickerOrder = <T extends Pick<Cartridge, 'title'>>(list: T[]): T[] => {
	const rank = (c: T) => (ORDER.indexOf(c.title) + ORDER.length + 1) % (ORDER.length + 1);
	return [...list].sort((a, b) => rank(a) - rank(b));
};
