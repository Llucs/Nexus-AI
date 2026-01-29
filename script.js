const chatWindow = document.getElementById('chat-window');
const chatList = document.getElementById('chat-list');
const promptInput = document.getElementById('prompt');
const nameModal = document.getElementById('name-modal');

let chats = JSON.parse(localStorage.getItem('nexus_chats') || '[]');
let userName = localStorage.getItem('nexus_user_name');
let themeColor = localStorage.getItem('nexus_theme_color') || '#8b5cf6';
let currentChatIndex = null;
let selectedImg = null;

function init() {
    setAccent(themeColor);
    if (!userName) {
        nameModal.style.display = 'flex';
    } else {
        updateWelcomeName();
    }
    renderChatList();
    if (chats.length > 0) {
        loadChat(0); // Carrega o chat mais recente por padrão
    }
}

function updateWelcomeName() {
    document.getElementById('display-name').innerText = `Olá, ${userName}`;
}

function saveUserName() {
    const val = document.getElementById('name-input').value.trim();
    if (val) {
        userName = val;
        localStorage.setItem('nexus_user_name', val);
        nameModal.style.display = 'none';
        updateWelcomeName();
    }
}

function changeName() {
    document.getElementById('name-input').value = userName || '';
    nameModal.style.display = 'flex';
    toggleSettings();
}

function setAccent(color, el) {
    document.documentElement.style.setProperty('--primary', color);
    localStorage.setItem('nexus_theme_color', color);
    document.querySelectorAll('.color-picker div').forEach(d => d.style.borderColor = 'transparent');
    if (el) el.style.borderColor = 'white';
}

function toggleMenu() {
    document.getElementById('sidebar').classList.toggle('active');
    document.getElementById('settings-panel').classList.remove('active');
}

function toggleSettings() {
    document.getElementById('settings-panel').classList.toggle('active');
    document.getElementById('sidebar').classList.remove('active');
}

function renderChatList() {
    chatList.innerHTML = '';
    chats.forEach((chat, i) => {
        const item = document.createElement('div');
        item.className = `chat-item ${i === currentChatIndex ? 'active-chat' : ''}`;
        item.textContent = chat.title || 'Nova conversa';
        item.onclick = () => loadChat(i);
        chatList.appendChild(item);
    });
}

function newChat() {
    chats.unshift({ title: 'Nova conversa', messages: [] });
    currentChatIndex = 0;
    renderChatList();
    chatWindow.innerHTML = '';
    document.getElementById('welcome-msg').style.display = 'block';
}

function loadChat(index) {
    currentChatIndex = index;
    renderChatList();
    chatWindow.innerHTML = '';
    document.getElementById('welcome-msg').style.display = chats[index].messages.length > 0 ? 'none' : 'block';
    chats[index].messages.forEach(msg => {
        appendMessage(msg.content, msg.role === 'user', msg.img, false);
    });
    chatWindow.scrollTop = chatWindow.scrollHeight;
}

function autoResize(textarea) {
    textarea.style.height = 'auto';
    textarea.style.height = textarea.scrollHeight + 'px';
}

function processImage(fileInput) {
    const file = fileInput.files[0];
    if (!file) return;
    if (file.size > 4 * 1024 * 1024) {
        alert("Imagem muito grande (máx 4MB).");
        return;
    }

    const reader = new FileReader();
    reader.onload = e => {
        const img = new Image();
        img.onload = () => {
            const canvas = document.createElement('canvas');
            let w = img.width, h = img.height;
            const max = 800;
            if (w > max || h > max) {
                if (w > h) { h *= max / w; w = max; }
                else { w *= max / h; h = max; }
            }
            canvas.width = w; canvas.height = h;
            canvas.getContext('2d').drawImage(img, 0, 0, w, h);
            selectedImg = canvas.toDataURL('image/jpeg', 0.72);
            document.getElementById('preview-img').src = selectedImg;
            document.getElementById('image-preview').style.display = 'block';
        };
        img.src = e.target.result;
    };
    reader.readAsDataURL(file);
    fileInput.value = '';
}

function cancelImage() {
    selectedImg = null;
    document.getElementById('image-preview').style.display = 'none';
}

function appendMessage(text, isUser, img = null, animate = true) {
    const div = document.createElement('div');
    div.className = `message ${isUser ? 'user-msg' : 'ai-msg'}`;

    let html = img ? `<img src="${img}" class="msg-img">` : '';

    if (!isUser && text.includes('https://image.pollinations.ai/')) {
        const match = text.match(/https:\/\/image\.pollinations\.ai\/[^\s)]+/);
        if (match) {
            html += `<img src="${match[0]}" class="msg-img" alt="Imagem gerada">`;
            text = text.replace(match[0], '').trim();
        }
    }

    html += `<div class="msg-content">${marked.parse(text)}</div>`;
    div.innerHTML = html;
    chatWindow.appendChild(div);

    if (!isUser) hljs.highlightAll();
    chatWindow.scrollTop = chatWindow.scrollHeight;
}

async function sendMessage() {
    const text = promptInput.value.trim();
    if (!text && !selectedImg) return;

    document.getElementById('welcome-msg').style.display = 'none';

    if (currentChatIndex === null) {
        newChat();
    }

    const userImg = selectedImg;
    appendMessage(text, true, userImg);

    if (currentChatIndex !== null) {
        chats[currentChatIndex].messages.push({ role: 'user', content: text, img: userImg });
        if (!chats[currentChatIndex].title || chats[currentChatIndex].title === 'Nova conversa') {
            chats[currentChatIndex].title = text.substring(0, 30) || 'Imagem enviada';
            renderChatList();
        }
    }

    promptInput.value = '';
    autoResize(promptInput);
    cancelImage();

    // Loading
    const loading = document.createElement('div');
    loading.className = 'message ai-msg loading';
    loading.innerHTML = '<div class="loading-dots"><div class="dot"></div><div class="dot"></div><div class="dot"></div></div>';
    chatWindow.appendChild(loading);
    chatWindow.scrollTop = chatWindow.scrollHeight;

    try {
        const history = chats[currentChatIndex].messages.slice(-7).map(msg => {
            if (msg.img) {
                return {
                    role: msg.role,
                    content: [
                        { type: "text", text: msg.content || "Descreva esta imagem em detalhes." },
                        { type: "image_url", image_url: { url: msg.img } }
                    ]
                };
            }
            return { role: msg.role, content: msg.content };
        });

        const system = {
            role: "system",
            content: `Você é Nexus, assistente pessoal do ${userName}. Responda em Português do Brasil. Seja direto, útil e amigável. Use Markdown quando apropriado. Se o usuário pedir para GERAR uma imagem, retorne APENAS a URL no formato: https://image.pollinations.ai/prompt/{descricao_em_ingles}?width=1024&height=1024&nologo=true&model=flux`
        };

        const res = await fetch('https://text.pollinations.ai/', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                messages: [system, ...history],
                model: 'openai'
            })
        });

        const data = await res.json();
        loading.remove();

        const aiText = data?.response || data?.choices?.[0]?.message?.content || 'Erro ao obter resposta.';
        appendMessage(aiText, false);
        chats[currentChatIndex].messages.push({ role: 'assistant', content: aiText });
        localStorage.setItem('nexus_chats', JSON.stringify(chats));

    } catch (err) {
        loading.remove();
        appendMessage('Erro ao conectar com a API. Tente novamente.', false);
    }
}

// Eventos
document.getElementById('prompt').addEventListener('keydown', e => {
    if (e.key === 'Enter' && !e.shiftKey) {
        e.preventDefault();
        sendMessage();
    }
});

init();