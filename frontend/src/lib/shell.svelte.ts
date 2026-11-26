// The player's chosen controller shell, shared so the page can echo its colour (phones: the selected tab, the
// bezel's chat toggle). Remembered per browser: the colour, and Solid or Atomic (see-through) where it has both.
import { SHELLS, VERSION_SHELL, saveShell, savedShell, variant, type Shell } from './shells';

const saved = savedShell();
/** value: the shell as worn (the atomic plastic when on); value.id is the colour's id either way. */
export const currentShell = $state<{ value: Shell; atomic: boolean; chosen: boolean }>({
	value: variant(saved.base, saved.atomic),
	atomic: saved.atomic,
	chosen: saved.chosen
});

export function pickShell(id: string) {
	const base = SHELLS.find((s) => s.id === id) ?? SHELLS[0];
	currentShell.value = variant(base, currentShell.atomic);
	currentShell.chosen = true;
	saveShell(base.id, currentShell.atomic);
}

/** Until the player picks a colour, the pad wears their version's (atomic; not saved, so it follows the game). */
export function wearVersion(version: keyof typeof VERSION_SHELL | null) {
	if (currentShell.chosen || !version) return;
	const base = SHELLS.find((s) => s.id === VERSION_SHELL[version]);
	if (!base) return;
	currentShell.atomic = true;
	currentShell.value = variant(base, true);
}

/** The Solid / Atomic switch: the same colour in the other plastic. */
export function setAtomic(on: boolean) {
	currentShell.atomic = on;
	pickShell(currentShell.value.id);
}
