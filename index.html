<!DOCTYPE html>
<html lang="pt-BR">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
    <title>Nexus AI</title>
    <link href="https://fonts.googleapis.com/css2?family=Plus+Jakarta+Sans:wght@300;400;600&family=Fira+Code:wght@400&display=swap" rel="stylesheet">
    <script src="https://cdn.jsdelivr.net/npm/marked/marked.min.js"></script>
    <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/highlight.js/11.9.0/styles/tokyo-night-dark.min.css">
    <script src="https://cdnjs.cloudflare.com/ajax/libs/highlight.js/11.9.0/highlight.min.js"></script>
    <style>
        :root { --primary: #8b5cf6; --bg: #050505; --surface: #111111; --border: rgba(255, 255, 255, 0.08); --text-main: #f8fafc; --text-dim: #94a3b8; }
        * { box-sizing: border-box; transition: transform 0.2s ease; -webkit-tap-highlight-color: transparent; }
        body, html { margin: 0; padding: 0; font-family: 'Plus Jakarta Sans', sans-serif; background: var(--bg); color: var(--text-main); height: 100%; overflow: hidden; }
        
        /* Modal de Nome Customizado */
        #name-modal { position: fixed; inset: 0; background: rgba(0,0,0,0.85); backdrop-filter: blur(20px); z-index: 9999; display: none; align-items: center; justify-content: center; padding: 20px; }
        .modal-content { background: var(--surface); border: 1px solid var(--border); padding: 30px; border-radius: 28px; width: 100%; max-width: 380px; text-align: center; box-shadow: 0 25px 50px -12px rgba(0,0,0,0.5); }
        .modal-content h2 { margin: 0 0 10px 0; color: var(--text-main); font-weight: 600; }
        .modal-content p { color: var(--text-dim); font-size: 0.9rem; margin-bottom: 25px; }
        .modal-content input { width: 100%; background: rgba(255,255,255,0.03); border: 1px solid var(--border); padding: 14px; border-radius: 14px; color: white; outline: none; margin-bottom: 20px; font-size: 1rem; text-align: center; }
        .modal-btn { background: var(--primary); color: white; border: none; padding: 14px; border-radius: 14px; font-weight: 600; cursor: pointer; width: 100%; font-size: 1rem; }

        #app { display: flex; flex-direction: column; height: 100dvh; }
        header { padding: 12px 16px; display: flex; justify-content: space-between; align-items: center; border-bottom: 1px solid var(--border); background: rgba(5,5,5,0.8); backdrop-filter: blur(10px); z-index: 10; }
        .brand { font-weight: 700; font-size: 1rem; color: var(--primary); letter-spacing: 1px; }
        .icon-btn { background: rgba(255,255,255,0.05); border: 1px solid var(--border); color: white; width: 36px; height: 36px; border-radius: 10px; cursor: pointer; display: flex; align-items: center; justify-content: center; }
        #sidebar, #settings-panel { position: absolute; left: -100%; top: 0; width: 280px; height: 100%; background: #0a0a0a; border-right: 1px solid var(--border); z-index: 1000; padding: 20px; display: flex; flex-direction: column; transition: left 0.3s cubic-bezier(0.4, 0, 0.2, 1); }
        #sidebar.active, #settings-panel.active { left: 0; box-shadow: 20px 0 50px rgba(0,0,0,0.8); }
        .chat-item { padding: 12px; border-radius: 12px; margin-bottom: 8px; background: rgba(255,255,255,0.03); cursor: pointer; font-size: 13px; border: 1px solid transparent; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
        #chat-window { flex: 1; overflow-y: auto; padding: 20px 12px; display: flex; flex-direction: column; gap: 16px; scroll-behavior: smooth; }
        .message { padding: 12px 16px; line-height: 1.5; max-width: 85%; width: fit-content; font-size: 0.92rem; border-radius: 18px; animation: fadeIn 0.3s ease; position: relative; }
        .user-msg { align-self: flex-end; background: var(--primary); color: white; border-bottom-right-radius: 4px; }
        .ai-msg { align-self: flex-start; background: var(--surface); border: 1px solid var(--border); border-bottom-left-radius: 4px; }
        .msg-img { max-width: 100%; border-radius: 10px; margin-bottom: 8px; display: block; border: 1px solid var(--border); }
        .input-container { padding: 16px; background: var(--bg); border-top: 1px solid var(--border); }
        .input-wrapper { max-width: 800px; margin: 0 auto; background: var(--surface); border: 1px solid var(--border); border-radius: 24px; padding: 6px 12px; display: flex; align-items: center; gap: 8px; }
        textarea { flex: 1; background: none; border: none; color: white; padding: 8px; resize: none; outline: none; font-size: 15px; max-height: 150px; }
        .add-btn { color: var(--text-dim); cursor: pointer; display: flex; align-items: center; justify-content: center; width: 32px; height: 32px; }
        #sendBtn { background: var(--primary); border: none; width: 36px; height: 36px; border-radius: 50%; cursor: pointer; display: flex; align-items: center; justify-content: center; }
        .settings-label { font-size: 11px; text-transform: uppercase; color: var(--text-dim); margin-bottom: 12px; display: block; }
        .color-picker { display: flex; gap: 10px; margin-bottom: 30px; }
        .color-opt { width: 30px; height: 30px; border-radius: 50%; cursor: pointer; }
        .credits { margin-top: auto; font-size: 11px; color: var(--text-dim); text-align: center; border-top: 1px solid var(--border); padding-top: 20px; }
        .credits b { color: var(--primary); }
        @keyframes fadeIn { from { opacity: 0; transform: translateY(8px); } to { opacity: 1; transform: translateY(0); } }
        .skeleton { height: 14px; background: var(--border); border-radius: 4px; margin: 8px 0; animation: pulse 1.5s infinite; }
        @keyframes pulse { 50% { opacity: 0.4; } }
    </style>
</head>
<body>

<div id="name-modal">
    <div class="modal-content">
        <h2>Nexus AI</h2>
        <p>Como gostaria de ser chamado?</p>
        <input type="text" id="name-input" placeholder="Seu nome aqui..." maxlength="20">
        <button class="modal-btn" onclick="saveUserName()">Confirmar</button>
    </div>
</div>

<div id="settings-panel">
    <div style="display:flex; justify-content:space-between; align-items:center; margin-bottom:30px;">
        <span style="font-weight:600">Configurações</span>
        <button onclick="toggleSettings()" style="background:none; border:none; color:white; font-size:24px;">✕</button>
    </div>
    <span class="settings-label">Cor em Destaque</span>
    <div class="color-picker">
        <div class="color-opt" style="background:#8b5cf6" onclick="setAccent('#8b5cf6')"></div>
        <div class="color-opt" style="background:#3b82f6" onclick="setAccent('#3b82f6')"></div>
        <div class="color-opt" style="background:#10b981" onclick="setAccent('#10b981')"></div>
        <div class="color-opt" style="background:#f43f5e" onclick="setAccent('#f43f5e')"></div>
    </div>
    <button onclick="changeName()" style="width:100%; padding:12px; background:var(--surface); border:1px solid var(--border); color:white; border-radius:10px; cursor:pointer; margin-top:10px;">👤 Alterar Nome</button>
    <div class="credits">Desenvolvido por <b>Llucs</b><br>Nexus AI © 2026</div>
</div>

<div id="sidebar">
    <div style="display:flex; justify-content:space-between; align-items:center; margin-bottom:20px;"><span style="font-weight:600">Histórico</span><button onclick="toggleMenu()" style="background:none; border:none; color:white; font-size:24px;">✕</button></div>
    <div id="chat-list" style="overflow-y:auto; flex:1"></div>
    <button onclick="toggleSettings()" style="background:rgba(255,255,255,0.05); border:none; color:white; padding:12px; border-radius:10px; margin-bottom:10px; cursor:pointer;">⚙ Ajustes</button>
    <button onclick="clearAll()" style="color:#ff4444; background:none; border:none; padding:10px; font-size:12px;">Limpar Tudo</button>
</div>

<div id="app">
    <header><button class="icon-btn" onclick="toggleMenu()">☰</button><div class="brand">NEXUS AI</div><button class="icon-btn" id="newChatBtn">+</button></header>
    <div id="chat-window">
        <div id="welcome-msg" style="text-align:center; margin-top:30%; opacity:0.6">
            <h3 id="display-name">Olá!</h3>
            <p>Anexe uma foto ou digite algo.</p>
        </div>
    </div>
    <div class="input-container">
        <div id="image-preview" style="display:none; margin-bottom:10px; position:relative; width:60px;">
            <img id="preview-img" style="width:60px; height:60px; border-radius:10px; object-fit:cover; border:2px solid var(--primary);">
            <div onclick="cancelImage()" style="position:absolute; top:-5px; right:-5px; background:red; border-radius:50%; width:18px; height:18px; font-size:12px; color:white; display:flex; align-items:center; justify-content:center; cursor:pointer;">✕</div>
        </div>
        <div class="input-wrapper">
            <label class="add-btn">
                <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"></circle><line x1="12" x2="12" y1="8" y2="16"></line><line x1="8" x2="16" y1="12" y2="12"></line></svg>
                <input type="file" id="file-input" hidden accept="image/*" onchange="processImage(this)">
            </label>
            <textarea id="prompt" placeholder="Sua mensagem..." rows="1"></textarea>
            <button id="sendBtn"><svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="white" stroke-width="3"><line x1="22" y1="2" x2="11" y2="13"></line><polygon points="22 2 15 22 11 13 2 9 22 2"></polygon></svg></button>
        </div>
    </div>
</div>

<script>
    const chatWindow = document.getElementById('chat-window'), input = document.getElementById('prompt'), chatList = document.getElementById('chat-list');
    const nameModal = document.getElementById('name-modal'), nameInput = document.getElementById('name-input');
    
    let chats = JSON.parse(localStorage.getItem('nexus_chats') || '[]'), currentChatIndex = null, selectedImg = null;
    let userName = localStorage.getItem('nexus_user_name');

    // SISTEMA DE NOME
    function initUser() {
        if (!userName) {
            nameModal.style.display = 'flex';
            nameInput.focus();
        } else {
            document.getElementById('display-name').innerText = `Olá, ${userName}`;
        }
    }

    function saveUserName() {
        const val = nameInput.value.trim();
        if (val) {
            userName = val;
            localStorage.setItem('nexus_user_name', val);
            document.getElementById('display-name').innerText = `Olá, ${val}`;
            nameModal.style.display = 'none';
        }
    }

    function changeName() {
        nameModal.style.display = 'flex';
        nameInput.value = userName || "";
        nameInput.focus();
    }

    initUser();

    // UTILITÁRIOS
    function setAccent(c) { document.documentElement.style.setProperty('--primary', c); localStorage.setItem('nexus_theme_color', c); }
    setAccent(localStorage.getItem('nexus_theme_color') || '#8b5cf6');
    function toggleMenu() { document.getElementById('sidebar').classList.toggle('active'); }
    function toggleSettings() { document.getElementById('sidebar').classList.remove('active'); document.getElementById('settings-panel').classList.toggle('active'); }

    function processImage(fileInput) {
        const file = fileInput.files[0]; if (!file) return;
        const reader = new FileReader();
        reader.onload = (e) => {
            const img = new Image();
            img.onload = () => {
                const canvas = document.createElement('canvas');
                let w = img.width, h = img.height, max = 600;
                if (w > h) { if (w > max) { h *= max / w; w = max; } }
                else { if (h > max) { w *= max / h; h = max; } }
                canvas.width = w; canvas.height = h;
                canvas.getContext('2d').drawImage(img, 0, 0, w, h);
                selectedImg = canvas.toDataURL('image/jpeg', 0.7);
                document.getElementById('preview-img').src = selectedImg;
                document.getElementById('image-preview').style.display = 'block';
            };
            img.src = e.target.result;
        };
        reader.readAsDataURL(file);
    }

    function cancelImage() { selectedImg = null; document.getElementById('image-preview').style.display = 'none'; document.getElementById('file-input').value = ''; }

    function appendMessage(text, isUser, anim = true, img = null) {
        const div = document.createElement('div'); div.className = `message ${isUser ? 'user-msg' : 'ai-msg'}`;
        div.innerHTML = (img ? `<img src="${img}" class="msg-img">` : '') + `<div class="msg-text"></div>`;
        chatWindow.appendChild(div);
        const txtDiv = div.querySelector('.msg-text');
        if (!isUser && anim) {
            let cur = "", words = text.split(" "), i = 0;
            const int = setInterval(() => { if(i < words.length){ cur += words[i] + " "; txtDiv.innerHTML = marked.parse(cur); i++; chatWindow.scrollTop = chatWindow.scrollHeight; } else clearInterval(int); }, 30);
        } else { txtDiv.innerHTML = marked.parse(text); }
        chatWindow.scrollTop = chatWindow.scrollHeight;
    }

    async function sendMessage() {
        const text = input.value.trim();
        if (!text && !selectedImg) return;
        
        if (currentChatIndex === null) { 
            chats.push({ title: text.substring(0, 15) || "Imagem", messages: [] }); 
            currentChatIndex = chats.length - 1; 
        }

        const imgToSend = selectedImg;
        appendMessage(text || "Analise esta foto", true, false, imgToSend);
        chats[currentChatIndex].messages.push({ text: text || "Enviou uma imagem", isUser: true, img: imgToSend });
        input.value = ''; cancelImage(); document.getElementById('welcome-msg').style.display = 'none';

        const skel = document.createElement('div'); skel.className = 'message ai-msg';
        skel.innerHTML = '<div class="skeleton" style="width:100px"></div>'; chatWindow.appendChild(skel);
        chatWindow.scrollTop = chatWindow.scrollHeight;

        try {
            const requestBody = {
                messages: [
                    { role: "system", content: `Você é o Nexus AI. O nome do usuário é ${userName}. Responda em Português do Brasil. Seja direto e útil.` },
                    { role: "user", content: imgToSend ? [{ type: "text", text: text || "O que é isso?" }, { type: "image_url", image_url: { url: imgToSend } }] : text }
                ],
                model: "gemini"
            };
            const res = await fetch('https://text.pollinations.ai/', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(requestBody) });
            const data = await res.text();
            skel.remove();
            appendMessage(data, false);
            chats[currentChatIndex].messages.push({ text: data, isUser: false });
            save();
        } catch (e) { skel.innerHTML = "Erro ao conectar."; }
    }

    function save() { localStorage.setItem('nexus_chats', JSON.stringify(chats)); renderList(); }
    
    function renderList() {
        chatList.innerHTML = '';
        chats.forEach((c, i) => {
            const d = document.createElement('div'); d.className = 'chat-item'; d.innerText = c.title;
            d.onclick = () => { 
                currentChatIndex = i; 
                document.getElementById('welcome-msg').style.display = 'none'; 
                chatWindow.innerHTML = ''; 
                chats[i].messages.forEach(m => appendMessage(m.text, m.isUser, false, m.img)); 
                toggleMenu(); 
            };
            chatList.appendChild(d);
        });
    }

    function clearAll() { if(confirm("Apagar tudo?")) { localStorage.clear(); location.reload(); } }
    
    // EVENTOS
    nameInput.onkeydown = (e) => { if(e.key === 'Enter') saveUserName(); };
    input.onkeydown = (e) => { if(e.key === 'Enter' && !e.shiftKey) { e.preventDefault(); sendMessage(); } };
    document.getElementById('sendBtn').onclick = sendMessage;
    document.getElementById('newChatBtn').onclick = () => { currentChatIndex = null; chatWindow.innerHTML = ''; document.getElementById('welcome-msg').style.display = 'block'; };
    
    renderList();
</script>
</body>
</html>
