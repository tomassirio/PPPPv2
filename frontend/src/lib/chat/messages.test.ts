import { describe, expect, it } from 'vitest';
import { compareIds, nameColor, merge, myReaction, remove, setReactions, type ChatMessage } from './messages';

const msg = (id: string): ChatMessage => ({
	id,
	user: 'u',
	name: 'U',
	text: id,
	kind: 'USER',
	at: 0
});
const ids = (l: ChatMessage[]) => l.map((m) => m.id);

describe('chat messages', () => {
	it('orders stream ids numerically', () => {
		expect(compareIds('10-0', '9-5')).toBeGreaterThan(0);
		expect(compareIds('9-1', '9-0')).toBeGreaterThan(0);
		expect(compareIds('9-0', '9-0')).toBe(0);
	});

	it('merges newest-first history and live messages oldest-first without duplicates', () => {
		let list = merge([], [msg('3-0'), msg('2-0'), msg('1-0')]); // history is newest first
		list = merge(list, [msg('4-0')]); // POST response
		list = merge(list, [msg('4-0')]); // same message via SSE
		list = merge(list, [msg('0-1'), msg('0-0')]); // older page
		expect(ids(list)).toEqual(['0-0', '0-1', '1-0', '2-0', '3-0', '4-0']);
	});

	it('sets one message\'s reactions and finds the viewer\'s own', () => {
		const list = setReactions([msg('1-0'), msg('2-0')], '2-0', { like: ['a', 'b'], angry: ['c'] });
		expect(list[0].reactions).toBeUndefined();
		expect(myReaction(list[1], 'c')).toBe('angry');
		expect(myReaction(list[1], 'z')).toBeUndefined();
		expect(myReaction(list[0], 'a')).toBeUndefined();
	});

	it('gives each user a stable colour, mostly distinct', () => {
		expect(nameColor('carlo@y.z')).toBe(nameColor('carlo@y.z'));
		const users = ['camilo@y.z', 'eze@y.z', 'carlo@y.z', 'dan@y.z', 'tomas@y.z', 'ana@y.z'];
		expect(new Set(users.map(nameColor)).size).toBe(users.length);
	});

	it('removes by id', () => {
		expect(ids(remove([msg('1-0'), msg('2-0')], '1-0'))).toEqual(['2-0']);
	});
});
