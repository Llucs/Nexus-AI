import { execSync } from "child_process";
import fs from "fs";

const PATCH_PATH = ".nexus/patch.diff";

if (!fs.existsSync(PATCH_PATH)) {
  console.log("[NEXUS] Nenhum patch encontrado.");
  process.exit(0);
}

const patchContent = fs.readFileSync(PATCH_PATH, "utf8").trim();

if (!patchContent) {
  console.error("[NEXUS] Patch está vazio. Abortando.");
  process.exit(1);
}

console.log("[NEXUS] Conteúdo do patch:\n");
console.log(patchContent);
console.log("\n[NEXUS] Tentando aplicar patch...\n");

try {
  // Primeiro: dry-run para validar
  execSync(`git apply --check ${PATCH_PATH}`, { stdio: "inherit" });

  // Se passou no check, aplica de verdade
  execSync(`git apply ${PATCH_PATH}`, { stdio: "inherit" });

  fs.unlinkSync(PATCH_PATH);
  console.log("\n[NEXUS] Patch aplicado com sucesso.");
} catch (e) {
  console.error("\n[NEXUS][ERRO] Patch inválido ou conflitante.");
  console.error("[NEXUS][ERRO] O patch NÃO foi aplicado.");
  process.exit(1);
}