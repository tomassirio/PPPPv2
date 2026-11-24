// First-time help on upright phones: the /play tour (TOUR) once, then single tips the first time something comes up
// (the Link cable panel, a player nearby, ...). Each is shown once per browser (localStorage), one at a time, by
// Coach.svelte. Targets are CSS selectors; a step whose targets are not on screen is skipped.

export type Step = { target: string[]; title: string; text: string; gesture?: 'swipe' };

const KEY = 'pppp.tour';
const PHONE = '(max-width: 760px) and (orientation: portrait)';

function load(): Set<string> {
	try {
		return new Set(JSON.parse(localStorage.getItem(KEY) ?? '[]'));
	} catch {
		return new Set();
	}
}
const seen = load();
function save() {
	try {
		localStorage.setItem(KEY, JSON.stringify([...seen]));
	} catch {
		// Not remembered: shown again next time.
	}
}

export const coach = $state<{ queue: { id: string; steps: Step[] }[] }>({ queue: [] });

const upright = () => typeof matchMedia !== 'undefined' && matchMedia(PHONE).matches;

/** Queues help `id` unless it was seen, is queued, or this is not an upright phone. */
export function offer(id: string, steps: Step[]) {
	if (seen.has(id) || coach.queue.some((q) => q.id === id) || !upright()) return;
	coach.queue.push({ id, steps });
}

/** The current help is done (finished or skipped): never again. */
export function finish(id: string) {
	seen.add(id);
	save();
	coach.queue = coach.queue.filter((q) => q.id !== id);
}

/** Settings' "Show the tour again". */
export function replayTour() {
	seen.delete('tour');
	save();
	offer('tour', TOUR);
}

export const TOUR: Step[] = [
	{
		target: ['.screen-slot'],
		title: 'Your own Game Boy',
		text: 'Nobody else plays it. It keeps running for an hour after you close the tab, and pauses after that.'
	},
	{
		target: ['.burger'],
		title: 'Everything is in here',
		text: 'Link cable, Your run, Scoreboard, Activity, Chat and Live. Settings is at the bottom.'
	},
	{
		target: ['.play-panel'],
		title: 'Swipe between panels',
		text: 'Drag sideways under the screen to flip to the next panel. They loop round.',
		gesture: 'swipe'
	},
	{
		target: ['.strips'],
		title: 'Strips',
		text: 'Where you are, your party, badges and more at a glance. Pick which ones, and their order, in ☰ → Settings.'
	},
	{
		target: ['.pad .move'],
		title: 'Move the controller',
		text: 'Tap the triangle to put the strips under the screen and the controller at the bottom, or back.'
	},
	{
		target: ['.speed', '.reset'],
		title: 'Fast and Reset',
		text: 'Fast runs the game about 3× (no sound). Reset starts it again from your last save.'
	}
];

export const TIPS: Record<string, Step> = {
	link: {
		target: ['[data-panel="link"]'],
		title: 'Link cable',
		text: 'Get ready to Trade or Battle. When someone accepts, you both dig to the Cable Club upstairs in a Pokémon Center.'
	},
	nearby: {
		target: ['[aria-label="A button"]'],
		title: 'Someone is nearby',
		text: 'Press A next to another Pib on your screen to link with them.'
	},
	run: {
		target: ['[data-panel="run"]'],
		title: 'Your run',
		text: 'Your Trainer Score, title, badges and Pokédex. Tap a badge or a Pokémon for more.'
	},
	toast: {
		target: ['.toasts .toast'],
		title: 'Points!',
		text: 'Catches, badges, level-ups and achievements pop up here with their points. The Scoreboard ranks everyone.'
	},
	mention: {
		target: ['.burger'],
		title: 'You were mentioned',
		text: 'Someone wrote your name in the chat. Chat is in the menu.'
	},
	away: {
		target: ['.away button'],
		title: 'Paused while you were away',
		text: 'After an hour untouched your Game Boy pauses, right where it was. Continue picks it up again.'
	}
};

/** A single tip, the first time its moment comes. */
export const tip = (id: keyof typeof TIPS) => offer(id, [TIPS[id]]);
