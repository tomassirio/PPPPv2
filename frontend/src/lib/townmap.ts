// Kanto (left) and Johto (right) as the Pokégear draws them, one letter per 4px of each region's town map
// (Crystal's KantoMap and JohtoMap tilemaps, frames and the region labels left out), with the edges worked out
// from the neighbours: w sea, h shallows along the coast, s beach, g land, m mountains, e cliff ledge (a mountain's
// south edge), p route, C town (2x2), P landmark: caves, towers, ruins (2x2). One overworld tile per cell.
export const TERRAIN = [
	'mmmmmmmmmmmmmmmmmmmmmmmmmmmessshwwwhmmmsssssssssssssssssssssemmmmmmmmmmm',
	'mmmmmmmmmmmmmmmmmmmmmmmmeeeggshwwwwhmmmmgggggggggggggggggggggeeemmmmmmmm',
	'mmmmmmmmmmmmmmmmmmmmmmmeggggshwwwwwhmmmmmmmmmmmgggggggggggggggggemmmmmmm',
	'mmmmmmmmmmmmmmmmmmmmeeeggggshwwwwwwhmmmmmmmmmmmmgggggggggggsggssgemmmmmm',
	'mmmmmmmmmmmmmmmmmmmmgggggggshwwwwwwhmmmmmmmmmmmeggggggggggshPPhhsgmmmmmm',
	'mmmmmmmmmmmmmmmmmmmmgggggggshwwwwwwhmmmmmmmmmmeggggggggggshhPPhsggmmmmmm',
	'meemPPmmmmmmmmmmmmmmggpppppphwwwwwwhmmmmmmmmmmggggggggggggssppsggmPPmmmm',
	'eggePPmmmmmmmmmmmmmmggppppppshhhwwwhmmmmmmeeeeggggggggggggggppggeePPmmmm',
	'sgggppemmmmmmmmmmmmeggppmmmggssshwwhmmmmmmppppppppCCppPPppppCCppppCCmmmm',
	'sgggppgeeemmeeeemmegggppeeeeggggshwhmmeeeeppppppppCCppPPppppCCppppCCmmmm',
	'sgggPPggggCCppppPPppppCCppppppPPgshhmeggggppggggggppgggmmgggggemmmppmmmm',
	'sgggPPggggCCppppPPppppCCppppppPPggshegggggppggggggppggmmeegggggemmppmmmm',
	'sgggppggggppemmmmeggggppemmmmePPgggsggggppCCggPPppppppCCppppggggmmppmmmm',
	'sgggppggggppgeeemgggggppgeeeegPPgggsssssppCCggPPppppppCCppppggggmmppmmmm',
	'ppppppppppppggppCCppppCCppppppCCPPshhhhhhhhhsgppgggmmmppmmppggggmmppmmPP',
	'ppppppppppppggppCCppppCCppppppCCPPhwwwwwwwwhssppggmmmmppmmppggggeeppmmPP',
	'sgggppggggCCggppggggggppggggggppshwwwwwwwwwwhhppggemPPppmeppggggppppmmmm',
	'sgggppsgggCCggppssssggppggggggpphwwwwwwwwwwwwhppgggePPppegppggggppppeeee',
	'sgggpphsggppggpphhhhsgCCpppppppphwwwwwwwwwwwwhppggggemppggppggggppmegggs',
	'sgggpphhsgppggpphwwhssCCppppppppshwwhhwwhhwwwhppgggggeppggppggggppeggggs',
	'sgggpphhsgppggpphwwwhhhhsgggggppgshhsshhPPhwwhCCggggggppggppggggppgggggs',
	'sgggpphhsgppggpphwwwwwhhsgggggppgshhsgshPPhwwhCCggggggppssppggggppgggggs',
	'PPpppphhsgppggpphwwwwhssggppppppgshhsgCChhwwwhppggggggpphhCCppppppppCCpp',
	'PPppppshssppgspphwhhhsggggppppppgshhsgCChwwwwhppggggggpphhCCppppppppCCpp',
	'hhhhsgshhhCCshpphhsssgggggppgggggshhsgshwwwwwhppggggggpphwhhsgggggggshhh',
	'wwwhsshwwhCChhpphsggggggggppgggggshhsshwwwwwhsppggggggppshwhssgggssshwww',
	'wwwwhhwwwwhhwhppppppCCppppppggggshwhshwwwwwhsgppggggggppshwwhhsgshhhwwww',
	'wwwwwwwwwwwwwhppppppCCppppppgssshwwwhwwwwwwhsgppggggggpphwwwwhsshwwwwwww',
	'wwwwwwwwwwwwwwhhhsggppggggggshhhwwwwwwwwwwwwhsppppCCppPPhwwwwwhhwwwwwwww',
	'wwwwwwwwwwhhwwhhwhssppsssssshwwwwwwwwwwwwwwwwhppppCCppPPhwwwwwwwwwwwwwww',
	'wwwwwwwwwhCChhPPhwhhhhhhhhhhwwwwwwwwwwwwwwwwwhsgggggggshwwwwwwwwwwwwwwww',
	'wwwwwwwwwhCChhPPhwwwwwwwwwwwwwwwwwwwwwwwwwwwwhsssssssshwwwwwwwwwwwwwwwww',
];

/** A player's spot (x, y in a region's 160x144 Pokégear screen) as a cell of TERRAIN. */
export function cellOf(region: 'johto' | 'kanto', x: number, y: number): { col: number; row: number } {
	const half = TERRAIN[0].length / 2;
	const col = Math.min(half - 1, Math.max(0, Math.floor(x / 4) - 2)) + (region === 'johto' ? half : 0);
	const row = Math.min(TERRAIN.length - 1, Math.max(0, Math.floor(y / 4) - 2));
	return { col, row };
}
