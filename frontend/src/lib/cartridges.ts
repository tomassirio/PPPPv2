// Cartridges as the backend lists them (Cartridge.java) and the box art we ship for the games we know.
import crystal from '#lib/assets/covers/crystal.jpg';
import gold from '#lib/assets/covers/gold.jpg';
import silver from '#lib/assets/covers/silver.jpg';

/** title: the ROM header's ("PM_CRYSTAL"); name: what players read ("Pokémon Crystal"). */
export type Cartridge = { id: string; title: string; name: string; size: number; cgb: boolean };

const COVERS: Record<string, string> = { POKEMON_GLD: gold, POKEMON_SLV: silver, PM_CRYSTAL: crystal };
/** Each version's colour, from its cartridge plastic. */
const COLORS: Record<string, string> = { POKEMON_GLD: '#d4a72c', POKEMON_SLV: '#a3adb7', PM_CRYSTAL: '#5aaed6' };

/** Box art for the cartridge, or null for a game we have none for. */
export const cover = (c: Pick<Cartridge, 'title'>): string | null => COVERS[c.title] ?? null;

/** The version's colour, or null for a game we have none for. */
export const color = (c: Pick<Cartridge, 'title'>): string | null => COLORS[c.title] ?? null;
