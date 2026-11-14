import { api, ApiError } from '#lib/api.ts';

// Shared by the admin panels: the aria-live notice.
export const notice = $state<{ text: string; error: boolean }>({ text: '', error: false });

/**
 * Runs one admin action and reports it in the live region.
 * `confirmText` adds a confirm() step for destructive actions; returns the result, or undefined if cancelled/failed.
 * `quiet` reports only failures (for loads).
 */
export async function act<T>(
	label: string,
	fn: () => Promise<T>,
	confirmText?: string,
	quiet = false
): Promise<T | undefined> {
	if (confirmText && !confirm(confirmText)) return undefined;
	try {
		const result = await fn();
		if (!quiet) {
			notice.text = `${label}: done`;
			notice.error = false;
		}
		return result;
	} catch (e) {
		notice.text = `${label} failed: ${e instanceof ApiError ? e.detail : String(e)}`;
		notice.error = true;
		return undefined;
	}
}
