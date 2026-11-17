import { describe, expect, it } from 'vitest';
import { activityText, ago, backoff, badgeMasks, bitCount, boardRows, hue, keyName, keyToButton, winRate, ordinal, PRESETS, rebind, place, playerStatus, profileFrom, progressPct, rarityOf, tierOf, shownAchievements, versionOf, type ScoreRow } from './play';

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
		expect(keyToButton({ key: 'w' })).toBeNull(); // arrows layout: letters do not move
		expect(keyToButton({ key: 'W' }, PRESETS.wasd)).toBe('up'); // Shift or Caps Lock held
		expect(keyToButton({ key: 'a' }, PRESETS.wasd)).toBe('left');
		expect(keyToButton({ key: 'k' }, PRESETS.wasd)).toBe('a');
		expect(keyToButton({ key: 'ArrowDown' }, PRESETS.wasd)).toBeNull();
		expect(keyToButton({ key: ' ', target: { tagName: 'BUTTON' } as never }, { ...PRESETS.arrows, a: ' ' })).toBeNull();
		expect(keyToButton({ key: 'z', target: { tagName: 'BUTTON', closest: () => ({}) } as never })).toBeNull(); // in a dialog
	});

	it('works out the share of link battles won', () => {
		expect(winRate({ wins: 1, losses: 3, trades: 0, partners: 1 })).toBe(25);
		expect(winRate({ wins: 0, losses: 0, trades: 4, partners: 2 })).toBeNull();
		expect(winRate(undefined)).toBeNull();
	});

	it('rebinds one key per button, swapping with the button that had it', () => {
		const { bindings, swapped } = rebind(PRESETS.arrows, 'a', 'X');
		expect(bindings.a).toBe('x');
		expect(swapped).toBe('b');
		expect(bindings.b).toBe('z');
		expect(rebind(PRESETS.arrows, 'up', 'q')).toEqual({ bindings: { ...PRESETS.arrows, up: 'q' }, swapped: null });
	});

	it('names keys for people', () => {
		expect(keyName('ArrowUp')).toBe('↑');
		expect(keyName(' ')).toBe('Space');
		expect(keyName('k')).toBe('K');
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

describe('scoreboard helpers', () => {
	const row = (userId: string, o: Partial<ScoreRow>): ScoreRow =>
		({ userId, points: 0, badges: 0, caught: 0, seen: 0, game: null, online: false, trainerScore: 0, title: '', grade: 'F', achievementPoints: 0, achievementsUnlocked: 0, party: [], ...o }) as ScoreRow;
	const names = { g: 'Pokémon Gold', c: 'Pokémon Crystal', s: 'Pokémon Silver' };
	const rows = [
		row('a', { trainerScore: 500, badges: 2, caught: 40, game: 'g', party: [{ species: 1, level: 20 }] }),
		row('b', { trainerScore: 400, badges: 5, caught: 10, game: 'c', link: { wins: 1, losses: 3, trades: 0, partners: 1 }, party: [{ species: 2, level: 9 }, { species: 3, level: 50, egg: true }] }),
		row('c', { trainerScore: 400, badges: 1, caught: 60, game: 's', link: { wins: 1, losses: 0, trades: 2, partners: 2 }, party: [{ species: 4, level: 33 }] })
	];
	const ids = (r: ScoreRow[]) => r.map((x) => x.userId).join('');

	it('sorts by the chosen column, ties keep the ranking, eggs do not count as levels', () => {
		expect(ids(boardRows(rows, 'All', 'points', names))).toBe('abc');
		expect(ids(boardRows(rows, 'All', 'badges', names))).toBe('bac');
		expect(ids(boardRows(rows, 'All', 'link', names))).toBe('cba'); // wins, then trades
		expect(ids(boardRows(rows, 'All', 'winrate', names))).toBe('cba'); // 100%, 25%, no battles last
		expect(ids(boardRows(rows, 'All', 'dex', names))).toBe('cab');
		expect(ids(boardRows(rows, 'All', 'level', names))).toBe('cab');
	});
	it('filters by version from the cartridge name or id', () => {
		expect(ids(boardRows(rows, 'Crystal', 'points', names))).toBe('b');
		expect(versionOf('POKEMON_SLV', {})).toBeNull();
		expect(versionOf('gold-rom', {})).toBe('Gold');
		expect(versionOf(null, names)).toBeNull();
	});
	it('splits badges and places', () => {
		expect(badgeMasks({ badges: 10 })).toEqual({ johto: 255, kanto: 3 });
		expect(badgeMasks({ badges: 3, johtoBadges: 0b101, kantoBadges: 0 })).toEqual({ johto: 5, kanto: 0 });
		expect(bitCount(0b1011)).toBe(3);
		expect(place('Ecruteak City · Pokémon Center')).toEqual({ city: 'Ecruteak City', spot: 'Pokémon Center' });
		expect(place(null)).toEqual({ city: '', spot: '' });
		expect(place("New Bark Town · Elm's Lab")).toEqual({ city: 'New Bark Town', spot: "Elm's Lab" });
		expect(place('Pokémon Center 2F')).toEqual({ city: 'Pokémon Center 2F', spot: '' }); // a room alone is the place
	});
});

describe('profile helpers', () => {
	it('pieces a profile together from the row, catalogue and feed', () => {
		const row = { userId: 'a@x', points: 0, badges: 1, caught: 5, seen: 9, game: null, online: true, trainerScore: 10, title: 'Rookie', grade: 'F', achievementPoints: 0, achievementsUnlocked: 1, party: [{ species: 25, level: 9 }, { species: 0, level: 0, egg: true }] } as ScoreRow;
		const p = profileFrom(row, [{ id: 'x', name: 'X', description: 'd', points: 5, unlockedAt: 1 }, { id: 'y', name: 'Y', description: 'd', points: 5, unlockedAt: null }],
			[{ user: 'a@x', type: 'BADGE', detail: 'Zephyr', points: 1, at: 1 }, { user: 'b@x', type: 'BADGE', detail: 'Hive', points: 1, at: 2 }], (n) => `#${n}`);
		expect(p.party.map((m) => m.name)).toEqual(['#25', 'Egg']);
		expect(p.achievements.map((a) => a.unlocked)).toEqual([true, false]);
		expect(p.recent).toHaveLength(1);
		expect(p.stats).toMatchObject({ caught: 5, seen: 9, trades: null });
	});
	it('shows progress only for locked counts', () => {
		expect(progressPct({ unlocked: false, progress: 3, goal: 10 })).toBe(30);
		expect(progressPct({ unlocked: false, progress: 10, goal: 10 })).toBe(99);
		expect(progressPct({ unlocked: true, progress: 3, goal: 10 })).toBeNull();
		expect(progressPct({ unlocked: false, progress: 0, goal: 1 })).toBeNull();
		expect(progressPct({ unlocked: false })).toBeNull();
	});

	it('rates achievements by points when the backend sends no rarity', () => {
		expect([1, 3, 5, 10, 25, 50].map(rarityOf)).toEqual(['common', 'common', 'uncommon', 'rare', 'epic', 'legendary']);
		const tiers = [['SEEN', 2], ['LEVEL', 5], ['CAUGHT', 10], ['BADGE', 100], ['HALL_OF_FAME', 500], ['ACHIEVEMENT', 25], ['TITLE', 0]] as const;
		expect(tiers.map(([type, points]) => tierOf({ type, points }))).toEqual(['common', 'uncommon', 'rare', 'epic', 'legendary', 'epic', 'common']);
	});

	it('sorts achievements by rarity and masks hidden ones until unlocked', () => {
		const a = (id: string, points: number, unlocked: boolean, extra = {}) => ({ id, name: id, description: `${id}!`, points, unlocked, ...extra });
		// Older backend: rarity from points, a locked legendary is hidden, an unlocked one is not.
		const old = shownAchievements([a('leg', 50, false), a('epic', 25, true), a('c1', 1, false), a('leg2', 50, true), a('c2', 2, true)]);
		expect(old.map((x) => x.id)).toEqual(['c1', 'c2', 'epic', 'leg', 'leg2']);
		expect(old[3]).toMatchObject({ name: '???', description: '???', rarity: 'legendary', masked: true });
		expect(old[4]).toMatchObject({ name: 'leg2', masked: false });
		// Newer backend: its rarity wins over points; hidden=true masks (with the legendary frame), hidden=false never does.
		const neu = shownAchievements([
			a('h', 10, false, { rarity: 'rare', hidden: true, name: null, description: null }),
			a('l', 50, false, { rarity: 'legendary', hidden: false }),
			a('u', 50, false, { rarity: 'uncommon', hidden: false })
		]);
		expect(neu.map((x) => [x.id, x.rarity, x.name])).toEqual([
			['u', 'uncommon', 'u'],
			['h', 'legendary', '???'],
			['l', 'legendary', 'l']
		]);
	});
});
