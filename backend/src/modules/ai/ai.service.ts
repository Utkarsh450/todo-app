import type { AiClient } from './ai.client.js';
import { buildBreakdownMessages, parseSubtasks, type BreakdownInput } from './breakdown.js';

export class AiService {
  constructor(
    private readonly client: AiClient,
    private readonly now: () => Date = () => new Date(),
  ) {}

  status() {
    return { configured: this.client.configured, model: this.client.model };
  }

  /** Suggests subtasks for a task. Nothing is saved: the app shows them and the user adds the ones they want. */
  async breakdown(input: BreakdownInput) {
    const reply = await this.client.complete(buildBreakdownMessages(input, this.now()));
    return { subtasks: parseSubtasks(reply) };
  }
}
