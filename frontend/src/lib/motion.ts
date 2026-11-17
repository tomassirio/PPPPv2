// Small live-update motions for lists (the scoreboard, the activity feed). Svelte's transitions ignore the
// reduced-motion setting, so each one takes no time when the viewer asked for less motion.
import { cubicOut } from 'svelte/easing';
import type { Rarity } from './play';

const calm = () => typeof matchMedia !== 'undefined' && matchMedia('(prefers-reduced-motion: reduce)').matches;

/** Milliseconds, or 0 under reduced motion. */
export const ms = (duration: number) => (calm() ? 0 : duration);

/** A new entry: drops in from just above, lit in the accent colour, then settles. Not played on first render. */
export function arrive(_node: Element, { duration = 900 } = {}) {
	return {
		duration: ms(duration),
		easing: cubicOut,
		css: (t: number) =>
			`opacity: ${Math.min(1, t * 2)}; transform: translateY(${(1 - t) * -10}px);` +
			`background-color: color-mix(in oklab, var(--accent) ${Math.round((1 - t) * 28)}%, transparent);`
	};
}

/** The tier colours: Poké red, Great blue, teal (Ultra's black would vanish on the dark theme), Master purple, GS gold. */
const TIER_COLOUR: Record<Rarity, string> = {
	common: '#e3350d',
	uncommon: '#3a6fd8',
	rare: '#2ec4b6',
	epic: '#9b5de5',
	legendary: '#e2b23a'
};
const TIER_MS: Record<Rarity, number> = { common: 700, uncommon: 1000, rare: 1400, epic: 1900, legendary: 2800 };
const tint = (colour: string, pct: number) => `color-mix(in oklab, ${colour} ${Math.round(pct)}%, transparent)`;

/**
 * A new feed entry, louder the higher its tier (tierOf): common drops in with a faint red wash; uncommon adds a blue
 * edge; rare slides in from the left in a teal ring; epic pops with a purple glow; legendary shakes, pops and
 * sweeps a gold shine across. Everything fades back to the plain row. Not played on first render.
 */
export function arriveTier(_node: Element, { tier }: { tier: Rarity }) {
	const c = TIER_COLOUR[tier];
	return {
		duration: ms(TIER_MS[tier]),
		css: (t: number) => {
			const e = cubicOut(t); // the motion settles fast
			const k = t < 0.3 ? 1 : 1 - (t - 0.3) / 0.7; // the colour holds, then fades
			const halo = (pct: number) => `0 0 0 6px ${tint(c, pct * k)}`; // the wash reaches past the text
			let css = `opacity: ${Math.min(1, t * 3)}; background-color: `;
			if (tier === 'common') {
				css += `${tint(c, 22 * k)}; box-shadow: ${halo(22)}; transform: translateY(${-8 * (1 - e)}px);`;
			} else if (tier === 'uncommon') {
				css += `${tint(c, 32 * k)}; box-shadow: ${halo(32)}, -6px 0 0 3px ${tint(c, 100 * k)};`;
				css += `transform: translateY(${-10 * (1 - e)}px);`;
			} else if (tier === 'rare') {
				css += `${tint(c, 34 * k)}; box-shadow: ${halo(34)}, 0 0 0 8px ${tint(c, 100 * k)};`;
				css += `transform: translateX(${-28 * (1 - e)}px);`;
			} else if (tier === 'epic') {
				const scale = 0.92 + 0.08 * e + 0.05 * Math.sin(Math.PI * Math.min(1, t * 1.6));
				css += `${tint(c, 42 * k)}; box-shadow: ${halo(42)}, 0 0 ${26 * k}px 8px ${tint(c, 70 * k)};`;
				css += `transform: scale(${scale});`;
			} else {
				const shake = t < 0.25 ? Math.sin(t * 90) * 4 * (1 - t / 0.25) : 0;
				const scale = 0.9 + 0.1 * e + 0.07 * Math.sin(Math.PI * Math.min(1, t * 1.4));
				const at = -50 + 200 * Math.min(1, t / 0.6); // the shine's centre, % across the row
				css += `${tint(c, 45 * k)}; box-shadow: ${halo(45)}, 0 0 0 8px ${tint(c, 100 * k)}, 0 0 ${34 * k}px 10px ${tint(c, 70 * k)};`;
				css += `background-image: linear-gradient(105deg, transparent ${at - 22}%, ${tint('#fff', 70 * k)} ${at}%, transparent ${at + 22}%);`;
				css += `transform: translateX(${shake}px) scale(${scale});`;
			}
			return css;
		}
	};
}

/** A glow that fades out: put on an element that mounts when something changes (inside a {#key}). */
export function glow(_node: Element, { duration = 1400 } = {}) {
	return {
		duration: ms(duration),
		css: (t: number) => `opacity: ${t < 0.15 ? t / 0.15 : 1 - (t - 0.15) / 0.85};`
	};
}
