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
  run(`git apply ${patchFile}`);
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
}

/* =========================
   CHECKLIST EXECUTOR REAL
========================= */
async function processChecklist() {
  let checklist = JSON.parse(fs.readFileSync(".nexus/checklist.json", "utf8"));
  const memory = fs.readFileSync(".nexus/memory.json", "utf8");

  for (const task of checklist.pending || []) {
    console.log("\n[NEXUS] Executando tarefa:", task);

    const prompt = `
MEMORY:
${memory}

TASK:
${task}

You may assume you can run terminal commands.

Generate ONLY a unified diff patch.
DO NOT rewrite full files.
Edit only necessary lines.
`;

    const patch = await callAI(prompt);

    if (!patch.includes("---") || !patch.includes("+++")) {
      console.log("[NEXUS] Patch inválido ou vazio. Pulando tarefa.");
      continue;
    }

    applyPatch(patch);

    // =========================
    // LOOP REAL DE VALIDAÇÃO
    // =========================
    const testOutput = run("npm test || true");
    const lintOutput = run("npm run lint || true");
    const buildOutput = run("npm run build || true");

    console.log("\n[NEXUS] Resultados:");
    console.log(testOutput);
    console.log(lintOutput);
    console.log(buildOutput);

    checklist.completed.push(task);
  }

  checklist.pending = [];
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