<!-- Why a link action failed (link.error): a callout with a warning sign. The backend's "Headline: details" messages
     (e.g. "You can't leave right now: in a menu, a dialog or an event. Try again in a moment.") are split into a bold
     headline and the details under it. Dismissable. -->
<script lang="ts">
	import { link } from '#lib/link.svelte.ts';

	const parts = $derived.by(() => {
		const m = link.error;
		const at = m.indexOf(': ');
		if (at < 0) return { head: m, body: '' };
		const body = m.slice(at + 2);
		return { head: m.slice(0, at), body: body.charAt(0).toUpperCase() + body.slice(1) };
	});
</script>

{#if link.error}
	<div class="callout" role="alert">
		<svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"
			><path d="M12 3l10 18H2z" /><path d="M12 10v5M12 18h.01" /></svg
		>
		<div class="text">
			<b>{parts.head}</b>
			{#if parts.body}<span>{parts.body}</span>{/if}
		</div>
		<button type="button" class="x" aria-label="Dismiss" onclick={() => (link.error = '')}>
			<svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" aria-hidden="true"><path d="M6 6l12 12M18 6L6 18" /></svg>
		</button>
	</div>
{/if}

<style>
	.callout {
		display: flex;
		align-items: flex-start;
		gap: 10px;
		padding: 10px 10px 10px 12px;
		border-radius: 10px;
		border: 1px solid color-mix(in oklab, var(--danger) 55%, transparent);
		background: color-mix(in oklab, var(--danger) 12%, transparent);
		color: var(--text);
		font-size: 13px;
		line-height: 18px;
	}
	.callout > svg {
		flex: none;
		margin-top: 1px;
		color: var(--danger);
	}
	.text {
		flex: 1;
		min-width: 0;
		display: flex;
		flex-direction: column;
		gap: 2px;
	}
	.text b {
		color: var(--danger);
	}
	.text span {
		color: var(--muted);
	}
	.x {
		flex: none;
		width: 24px;
		height: 24px;
		display: grid;
		place-items: center;
		padding: 0;
		border: none;
		border-radius: 6px;
		background: none;
		color: var(--muted);
		cursor: pointer;
	}
	.x:hover {
		background: var(--sunk);
		color: var(--text);
	}
	.x:focus-visible {
		outline: 3px solid var(--accent-text);
		outline-offset: 2px;
	}
	@media (prefers-reduced-motion: no-preference) {
		.callout {
			animation: in 0.2s ease-out;
		}
	}
	@keyframes in {
		from {
			opacity: 0;
			transform: translateY(-3px);
		}
	}
</style>
