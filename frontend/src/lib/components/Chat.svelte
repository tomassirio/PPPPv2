<!-- CONTRACT: self-contained chat panel, no props. Fills its container's height; the play page decides placement. -->
<script lang="ts">
	import { nameOf } from '#lib/pictures.svelte.ts';
	import { EMOTES, FORMATS, isEmote, plainText } from '#lib/chat/bubble.ts';
	import Name from '#lib/components/Name.svelte';
	import { onMount, tick } from 'svelte';
	import { api, ApiError } from '../api';
	import { on } from '../events';
	import { session, isAdmin } from '../session.svelte';
	import {
		merge,
		remove,
		nameColor,
		REACTIONS,
		myReaction,
		setReactions,
		type ChatMessage
	} from '../chat/messages';

	const PAGE = 50;
	const MAX = 300;

	let messages = $state<ChatMessage[]>([]);
	let hasMore = $state(true);
	let loadingOlder = $state(false);
	let draft = $state('');
	let sending = $state(false);
	let error = $state('');
	let note = $state('');
	let muteMinutes = $state(60);
	/** Message whose reaction choices are open, or null. */
	let picking = $state<string | null>(null);
	let list: HTMLUListElement;

	const admin = $derived(isAdmin(session.me));

	// Per-viewer preference: hide SYSTEM (and, for admins, ADMIN) lines so only people chatting show.
	let showSystem = $state(read() !== '0');
	function read() {
		try {
			return localStorage.getItem('chat.showSystem');
		} catch {
			return null;
		}
	}
	$effect(() => {
		try {
			localStorage.setItem('chat.showSystem', showSystem ? '1' : '0');
		} catch {
			// storage blocked: preference lasts this page only
		}
	});
	const isSystem = (m: ChatMessage) => m.kind === 'SYSTEM' || m.kind === 'ADMIN';
	// Emotes (formatted lines, ok, <3, :youlost: ...) play over the speaker's head; the list leaves them out unless
	// the reader turns them on, like system lines.
	let showEmotes = $state(readKey('chat.showEmotes') === '1');
	function readKey(key: string) {
		try {
			return localStorage.getItem(key);
		} catch {
			return null;
		}
	}
	$effect(() => {
		try {
			localStorage.setItem('chat.showEmotes', showEmotes ? '1' : '0');
		} catch {
			// storage blocked: preference lasts this page only
		}
	});
	const emote = (m: ChatMessage) => m.kind === 'USER' && isEmote(m.text);
	// Only admins see system lines and emotes in the list (and the switches for them); players only see people.
	const shown = $derived(
		messages.filter((m) => (admin && showSystem) || !isSystem(m)).filter((m) => (admin && showEmotes) || !emote(m))
	);

	const nearBottom = () => list.scrollHeight - list.scrollTop - list.clientHeight < 60;
	/** True while the reader sits at the bottom; only their own scrolling changes it. */
	let stick = true;
	const pin = () => {
		if (stick) list.scrollTop = list.scrollHeight;
	};

	// Toggling system messages or a deletion changes the content, not the box: pin again.
	$effect(() => {
		void shown.length;
		if (list) tick().then(pin);
	});

	function onScroll() {
		stick = nearBottom();
		if (list.scrollTop < 40) loadOlder();
	}

	/** Adds messages; follows the bottom unless the reader scrolled up. */
	async function add(incoming: ChatMessage[]) {
		messages = merge(messages, incoming);
		await tick();
		pin();
	}

	async function loadOlder() {
		if (loadingOlder || !hasMore || !messages.length) return;
		loadingOlder = true;
		try {
			const page = await api<ChatMessage[]>('GET', `/chat/messages?limit=${PAGE}&before=${messages[0].id}`);
			hasMore = page.length === PAGE;
			const before = list.scrollHeight;
			messages = merge(messages, page);
			await tick();
			list.scrollTop += list.scrollHeight - before; // keep the reader's place
		} catch (e) {
			error = e instanceof ApiError ? e.detail : 'Could not load older messages.';
		} finally {
			loadingOlder = false;
		}
	}

	onMount(() => {
		api<ChatMessage[]>('GET', `/chat/messages?limit=${PAGE}`)
			.then(async (page) => {
				hasMore = page.length === PAGE;
				messages = merge(messages, page);
				await tick();
				pin();
			})
			.catch(() => (error = 'Could not load chat history.'));
		// The page re-lays out (screen size, party box loading): stay on the newest line.
		const resized = new ResizeObserver(pin);
		resized.observe(list);
		const offs = [
			on('CHAT_MESSAGE', (m: ChatMessage) => add([m])),
			on('CHAT_DELETED', ({ id }: { id: string }) => (messages = remove(messages, id))),
			on('CHAT_REACTION', async ({ id, reactions }: { id: string; reactions: Record<string, string[]> }) => {
				messages = setReactions(messages, id, reactions);
				await tick();
				pin();
			}),
			on('CHAT_CLEARED', () => (messages = messages.filter((m) => !isSystem(m))))
		];
		return () => {
			offs.forEach((off) => off());
			resized.disconnect();
		};
	});

	// A format or emote from the help panel into the message: an emote replaces it (it only works alone).
	function insert(text: string, alone = false) {
		draft = alone ? text : draft ? `${draft.replace(/\s*$/, '')} ${text}` : text;
		queueMicrotask(() => document.getElementById('chat-input')?.focus());
	}

	async function send(e?: Event) {
		e?.preventDefault();
		const text = draft.trim();
		if (!text || sending) return;
		sending = true;
		error = note = '';
		try {
			const m = await api<ChatMessage>('POST', '/chat/messages', { text });
			draft = '';
			stick = true; // your own message: always jump down
			await add([m]);
		} catch (err) {
			if (!(err instanceof ApiError)) error = 'Could not send. Check your connection.';
			else if (err.status === 429) error = 'Slow down: one message per second.';
			else error = err.detail;
		} finally {
			sending = false;
		}
	}

	function onKey(e: KeyboardEvent) {
		if (e.key === 'Enter' && !e.shiftKey && !e.isComposing) send(e);
	}

	async function del(m: ChatMessage) {
		try {
			await api('DELETE', `/admin/chat/messages/${encodeURIComponent(m.id)}`);
			messages = remove(messages, m.id);
		} catch (e) {
			error = e instanceof ApiError ? e.detail : 'Could not delete.';
		}
	}

	/** Picks a reaction, or takes yours back when you pick the one you already have. */
	async function react(m: ChatMessage, reaction: string) {
		picking = null;
		const undo = myReaction(m, session.me?.id) === reaction;
		const path = `/chat/messages/${encodeURIComponent(m.id)}/reaction`;
		try {
			const reactions = await api<Record<string, string[]>>(undo ? 'DELETE' : 'PUT', path, undo ? undefined : { reaction });
			messages = setReactions(messages, m.id, reactions);
			await tick();
			pin();
		} catch (e) {
			if (!(e instanceof ApiError)) error = 'Could not react. Check your connection.';
			else if (e.status === 429) error = 'Slow down a little.';
			else error = e.detail;
		}
	}

	async function mute(m: ChatMessage) {
		try {
			await api('POST', `/admin/users/${encodeURIComponent(m.user)}/mute`, { minutes: muteMinutes });
			error = '';
			note = `${nameOf(m.user)} muted for ${muteMinutes} min.`;
		} catch (e) {
			error = e instanceof ApiError ? e.detail : 'Could not mute.';
		}
	}

	/** "9 Nov, 11:46" in the viewer's locale. */
	const time = (at: number) =>
		new Date(at).toLocaleString([], { day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit' });
</script>

<!-- Keys typed or pressed anywhere in chat must never reach the game's keyboard controls. -->
<!-- svelte-ignore a11y_no_noninteractive_element_interactions -->
<section
	class="panel chat"
	aria-label="Chat"
	onkeydown={(e) => {
		e.stopPropagation();
		if (e.key === 'Escape') picking = null;
	}}
	onkeyup={(e) => e.stopPropagation()}
>
	<header>
		<h2>Chat</h2>
		{#if admin}
			<!-- Admins only: switch system lines and emotes into the list. -->
			<div class="filters" role="group" aria-label="Show in the list">
				<button type="button" class="filter" aria-pressed={showSystem} onclick={() => (showSystem = !showSystem)}>System</button>
				<button type="button" class="filter" aria-pressed={showEmotes} onclick={() => (showEmotes = !showEmotes)}>Emotes</button>
			</div>
			<label class="mute-pick">
				Mute for
				<select bind:value={muteMinutes}>
					<option value={5}>5 min</option>
					<option value={60}>1 hour</option>
					<option value={1440}>1 day</option>
				</select>
			</label>
		{/if}
	</header>

	<ul bind:this={list} onscroll={onScroll}>
		{#if hasMore && messages.length}
			<li class="older">
				<button type="button" onclick={loadOlder} disabled={loadingOlder}>
					{loadingOlder ? 'Loading…' : 'Load older messages'}
				</button>
			</li>
		{/if}
		{#each shown as m (m.id)}
			{@const system = isSystem(m)}
			<li class:system class:mine={m.user === session.me?.id}>
				<!-- Name, react and time on one line; the message underneath. System lines keep their text on the first line. -->
				<div class="head">
					{#if system}<span class="sr-only">System: </span><span class="text grow">{m.text}</span>{:else}<span class="who grow" style:--name={nameColor(m.user)}><Name user={m.user} link /></span>{/if}
					{#if m.kind === 'ADMIN'}<span class="tag admin-only">admin only</span>{/if}
					{#if m.kind !== 'ADMIN'}
						<button
							type="button"
							class="react"
							class:open={picking === m.id}
							aria-expanded={picking === m.id}
							aria-label="React to {system ? 'system message' : `message from ${nameOf(m.user)}`}"
							title="React"
							onclick={() => (picking = picking === m.id ? null : m.id)}><img src={REACTIONS[0].src} alt="" /></button
						>
					{/if}
					<time class="ts" datetime={new Date(m.at).toISOString()}>{time(m.at)}</time>
				</div>
				{#if !system}<p class="text body">{plainText(m.text)}</p>{/if}
				{#if admin && !system}
					<span class="mod">
						<button type="button" onclick={() => del(m)} aria-label="Delete message from {nameOf(m.user)}">Delete</button>
						{#if m.user !== session.me?.id}
							<button type="button" onclick={() => mute(m)} aria-label="Mute {nameOf(m.user)}">Mute</button>
						{/if}
					</span>
				{/if}
				{#if m.kind !== 'ADMIN' && (picking === m.id || Object.keys(m.reactions ?? {}).length)}
					{@const mine = myReaction(m, session.me?.id)}
					<div class="reactions">
						{#each REACTIONS as r (r.id)}
							{@const n = m.reactions?.[r.id]?.length ?? 0}
							{#if n || picking === m.id}
								<button
									type="button"
									class="reaction"
									aria-pressed={mine === r.id}
									aria-label="{r.label}{n ? `, ${n}` : ''}"
									title={r.label}
									onclick={() => react(m, r.id)}
								>
									<img src={r.src} alt="" />
									{#if n}<span class="n">{n}</span>{/if}
								</button>
							{/if}
						{/each}
					</div>
				{/if}
			</li>
		{/each}
	</ul>

	<form onsubmit={send}>
		<label for="chat-input" class="sr-only">Message</label>
		<textarea
			id="chat-input"
			rows="1"
			maxlength={MAX}
			placeholder="Say something to the group"
			bind:value={draft}
			onkeydown={onKey}
			readonly={sending}
			aria-describedby="chat-count chat-error"
		></textarea>
		<!-- What can be typed: formats and emotes, on hover or focus; a pick goes into the message. -->
		<div class="help">
			<button type="button" class="help-btn" aria-label="Formats and emotes" aria-describedby="chat-help">Aa</button>
			<div id="chat-help" class="help-panel" role="tooltip">
				<p class="help-h">Formats</p>
				<ul>
					{#each FORMATS as f (f.example)}
						<li><button type="button" onclick={() => insert(f.insert)}><code>{f.example}</code><span>{f.label}</span></button></li>
					{/each}
				</ul>
				<p class="help-h">Emotes <span>(sent alone: over your head on the screen)</span></p>
				<ul class="emotes">
					{#each EMOTES as e (e.example)}
						<li><button type="button" onclick={() => insert(e.example, true)}><code>{e.example}</code><span>{e.shows}</span></button></li>
					{/each}
				</ul>
			</div>
		</div>
		<button type="submit" disabled={sending || !draft.trim()}>Send</button>
	</form>
	<div class="foot">
		<p id="chat-error" class:error={!!error} role="status">{error || note}</p>
		<span id="chat-count" class="count" class:full={draft.length >= MAX}>{draft.length}/{MAX}</span>
	</div>
</section>

<style>
	/* The panel spans the message row (the form), so it stays inside the chat card. */
	form {
		position: relative;
	}
	.help {
		align-self: stretch;
		display: flex;
	}
	.help-btn {
		min-width: 40px;
		padding: 0 10px;
		border: 1px solid var(--border);
		border-radius: 8px;
		background: transparent;
		color: var(--muted);
		font: 700 14px var(--body-font);
		cursor: help;
	}
	.help-panel {
		position: absolute;
		bottom: calc(100% + 8px);
		left: 0;
		right: 0;
		z-index: 30;
		max-height: 60vh;
		overflow-y: auto;
		padding: 10px 12px;
		border: 1px solid var(--border);
		border-radius: 12px;
		background: var(--panel);
		box-shadow: 0 10px 30px rgb(0 0 0 / 0.35);
		visibility: hidden;
		opacity: 0;
		translate: 0 4px;
		transition:
			opacity 0.12s,
			translate 0.12s,
			visibility 0.12s;
	}
	/* Shown while hovered or focused (keyboard: Tab to "Aa", then into the list; a tap on touch screens). */
	.help:hover .help-panel,
	.help:focus-within .help-panel {
		visibility: visible;
		opacity: 1;
		translate: 0 0;
	}
	.help-h {
		margin: 6px 0 4px;
		font-size: 11px;
		font-weight: 800;
		text-transform: uppercase;
		letter-spacing: 0.06em;
		color: var(--muted);
	}
	.help-h span {
		text-transform: none;
		font-weight: 500;
		letter-spacing: 0;
	}
	.help-panel ul {
		margin: 0;
		padding: 0;
		list-style: none;
		display: grid;
		gap: 2px;
	}
	.help-panel ul.emotes {
		grid-template-columns: 1fr 1fr;
	}
	.help-panel li button {
		width: 100%;
		height: auto;
		min-height: 0;
		line-height: 1.3;
		display: flex;
		align-items: center;
		justify-content: space-between;
		gap: 8px;
		padding: 4px 6px;
		border: none;
		border-radius: 6px;
		background: transparent;
		color: var(--text);
		font: 13px var(--body-font);
		text-align: left;
		cursor: pointer;
	}
	.help-panel li button:hover,
	.help-panel li button:focus-visible {
		background: color-mix(in srgb, var(--muted) 14%, transparent);
	}
	.help-panel code {
		font: 12px ui-monospace, monospace;
		color: var(--accent-text);
	}
	.help-panel li span {
		color: var(--muted);
		font-size: 12px;
	}
	@media (prefers-reduced-motion: reduce) {
		.help-panel {
			transition: none;
		}
	}
	.chat {
		height: 100%;
		min-height: 0;
		box-sizing: border-box;
		padding: 20px;
		display: flex;
		flex-direction: column;
		gap: 14px;
	}
	header {
		display: flex;
		align-items: center;
		gap: 12px;
		min-height: 28px;
	}
	h2 {
		margin: 0;
		font-size: 12px;
		font-weight: 600;
		letter-spacing: 0.08em;
		text-transform: uppercase;
		color: var(--muted);
	}
	.filters {
		margin-left: auto;
		display: flex;
		gap: 4px;
	}
	.filter {
		height: 26px;
		padding: 0 10px;
		border-radius: 999px;
		border: 1px solid var(--border);
		background: none;
		color: var(--muted);
		font: inherit;
		font-size: 12px;
		font-weight: 600;
		cursor: pointer;
	}
	.filter[aria-pressed='true'] {
		border-color: var(--accent-text);
		background: var(--accent-tint);
		color: var(--accent-text);
	}
	.filter:focus-visible {
		outline: 2px solid var(--accent-text);
		outline-offset: 2px;
	}
	/* First in the line but floated right, so long messages wrap under it. */
	.head {
		display: flex;
		align-items: center;
		gap: 6px;
		min-width: 0;
	}
	.grow {
		flex: 1;
		min-width: 0;
	}
	.body {
		margin: 2px 0 0;
	}
	.ts {
		flex: none;
		line-height: 20px;
		font-size: 11px;
		font-style: normal;
		font-variant-numeric: tabular-nums;
		color: var(--muted);
	}
	.mute-pick {
		display: flex;
		align-items: center;
		gap: 6px;
		font-size: 12px;
		color: var(--muted);
	}
	.mute-pick select {
		padding: 3px 6px;
	}
	select,
	textarea {
		background: var(--bg);
		border: 1px solid var(--border-strong);
		border-radius: 8px;
		color: var(--text);
		font: inherit;
	}
	ul {
		margin: 0;
		padding: 0;
		list-style: none;
		display: flex;
		flex-direction: column;
		gap: 12px;
		flex: 1 1 auto;
		min-height: 0;
		overflow-y: auto;
	}
	li {
		position: relative; /* anchors the screen-reader-only "System:" inside the scrolled list */
		font-size: 14px;
		line-height: 20px;
		overflow-wrap: anywhere;
	}
	.text {
		white-space: pre-wrap;
	}
	.who {
		font-weight: 700;
		color: color-mix(in oklab, var(--name, var(--text)), var(--text) var(--name-dim));
	}
	/* Status lines, quieter than people's messages: smaller, muted, italic, a touch of indent. */
	.system {
		padding-left: 10px;
		font-size: 13px;
		color: var(--muted);
		font-style: italic;
	}
	.admin-only {
		font-style: normal;
		margin-right: 4px;
	}
	.mine {
		border-left: 2px solid var(--accent-text);
		padding-left: 8px;
	}
	.tag {
		font-size: 11px;
		padding: 0 5px;
		border-radius: 4px;
		border: 1px solid var(--border);
		color: var(--muted);
		vertical-align: 1px;
	}
	.older {
		text-align: center;
	}
	.older button,
	.mod button {
		background: none;
		border: none;
		color: var(--muted);
		font-size: 12px;
		padding: 2px 4px;
		text-decoration: underline;
	}
	/* React: a greyed Pikachu left of the time on every line's first row; full colour on hover, focus or while open. */
	.react {
		flex: none;
		display: grid;
		place-items: center;
		width: 24px;
		height: 20px;
		padding: 0;
		background: none;
		border: none;
		border-radius: 6px;
	}
	.react img {
		width: 18px;
		height: 18px;
		filter: grayscale(1);
		opacity: 0.45;
	}
	.react:hover img,
	.react:focus-visible img,
	.react.open img {
		filter: none;
		opacity: 1;
	}
	.reactions {
		display: flex;
		flex-wrap: wrap;
		gap: 4px;
		margin-top: 4px;
		font-style: normal;
	}
	.reaction {
		display: inline-flex;
		align-items: center;
		gap: 2px;
		height: 28px;
		padding: 0 8px 0 4px;
		border-radius: 15px;
		border: 1px solid var(--border);
		background: var(--bg);
		color: var(--text);
		font-size: 12px;
		font-variant-numeric: tabular-nums;
	}
	.reaction[aria-pressed='true'] {
		border-color: var(--accent-text);
		background: color-mix(in oklab, var(--accent-text) 15%, var(--bg));
	}
	.reaction img {
		width: 22px;
		height: 22px;
	}
	.reaction .n {
		min-width: 1ch;
		line-height: 1;
	}
	.mod button:hover {
		color: var(--danger);
	}
	form {
		display: flex;
		gap: 8px;
		align-items: flex-end;
	}
	textarea {
		flex: 1 1 auto;
		min-width: 0;
		min-height: 44px;
		max-height: 120px;
		box-sizing: border-box;
		padding: 11px 12px;
		font-size: 14px;
		resize: vertical;
	}
	form button {
		height: 44px;
		padding: 0 16px;
		border-radius: 8px;
		border: none;
		background: var(--accent);
		color: var(--ink);
		font-weight: 600;
		font-size: 14px;
	}
	form button:disabled {
		opacity: 0.5;
		cursor: default;
	}
	button:focus-visible,
	textarea:focus-visible,
	select:focus-visible {
		outline: 2px solid var(--accent-text);
		outline-offset: 2px;
	}
	.foot {
		display: flex;
		justify-content: space-between;
		gap: 8px;
		font-size: 12px;
		line-height: 17px;
		margin-top: -8px;
	}
	.foot p {
		margin: 0;
		color: var(--muted);
	}
	.foot p.error {
		color: var(--danger);
	}
	.count {
		color: var(--muted);
		font-variant-numeric: tabular-nums;
	}
	.count.full {
		color: var(--danger);
	}
	.sr-only {
		position: absolute;
		width: 1px;
		height: 1px;
		overflow: hidden;
		clip: rect(0 0 0 0);
		white-space: nowrap;
	}
	/* Phones: the send button and the checkbox in the player's plate colour (set on the play page). */
	@media (max-width: 760px), (orientation: landscape) and (max-height: 520px) {
		.filter[aria-pressed='true'] {
			border-color: var(--plate, var(--accent-text));
			color: var(--plate, var(--accent-text));
		}
		form button {
			background: var(--plate, var(--accent));
			color: var(--plate-ink, var(--ink));
		}
	}
</style>
