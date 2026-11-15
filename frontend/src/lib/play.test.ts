import { describe, expect, it } from 'vitest';
import { activityText, afterArrowRelease, ago, backoff, hue, keyToButton, ordinal, playerStatus } from './play';

describe('play helpers', () => {
	it('maps keys, but not while typing or for held keys', () => {
		expect(keyToButton({ key: 'ArrowLeft' })).toBe('left');
		expect(keyToButton({ key: 'z' })).toBe('a');
		expect(keyToButton({ key: 'Shift' })).toBe('select');
		expect(keyToButton({ key: 'q' })).toBeNull();
		expect(keyToButton({ key: 'z', repeat: true })).toBeNull();
		expect(keyToButton({ key: 'z', target: { tagName: 'INPUT' } as never })).toBeNull();
		expect(keyToButton({ key: 'Enter', target: { tagName: 'BUTTON' } as never })).toBeNull();
		expect(keyToButton({ key: 'x', target: { tagName: 'BUTTON' } as never })).toBe('b');
	});

	it('backs off exponentially with a cap', () => {
		expect([0, 1, 2].map(backoff)).toEqual([500, 1000, 2000]);
		expect(backoff(20)).toBe(30_000);
	});
});

import { inkFor, SHELLS, shellVars } from './shells';

describe('shells', () => {
	it('picks readable ink and keeps the original first', () => {
		expect(inkFor('#ffffff')).toBe('#1d1f1c');
		expect(inkFor('#141416')).toBe('#ffffff');
		expect(inkFor('rgb(235 235 242 / 0.45)')).toBe('#1d1f1c'); // translucent "Clear" shells
		expect(inkFor('rgb(40 140 225 / 0.65)')).toBe('#ffffff');
		expect(SHELLS[0].id).toBe('original');
		expect(new Set(SHELLS.map((s) => s.id)).size).toBe(SHELLS.length);
		expect(shellVars(SHELLS[0])).toContain('--ab:#bc4b51');
	});
});

describe('walking with held arrows', () => {
	it('keeps walking the new direction when the old arrow is let go', () => {
		// holding UP, pressed LEFT (now walking LEFT), then UP goes up
		expect(afterArrowRelease(['left'], 'left', 'up')).toBe('keep');
	});
	it('goes back to the arrow still held when the walked one is let go', () => {
		expect(afterArrowRelease(['up'], 'left', 'left')).toBe('up');
	});
	it('stops when nothing is held', () => {
		expect(afterArrowRelease([], 'left', 'left')).toBeNull();
	});
});

describe('scoreboard activity', () => {
	it('words each kind of event and how long ago it was', () => {
		const e = { user: 'jordan@x.y', type: 'BADGE', detail: 'Boulder', points: 100, at: 0 };
		expect(activityText(e)).toBe('jordan earned the Boulder Badge');
		expect(activityText({ ...e, type: 'LEVEL', detail: '20' })).toBe('jordan reached Lv. 20');
		expect(activityText({ ...e, type: 'HALL_OF_FAME', detail: '' })).toBe('jordan entered the Hall of Fame');
		expect(activityText({ ...e, type: 'CAUGHT', detail: 'Pidgey' }, 'You')).toBe('You caught Pidgey');
		expect([0, 59_000, 120_000, 3 * 3_600_000, 49 * 3_600_000].map(ago)).toEqual(['now', 'now', '2 min', '3 h', '2 d']);
	});
});

describe('scoreboard status', () => {
	const lobby = [{ userId: 'b@x', kind: 'BATTLE' as const }];
	it('prefers linked, then ready, then online', () => {
		const links = { 'a@x': 'me@x', 'me@x': 'a@x' };
		expect(playerStatus('a@x', true, 'me@x', lobby, links)).toEqual({ kind: 'linked', label: 'Linked with you' });
		expect(playerStatus('me@x', true, 'me@x', lobby, links).label).toBe('Linked with a');
		expect(playerStatus('b@x', true, 'me@x', lobby, links)).toEqual({ kind: 'battle', label: 'Ready to battle' });
		expect(playerStatus('c@x', true, 'me@x', lobby, links).kind).toBe('playing');
		expect(playerStatus('c@x', false, 'me@x', lobby, links).kind).toBe('offline');
	});
	it('ranks and colours', () => {
		expect([1, 2, 3, 4, 11, 12, 13, 21, 22, 101].map(ordinal)).toEqual(['1st', '2nd', '3rd', '4th', '11th', '12th', '13th', '21st', '22nd', '101st']);
		expect(hue('a@x')).toBe(hue('a@x'));
		expect(hue('a@x')).toBeGreaterThanOrEqual(0);
		expect(hue('a@x')).toBeLessThan(360);
	});
});
