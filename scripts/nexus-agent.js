const fs = require("fs");
const fetch = require("node-fetch");
const { execSync } = require("child_process");

const instruction = process.env.NEXUS_INSTRUCTION || "";
const analysis = fs.readFileSync(".nexus/project-analysis.txt", "utf8");

function callAI(prompt) {
  return fetch("https://text.pollinations.ai/", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      model: "gemini",
      messages: [
        { role: "system", content: "Você é um agente autônomo de código. Gere apenas JSON ou unified diff." },
        { role: "user", content: prompt }
      ]
    })
  }).then(r => r.text());
}

async function buildMemoryAndChecklist() {
  const prompt = `
Analise o projeto abaixo.
Gere:
1) memory.json COMPLETO
2) checklist.json com tarefas reais

Formato:
MEMORY_JSON:
{...}

CHECKLIST_JSON:
{...}

${analysis}
`;

  const out = await callAI(prompt);

  const memMatch = out.match(/MEMORY_JSON:\s*({[\s\S]*?})/);
  const chkMatch = out.match(/CHECKLIST_JSON:\s*({[\s\S]*?})/);

  if (memMatch) fs.writeFileSync(".nexus/memory.json", memMatch[1]);
  if (chkMatch) fs.writeFileSync(".nexus/checklist.json", chkMatch[1]);
}

async function processChecklist() {
  let checklist = JSON.parse(fs.readFileSync(".nexus/checklist.json", "utf8"));
  const memory = fs.readFileSync(".nexus/memory.json", "utf8");

  for (const task of checklist.pending || []) {
    const prompt = `
MEMÓRIA:
${memory}

TAREFA:
${task}

Gere APENAS unified diff.
`;

    const patch = await callAI(prompt);
    fs.writeFileSync(".nexus/patch.diff", patch);

    execSync("node scripts/apply-patch.js", { stdio: "inherit" });

    checklist.completed.push(task);
  }

  checklist.pending = [];
  fs.writeFileSync(".nexus/checklist.json", JSON.stringify(checklist, null, 2));
}

(async () => {
  await buildMemoryAndChecklist();
  await processChecklist();
  console.log("Nexus AI Agent finalizado.");
})();