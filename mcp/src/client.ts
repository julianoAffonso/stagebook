// Talks to Stagebook's "Edit on the computer" server on the tablet: the same HTTP API its web page uses.
// Every call carries the X-Stagebook header and, once approved on the tablet, the stagebook_key cookie.

import { mkdir, readFile, writeFile } from "node:fs/promises";
import { networkInterfaces, homedir } from "node:os";
import { dirname, join } from "node:path";

export const COOKIE = "stagebook_key";
const PORTS = [8090, 8091, 8092];

export interface Saved {
  url?: string;
  key?: string;
}

/** Where the tablet's address and key are kept between sessions (or STAGEBOOK_CONFIG). */
export function configPath(): string {
  return process.env.STAGEBOOK_CONFIG ?? join(homedir(), ".config", "stagebook-mcp", "config.json");
}

export async function loadSaved(): Promise<Saved> {
  try {
    return JSON.parse(await readFile(configPath(), "utf8")) as Saved;
  } catch {
    return {};
  }
}

export async function save(s: Saved): Promise<void> {
  await mkdir(dirname(configPath()), { recursive: true });
  // the key gives access to the library: readable by this user only
  await writeFile(configPath(), JSON.stringify(s, null, 2), { mode: 0o600 });
}

/** A problem the assistant should explain to the person, with what to do. */
export class StagebookError extends Error {
  constructor(message: string, readonly status?: number) {
    super(message);
  }
}

export class StagebookClient {
  constructor(public url: string | undefined, public key: string | undefined, private readonly timeoutMs = 15000) {}

  static async fromEnv(): Promise<StagebookClient> {
    const saved = await loadSaved();
    return new StagebookClient(process.env.STAGEBOOK_URL ?? saved.url, process.env.STAGEBOOK_KEY ?? saved.key);
  }

  private base(): string {
    if (!this.url) {
      throw new StagebookError(
        "Stagebook's address isn't known yet. Open Stagebook on the tablet, turn on Settings → Edit on the computer, " +
          "then use the connect tool (it finds the tablet on the network) or set STAGEBOOK_URL (e.g. http://192.168.0.10:8090).",
      );
    }
    return this.url.replace(/\/+$/, "");
  }

  /** One API call. [body]: JSON object, raw bytes (uploads) or nothing. */
  async call(method: string, path: string, body?: unknown, opts: { auth?: boolean; query?: Record<string, string>; timeoutMs?: number } = {}): Promise<{ status: number; json: any; headers: Headers }> {
    const url = new URL(this.base() + path);
    for (const [k, v] of Object.entries(opts.query ?? {})) url.searchParams.set(k, v);
    const headers: Record<string, string> = { "X-Stagebook": "1" };
    if (opts.auth !== false) {
      await this.awaitApproval();
      if (!this.key) throw new StagebookError("This computer isn't approved on the tablet yet: use the connect tool first.");
      headers.Cookie = `${COOKIE}=${this.key}`;
    }
    let payload: BodyInit | undefined;
    if (body instanceof Uint8Array) {
      payload = body as unknown as BodyInit;
      headers["Content-Type"] = "application/octet-stream";
    } else if (body !== undefined) {
      payload = JSON.stringify(body);
      headers["Content-Type"] = "application/json";
    }
    let res: Response;
    try {
      res = await fetch(url, { method, headers, body: payload, signal: AbortSignal.timeout(opts.timeoutMs ?? this.timeoutMs) });
    } catch (e) {
      throw new StagebookError(unreachable(this.base(), e));
    }
    const text = await res.text();
    let json: any = undefined;
    if (text) {
      try {
        json = JSON.parse(text);
      } catch {
        json = text;
      }
    }
    if (res.status === 401) {
      // the dead key goes: kept, the next call (after connecting again) sent it instead of waiting for Allow (audit 03/11)
      if (opts.auth !== false) this.key = undefined;
      throw new StagebookError("The tablet no longer accepts this computer's key (removed, or 90 days unused): use the connect tool again.", 401);
    }
    if (res.status === 403) throw new StagebookError("The tablet refused the call (forbidden).", 403);
    if (res.status >= 400 && res.status !== 404) {
      const why = json && typeof json === "object" && json.error ? json.error : `HTTP ${res.status}`;
      throw new StagebookError(problemText(why), res.status);
    }
    return { status: res.status, json, headers: res.headers };
  }

  async get(path: string): Promise<any> {
    const r = await this.call("GET", path);
    if (r.status === 404) throw new StagebookError("Not found on the tablet.", 404);
    return r.json;
  }

  async send(method: string, path: string, body?: unknown, query?: Record<string, string>, timeoutMs?: number): Promise<any> {
    const r = await this.call(method, path, body, { query, timeoutMs });
    if (r.status === 404) throw new StagebookError("Not found on the tablet.", 404);
    return r.json;
  }

  /** The approval being waited for (pairing runs past the tool call: assistants give up on a call after ~60 s). */
  pending?: Promise<string>;

  /**
   * Asks the tablet to let this computer in: Stagebook shows a request with a code. Returns the code at once; the wait
   * for the person to tap Allow goes on in [pending] (up to [waitMs]), which saves the key when it comes.
   */
  async startPair(name: string, waitMs = 180000): Promise<string> {
    // a new request replaces whatever key was loaded (another tablet, or one the tablet no longer accepts): the next
    // call waits for this approval instead of sending the old key (audit 03/11)
    this.key = undefined;
    const asked = await this.call("POST", "/api/ask", { name }, { auth: false });
    if (asked.status === 429) throw new StagebookError("The tablet is already showing a request: answer it there first.");
    const { id, code } = asked.json ?? {};
    if (!id) throw new StagebookError("The tablet didn't accept the request.");
    this.pending = this.waitApproval(String(id), waitMs).finally(() => { this.pending = undefined; });
    this.pending.catch(() => {});   // a refusal is reported by the next call that waits on it
    return String(code ?? "");
  }

  private async waitApproval(id: string, waitMs: number): Promise<string> {
    const until = Date.now() + waitMs;
    while (Date.now() < until) {
      let r;
      try {
        r = await this.call("GET", "/api/ask", undefined, { auth: false, query: { id } });
      } catch (e) {
        if (e instanceof StagebookError && e.status === 403) throw new StagebookError("The request was denied on the tablet.");
        throw e;
      }
      if (r.status === 200) {
        const key = cookieValue(r.headers.get("set-cookie"));
        if (!key) throw new StagebookError("Approved, but the tablet sent no key.");
        this.key = key;
        await save({ url: this.url, key });
        return key;
      }
      if (r.status === 404) throw new StagebookError("The request expired on the tablet: connect again.");
      await new Promise((ok) => setTimeout(ok, 1500));
    }
    throw new StagebookError("Nobody tapped Allow on the tablet in 3 minutes: connect again.");
  }

  /** Waits a while for a pending approval before a call needs the key (within one tool call's time). */
  async awaitApproval(maxMs = 45000): Promise<void> {
    if (this.key || !this.pending) return;
    const timeout = new Promise<void>((ok) => setTimeout(ok, maxMs));
    await Promise.race([this.pending.then(() => undefined), timeout]);
    if (!this.key) throw new StagebookError("Still waiting for Allow on the tablet: tap it there, then try again.");
  }

  /** Pairs and waits for the approval (tests and scripts; tools use startPair). */
  async pair(name: string, onCode: (code: string) => void, waitMs = 120000): Promise<string> {
    onCode(await this.startPair(name, waitMs));
    return this.pending!;
  }
}

/**
 * Why the tablet couldn't be reached, in words. EHOSTUNREACH while the tablet answers a browser is usually macOS's
 * Local Network privacy blocking the program that runs this server (Node), not the network.
 */
export function unreachable(base: string, e: unknown): string {
  const code = (e as { cause?: { code?: string } })?.cause?.code;
  if (code === "EHOSTUNREACH" && process.platform === "darwin") {
    return `macOS blocked the connection to ${base} (Local Network privacy). Open System Settings → Privacy & Security → ` +
      "Local Network and turn on the app that runs this assistant (Claude, Terminal, Cursor, VS Code…), then restart it.";
  }
  return `Couldn't reach Stagebook at ${base}. Check that the tablet is on the same Wi-Fi, Stagebook is open and ` +
    "Settings → Edit on the computer is on (it turns itself off when unused).";
}

/** The stagebook_key value in a Set-Cookie header. */
export function cookieValue(setCookie: string | null): string | undefined {
  const m = setCookie?.match(new RegExp(`${COOKIE}=([^;]+)`));
  return m?.[1];
}

/** The app's error codes, in words an assistant can pass on. */
export function problemText(code: string): string {
  const known: Record<string, string> = {
    title_empty: "The song needs a title.",
    title_taken: "There's already a song with this title and artist.",
    not_song: "That doesn't look like lyrics or chords (Stagebook keeps only song text).",
    offline: "Stagebook has the internet or the band turned off (essential mode), or there's no connection.",
    bad_address: "The address must start with http:// or https://.",
    unreadable: "Couldn't read that page or file.",
    not_found: "Not found.",
    search_page: "That's a search page, not a song page: pass the song's own address.",
    expired: "That expired: start again.",
    format: "That file isn't an audio format Stagebook plays (OGG, MP3 or WAV).",
  };
  return known[code] ?? `The tablet answered: ${code}`;
}

/** Does Stagebook's editor answer at [base]? (Its page's strings: the editor is on and it is Stagebook.) */
export async function answers(base: string, timeoutMs = 600): Promise<boolean> {
  try {
    const res = await fetch(base.replace(/\/+$/, "") + "/api/strings", { headers: { "X-Stagebook": "1" }, signal: AbortSignal.timeout(timeoutMs) });
    return res.ok && (await res.text()).includes("{");
  } catch {
    return false;
  }
}

/**
 * Looks for Stagebook on the local networks (every address of each /24 this computer is on, ports 8090-8092): the
 * editor answers its page there while it's on. Returns every tablet that answers: on a band's Wi-Fi there can be
 * several, and the request must not land on a bandmate's tablet (audit 03/11).
 */
export async function discoverAll(timeoutMs = 600): Promise<string[]> {
  const prefixes = new Set<string>();
  for (const list of Object.values(networkInterfaces())) {
    for (const a of list ?? []) {
      if (a.family === "IPv4" && !a.internal && /^(10|172|192)\./.test(a.address)) prefixes.add(a.address.split(".").slice(0, 3).join("."));
    }
  }
  const candidates: string[] = [];
  for (const p of prefixes) for (let i = 1; i < 255; i++) for (const port of PORTS) candidates.push(`http://${p}.${i}:${port}`);
  const found: string[] = [];
  // in batches, so a whole /24 doesn't open hundreds of sockets at once
  for (let i = 0; i < candidates.length; i += 128) {
    const batch = candidates.slice(i, i + 128);
    const ok = await Promise.all(batch.map((u) => answers(u, timeoutMs)));
    batch.forEach((u, k) => { if (ok[k]) found.push(u); });
  }
  return found;
}
