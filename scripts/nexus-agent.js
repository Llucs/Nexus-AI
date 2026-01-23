const fs = require("fs");
const fetch = require("node-fetch");
const { execSync } = require("child_process");

const instruction = process.env.NEXUS_INSTRUCTION || "";
const analysis = fs.readFileSync(".nexus/project-analysis.txt", "utf8");

/* =========================
   EXECUTOR REAL DE TERMINAL
========================= */
function run(cmd) {
  console.log("\n[NEXUS:RUN]", cmd);
  try {
    return execSync(cmd, { encoding: "utf8", stdio: "pipe" });
  } catch (err) {
    return (
      (err.stdout ? err.stdout.toString() : "") +
      (err.stderr ? err.stderr.toString() : "")
    );
  }
}

/* =========================
   UTIL: LIMPAR CERCA DE CÓDIGO
========================= */
function stripCodeFences(text) {
  return text
    .replace(/```json/gi, "")
    .replace(/```/g, "")
    .trim();
}

/* =========================
   UTIL: EXTRAIR UNIFIED DIFF
========================= */
function extractUnifiedDiff(text) {
  const lines = text.split("\n");
  const diffLines = [];
  let inDiff = false;

  for (const line of lines) {
    if (line.startsWith("diff --git") || line.startsWith("--- ")) {
      inDiff = true;
    }

    if (inDiff) {
      diffLines.push(line);
    }
  }

  return diffLines.join("\n").trim();
}

/* =========================
   POLÍTICA DO AGENTE
========================= */
const AGENT_POLICY = `
RULES (MANDATORY):
- You HAVE terminal access.
- ALWAYS generate unified diff patches.
- NEVER rewrite entire files.
- ONLY edit required lines.
- Prefer minimal, surgical changes.
- NO explanations. NO markdown. NO ads.
- OUTPUT DIFF OR STRICT JSON ONLY.
`;

/* =========================
   CHAMADA À IA (COM RETRY)
========================= */
async function callAI(prompt, retries = 3) {
  for (let i = 1; i <= retries; i++) {
    try {
      const res = await fetch("https://text.pollinations.ai/", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          model: "gemini",
          messages: [
            {
              role: "system",
              content:
                "You are an autonomous software engineering agent.\n" +
                AGENT_POLICY
            },
            { role: "user", content: prompt }
          ]
        })
      });

      const text = await res.text();
      return text;
    } catch (err) {
      console.error(`[NEXUS][WARN] Falha na IA (tentativa ${i})`, err.message);
      if (i === retries) throw err;
    }
  }
}

/* =========================
   PATCH APLICADOR
========================= */
function applyPatch(patchText) {
  const patchFile = ".nexus/patch.diff";
  fs.writeFileSync(patchFile, patchText);

  console.log("\n[NEXUS] Aplicando patch...");

  execSync(`git apply ${patchFile}`, { stdio: "inherit" });
  console.log("[NEXUS] Patch aplicado com sucesso.");
}

/* =========================
   MEMÓRIA + CHECKLIST (JSON PURO)
========================= */
async function buildMemoryAndChecklist() {
  const prompt = `
Return ONLY valid JSON. No markdown. No explanations.

Schema:

{
  "memory": {},
  "checklist": {
    "pending": [],
    "completed": []
  }
}

Rules for checklist:
- Each task MUST be tiny
- Each task MUST touch only 1 file
- Each task MUST be patchable in <50 lines
- NO big refactors
- NO conceptual tasks
- NO audits, NO tests, NO accessibility reviews

Project:
${analysis}
`;

  let raw = await callAI(prompt);
  raw = stripCodeFences(raw);

  console.log("\n[NEXUS][DEBUG] RAW AI JSON:");
  console.log(raw);

  let parsed;
  try {
    parsed = JSON.parse(raw);
  } catch (err) {
    console.error("[NEXUS][FATAL] IA retornou JSON inválido.");
    fs.writeFileSync(".nexus/ai-invalid-response.txt", raw);
    throw err;
  }

  const memory = parsed.memory || {};
  const checklist = parsed.checklist || { pending: [], completed: [] };

  fs.writeFileSync(".nexus/memory.json", JSON.stringify(memory, null, 2));
  fs.writeFileSync(".nexus/checklist.json", JSON.stringify(checklist, null, 2));

  console.log("[NEXUS] Memory e Checklist gerados.");
}

/* =========================
   EXECUTOR DE CHECKLIST
========================= */
async function processChecklist() {
  let checklist = JSON.parse(fs.readFileSync(".nexus/checklist.json", "utf8"));
  const memory = fs.readFileSync(".nexus/memory.json", "utf8");

  checklist.pending = checklist.pending || [];
  checklist.completed = checklist.completed || [];

  if (checklist.pending.length === 0) {
    console.log("[NEXUS][DEBUG] Checklist vazio. IA não gerou tarefas úteis.");
    return;
  }

  for (const task of [...checklist.pending]) {
    console.log("\n[NEXUS] Executando tarefa:", task);

    let applied = false;
    let lastError = "";

    for (let attempt = 1; attempt <= 3 && !applied; attempt++) {
      const prompt = `
MEMORY:
${memory}

TASK:
${task}

WORKFLOW INSTRUCTION:
${instruction}

LAST ERROR:
${lastError}

Generate ONLY unified diff.
- Minimal edits
- Include --- a/ and +++ b/
- No markdown
- No explanations
`;

      const rawPatch = await callAI(prompt);
      const patch = extractUnifiedDiff(rawPatch);

      console.log("\n[NEXUS][DEBUG] PATCH FILTRADO:");
      console.log(patch);

      if (!patch || (!patch.startsWith("diff --git") && !patch.startsWith("--- "))) {
        lastError = "Resposta não contém unified diff válido.";
        continue;
      }

      try {
        applyPatch(patch);
        applied = true;
      } catch (err) {
        lastError = err.message || String(err);
      }
    }

    if (!applied) {
      console.error("[NEXUS][ERRO] Não foi possível aplicar patch para:", task);
      continue;
    }

    checklist.completed.push(task);
    checklist.pending = checklist.pending.filter(t => t !== task);
  }

  fs.writeFileSync(".nexus/checklist.json", JSON.stringify(checklist, null, 2));
}

/* =========================
   MAIN
========================= */
(async () => {
  console.log("[NEXUS] Agente iniciado.");
  console.log("[NEXUS] Instrução:", instruction);

  await buildMemoryAndChecklist();
  await processChecklist();

  console.log("Nexus AI Agent finalizado.");
})();