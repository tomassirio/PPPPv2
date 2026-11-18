// Hovering a Pokémon to see its Pokédex card (DexCard). One DexHover per component: show(n, el, ...) on pointer enter,
// hide() on leave; render <DexCard {...hover.card} /> while hover.card is set. of: whose Pokédex answers (a Pib's id;
// none: the viewer's own), GET /game/dex/{n}?of=. Answers are cached per Pokédex, number and key (a new catch asks again).
import { api } from '#lib/api.ts';

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
};

type Side = 'right' | 'left' | 'above';
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

export function loadDex(n: number, of?: string, key = ''): Promise<DexEntry | null> {
	const id = `${of ?? ''}:${n}:${key}`;
	let p = cache.get(id);
	if (!p) {
		p = api<DexEntry>('GET', `/game/dex/${n}${of ? `?of=${encodeURIComponent(of)}` : ''}`).catch(() => null);
		cache.set(id, p);
	}
	return p;
}

export class DexHover {
	card = $state<DexCardState | null>(null);
	#timer: ReturnType<typeof setTimeout> | undefined;

	/**
	 * The card for n beside el: side "above" centres it over el, "right"/"left" centre it vertically beside el, kept
	 * on screen (half: about half the card's height plus a margin). state: what to show before the answer comes.
	 */
	show(
		n: number,
		el: HTMLElement,
		opts: { side?: Side; of?: string; key?: string; state?: DexCardState['state']; name?: string; note?: string } = {}
	) {
		clearTimeout(this.#timer);
		const r = el.getBoundingClientRect();
		const zoom = (el as HTMLElement & { currentCSSZoom?: number }).currentCSSZoom ?? 1;
		const side = opts.side ?? 'above';
		const half = 130 * zoom;
		let x: number, y: number;
		if (side === 'above') {
			x = Math.min(Math.max(160 * zoom, r.left + r.width / 2), innerWidth - 160 * zoom);
			y = r.top - 8;
			if (y < 280 * zoom) return this.#beside(n, r, zoom, opts); // no room above: beside it instead
		} else {
			x = side === 'right' ? r.right + 10 : r.left - 10;
			y = Math.min(Math.max(half, r.top + r.height / 2), innerHeight - half);
		}
		this.#set({ n, x, y, side, zoom, state: opts.state ?? 'caught', entry: null, name: opts.name, note: opts.note }, opts);
	}

	#beside(n: number, r: DOMRect, zoom: number, opts: { of?: string; key?: string; state?: DexCardState['state']; name?: string; note?: string }) {
		const right = r.right + 320 * zoom < innerWidth;
		const half = 130 * zoom;
		const y = Math.min(Math.max(half, r.top + r.height / 2), innerHeight - half);
		this.#set({ n, x: right ? r.right + 10 : r.left - 10, y, side: right ? 'right' : 'left', zoom, state: opts.state ?? 'caught', entry: null, name: opts.name, note: opts.note }, opts);
	}

	#set(card: DexCardState, opts: { of?: string; key?: string }) {
		const same = this.card?.n === card.n;
		this.card = { ...card, entry: same ? (this.card?.entry ?? null) : null };
		loadDex(card.n, opts.of, opts.key).then((entry) => {
			if (this.card?.n === card.n) this.card = { ...this.card, entry, state: entry?.state ?? this.card.state };
		});
	}

	hide() {
		this.#timer = setTimeout(() => (this.card = null), 80);
	}
}
