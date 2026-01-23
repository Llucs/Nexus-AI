import fs from "fs";
import fetch from "node-fetch";
import { execSync } from "child_process";

const instruction = process.env.NEXUS_INSTRUCTION;
const analysis = fs.readFileSync(".nexus/project-analysis.txt", "utf8");

let memory = JSON.parse(fs.readFileSync(".nexus/memory.json", "utf8"));
let checklist = JSON.parse(fs.readFileSync(".nexus/checklist.json", "utf8"));

async function callAI(prompt) {
  const body = {
    model: "gemini",
    messages: [
      { role: "system", content: "Você é um agente autônomo de código. Gere apenas JSON ou unified diff." },
      { role: "user", content: prompt }
    ]
  };

  const res = await fetch("https://text.pollinations.ai/", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body)
  });

  return await res.text();
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
  checklist = JSON.parse(fs.readFileSync(".nexus/checklist.json", "utf8"));

  for (const task of checklist.pending) {
    const mem = fs.readFileSync(".nexus/memory.json", "utf8");

    const prompt = `
MEMÓRIA:
${mem}

TAREFA:
${task}

Gere APENAS unified diff para aplicar a mudança.
NUNCA reescreva arquivos inteiros.
`;

    const patch = await callAI(prompt);
    fs.writeFileSync(".nexus/patch.diff", patch);

    execSync("node scripts/apply-patch.js", { stdio: "inherit" });

    checklist.completed.push(task);
  }

  checklist.pending = [];
  fs.writeFileSync(".nexus/checklist.json", JSON.stringify(checklist, null, 2));
}

await buildMemoryAndChecklist();
await processChecklist();

console.log("Nexus AI Agent finalizado.");