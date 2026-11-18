import { describe, expect, it } from 'vitest';
import { initials, usernameOf } from './pictures.svelte';

describe('names', () => {
	it('makes initials from letters only', () => {
		expect(initials('Ash Ketchum')).toBe('AK');
		expect(initials('Tomás 🔥 Test')).toBe('TT');
		expect(initials('🔥 Ölaf')).toBe('ÖL');
		expect(initials('🔥')).toBe('');
	});
	it('makes usernames from the e-mail', () => {
		expect(usernameOf('ash.k@x.com')).toBe('@ash.k');
	});
});
