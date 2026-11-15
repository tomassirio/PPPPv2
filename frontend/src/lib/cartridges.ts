// Cartridges as the backend lists them (Cartridge.java) and the box art we ship for the games we know.
import crystal from '#lib/assets/covers/crystal.jpg';
import gold from '#lib/assets/covers/gold.jpg';
import silver from '#lib/assets/covers/silver.jpg';

/** title: the ROM header's ("PM_CRYSTAL"); name: what players read ("Pokémon Crystal"). */
export type Cartridge = { id: string; title: string; name: string; size: number; cgb: boolean };

const COVERS: Record<string, string> = { POKEMON_GLD: gold, POKEMON_SLV: silver, PM_CRYSTAL: crystal };

/** Box art for the cartridge, or null for a game we have none for. */
export const cover = (c: Pick<Cartridge, 'title'>): string | null => COVERS[c.title] ?? null;
