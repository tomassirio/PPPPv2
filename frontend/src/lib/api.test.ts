import { describe, expect, it, vi } from 'vitest';
import { api, ApiError } from './api';

describe('api', () => {
	it('sends the CSRF cookie as X-XSRF-TOKEN and maps ProblemDetail errors', async () => {
		vi.stubGlobal('document', { cookie: 'a=1; XSRF-TOKEN=tok%3D' });
		const fetchMock = vi.fn().mockResolvedValue(
			new Response(JSON.stringify({ detail: 'Your console is not running' }), { status: 409 })
		);
		vi.stubGlobal('fetch', fetchMock);

		const err = await api('POST', '/me/inputs', { command: 'a' }).catch((e) => e);

		expect(fetchMock.mock.calls[0][1].headers['X-XSRF-TOKEN']).toBe('tok=');
		expect(err).toBeInstanceOf(ApiError);
		expect(err).toMatchObject({ status: 409, detail: 'Your console is not running' });
	});
});
