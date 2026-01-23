diff --git a/scripts/nexus-agent.js b/scripts/nexus-agent.js
index 8f1c2ab..c42b9aa 100644
--- a/scripts/nexus-agent.js
+++ b/scripts/nexus-agent.js
@@ -8,6 +8,7 @@ const instruction = process.env.NEXUS_INSTRUCTION || "";
 
 /* =========================
    GARANTIR ARQUIVOS NEXUS
 ========================= */
 function ensureNexusFiles() {
+  let recreated = false;
   if (!fs.existsSync(".nexus")) {
     fs.mkdirSync(".nexus", { recursive: true });
     console.log("[NEXUS] Pasta .nexus criada.");
@@ -18,18 +19,32 @@ function ensureNexusFiles() {
     fs.writeFileSync(".nexus/memory.json", "{}");
     console.log("[NEXUS] memory.json criado.");
+    recreated = true;
   }
 
   if (!fs.existsSync(".nexus/checklist.json")) {
     fs.writeFileSync(
       ".nexus/checklist.json",
       JSON.stringify({ pending: [], completed: [] }, null, 2)
     );
     console.log("[NEXUS] checklist.json criado.");
+    recreated = true;
   }
 
   if (!fs.existsSync(".nexus/project-analysis.txt")) {
     fs.writeFileSync(".nexus/project-analysis.txt", "");
     console.log("[NEXUS] project-analysis.txt criado (vazio).");
+    recreated = true;
+  }
+
+  // Validação de JSON para evitar crash por arquivo corrompido
+  try {
+    JSON.parse(fs.readFileSync(".nexus/checklist.json", "utf8"));
+  } catch {
+    fs.writeFileSync(
+      ".nexus/checklist.json",
+      JSON.stringify({ pending: [], completed: [] }, null, 2)
+    );
+    console.log("[NEXUS][WARN] checklist.json inválido. Recriado.");
+    recreated = true;
+  }
+
+  if (recreated) {
+    console.log("[NEXUS] Arquivos Nexus verificados/recriados com sucesso.");
   }
 }
 
@@ -150,7 +165,15 @@ async function processChecklist() {
   let checklist = JSON.parse(fs.readFileSync(".nexus/checklist.json", "utf8"));
   const memory = fs.readFileSync(".nexus/memory.json", "utf8");
 
-  checklist.pending = checklist.pending || [];
-  checklist.completed = checklist.completed || [];
+  if (!Array.isArray(checklist.pending)) {
+    checklist.pending = [];
+  }
+  if (!Array.isArray(checklist.completed)) {
+    checklist.completed = [];
+  }
+
+  if (!checklist.pending.length) {
+    console.log("[NEXUS] Nenhuma tarefa pendente no checklist.");
+  }
 
   if (checklist.pending.length === 0) {
     console.log("[NEXUS][DEBUG] Checklist vazio. IA não gerou tarefas úteis.");