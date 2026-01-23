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
   POLÍTICA DO AGENTE
========================= */
const AGENT_POLICY = `
RULES (MANDATORY):
- You HAVE terminal access.
- ALWAYS run relevant commands (tests, lint, build).
- NEVER rewrite entire files.
- ALWAYS generate unified diff patches.
- ONLY edit required lines.
- Apply changes using git apply.
- Prefer minimal, surgical changes.
- Do NOT replace full file contents.
`;

/* =========================
   CHAMADA À IA
========================= */
function callAI(prompt) {
  return fetch("https://text.pollinations.ai/", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      model: "gemini",
      messages: [
        {
          role: "system",
          content:
            "You are an autonomous software engineering agent.\n" +
            AGENT_POLICY +
            "\nYou MUST output ONLY unified diff patches or strict JSON when requested."
        },
        { role: "user", content: prompt }
      ]
    })
  }).then(r => r.text());
}

/* =========================
   PATCH APLICADOR SEGURO
========================= */
function applyPatch(patchText) {
  const patchFile = ".nexus/patch.diff";
  fs.writeFileSync(patchFile, patchText);

  console.log("\n[NEXUS] Aplicando patch...");

  try {
    execSync(`git apply ${patchFile}`, { stdio: "inherit" });
    console.log("[NEXUS] Patch aplicado com sucesso.");
  } catch (err) {
    console.error("[NEXUS][ERRO] Falha ao aplicar patch.");
    throw err;
  }
}

/* =========================
   MEMÓRIA + CHECKLIST
========================= */
async function buildMemoryAndChecklist() {
  const prompt = `
Analyze the project.

Generate:

MEMORY_JSON:
{ complete structured memory }

CHECKLIST_JSON:
{ real actionable engineering tasks }

${analysis}
`;

  const out = await callAI(prompt);

  const memMatch = out.match(/MEMORY_JSON:\s*({[\s\S]*?})/);
  const chkMatch = out.match(/CHECKLIST_JSON:\s*({[\s\S]*?})/);

  if (memMatch) fs.writeFileSync(".nexus/memory.json", memMatch[1]);
  if (chkMatch) fs.writeFileSync(".nexus/checklist.json", chkMatch[1]);

  if (!fs.existsSync(".nexus/checklist.json")) {
    fs.writeFileSync(
      ".nexus/checklist.json",
      JSON.stringify({ pending: [], completed: [] }, null, 2)
    );
  }
}

/* =========================
   CHECKLIST EXECUTOR REAL
========================= */
async function processChecklist() {
  let checklist = JSON.parse(fs.readFileSync(".nexus/checklist.json", "utf8"));
  const memory = fs.readFileSync(".nexus/memory.json", "utf8");

  checklist.pending = checklist.pending || [];
  checklist.completed = checklist.completed || [];

  if (checklist.pending.length === 0) {
    console.log("[NEXUS][DEBUG] Checklist vazio. Nada para executar.");
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

LAST ERROR (if any):
${lastError}

Generate ONLY unified diff.
- Do NOT rewrite full files.
- Edit minimal lines.
- ALWAYS include --- a/ and +++ b/.
`;

      const patch = await callAI(prompt);

      console.log("\n[NEXUS][DEBUG] PATCH RAW:");
      console.log(patch);

      if (!patch.includes("---") || !patch.includes("+++")) {
        lastError = "Patch inválido (sem unified diff).";
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
      console.error("[NEXUS][ERRO] Não foi possível aplicar patch para tarefa:", task);
      continue;
    }

    // Validação real
    const testOutput = run("npm test || true");
    const lintOutput = run("npm run lint || true");
    const buildOutput = run("npm run build || true");

    console.log("\n[NEXUS] Resultados:");
    console.log(testOutput);
    console.log(lintOutput);
    console.log(buildOutput);

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