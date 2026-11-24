// The player's chosen controller shell, shared so the page can echo its colour (phones: the selected tab, the
// bezel's chat toggle). Remembered per browser: the colour, and Solid or Atomic (see-through) where it has both.
import { SHELLS, saveShell, savedShell, variant, type Shell } from './shells';

const saved = savedShell();
/** value: the shell as worn (the atomic plastic when on); value.id is the colour's id either way. */
export const currentShell = $state<{ value: Shell; atomic: boolean }>({ value: variant(saved.base, saved.atomic), atomic: saved.atomic });

export function pickShell(id: string) {
	const base = SHELLS.find((s) => s.id === id) ?? SHELLS[0];
	currentShell.value = variant(base, currentShell.atomic);
	saveShell(base.id, currentShell.atomic);
}

/** The Solid / Atomic switch: the same colour in the other plastic. */
export function setAtomic(on: boolean) {
	currentShell.atomic = on;
	pickShell(currentShell.value.id);
}
