export default async function handler(req: Request): Promise<Response> {
  if (req.method !== "POST") {
    return new Response("Method not allowed", { status: 405 });
  }

  const secret = process.env.SYNC_FIFA_SECRET;
  if (!secret) {
    return new Response("Missing SYNC_FIFA_SECRET", { status: 500 });
  }

  try {
    const response = await fetch(
      "https://xkdggawpuldcdfweanzy.supabase.co/functions/v1/sync-fifa-matches",
      {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          "x-sync-secret": secret,
        },
        body: "{}",
      },
    );

    const body = await response.text();
    return new Response(body, {
      status: response.ok ? 200 : 502,
      headers: { "Content-Type": "application/json" },
    });
  } catch (err) {
    return new Response(String(err), { status: 500 });
  }
}

export const config = { runtime: "nodejs" };
