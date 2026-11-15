// Colour theme: 'auto' follows the OS (no data-theme attribute), 'light'/'dark' pin it.
// app.html applies the saved choice before first paint so there is no flash.
export type Theme = 'auto' | 'light' | 'dark';
const KEY = 'pppp.theme';

function saved(): Theme {
	try {
		const t = localStorage.getItem(KEY);
		return t === 'light' || t === 'dark' ? t : 'auto';
	} catch {
		return 'auto';
	}
}

export const theme = $state({ value: saved() });

export function setTheme(t: Theme) {
	theme.value = t;
	if (t === 'auto') delete document.documentElement.dataset.theme;
	else document.documentElement.dataset.theme = t;
	try {
		if (t === 'auto') localStorage.removeItem(KEY);
		else localStorage.setItem(KEY, t);
	} catch {
		/* private mode: not remembered */
	}
}

/**
 * Switches theme with a reveal: the new colours spread as a circle from `from` (the toggle that was clicked).
 * Falls back to an instant switch without the View Transitions API or with prefers-reduced-motion.
 */
export function switchTheme(t: Theme, from?: Element | null) {
	const doc = document as Document & { startViewTransition?: (cb: () => void) => { ready: Promise<void> } };
	if (theme.value === t || !doc.startViewTransition || matchMedia('(prefers-reduced-motion: reduce)').matches) return setTheme(t);
	const box = from?.getBoundingClientRect();
	const x = box ? box.left + box.width / 2 : innerWidth - 40;
	const y = box ? box.top + box.height / 2 : 40;
	const r = Math.hypot(Math.max(x, innerWidth - x), Math.max(y, innerHeight - y));
	doc.startViewTransition(() => setTheme(t)).ready.then(() =>
		document.documentElement.animate(
			{ clipPath: [`circle(0px at ${x}px ${y}px)`, `circle(${r}px at ${x}px ${y}px)`] },
			{ duration: 600, easing: 'cubic-bezier(0.4, 0, 0.2, 1)', pseudoElement: '::view-transition-new(root)' }
		)
	);
}
