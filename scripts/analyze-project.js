import fs from "fs";
import { execSync } from "child_process";

function run(cmd) {
  return execSync(cmd, { encoding: "utf8" });
}

const structure = run("find . -maxdepth 4 -type f");
const pkg = fs.existsSync("package.json")
  ? fs.readFileSync("package.json", "utf8")
  : "";

const analysis = `
=== FILE STRUCTURE ===
${structure}

=== PACKAGE.JSON ===
${pkg}
`;

fs.writeFileSync(".nexus/project-analysis.txt", analysis);

console.log("Projeto analisado.");