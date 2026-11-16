// use:fitItems: shows only the items (default: li) that fit inside the element's height, hiding the rest from the
// first one that would be cut. Lists fill the space they get without ever scrolling. Re-checks on resize and when
// the list changes.
export function fitItems(node: HTMLElement, selector = 'li') {
	const run = () => {
		const items = [...node.querySelectorAll<HTMLElement>(selector)];
		items.forEach((i) => (i.style.display = ''));
		const bottom = node.getBoundingClientRect().bottom + 0.5;
		let hiding = false;
		for (const i of items) {
			if (!hiding && i.getBoundingClientRect().bottom > bottom) hiding = true;
			if (hiding) i.style.display = 'none';
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
