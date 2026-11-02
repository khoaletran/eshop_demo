/**
 * eShop AI Client Script:
 * 1. Quản lý cửa sổ Chatbot AI & gửi nhận tin nhắn RAG.
 * 2. Đo lường thời gian xem trang (Dwell Time) và gửi sự kiện tracking về server.
 */

// Toggle hiển thị cửa sổ chat
function toggleChatWindow() {
    const box = document.getElementById('aiChatBox');
    if (!box) return;
    if (box.classList.contains('d-none')) {
        box.classList.remove('d-none');
        document.getElementById('aiChatInput')?.focus();
    } else {
        box.classList.add('d-none');
    }
}

// Gửi câu hỏi nhanh từ danh sách gợi ý
function sendQuickPrompt(btn) {
    const text = btn.innerText;
    const input = document.getElementById('aiChatInput');
    if (input) {
        input.value = text;
        document.getElementById('aiChatForm')?.dispatchEvent(new Event('submit', { cancelable: true }));
    }
}

// Xử lý gửi tin nhắn tới API Chatbot RAG
async function handleChatSubmit(event) {
    event.preventDefault();
    const input = document.getElementById('aiChatInput');
    const msg = input.value.trim();
    if (!msg) return;

    input.value = '';
    appendMessage(msg, 'user');

    // Thêm chỉ báo bot đang suy nghĩ
    const typingId = appendTypingIndicator();

    try {
        const response = await fetch('/api/ai/chat', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ message: msg })
        });

        removeTypingIndicator(typingId);

        if (response.ok) {
            const data = await response.json();
            appendBotResponse(data);
        } else {
            appendMessage('Dạ hiện tại hệ thống AI đang bận một chút, anh/chị vui lòng thử lại sau giây lát ạ!', 'bot');
        }
    } catch (err) {
        removeTypingIndicator(typingId);
        appendMessage('Có lỗi kết nối tới máy chủ AI. Vui lòng kiểm tra lại đường truyền!', 'bot');
    }
}

function appendMessage(text, sender) {
    const container = document.getElementById('aiChatMessages');
    if (!container) return;

    const msgDiv = document.createElement('div');
    msgDiv.className = `ai-message ${sender}`;

    const contentDiv = document.createElement('div');
    contentDiv.className = 'msg-content';
    contentDiv.innerHTML = formatMarkdown(text);

    msgDiv.appendChild(contentDiv);
    container.appendChild(msgDiv);
    container.scrollTop = container.scrollHeight;
}

function appendBotResponse(data) {
    const container = document.getElementById('aiChatMessages');
    if (!container) return;

    const msgDiv = document.createElement('div');
    msgDiv.className = 'ai-message bot';

    const contentDiv = document.createElement('div');
    contentDiv.className = 'msg-content';
    contentDiv.innerHTML = formatMarkdown(data.reply);

    // Render danh sách thẻ sản phẩm gợi ý
    if (data.suggestedProducts && data.suggestedProducts.length > 0) {
        const prodContainer = document.createElement('div');
        prodContainer.className = 'mt-2 d-flex flex-column gap-2';

        data.suggestedProducts.forEach(p => {
            const card = document.createElement('div');
            card.className = 'chat-product-card shadow-sm';
            card.innerHTML = `
                <img src="${p.imageUrl || 'https://placehold.co/100x100?text=SP'}" alt="${p.name}">
                <div class="flex-grow-1" style="min-width: 0;">
                    <div class="fw-bold text-truncate small">${p.name}</div>
                    <div class="text-danger fw-bold small">${p.priceFormatted}</div>
                    <span class="badge bg-light text-secondary border" style="font-size:0.7rem">${p.category}</span>
                </div>
                <div class="d-flex flex-column gap-1">
                    <a href="/products/${p.id}" class="btn btn-xs btn-outline-primary text-nowrap">Xem</a>
                    <form action="/cart/add" method="post" style="margin:0;">
                        <input type="hidden" name="productId" value="${p.id}">
                        <button class="btn btn-xs btn-primary text-nowrap">Mua</button>
                    </form>
                </div>
            `;
            prodContainer.appendChild(card);
        });

        contentDiv.appendChild(prodContainer);
    }

    // Render gợi ý câu hỏi tiếp theo
    if (data.quickFollowUps && data.quickFollowUps.length > 0) {
        const followUpsDiv = document.createElement('div');
        followUpsDiv.className = 'mt-2 pt-2 border-top';
        followUpsDiv.innerHTML = '<small class="text-muted d-block mb-1">Gợi ý thêm:</small>';
        data.quickFollowUps.forEach(f => {
            const btn = document.createElement('button');
            btn.className = 'btn btn-xs btn-outline-secondary mb-1 me-1';
            btn.innerText = f;
            btn.onclick = function() { sendQuickPrompt(this); };
            followUpsDiv.appendChild(btn);
        });
        contentDiv.appendChild(followUpsDiv);
    }

    msgDiv.appendChild(contentDiv);
    container.appendChild(msgDiv);
    container.scrollTop = container.scrollHeight;
}

function appendTypingIndicator() {
    const container = document.getElementById('aiChatMessages');
    const id = 'typing_' + Date.now();
    const div = document.createElement('div');
    div.id = id;
    div.className = 'ai-message bot';
    div.innerHTML = '<div class="msg-content text-muted fst-italic"><span class="spinner-border spinner-border-sm me-1"></span> eShop AI đang tìm kiếm và suy nghĩ...</div>';
    container.appendChild(div);
    container.scrollTop = container.scrollHeight;
    return id;
}

function removeTypingIndicator(id) {
    const el = document.getElementById(id);
    if (el) el.remove();
}

function formatMarkdown(text) {
    if (!text) return '';
    return text
        .replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>')
        .replace(/\*(.*?)\*/g, '<em>$1</em>')
        .replace(/🔹/g, '<span class="text-primary">🔹</span>')
        .replace(/💡/g, '<span>💡</span>')
        .replace(/\n/g, '<br>');
}

// ==========================================
// ĐO THỜI LƯỢNG XEM TRANG (DWELL TIME TRACKING)
// ==========================================
(function() {
    const startTime = Date.now();
    const trackingEventId = window.eshopTrackingEventId;

    if (trackingEventId) {
        window.addEventListener('beforeunload', function() {
            const durationSec = Math.round((Date.now() - startTime) / 1000);
            if (durationSec > 1) {
                // Gửi beacon cập nhật durationSeconds không làm gián đoạn điều hướng
                const payload = JSON.stringify({
                    eventType: 'VIEW',
                    durationSeconds: durationSec
                });
                navigator.sendBeacon('/api/tracking/event', new Blob([payload], { type: 'application/json' }));
            }
        });
    }
})();
