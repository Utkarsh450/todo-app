import { aiBadResponse, aiNotConfigured, aiTimeout, aiUpstream } from '../../lib/errors.js';
import type { Logger } from '../../lib/logger.js';

export interface ChatMessage {
  role: 'system' | 'user';
  content: string;
}

/** The one thing the AI features need from a language model: send messages, get text back. */
export interface AiClient {
  readonly configured: boolean;
  readonly model: string;
  complete(messages: ChatMessage[]): Promise<string>;
}

export interface XaiOptions {
  apiKey?: string | undefined;
  baseUrl: string;
  model: string;
  timeoutMs: number;
  logger?: Logger;
  /** Injected in tests; defaults to the global `fetch`. */
  fetchImpl?: typeof fetch;
  /** Injected in tests; the pause before the one retry. */
  sleep?: (ms: number) => Promise<void>;
}

const SNIPPET = 200;
const RETRY_DELAY_MS = 500;

/**
 * Talks to xAI's OpenAI-style chat endpoint. Errors are turned into short messages that are safe to show:
 * the API key and the raw response body never leave this file (only a short snippet goes to the log).
 */
export function createXaiClient(options: XaiOptions): AiClient {
  const { apiKey, model, timeoutMs, logger } = options;
  const url = `${options.baseUrl.replace(/\/+$/, '')}/chat/completions`;
  const doFetch = options.fetchImpl ?? fetch;

  const sleep = options.sleep ?? ((ms: number) => new Promise<void>((resolve) => setTimeout(resolve, ms)));

  return {
    configured: Boolean(apiKey),
    model,
    async complete(messages) {
      if (!apiKey) throw aiNotConfigured();

      const send = async (): Promise<Response> => {
        try {
          return await doFetch(url, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${apiKey}` },
            body: JSON.stringify({ model, messages, stream: false }),
            signal: AbortSignal.timeout(timeoutMs),
          });
        } catch (e) {
          const name = (e as { name?: string }).name;
          if (name === 'TimeoutError' || name === 'AbortError') throw aiTimeout();
          logger?.warn('ai request failed', { reason: (e as Error).message });
          throw aiUpstream('Could not reach the AI service. Check your internet connection.');
        }
      };

      let response = await send();
      // Busy (429) or broken for a moment (5xx): one more try after a short pause, then the usual clear message.
      if (response.status === 429 || response.status >= 500) {
        await response.body?.cancel().catch(() => undefined);
        logger?.warn('ai service busy, retrying once', { status: response.status });
        await sleep(RETRY_DELAY_MS);
        response = await send();
      }

      if (!response.ok) {
        const body = await response.text().catch(() => '');
        logger?.warn('ai service error', { status: response.status, body: body.slice(0, SNIPPET) });
        throw aiUpstream(upstreamMessage(response.status));
      }

      let data: unknown;
      try {
        data = await response.json();
      } catch {
        throw aiBadResponse('The AI service sent a reply that could not be read.');
      }
      const content = (data as { choices?: { message?: { content?: unknown } }[] })?.choices?.[0]?.message?.content;
      if (typeof content !== 'string' || content.trim() === '') throw aiBadResponse('The AI service sent an empty answer.');
      return content;
    },
  };
}

function upstreamMessage(status: number): string {
  if (status === 401 || status === 403) return 'The AI service did not accept the API key. Check XAI_API_KEY in backend/.env.';
  if (status === 404) return 'The AI service does not know that model. Check XAI_MODEL in backend/.env.';
  if (status === 429) return 'The AI service is busy or over its limit. Try again in a moment.';
  return `The AI service had a problem (status ${status}). Try again.`;
}
