// @ts-check
'use strict';

const readline = require('node:readline');

/**
 * A tool of the server: what the client lists, and what runs when it calls it. `run` returns the structured result;
 * a result with `isError` tells the model the command failed, in a way it can act on.
 * @typedef {{
 *   name: string;
 *   title: string;
 *   description: string;
 *   inputSchema: Record<string, unknown>;
 *   annotations: { readOnlyHint: boolean; destructiveHint?: boolean; idempotentHint?: boolean; openWorldHint: boolean };
 *   run: (args: Record<string, unknown>) => Promise<{ result: unknown; isError?: boolean }>;
 * }} Tool
 */

// The revision without a handshake, where every request names its version; and the ones with `initialize`, which
// clients still speak (Claude Code takes either, by its runtime).
const MODERN = '2026-07-28';
const LEGACY = ['2025-11-25', '2025-06-18', '2025-03-26'];
const SUPPORTED = [MODERN, ...LEGACY];
const VERSION = 'io.modelcontextprotocol/protocolVersion';
const SERVER_INFO = 'io.modelcontextprotocol/serverInfo';

/**
 * Serves tools over stdio as an MCP server: one JSON-RPC message per line in and out, nothing else on the output.
 * Requests run one after another, so that a command's output, which the tools catch, never mixes with another's. It
 * ends when the input does.
 * @param {{
 *   info: { name: string; version: string };
 *   instructions: string;
 *   tools: Tool[];
 *   input?: NodeJS.ReadableStream;
 *   send?: (line: string) => void;
 * }} server `send` writes a line to the client: the real stdout, taken before any tool runs
 * @returns {Promise<void>} when the input ends
 */
function serve({ info, instructions, tools, input = process.stdin, send }) {
  const write = send ?? process.stdout.write.bind(process.stdout);
  const reply = (/** @type {Record<string, unknown>} */ message) =>
    write(JSON.stringify({ jsonrpc: '2.0', ...message }) + '\n');
  let queue = Promise.resolve();
  return new Promise((resolve) => {
    const lines = readline.createInterface({ input });
    lines.on('line', (line) => {
      if (!line.trim()) return;
      /** @type {any} */
      let message;
      try {
        message = JSON.parse(line);
      } catch {
        reply({ id: null, error: { code: -32700, message: 'Parse error' } });
        return;
      }
      if (message?.jsonrpc !== '2.0' || typeof message.method !== 'string') {
        if (message?.id !== undefined) {
          reply({ id: message.id, error: { code: -32600, message: 'Invalid request' } });
        }
        return;
      }
      // A notification (initialized, cancelled) asks for no answer.
      if (message.id === undefined) return;
      queue = queue.then(() => handle(message));
    });
    lines.on('close', () => queue.then(() => resolve()));
  });

  /**
   * @param {{ id: string | number; method: string; params?: any }} message
   */
  async function handle({ id, method, params }) {
    try {
      const requested = params?._meta?.[VERSION];
      if (requested !== undefined && !SUPPORTED.includes(requested)) {
        throw rpcError(-32022, 'Unsupported protocol version', { supported: SUPPORTED, requested });
      }
      /** @type {Record<string, unknown>} */
      let result;
      if (method === 'initialize') {
        const asked = params?.protocolVersion;
        result = {
          protocolVersion: LEGACY.includes(asked) ? asked : LEGACY[0],
          capabilities: { tools: { listChanged: false } },
          serverInfo: info,
          instructions,
        };
      } else if (method === 'ping') {
        result = {};
      } else if (method === 'server/discover') {
        result = {
          supportedVersions: SUPPORTED,
          capabilities: { tools: { listChanged: false } },
          instructions,
          ttlMs: 0,
          cacheScope: 'private',
        };
      } else if (method === 'tools/list') {
        result = {
          tools: tools.map(({ name, title, description, inputSchema, annotations }) => ({
            name,
            title,
            description,
            inputSchema,
            annotations,
          })),
          ttlMs: 0,
          cacheScope: 'private',
        };
      } else if (method === 'tools/call') {
        const tool = tools.find(({ name }) => name === params?.name);
        if (!tool) throw rpcError(-32602, `Unknown tool: ${params?.name}`);
        const arguments_ = params?.arguments ?? {};
        if (typeof arguments_ !== 'object' || Array.isArray(arguments_)) {
          throw rpcError(-32602, 'arguments is an object');
        }
        const { result: structured, isError = false } = await tool.run(arguments_);
        const text =
          typeof structured === 'string' ? structured : JSON.stringify(structured, null, 2);
        // Claude Code shows the structured result in place of the text: both carry the same.
        result = {
          content: [{ type: 'text', text }],
          ...(typeof structured === 'object' &&
            structured !== null && { structuredContent: structured }),
          isError,
        };
      } else {
        throw rpcError(-32601, `Method not found: ${method}`);
      }
      if (requested === MODERN) {
        result = { resultType: 'complete', ...result, _meta: { [SERVER_INFO]: info } };
      }
      reply({ id, result });
    } catch (error) {
      const { code = -32603, message = String(error), data } = /** @type {any} */ (error);
      reply({ id, error: { code, message, ...(data !== undefined && { data }) } });
    }
  }
}

/**
 * @param {number} code
 * @param {string} message
 * @param {unknown} [data]
 */
function rpcError(code, message, data) {
  return Object.assign(new Error(message), { code, data });
}

module.exports = { serve, SUPPORTED };
