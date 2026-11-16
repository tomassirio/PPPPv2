// Kanto (left) and Johto (right) as the Pokégear draws them, one letter per 8px cell of each region's town map
// (Crystal's KantoMap and JohtoMap tilemaps, frames and the region labels left out): w sea, g land, m mountains,
// p route, C town or city, P landmark (caves, towers, ruins). The sign-in map draws one overworld tile per cell.
export const TERRAIN = [
	'mmmmmmmmmmmmmmgwwwmmggggggggggmmmmmm',
	'mmmmmmmmmmmmggwwwwmmmmmmggggggggmmmm',
	'mmmmmmmmmmggggwwwwmmmmmmgggggwPwgmmm',
	'mmPmmmmmmmgpppgwwwmmmmmgggggggpgmPmm',
	'ggpmmmmmmmgpmmgggwmmmppppCpPppCppCmm',
	'ggPggCppPppCpppPggmggpgggpgmmggmmpmm',
	'ggpggpmmmggpmmmPggggpCgPpppCppggmpmm',
	'ppppppgpCppCpppCPwwwwwgpgmmpmpggmpmP',
	'ggpggCgpgggpgggpwwwwwwwpgmPpmpggppmm',
	'ggpggpgpwwgCppppgwwwwwwpggmpgpggpmgg',
	'ggpwgpgpwwwwgggpgwggPwwCgggpgpggpggg',
	'PppggpgpwwwggpppgwgCwwwpgggpwCppppCp',
	'wwgwwCwpwggggpgggwgwwwwpgggpgwggggww',
	'wwwwwwwpppCpppggwwwwwwgpgggpwwwgwwww',
	'wwwwwwwwggpgggwwwwwwwwgppCpPwwwwwwww',
	'wwwwwCwPwwwwwwwwwwwwwwwggggwwwwwwwww',
];

/** A player's spot (x, y in a region's 160x144 Pokégear screen) as a cell of TERRAIN. */
export function cellOf(region: 'johto' | 'kanto', x: number, y: number): { col: number; row: number } {
	const col = Math.min(17, Math.max(0, Math.floor(x / 8) - 1)) + (region === 'johto' ? 18 : 0);
	const row = Math.min(15, Math.max(0, Math.floor(y / 8) - 1));
	return { col, row };
}
