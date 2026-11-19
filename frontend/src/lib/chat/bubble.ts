// What a chat line looks like over a player's head on the game screen (GameScreen). Plain text, never HTML: the line
// is cut into segments and each is rendered as text (or an icon) in its own element.
//
// Inline:  **rainbow**  !!shout!!  ~~wobble~~  _glitch_  ((whisper))  @name (a player: their colour, and a ping)
//          a Pokémon's name (as their game spells it) turns into its icon; :shrug: ¯\_(ツ)_/¯; :fucku: 🖕
// Alone:   ok 👍 · gg 🏆 · lol / haha 😂 · :fucku: 🖕 · <3 a pulsing pixel heart · ? a red ! (an NPC noticing you)
//          zzz floating Z's · :youlost: an upside-down 👌 by the right leg (the circle game) · ((whisper)) a dashed bubble
// Unclosed or empty markers stay as typed.

export type Effect = 'rainbow' | 'shout' | 'wobble' | 'glitch' | 'whisper';
export type BubbleSeg =
	| { kind: 'text'; text: string }
	| { kind: Effect; text: string }
	| { kind: 'mention'; text: string; user: string }
	| { kind: 'mon'; text: string; species: number };
export type Special = 'heart' | 'notice' | 'zzz' | 'youlost';
export type Bubble = { emoji: string } | { special: Special } | { segs: BubbleSeg[]; whisper: boolean };

/** Who can be mentioned (handle: what follows the @, e.g. their username) and the Pokémon names (index + 1 = species). */
export type BubbleContext = { players?: { id: string; handles: string[] }[]; species?: string[] };

const SHRUG = '¯\\_(ツ)_/¯';
const ALONE: [RegExp, Bubble][] = [
	[/^(ok|okay|oki|okey)[.!]*$/i, { emoji: '👍' }],
	[/^gg+[.!]*$/i, { emoji: '🏆' }],
	[/^(lo+l+|lmao+|(ha){2,}h?|(ja){2,}j?)[.!]*$/i, { emoji: '😂' }],
	[/^:fucku:$/i, { emoji: '🖕' }],
	[/^<3+$/, { special: 'heart' }],
	[/^\?+$/, { special: 'notice' }],
	[/^z{3,}$/i, { special: 'zzz' }],
	[/^:youlost:$/i, { special: 'youlost' }]
];

const MARKS: [RegExp, Effect][] = [
	[/\*\*(.+?)\*\*/y, 'rainbow'],
	[/!!(.+?)!!/y, 'shout'],
	[/~~(.+?)~~/y, 'wobble'],
	[/\(\((.+?)\)\)/y, 'whisper'],
	[/_([^_]+?)_(?![\p{L}\p{N}_])/uy, 'glitch']
];

export function bubble(text: string, ctx: BubbleContext = {}): Bubble {
	const t = text.trim();
	for (const [re, b] of ALONE) if (re.test(t)) return b;
	const segs = parse(text, ctx);
	return { segs, whisper: segs.length === 1 && segs[0].kind === 'whisper' };
}

function parse(text: string, ctx: BubbleContext): BubbleSeg[] {
	const segs: BubbleSeg[] = [];
	let plain = '';
	const flush = () => {
		if (plain) segs.push({ kind: 'text', text: plain });
		plain = '';
	};
	const species = new Map((ctx.species ?? []).map((n, i) => [n.toLowerCase(), i + 1]));
	const handles = new Map((ctx.players ?? []).flatMap((p) => p.handles.map((h) => [h.toLowerCase(), p.id] as const)));
	let i = 0;
	outer: while (i < text.length) {
		const prev = i ? text[i - 1] : ' ';
		for (const [re, kind] of MARKS) {
			// _glitch_ only where an underscore starts a word (snake_case stays as typed).
			if (kind === 'glitch' && /[\p{L}\p{N}_]/u.test(prev)) continue;
			re.lastIndex = i;
			const m = re.exec(text);
			if (m && m[1].trim()) {
				flush();
				segs.push({ kind, text: m[1] });
				i = re.lastIndex;
				continue outer;
			}
		}
		if (text.startsWith(':shrug:', i)) {
			plain += SHRUG;
			i += 7;
			continue;
		}
		if (text.startsWith(':fucku:', i)) {
			plain += '🖕';
			i += 7;
			continue;
		}
		if (text[i] === '@' && !/[\p{L}\p{N}]/u.test(prev)) {
			const m = /@([\p{L}\p{N}._-]+)/uy;
			m.lastIndex = i;
			const hit = m.exec(text);
			const handle = hit?.[1].replace(/[._-]+$/, '');
			const user = handle ? handles.get(handle.toLowerCase()) : undefined;
			if (hit && handle && user) {
				flush();
				segs.push({ kind: 'mention', text: `@${handle}`, user });
				i += handle.length + 1;
				continue;
			}
		}
		if (/[\p{L}]/u.test(text[i]) && !/[\p{L}\p{N}]/u.test(prev)) {
			const w = /[\p{L}][\p{L}'♀♂-]*/uy;
			w.lastIndex = i;
			const word = w.exec(text)![0];
			const n = species.get(word.toLowerCase());
			if (n) {
				flush();
				segs.push({ kind: 'mon', text: word, species: n });
			} else plain += word;
			i += word.length;
			continue;
		}
		plain += text[i++];
	}
	flush();
	return segs;
}

/**
 * An emote rather than something said: a lone swap (ok, gg, <3, :youlost: ...), :shrug: alone, or a line with an effect
 * (**rainbow**, !!shout!! ...). These show over the player's head only; the chat list and overlay leave them out
 * (unless the reader turns emotes on), so the chat is not flooded with them.
 */
export function isEmote(text: string): boolean {
	const b = bubble(text);
	if (!('segs' in b)) return true;
	if (/^\s*:shrug:\s*$/i.test(text)) return true;
	return b.segs.some((s) => s.kind !== 'text' && s.kind !== 'mention' && s.kind !== 'mon');
}

/** Whether a chat line mentions this player (any of their handles). */
export function mentions(text: string, handles: string[]): boolean {
	const lower = handles.map((h) => h.toLowerCase());
	for (const m of text.matchAll(/(^|[^\p{L}\p{N}])@([\p{L}\p{N}._-]+)/gu)) {
		if (lower.includes(m[2].replace(/[._-]+$/, '').toLowerCase())) return true;
	}
	return false;
}

/** The letters of a segment, one element each (emoji and accented letters kept whole). */
export const letters = (text: string) => Array.from(text);
