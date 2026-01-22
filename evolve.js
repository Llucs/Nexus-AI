const fs = require('fs');

async function evolve() {
    // 1. Mapeia os arquivos atuais (ignorando lixo)
    const files = fs.readdirSync('./').filter(f => 
        !f.startsWith('.') && 
        f !== 'evolve.js' && 
        !f.includes('node_modules') &&
        f !== 'package.json'
    );
    
    let memory = fs.existsSync('nexus_memory.json') ? fs.readFileSync('nexus_memory.json', 'utf8') : "{}";
    let context = `Proprietário: Llucs\nMemória Técnica: ${memory}\n`;
    
    files.forEach(f => {
        if (fs.lstatSync(f).isFile()) {
            context += `\nFILE [${f}]:\n${fs.readFileSync(f, 'utf8')}\n`;
        }
    });

    // 2. Prompt ultra-rígido para evitar erros de formato
    const prompt = `Você é o sistema NEXUS. Dono: Llucs. 
    TAREFA: Melhore o código e a UI. 
    REGRAS: 
    1. Responda APENAS JSON puro. Sem conversas.
    2. Formato: {"files": [{"path": "...", "content": "..."}], "update_memory": {}}
    3. Mantenha os créditos exclusivos para Llucs.`;

    try {
        const response = await fetch('https://text.pollinations.ai/', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                messages: [
                    { role: 'system', content: 'Você é um arquiteto que exporta apenas JSON puro.' },
                    { role: 'user', content: prompt + "\n\nContexto:\n" + context }
                ],
                model: 'openai'
            })
        });

        const text = await response.text();
        console.log("Resposta da IA recebida.");

        // Tenta extrair apenas a parte que é JSON (caso a IA fale bobagem antes)
        const jsonMatch = text.match(/\{[\s\S]*\}/);
        if (!jsonMatch) throw new Error("A IA não retornou um JSON válido.");
        
        const data = JSON.parse(jsonMatch[0]);

        // 3. Só tenta salvar se 'files' for uma lista válida
        if (data && data.files && Array.isArray(data.files)) {
            data.files.forEach(file => {
                console.log(`Atualizando arquivo: ${file.path}`);
                fs.writeFileSync(file.path, file.content);
            });
        }

        if (data.update_memory) {
            fs.writeFileSync('nexus_memory.json', JSON.stringify(data.update_memory, null, 2));
        }
    } catch (e) {
        console.error("Erro no processamento:", e.message);
        process.exit(1);
    }
}
evolve();
