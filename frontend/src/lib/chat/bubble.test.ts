import { describe, expect, it } from 'vitest';
import { bubble, isEmote, letters, mentions, plainText, type BubbleContext } from './bubble';

const ctx: BubbleContext = {
	players: [{ id: 'ash@x.y', handles: ['ash', 'Ashy'] }],
	species: ['Bulbasaur', 'Ivysaur', 'Venusaur', 'Charmander', 'Charmeleon', 'Charizard', 'Squirtle', 'Wartortle', 'Blastoise', 'Caterpie', 'Metapod', 'Butterfree', 'Weedle', 'Kakuna', 'Beedrill', 'Pidgey', 'Pidgeotto', 'Pidgeot', 'Rattata', 'Raticate', 'Spearow', 'Fearow', 'Ekans', 'Arbok', 'Pikachu']
};
const segs = (t: string) => {
	const b = bubble(t, ctx);
	return 'segs' in b ? b.segs : b;
};

describe('chat bubbles', () => {
	it('swaps a lone word for an emoji or a special', () => {
		for (const t of ['ok', 'OK', ' okay! ', 'oki']) expect(bubble(t)).toEqual({ emoji: '👍' });
		expect(bubble('gg')).toEqual({ emoji: '🏆' });
		for (const t of ['lol', 'LOOL', 'haha', 'jajaja', 'lmao']) expect(bubble(t)).toEqual({ emoji: '😂' });
		expect(bubble(':fucku:')).toEqual({ emoji: '🖕' });
		expect(bubble('<3')).toEqual({ special: 'heart' });
		expect(bubble('??')).toEqual({ special: 'notice' });
		expect(bubble('zzzz')).toEqual({ special: 'zzz' });
		expect(bubble(':youlost:')).toEqual({ special: 'youlost' });
		expect(bubble('ok then')).not.toHaveProperty('emoji');
		expect(bubble('book')).not.toHaveProperty('emoji');
	});

	it('marks each effect and keeps the rest as typed', () => {
		expect(segs('so **cool** right')).toEqual([
			{ kind: 'text', text: 'so ' },
			{ kind: 'rainbow', text: 'cool' },
			{ kind: 'text', text: ' right' }
		]);
		expect(segs('!!no!! ~~wob~~ _err_ ((psst))')).toEqual([
			{ kind: 'shout', text: 'no' },
			{ kind: 'text', text: ' ' },
			{ kind: 'wobble', text: 'wob' },
			{ kind: 'text', text: ' ' },
			{ kind: 'glitch', text: 'err' },
			{ kind: 'text', text: ' ' },
			{ kind: 'whisper', text: 'psst' }
		]);
		expect(segs('2 ** 3 = 8')).toEqual([{ kind: 'text', text: '2 ** 3 = 8' }]); // unclosed
		expect(segs('snake_case_name')).toEqual([{ kind: 'text', text: 'snake_case_name' }]);
		expect(segs('<b>**x**</b>')).toEqual([
			{ kind: 'text', text: '<b>' },
			{ kind: 'rainbow', text: 'x' },
			{ kind: 'text', text: '</b>' }
		]); // text, never HTML
	});

	it('whispers a whole bubble, shrugs and flips off inline', () => {
		expect(bubble('((psst))', ctx)).toEqual({ segs: [{ kind: 'whisper', text: 'psst' }], whisper: true });
		expect(segs('idk :shrug:')).toEqual([{ kind: 'text', text: 'idk ¯\\_(ツ)_/¯' }]);
		expect(segs('you :fucku:')).toEqual([{ kind: 'text', text: 'you 🖕' }]);
	});

	it('links players and Pokémon', () => {
		expect(segs('hi @ASH! and @nobody')).toEqual([
			{ kind: 'text', text: 'hi ' },
			{ kind: 'mention', text: '@ASH', user: 'ash@x.y' },
			{ kind: 'text', text: '! and @nobody' }
		]);
		expect(segs('my pikachu, not Pikachus')).toEqual([
			{ kind: 'text', text: 'my ' },
			{ kind: 'mon', text: 'pikachu', species: 25 },
			{ kind: 'text', text: ', not Pikachus' }
		]);
		expect(mentions('yo @ashy.', ['ash', 'ashy'])).toBe(true);
		expect(mentions('mail me a@ash.com', ['ash'])).toBe(false);
		expect(mentions('hi @ashley', ['ash'])).toBe(false);
	});

	it('tells emotes from things said', () => {
		for (const t of ['ok', ':youlost:', '<3', ':shrug:', ':fucku:', 'zzz']) expect(isEmote(t)).toBe(true);
		for (const t of ['ok then', 'hi @ash', 'my pikachu', 'idk :shrug: maybe', '2 ** 3', '**tuvi**', 'so !!mad!!', '((psst))']) expect(isEmote(t)).toBe(false);
		// Formatted lines are said, shown without their markers.
		expect(plainText('so !!mad!! and **happy** ~~ok~~ ((psst)) _err_')).toBe('so mad and happy ok psst err');
		expect(plainText('idk :shrug: maybe')).toBe('idk ¯\\_(ツ)_/¯ maybe');
		expect(plainText('2 ** 3')).toBe('2 ** 3');
	});

	it('splits letters without breaking emoji', () => {
		expect(letters('hé🔥')).toEqual(['h', 'é', '🔥']);
	});
});
