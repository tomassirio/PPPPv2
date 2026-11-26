// use:fitItems: shows only the items (default: li) that fit inside the element's height, hiding the rest from the
// first one that would be cut. Lists fill the space they get without ever scrolling. keep: an item that must stay in
// sight (the player's own row): if it would be cut, the items just above it give way instead. Re-checks on resize and
// when the list changes.
export function fitItems(node: HTMLElement, opts: string | { selector?: string; keep?: string } = 'li') {
	const { selector = 'li', keep } = typeof opts === 'string' ? { selector: opts, keep: undefined } : opts;
	const run = () => {
		const items = [...node.querySelectorAll<HTMLElement>(selector)];
		items.forEach((i) => (i.style.display = ''));
		const bottom = node.getBoundingClientRect().bottom + 0.5;
		let hiding = false;
		for (const i of items) {
			if (!hiding && i.getBoundingClientRect().bottom > bottom) hiding = true;
			if (hiding) i.style.display = 'none';
		}
		const kept = keep ? items.find((i) => i.matches(keep)) : undefined;
		if (!kept || kept.style.display !== 'none') return;
		kept.style.display = '';
		for (let i = items.indexOf(kept) - 1; i >= 0 && kept.getBoundingClientRect().bottom > bottom; i--) {
			items[i].style.display = 'none';
		}
	};
	const sizes = new ResizeObserver(run);
	sizes.observe(node);
	const changes = new MutationObserver(run);
	changes.observe(node, { childList: true, subtree: true });
	run();
	return {
		destroy() {
			sizes.disconnect();
			changes.disconnect();
		}
	};
}

// use:fitDrops: in an element whose height is set from outside (container-type: size), shows everything, then, while
// its content overflows, gives up parts by priority, lowest number first: [data-squeeze="n"] gets the class
// "squeezed" (a compact form) at step n, [data-drop="n"] is hidden at step n. Elsewhere (the element grows with its
// content) everything shows. Re-checks on resize and when the content changes.
export function fitDrops(node: HTMLElement) {
	let running = false;
	const run = () => {
		if (running) return;
		running = true;
		const steps: { at: number; el: HTMLElement; squeeze: boolean }[] = [];
		for (const el of node.querySelectorAll<HTMLElement>('[data-drop], [data-squeeze]')) {
			el.style.display = '';
			el.classList.remove('squeezed');
			if (el.dataset.squeeze) steps.push({ at: +el.dataset.squeeze, el, squeeze: true });
			if (el.dataset.drop) steps.push({ at: +el.dataset.drop, el, squeeze: false });
		}
		if (getComputedStyle(node).containerType === 'size') {
			steps.sort((a, b) => a.at - b.at);
			for (const s of steps) {
				if (node.scrollHeight <= node.clientHeight + 1) break;
				if (s.squeeze) s.el.classList.add('squeezed');
				else s.el.style.display = 'none';
			}
		}
		running = false;
	};
	const sizes = new ResizeObserver(run);
	sizes.observe(node);
	const changes = new MutationObserver(run);
	changes.observe(node, { childList: true, subtree: true, characterData: true });
	run();
	return {
		destroy() {
			sizes.disconnect();
			changes.disconnect();
		}
	};
}
