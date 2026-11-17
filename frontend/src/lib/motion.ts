// Small live-update motions for lists (the scoreboard, the activity feed). Svelte's transitions ignore the
// reduced-motion setting, so each one takes no time when the viewer asked for less motion.
import { cubicOut } from 'svelte/easing';

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

/** A glow that fades out: put on an element that mounts when something changes (inside a {#key}). */
export function glow(_node: Element, { duration = 1400 } = {}) {
	return {
		duration: ms(duration),
		css: (t: number) => `opacity: ${t < 0.15 ? t / 0.15 : 1 - (t - 0.15) / 0.85};`
	};
}
