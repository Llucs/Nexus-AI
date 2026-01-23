import { execSync } from "child_process";
import fs from "fs";

if (!fs.existsSync(".nexus/patch.diff")) {
  console.log("Nenhum patch encontrado.");
  process.exit(0);
}

try {
  execSync("git apply .nexus/patch.diff", { stdio: "inherit" });
  fs.unlinkSync(".nexus/patch.diff");
  console.log("Patch aplicado com sucesso.");
} catch (e) {
  console.error("Erro ao aplicar patch.");
  process.exit(1);
}