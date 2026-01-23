import fs from "fs";
import { execSync } from "child_process";
import path from "path";

function run(cmd) {
  return execSync(cmd, { encoding: "utf8", maxBuffer: 10 * 1024 * 1024 });
}

function isTextFile(filePath) {
  const ext = path.extname(filePath).toLowerCase();
  const textExts = [
    ".js", ".ts", ".jsx", ".tsx",
    ".json", ".html", ".css", ".md",
    ".yml", ".yaml", ".txt"
  ];
  return textExts.includes(ext);
}

const structure = run("find . -maxdepth 4 -type f");

let filesContent = "";

structure
  .split("\n")
  .filter(Boolean)
  .forEach((file) => {
    if (
      file.includes("node_modules") ||
      file.includes(".git") ||
      file.includes(".nexus")
    ) {
      return;
    }

    if (!isTextFile(file)) return;

    try {
      const content = fs.readFileSync(file, "utf8");

      filesContent += `
\n\n===== FILE: ${file} =====
${content}
===== END FILE: ${file} =====
`;
    } catch (e) {
      filesContent += `
\n\n===== FILE: ${file} =====
[ERRO AO LER ARQUIVO]
===== END FILE: ${file} =====
`;
    }
  });

const pkg = fs.existsSync("package.json")
  ? fs.readFileSync("package.json", "utf8")
  : "";

const analysis = `
============================
=== FILE STRUCTURE ===
============================
${structure}

============================
=== PACKAGE.JSON ===
============================
${pkg}

============================
=== FILE CONTENTS ===
============================
${filesContent}
`;

fs.writeFileSync(".nexus/project-analysis.txt", analysis);

console.log("[NEXUS] Projeto analisado com conteúdo completo.");