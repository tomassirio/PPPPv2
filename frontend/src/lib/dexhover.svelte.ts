// Hovering a Pokémon to see its Pokédex card (DexCard). One DexHover per component: show(n, el, ...) on pointer enter,
// hide() on leave; render <DexCard {...hover.card} /> while hover.card is set. Always the viewer's own Pokédex
// (GET /game/dex/{n}): someone else's Pokémon shows only what you have seen or caught. Answers are cached per number
// and key (a new catch asks again).
// Phones: the card goes wherever it fits on screen (beside, else above or below, kept inside the edges), and a tap
// opens it and keeps it open (a finger's pointerleave comes right after it lifts) until a tap elsewhere or a scroll.
import { api } from '#lib/api.ts';
import type { Tier } from '#lib/play.ts';

export type DexEntry = {
	number: number;
	state: 'unseen' | 'seen' | 'caught';
	name: string | null;
	category: string | null;
	types: string[] | null;
	heightFeet: number | null;
	heightInches: number | null;
	weightPounds: number | null;
	text: string | null;
	/** Power Rating tier once seen (null while unseen). */
	tier?: Tier | null;
};

type Side = 'right' | 'left' | 'above' | 'below';
export type DexCardState = {
	n: number;
	x: number;
	y: number;
	side: Side;
	zoom: number;
	state: 'unseen' | 'seen' | 'caught';
	entry: DexEntry | null;
	name?: string;
	note?: string;
};

const cache = new Map<string, Promise<DexEntry | null>>();

/** The card's size in CSS px (DexCard: 300px wide; the tallest, a caught entry, about 280px). */
const CARD_W = 300;
const CARD_H = 280;
const EDGE = 8;

// What the last press was made with: a touch has no hover, so its card stays until the next tap.
let lastPointer = 'mouse';
// Every card a tap has opened, to close them all at once (a swipe between panels starts).
const tapped = new Set<() => void>();
export function closeDexCards() {
	for (const close of [...tapped]) close();
}
// pointerover too: it comes before the pointerenter that shows a card, pointerdown only after it.
if (typeof window !== 'undefined')
	for (const type of ['pointerover', 'pointerdown'] as const)
		addEventListener(type, (e) => {
			lastPointer = e.pointerType;
		}, { capture: true });

export function loadDex(n: number, key = ''): Promise<DexEntry | null> {
	const id = `${n}:${key}`;
	let p = cache.get(id);
	if (!p) {
		p = api<DexEntry>('GET', `/game/dex/${n}`).catch(() => null);
		cache.set(id, p);
	}
	return p;
}

/** For a Pokémon inside a link (a scoreboard row): on touch, the tap only opens its card. Use as its onclick. */
export function keepTap(e: MouseEvent) {
	if (lastPointer !== 'touch') return;
	e.preventDefault();
	e.stopPropagation();
}

export class DexHover {
	card = $state<DexCardState | null>(null);
	#timer: ReturnType<typeof setTimeout> | undefined;
	#anchor: HTMLElement | null = null;
	#off: (() => void) | null = null;

	/**
	 * The card for n beside el: side "above" centres it over el, "right"/"left" centre it vertically beside el, kept
	 * on screen (half: about half the card's height plus a margin). state: what to show before the answer comes
	 * (unseen unless known; no answer, e.g. no game picked yet, keeps it).
	 */
	show(
		n: number,
		el: HTMLElement,
		opts: { side?: Side; key?: string; state?: DexCardState['state']; name?: string; note?: string } = {}
	) {
		clearTimeout(this.#timer);
		this.#anchor = el;
		const r = el.getBoundingClientRect();
		const zoom = (el as HTMLElement & { currentCSSZoom?: number }).currentCSSZoom ?? 1;
		const w = CARD_W * zoom;
		const h = CARD_H * zoom;
		const fits: Record<Side, boolean> = {
			right: r.right + 10 + w <= innerWidth - EDGE,
			left: r.left - 10 - w >= EDGE,
			above: r.top - 8 - h >= EDGE,
			below: r.bottom + 8 + h <= innerHeight - EDGE
		};
		// The asked side if it fits, else the other side of the same axis, else the other axis; above as a last resort.
		const asked = opts.side ?? 'above';
		const order: Side[] =
			asked === 'right' || asked === 'left'
				? [asked, asked === 'right' ? 'left' : 'right', 'above', 'below']
				: [asked, asked === 'above' ? 'below' : 'above', 'right', 'left'];
		const side = order.find((s) => fits[s]) ?? 'above';
		const clampX = (v: number) => Math.min(Math.max(EDGE + w / 2, v), innerWidth - EDGE - w / 2);
		const clampY = (v: number) => Math.min(Math.max(EDGE + h / 2, v), innerHeight - EDGE - h / 2);
		const x = side === 'right' ? r.right + 10 : side === 'left' ? r.left - 10 : clampX(r.left + r.width / 2);
		const y = side === 'above' ? Math.max(r.top - 8, EDGE + h) : side === 'below' ? r.bottom + 8 : clampY(r.top + r.height / 2);
		this.#set({ n, x, y, side, zoom, state: opts.state ?? 'unseen', entry: null, name: opts.name, note: opts.note }, opts);
		if (lastPointer === 'touch') this.#stayUntilTapElsewhere();
	}

	/** Touch: keep the card until a tap outside the Pokémon it belongs to (another Pokémon moves it there) or a scroll. */
	#stayUntilTapElsewhere() {
		if (this.#off) return;
		const close = (e: Event) => {
			if (e.type === 'pointerdown' && this.#anchor?.contains(e.target as Node)) return;
			this.#close();
		};
		addEventListener('pointerdown', close, { capture: true });
		addEventListener('scroll', close, { capture: true, passive: true });
		const closeThis = () => this.#close();
		tapped.add(closeThis);
		this.#off = () => {
			removeEventListener('pointerdown', close, { capture: true });
			removeEventListener('scroll', close, { capture: true });
			tapped.delete(closeThis);
		};
	}

	#close() {
		this.#off?.();
		this.#off = null;
		this.card = null;
	}

	#set(card: DexCardState, opts: { key?: string }) {
		const same = this.card?.n === card.n;
		this.card = { ...card, entry: same ? (this.card?.entry ?? null) : null };
		loadDex(card.n, opts.key).then((entry) => {
			if (this.card?.n === card.n) this.card = { ...this.card, entry, state: entry?.state ?? this.card.state };
		});
	}

	hide() {
		if (this.#off) return; // opened by a tap: stays until a tap elsewhere
		this.#timer = setTimeout(() => this.#close(), 80);
	}
}
