import { api, ApiError } from './api';

/** name: their Google name; nickname: what everyone sees them as; username: "@" and their e-mail's local part. */
export type Me = { id: string; name: string; picture: string | null; roles: string[]; nickname?: string; username?: string };

// Shared session state. `me` is null until loaded or when signed out.
export const session = $state<{ me: Me | null; loaded: boolean }>({ me: null, loaded: false });

export async function loadMe(): Promise<Me | null> {
	try {
		session.me = (await api<Me | undefined>('GET', '/me')) ?? null; // 204: not signed in
	} catch (e) {
		if (!(e instanceof ApiError) || e.status !== 401) throw e;
		session.me = null;
	}
	session.loaded = true;
	return session.me;
}

export const isAdmin = (me: Me | null) => !!me?.roles.includes('ADMIN');

export async function logout() {
	await api('POST', '/logout');
	session.me = null;
	location.href = '/';
}

// Holds live state and event subscriptions: hot-swapping it in dev would leave a fresh copy with no listeners
// while the page keeps the old one, so a change here reloads the page instead.
import.meta.hot?.accept(() => location.reload());

/** Where "Sign in with Google" goes: Google, or locally the backend's dev login (its first PPPP_ADMINS e-mail). */
export const SIGN_IN = import.meta.env.DEV ? '/dev/login' : '/oauth2/authorization/google';
