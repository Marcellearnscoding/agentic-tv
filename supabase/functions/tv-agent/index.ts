// The assistant's "brain". Runs on Supabase as an Edge Function.
//
// The TV sends: { text: "put on something funny", apps: [{ name, package }],
//                 person?: { name, likes: ["loves sci-fi", ...] } }   (no person = a guest)
// We ask Claude, which answers by calling "tools" (open_app, search_youtube, ...).
// We send those back to the TV as actions: { reply, actions: [{ type, ... }] }.
//
// Secrets:
//   ANTHROPIC_API_KEY  your Claude API key, stored as a Supabase secret (never in GitHub)
//   The TV's password: only its SHA-256 fingerprint is below. The password itself
//   lives only on the TV, so strangers can't use (and spend) your assistant.
//
// Privacy: nothing is stored. The request is forgotten once the answer is sent.
// What the assistant remembers about a person lives only on the TV, which sends
// it along with each question. Guests are never remembered.

import Anthropic from "@anthropic-ai/sdk";

const anthropic = new Anthropic({ apiKey: Deno.env.get("ANTHROPIC_API_KEY") });
const TV_TOKEN_SHA256 = "2582aaa1d1e82c1d75a0e0ce0014e85f6d9909e78930741ec0e219d15864872e";

async function sha256(text: string): Promise<string> {
  const bytes = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(text));
  return Array.from(new Uint8Array(bytes)).map((b) => b.toString(16).padStart(2, "0")).join("");
}

// The things Claude is allowed to make the TV do.
const tools = [
  {
    name: "open_app",
    description: "Open an app installed on the TV. Use the exact package name from the installed apps list.",
    input_schema: {
      type: "object",
      properties: { package: { type: "string", description: "Package name, e.g. com.netflix.ninja" } },
      required: ["package"],
      additionalProperties: false,
    },
  },
  {
    name: "search_youtube",
    description: "Open YouTube on the TV with search results for a query. Good for 'show me videos about X' or a specific song/show.",
    input_schema: {
      type: "object",
      properties: { query: { type: "string" } },
      required: ["query"],
      additionalProperties: false,
    },
  },
  {
    name: "set_volume",
    description: "Set the TV volume to a percentage (0-100).",
    input_schema: {
      type: "object",
      properties: { percent: { type: "integer", description: "0 to 100" } },
      required: ["percent"],
      additionalProperties: false,
    },
  },
  {
    name: "change_volume",
    description: "Nudge the volume up or down one step, or mute/unmute.",
    input_schema: {
      type: "object",
      properties: { direction: { type: "string", enum: ["up", "down", "mute", "unmute"] } },
      required: ["direction"],
      additionalProperties: false,
    },
  },
  {
    name: "open_settings",
    description: "Open the TV's settings screen.",
    input_schema: { type: "object", properties: {}, required: [], additionalProperties: false },
  },
].map((t) => ({ ...t, strict: true }));

// Only offered when a family member (not a guest) is using the TV.
const memoryTools = [
  {
    name: "remember",
    description:
      "Save a short note about what this person likes or dislikes to watch, e.g. 'loves cooking shows' or 'prefers German audio'. " +
      "Use it when they share a lasting taste, not for one-off requests. Never save private or sensitive details (health, politics, religion, passwords, addresses).",
    input_schema: {
      type: "object",
      properties: { note: { type: "string", description: "Under 12 words, written about the person" } },
      required: ["note"],
      additionalProperties: false,
    },
  },
  {
    name: "forget_likes",
    description: "Erase everything remembered about this person. Use only when they ask you to forget what you know about them.",
    input_schema: { type: "object", properties: {}, required: [], additionalProperties: false },
  },
].map((t) => ({ ...t, strict: true }));

const SYSTEM = `You are the friendly assistant built into a TV in a family's guest room.
Guests and family talk to you with the remote. Do what they ask using your tools, then
answer in one or two short, warm sentences that will be shown on the TV screen.
Reply in the same language the person used.
Only open apps from the installed apps list. If they want something to watch and no
installed app clearly fits, search YouTube. If you can't do something, say so kindly.
Never ask follow-up questions unless the request is truly unclear; pick a sensible option.`;

function json(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}

Deno.serve(async (req) => {
  if (req.method !== "POST") return json({ error: "Use POST" }, 405);
  if ((await sha256(req.headers.get("x-tv-token") ?? "")) !== TV_TOKEN_SHA256) {
    return json({ error: "Not allowed" }, 401);
  }

  let text = "";
  let apps: { name: string; package: string }[] = [];
  let person: { name: string; likes: string[] } | null = null;
  try {
    const body = await req.json();
    text = String(body.text ?? "").slice(0, 500);
    apps = Array.isArray(body.apps) ? body.apps.slice(0, 200) : [];
    if (body.person && typeof body.person.name === "string") {
      person = {
        name: body.person.name.slice(0, 20),
        likes: Array.isArray(body.person.likes) ? body.person.likes.slice(0, 20).map((l: unknown) => String(l).slice(0, 120)) : [],
      };
    }
  } catch {
    return json({ error: "Bad request" }, 400);
  }
  if (!text.trim()) return json({ reply: "I didn't catch that. Try again?", actions: [] });

  const appList = apps.map((a) => `- ${a.name}: ${a.package}`).join("\n");
  const who = person
    ? `You're talking with ${person.name}, a family member.\n` +
      (person.likes.length
        ? `What you remember they like:\n${person.likes.map((l) => `- ${l}`).join("\n")}\nUse this to pick things they'll enjoy.`
        : "You don't know their tastes yet.")
    : "You're talking with a guest. Don't try to remember anything about them.";

  try {
    // deno-lint-ignore no-explicit-any
    const response: any = await anthropic.beta.messages.create({
      model: "claude-opus-5-5",
      max_tokens: 4000,
      output_config: { effort: "low" }, // quick answers; this is a TV remote, not homework
      betas: ["server-side-fallback-2026-07-01"],
      fallbacks: "default", // if a request is declined, the API retries on a fallback model
      system: SYSTEM,
      tools: person ? [...tools, ...memoryTools] : tools,
      tool_choice: { type: "auto" },
      messages: [
        {
          role: "user",
          content: `${who}\n\nInstalled apps:\n${appList}\n\nThe person said: "${text}"`,
        },
      ],
      // deno-lint-ignore no-explicit-any
    } as any);

    if (response.stop_reason === "refusal") {
      return json({ reply: "Sorry, I can't help with that one.", actions: [] });
    }

    const actions: Record<string, unknown>[] = [];
    const replyParts: string[] = [];
    for (const block of response.content) {
      if (block.type === "tool_use") actions.push({ type: block.name, ...block.input });
      if (block.type === "text" && block.text.trim()) replyParts.push(block.text.trim());
    }
    return json({ reply: replyParts.join(" "), actions });
  } catch (err) {
    console.error("Claude request failed", err);
    return json({ reply: "Sorry, my brain is having a moment. Try again in a bit.", actions: [] }, 502);
  }
});
