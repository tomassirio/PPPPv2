import { api, ApiError } from '#lib/api.ts';

// Shared by the admin panels: the aria-live notice.
export const notice = $state<{ text: string; error: boolean }>({ text: '', error: false });

// Done messages go away by themselves after a few seconds; errors stay until dismissed (or replaced).
let hide: ReturnType<typeof setTimeout> | undefined;
function say(text: string, error: boolean) {
	notice.text = text;
	notice.error = error;
	clearTimeout(hide);
	if (!error) hide = setTimeout(() => (notice.text = ''), 4000);
}

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
		if (!quiet) say(`${label}: done`, false);
		return result;
	} catch (e) {
		say(`${label} failed: ${e instanceof ApiError ? e.detail : String(e)}`, true);
		return undefined;
	}
}
