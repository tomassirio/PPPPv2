<!-- First-time help (lib/tour.svelte.ts) as coach marks: the page dims round a spotlight on the step's targets, a
     speech bubble beside it says what they are (a hand shows the swipe), with Back / Next and Skip. Steps whose targets
     are not on screen are skipped. One help at a time; finishing or skipping marks it seen. Place once on the page. -->
<script lang="ts">
	import { coach, finish } from '#lib/tour.svelte.ts';

	const PAD = 8;
	const current = $derived(coach.queue[0]);
	let index = $state(0);
	let rect = $state<{ x: number; y: number; w: number; h: number } | null>(null);
	let next = $state<HTMLButtonElement>();
	let vh = $state(typeof innerHeight === 'number' ? innerHeight : 800);

	// The union of the step's targets that are on screen, or null.
	function measure(targets: string[]) {
		let x1 = Infinity, y1 = Infinity, x2 = -Infinity, y2 = -Infinity;
		for (const sel of targets) {
			for (const el of document.querySelectorAll(sel)) {
				const r = el.getBoundingClientRect();
				if (r.width < 2 || r.height < 2 || r.bottom < 0 || r.top > innerHeight) continue;
				x1 = Math.min(x1, r.left);
				y1 = Math.min(y1, r.top);
				x2 = Math.max(x2, r.right);
				y2 = Math.max(y2, r.bottom);
			}
		}
		return x1 === Infinity ? null : { x: x1 - PAD, y: y1 - PAD, w: x2 - x1 + PAD * 2, h: y2 - y1 + PAD * 2 };
	}

	// Lands on the first step from `from` (going `dir`) that has something to show; off the end finishes.
	function show(from: number, dir = 1) {
		if (!current) return;
		for (let i = from; i >= 0 && i < current.steps.length; i += dir) {
			const r = measure(current.steps[i].target);
			if (r) {
				index = i;
				rect = r;
				queueMicrotask(() => next?.focus());
				return;
			}
		}
		if (dir > 0) done();
		else show(index, 1);
	}
	function done() {
		if (!current) return;
		finish(current.id);
		index = 0;
		rect = null;
	}

	// A new help: wait for the page to settle (panels sliding, the screen connecting), then the first step.
	$effect(() => {
		if (!current) return;
		const t = setTimeout(() => show(0), 400);
		return () => clearTimeout(t);
	});
	// Keep the spotlight on its targets while the layout moves.
	$effect(() => {
		if (!current || !rect) return;
		const steps = current.steps;
		const follow = () => {
			vh = innerHeight;
			const r = measure(steps[index].target);
			if (r) rect = r;
		};
		const t = setInterval(follow, 300);
		addEventListener('resize', follow);
		return () => {
			clearInterval(t);
			removeEventListener('resize', follow);
		};
	});

	const step = $derived(current && rect ? current.steps[index] : null);
	const many = $derived((current?.steps.length ?? 0) > 1);
	// The bubble below the spotlight when it sits in the top half, else above it.
	const below = $derived(rect ? rect.y + rect.h / 2 < vh / 2 : true);
</script>

<svelte:window
	onkeydown={(e) => {
		if (!step) return;
		if (e.key === 'Escape') done();
		else if (e.key === 'ArrowRight') show(index + 1);
		else if (e.key === 'ArrowLeft' && index > 0) show(index - 1, -1);
	}}
/>

{#if step && rect}
	<div class="coach" role="dialog" aria-modal="true" aria-labelledby="coach-title" aria-describedby="coach-text">
		<!-- The dim with a hole: one box whose huge shadow is the dim. Taps on the dim go nowhere. -->
		<div class="spot" style:left="{rect.x}px" style:top="{rect.y}px" style:width="{rect.w}px" style:height="{rect.h}px">
			{#if step.gesture === 'swipe'}
				<span class="hand" aria-hidden="true">👆</span>
			{/if}
		</div>
		<div
			class="bubble"
			class:below
			style:--tail="{Math.max(24, Math.min(innerWidth - 56, rect.x + rect.w / 2 - 16))}px"
			style:top={below ? `${Math.min(rect.y + rect.h + 14, vh - 200)}px` : null}
			style:bottom={below ? null : `${Math.min(vh - rect.y + 14, vh - 200)}px`}
		>
			{#if many}<p class="count">{index + 1} / {current!.steps.length}</p>{/if}
			<h2 id="coach-title" class="pixel">{step.title}</h2>
			<p id="coach-text">{step.text}</p>
			<div class="actions">
				{#if many && index + 1 < current!.steps.length}<button type="button" class="skip" onclick={done}>Skip tour</button>{/if}
				<span class="grow"></span>
				{#if many && index > 0}<button type="button" class="back" onclick={() => show(index - 1, -1)}>Back</button>{/if}
				<button type="button" class="next" bind:this={next} onclick={() => show(index + 1)}
					>{!many ? 'Got it' : index + 1 < current!.steps.length ? 'Next' : 'Done'}</button
				>
			</div>
		</div>
	</div>
{/if}

<style>
	.coach {
		position: fixed;
		inset: 0;
		z-index: 60; /* over the panels, the toasts and the burger */
	}
	.spot {
		position: fixed;
		border-radius: 14px;
		box-shadow:
			0 0 0 3px rgb(255 255 255 / 0.9),
			0 0 0 200vmax rgb(8 8 16 / 0.72);
		transition:
			left 0.3s ease,
			top 0.3s ease,
			width 0.3s ease,
			height 0.3s ease;
		animation: ring 1.6s ease-out infinite;
		pointer-events: none;
	}
	@keyframes ring {
		0% {
			outline: 0 solid rgb(242 193 78 / 0.7);
			outline-offset: 0;
		}
		100% {
			outline: 3px solid rgb(242 193 78 / 0);
			outline-offset: 14px;
		}
	}
	.hand {
		position: absolute;
		top: 50%;
		left: 70%;
		font-size: 40px;
		translate: -50% -50%;
		filter: drop-shadow(0 4px 6px rgb(0 0 0 / 0.5));
		animation: swipe 1.6s ease-in-out infinite;
	}
	@keyframes swipe {
		0% {
			left: 75%;
			opacity: 0;
		}
		15% {
			opacity: 1;
		}
		70% {
			left: 25%;
			opacity: 1;
		}
		100% {
			left: 20%;
			opacity: 0;
		}
	}
	.bubble {
		position: fixed;
		left: 16px;
		right: 16px;
		padding: 14px 16px 12px;
		border-radius: 14px;
		background: #fff;
		color: #1d1f24;
		border: 3px solid #2a2f3a;
		box-shadow:
			0 0 0 3px #fff,
			0 12px 32px rgb(0 0 0 / 0.5);
		animation: pop 0.25s ease-out;
	}
	/* The bubble's tail, pointing at the spotlight. */
	.bubble::before {
		content: '';
		position: absolute;
		left: var(--tail, 50%);
		width: 14px;
		height: 14px;
		background: #fff;
		border: 3px solid #2a2f3a;
		translate: -50% 0;
		rotate: 45deg;
	}
	.bubble.below::before {
		top: -10px;
		border-right: none;
		border-bottom: none;
	}
	.bubble:not(.below)::before {
		bottom: -10px;
		border-left: none;
		border-top: none;
	}
	@keyframes pop {
		from {
			opacity: 0;
			scale: 0.94;
		}
	}
	.count {
		margin: 0 0 6px;
		font-size: 11px;
		font-weight: 700;
		color: #6b7280;
	}
	h2 {
		margin: 0 0 8px;
		font-size: 12px;
		line-height: 1.4;
	}
	#coach-text {
		margin: 0;
		font-size: 14px;
		line-height: 1.45;
	}
	.actions {
		display: flex;
		align-items: center;
		gap: 8px;
		margin-top: 12px;
	}
	.grow {
		flex: 1;
	}
	button {
		min-height: 36px;
		padding: 0 14px;
		border-radius: 10px;
		font: 700 13px var(--body-font);
		cursor: pointer;
	}
	.skip {
		padding: 0 4px;
		border: none;
		background: none;
		color: #6b7280;
		text-decoration: underline;
	}
	.back {
		border: 2px solid #2a2f3a;
		background: #fff;
		color: #1d1f24;
	}
	.next {
		border: 2px solid #2a2f3a;
		background: #2a2f3a;
		color: #fff;
	}
	button:focus-visible {
		outline: 3px solid #5b9bf0;
		outline-offset: 2px;
	}
	@media (prefers-reduced-motion: reduce) {
		.spot,
		.hand,
		.bubble {
			animation: none;
			transition: none;
		}
		.hand {
			left: 50%;
		}
	}
</style>
