import { execSync } from "child_process";
import fs from "fs";

const PATCH_PATH = ".nexus/patch.diff";

if (!fs.existsSync(PATCH_PATH)) {
  console.log("[NEXUS] Nenhum patch encontrado.");
  process.exit(0);
}

let rawPatch = fs.readFileSync(PATCH_PATH, "utf8");

// Extrai SOMENTE o diff unificado válido
function extractUnifiedDiff(text) {
  const lines = text.split("\n");

  const startIndex = lines.findIndex(
    (l) => l.startsWith("--- ") || l.startsWith("diff --git")
  );

  if (startIndex === -1) return "";

  return lines.slice(startIndex).join("\n").trim();
}

const patchContent = extractUnifiedDiff(rawPatch);

if (!patchContent) {
  console.error("[NEXUS][ERRO] Nenhum diff unificado válido encontrado no patch.");
  console.error("[NEXUS][ERRO] Conteúdo bruto recebido:\n");
  console.error(rawPatch);
  process.exit(1);
}

// Sobrescreve o patch com versão limpa
fs.writeFileSync(PATCH_PATH, patchContent);

console.log("[NEXUS] Patch limpo detectado:\n");
console.log(patchContent);
console.log("\n[NEXUS] Validando patch...\n");

try {
  execSync(`git apply --check ${PATCH_PATH}`, { stdio: "inherit" });

  execSync(`git apply ${PATCH_PATH}`, { stdio: "inherit" });

  fs.unlinkSync(PATCH_PATH);
  console.log("\n[NEXUS] Patch aplicado com sucesso.");
} catch (e) {
  console.error("\n[NEXUS][ERRO] Patch inválido ou conflitante.");
  console.error("[NEXUS][ERRO] Patch final usado:\n");
  console.error(patchContent);
  process.exit(1);
}