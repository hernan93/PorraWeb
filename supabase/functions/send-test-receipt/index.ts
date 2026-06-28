import { serve } from "https://deno.land/std@0.168.0/http/server.ts";

const corsHeaders: Record<string, string> = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};

function escapeHtml(value: string): string {
  return value
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;")
    .replace(/'/g, "&#039;");
}

const MOCK_ROWS = [
  [73, "Ronda de 32", "Espa\u00F1a (ESP)", "2 - 1", "Marruecos (MAR)", "Espa\u00F1a (ESP)"],
  [74, "Ronda de 32", "Alemania (GER)", "3 - 0", "Por definir", "Alemania (GER)"],
  [75, "Ronda de 32", "Argentina (ARG)", "2 - 1", "Croacia (CRO)", "Argentina (ARG)"],
  [76, "Ronda de 32", "Francia (FRA)", "2 - 0", "Senegal (SEN)", "Francia (FRA)"],
  [77, "Ronda de 32", "Portugal (POR)", "1 - 1", "Por definir", "Portugal (POR)"],
  [78, "Ronda de 32", "Brasil (BRA)", "3 - 1", "Uruguay (URU)", "Brasil (BRA)"],
  [79, "Ronda de 32", "Inglaterra (ENG)", "2 - 0", "Ecuador (ECU)", "Inglaterra (ENG)"],
  [80, "Ronda de 32", "Pa\u00EDses Bajos (NED)", "2 - 1", "Jap\u00F3n (JPN)", "Pa\u00EDses Bajos (NED)"],
  [81, "Ronda de 32", "Italia (ITA)", "1 - 0", "Colombia (COL)", "Italia (ITA)"],
  [82, "Ronda de 32", "B\u00E9lgica (BEL)", "1 - 1", "M\u00E9xico (MEX)", "B\u00E9lgica (BEL)"],
  [83, "Ronda de 32", "Dinamarca (DEN)", "2 - 1", "Ir\u00E1n (IRN)", "Dinamarca (DEN)"],
  [84, "Ronda de 32", "Suiza (SUI)", "0 - 0", "Estados Unidos (USA)", "Estados Unidos (USA)"],
  [85, "Ronda de 32", "Noruega (NOR)", "2 - 0", "Arabia Saudita (KSA)", "Noruega (NOR)"],
  [86, "Ronda de 32", "Canad\u00E1 (CAN)", "0 - 1", "Sud\u00E1frica (RSA)", "Sud\u00E1frica (RSA)"],
  [87, "Ronda de 32", "Ghana (GHA)", "1 - 2", "Corea del Sur (KOR)", "Corea del Sur (KOR)"],
  [88, "Ronda de 32", "Chile (CHI)", "1 - 0", "Egipto (EGY)", "Chile (CHI)"],
  [89, "Octavos", "Espa\u00F1a (ESP)", "2 - 1", "Alemania (GER)", "Espa\u00F1a (ESP)"],
  [90, "Octavos", "Argentina (ARG)", "1 - 0", "Francia (FRA)", "Argentina (ARG)"],
  [91, "Octavos", "Portugal (POR)", "1 - 1", "Brasil (BRA)", "Brasil (BRA)"],
  [92, "Octavos", "Inglaterra (ENG)", "0 - 0", "Pa\u00EDses Bajos (NED)", "Pa\u00EDses Bajos (NED)"],
  [93, "Octavos", "Italia (ITA)", "2 - 0", "B\u00E9lgica (BEL)", "Italia (ITA)"],
  [94, "Octavos", "Dinamarca (DEN)", "3 - 2", "Estados Unidos (USA)", "Dinamarca (DEN)"],
  [95, "Octavos", "Noruega (NOR)", "1 - 0", "Sud\u00E1frica (RSA)", "Noruega (NOR)"],
  [96, "Octavos", "Corea del Sur (KOR)", "0 - 1", "Chile (CHI)", "Chile (CHI)"],
  [97, "Cuartos de final", "Espa\u00F1a (ESP)", "2 - 1", "Argentina (ARG)", "Espa\u00F1a (ESP)"],
  [98, "Cuartos de final", "Brasil (BRA)", "2 - 0", "Pa\u00EDses Bajos (NED)", "Brasil (BRA)"],
  [99, "Cuartos de final", "Italia (ITA)", "1 - 0", "Dinamarca (DEN)", "Italia (ITA)"],
  [100, "Cuartos de final", "Noruega (NOR)", "0 - 2", "Chile (CHI)", "Chile (CHI)"],
  [101, "Semifinales", "Espa\u00F1a (ESP)", "3 - 2", "Brasil (BRA)", "Espa\u00F1a (ESP)"],
  [102, "Semifinales", "Italia (ITA)", "1 - 1", "Chile (CHI)", "Italia (ITA)"],
  [104, "Tercer puesto", "Brasil (BRA)", "2 - 1", "Chile (CHI)", "Brasil (BRA)"],
  [103, "Final", "Espa\u00F1a (ESP)", "2 - 1", "Italia (ITA)", "Espa\u00F1a (ESP)"],
];

const matchRows = MOCK_ROWS.map(([num, phase, home, score, away, winner]) => {
  return `<tr>` +
    `<td style="padding:6px 8px;border-bottom:1px solid #e2e8f0">${escapeHtml(String(num))}</td>` +
    `<td style="padding:6px 8px;border-bottom:1px solid #e2e8f0">${escapeHtml(String(phase))}</td>` +
    `<td style="padding:6px 8px;border-bottom:1px solid #e2e8f0">${escapeHtml(String(home))}</td>` +
    `<td style="padding:6px 8px;border-bottom:1px solid #e2e8f0;text-align:center">${escapeHtml(String(score))}</td>` +
    `<td style="padding:6px 8px;border-bottom:1px solid #e2e8f0">${escapeHtml(String(away))}</td>` +
    `<td style="padding:6px 8px;border-bottom:1px solid #e2e8f0;font-weight:700">${escapeHtml(String(winner))}</td>` +
    `</tr>`;
}).join("");

const RECEIPT_HTML = `
  <table style="border-collapse:collapse;width:100%">
    <thead>
      <tr style="background:#f1f5f9">
        <th style="padding:8px;text-align:left;border-bottom:2px solid #e2e8f0">Partido</th>
        <th style="padding:8px;text-align:left;border-bottom:2px solid #e2e8f0">Ronda</th>
        <th style="padding:8px;text-align:left;border-bottom:2px solid #e2e8f0">Local</th>
        <th style="padding:8px;text-align:center;border-bottom:2px solid #e2e8f0">Marcador</th>
        <th style="padding:8px;text-align:left;border-bottom:2px solid #e2e8f0">Visitante</th>
        <th style="padding:8px;text-align:left;border-bottom:2px solid #e2e8f0">Avanza</th>
      </tr>
    </thead>
    <tbody>${matchRows}</tbody>
  </table>
`;

serve(async (req: Request): Promise<Response> => {
  if (req.method === "OPTIONS") {
    return new Response("ok", { headers: corsHeaders });
  }

  const expectedSecret = Deno.env.get("PREAVISO_SECRET");
  const secretOk = expectedSecret && req.headers.get("x-sync-secret") === expectedSecret;
  if (!secretOk) {
    return new Response(JSON.stringify({ ok: false, error: "No autorizado" }), {
      status: 401,
      headers: { ...corsHeaders, "Content-Type": "application/json" },
    });
  }

  const apiKey = Deno.env.get("RESEND_API_KEY");
  if (!apiKey) {
    return new Response(JSON.stringify({ ok: false, error: "Missing RESEND_API_KEY" }), {
      status: 500,
      headers: { ...corsHeaders, "Content-Type": "application/json" },
    });
  }

  const from = Deno.env.get("RESEND_FROM_EMAIL") ?? "PorraWeb <onboarding@resend.dev>";
  const to = Deno.env.get("ADMIN_RECEIPT_EMAIL") ?? "hernancit1993@gmail.com";

  const subject = "Test - Confirmacion de prediccion de eliminatorias - PorraWeb";
  const text = MOCK_ROWS.map(([num, phase, home, score, away, winner]) => {
    return `Partido ${num} - ${phase}: ${home} ${score} ${away}. Avanza: ${winner}`;
  }).join("\n");

  const html = `
    <p>Hola Andrea Mora,</p>
    <p>Recibimos tu prediccion de eliminatorias en PorraWeb. Podras seguir el ranking en la pagina cuando avancen los resultados oficiales.</p>
    <p><strong>Este es el resguardo de lo que enviaste:</strong></p>
    ${RECEIPT_HTML}
    <p>Gracias por participar.</p>
  `;

  try {
    const response = await fetch("https://api.resend.com/emails", {
      method: "POST",
      headers: {
        "Authorization": `Bearer ${apiKey}`,
        "Content-Type": "application/json",
      },
      body: JSON.stringify({ from, to: [to], subject, text, html }),
    });

    const payload = await response.json().catch(() => ({}));

    return new Response(JSON.stringify({
      ok: response.ok,
      status: response.status,
      to,
      payload,
    }), {
      headers: { ...corsHeaders, "Content-Type": "application/json" },
    });
  } catch (err) {
    const message = err instanceof Error ? err.message : "Unknown error";
    return new Response(JSON.stringify({ ok: false, error: message }), {
      status: 500,
      headers: { ...corsHeaders, "Content-Type": "application/json" },
    });
  }
});
