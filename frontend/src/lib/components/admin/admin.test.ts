import { describe, expect, it, vi } from 'vitest';
import { ApiError } from '#lib/api.ts';
import { act, notice } from './admin.svelte';

describe('act', () => {
	it('reports success, ApiError detail, and skips when confirm is declined', async () => {
		expect(await act('Reset', async () => 42)).toBe(42);
		expect(notice).toEqual({ text: 'Reset: done', error: false });

		await act('Upload', () => Promise.reject(new ApiError(409, 'Cartridge red already exists')));
		expect(notice).toEqual({ text: 'Upload failed: Cartridge red already exists', error: true });

		vi.stubGlobal('confirm', () => false);
		const fn = vi.fn();
		expect(await act('Flush', fn, 'Sure?')).toBeUndefined();
		expect(fn).not.toHaveBeenCalled();
	});
});
