// Small live-update motions for lists (the scoreboard, the activity feed). Svelte's transitions ignore the
// reduced-motion setting, so each one takes no time when the viewer asked for less motion.
import { backOut, cubicInOut, cubicOut } from 'svelte/easing';
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
 * A new feed entry, louder the higher its tier (tierOf). Every tier drops in from just above in its colour, which
 * holds for the first third and then fades back to the plain row. On top: common, a faint red wash; uncommon, a blue
 * edge growing in on the left; rare, a teal ring closing in tight around the row; epic, a springy pop in a purple
 * glow; legendary, a bigger pop in a gold ring whose glow breathes while a shine sweeps across. Not played on first
 * render.
 */
export function arriveTier(_node: Element, { tier }: { tier: Rarity }) {
	const c = TIER_COLOUR[tier];
	return {
		duration: ms(TIER_MS[tier]),
		css: (t: number) => {
			const settle = cubicOut(Math.min(1, t / 0.5)); // the drop
			const k = t < 0.33 ? 1 : 1 - cubicInOut((t - 0.33) / 0.67); // the colour holds, then eases out
			const halo = (pct: number) => `0 0 0 6px ${tint(c, pct * k)}`; // the wash reaches past the text
			const pop = (from: number, until: number) => from + (1 - from) * backOut(Math.min(1, t / until));
			let transform = `translateY(${-8 * (1 - settle)}px)`;
			let wash = 20,
				shadow: string[] = [];
			if (tier === 'uncommon') {
				wash = 28;
				shadow = [`-${6 + 4 * settle}px 0 0 0 ${tint(c, 100 * k)}`]; // the edge grows out of the halo
			} else if (tier === 'rare') {
				wash = 30;
				const close = cubicOut(Math.min(1, t / 0.4));
				shadow = [`0 0 0 ${8 + 10 * (1 - close)}px ${tint(c, 100 * k * close)}`];
			} else if (tier === 'epic') {
				wash = 38;
				transform += ` scale(${pop(0.9, 0.45)})`;
				shadow = [`0 0 ${24 * k}px 8px ${tint(c, 65 * k)}`];
			} else if (tier === 'legendary') {
				wash = 42;
				transform += ` scale(${pop(0.86, 0.5)})`;
				const breath = 0.75 + 0.25 * Math.sin(Math.PI * 4 * t);
				shadow = [`0 0 0 8px ${tint(c, 100 * k)}`, `0 0 ${36 * breath * k}px 10px ${tint(c, 70 * k)}`];
			}
			let css = `opacity: ${Math.min(1, t * 4)}; transform: ${transform};`;
			css += `background-color: ${tint(c, wash * k)}; box-shadow: ${[halo(wash), ...shadow].join(', ')};`;
			if (tier === 'legendary') {
				const at = -40 + 180 * cubicInOut(Math.min(1, t / 0.7)); // the shine's centre, % across the row
				css += `background-image: linear-gradient(105deg, transparent ${at - 22}%, ${tint('#fff', 70 * k)} ${at}%, transparent ${at + 22}%);`;
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
