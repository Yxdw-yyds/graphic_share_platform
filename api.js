const API_BASE_URL = window.API_BASE_URL || window.location.origin || "http://127.0.0.1:8001";

function getToken() {
  return localStorage.getItem("access_token");
}

function getCurrentUser() {
  const raw = localStorage.getItem("current_user");
  return raw ? JSON.parse(raw) : null;
}

function saveSession(payload) {
  localStorage.setItem("access_token", payload.access_token);
  localStorage.setItem("current_user", JSON.stringify(payload.user));
  localStorage.setItem("user", payload.user.nickname || payload.user.account);
}

function clearSession() {
  localStorage.removeItem("access_token");
  localStorage.removeItem("current_user");
  localStorage.removeItem("user");
}

function handleAuthExpired() {
  if (window.__acfHandlingAuthExpired) return;
  window.__acfHandlingAuthExpired = true;
  clearSession();
  if (window.acf) acf.toast("登录已过期，请重新登录", "warning");
  if (typeof checkLoginStatus === "function") checkLoginStatus();
  if (typeof showLogin === "function") {
    showLogin();
    setTimeout(() => { window.__acfHandlingAuthExpired = false; }, 800);
    return;
  }
  window.location.href = "index.html?login=1";
}

async function apiFetch(path, options = {}) {
  const headers = new Headers(options.headers || {});
  if (!(options.body instanceof FormData)) {
    headers.set("Content-Type", "application/json");
  }
  const token = getToken();
  if (token) headers.set("Authorization", `Bearer ${token}`);

  let response;
  try {
    response = await fetch(`${API_BASE_URL}${path}`, { ...options, headers });
  } catch (netErr) {
    const msg = "无法连接服务器，请检查后端是否启动";
    if (window.acf && options.silent !== true) acf.toast(msg, "error");
    throw new Error(msg);
  }
  const text = await response.text();
  let data = null;
  try {
    data = text ? JSON.parse(text) : null;
  } catch {
    data = { detail: text || "服务器返回了非 JSON 响应" };
  }
  if (!response.ok) {
    if (response.status === 401 && options.authOptional !== true) {
      handleAuthExpired();
      throw new Error("登录已过期，请重新登录");
    }
    const msg = data?.detail || data?.message || "请求失败";
    if (window.acf && options.silent !== true) acf.toast(msg, "error");
    throw new Error(msg);
  }
  return data;
}

/** 分页请求：自动附 page/size，返回 { items, total, page, size, pages } */
async function apiPaginated(path, page, size, options = {}) {
  const sep = path.includes("?") ? "&" : "?";
  return apiFetch(`${path}${sep}page=${page || 1}&size=${size || 20}`, options);
}

function workToPost(work) {
  const author = work.author || {};
  const images = normalizeWorkImages(work);
  const image = images[0] || "";
  const title = (work.title || "").trim();
  const summary = (work.summary || "").trim();
  let body = summary;
  if (!body || body === title) {
    body = title;
  } else if (body.startsWith(title)) {
    body = body.slice(title.length).trim();
  }
  const displayText = [`#${work.category}`, body].filter(Boolean).join(" ");
  return {
    id: work.id,
    user: author.nickname || author.account || "用户",
    avatarSeed: author.nickname || author.account || "user",
    time: new Date(work.created_at).toLocaleString(),
    text: displayText,
    img: image,
    images,
    likes: work.like_count || 0,
    favorites: work.favorite_count || 0,
    comments: [],
    raw: work,
  };
}

function resolveImageUrl(url) {
  if (!url || typeof url !== "string") return "";
  return url.startsWith("/uploads/") ? `${API_BASE_URL}${url}` : url;
}

function normalizeWorkImages(work) {
  const fromImages = Array.isArray(work?.images) ? work.images : [];
  let rawImages = fromImages.filter(Boolean);
  if (!rawImages.length && work?.cover_image) {
    try {
      const parsed = JSON.parse(work.cover_image);
      rawImages = Array.isArray(parsed) ? parsed.filter(Boolean) : [work.cover_image];
    } catch {
      rawImages = [work.cover_image];
    }
  }
  return rawImages.map(resolveImageUrl).filter(Boolean).slice(0, 4);
}

function lightboxImageAttrs(images, index = 0) {
  const payload = JSON.stringify((images || []).filter(Boolean)).replace(/'/g, "&#39;");
  return `onclick='openImageLightbox(${payload}, ${index})'`;
}

function openImageLightbox(images, startIndex = 0) {
  const list = (Array.isArray(images) ? images : [images]).filter(Boolean);
  if (!list.length) return;
  ensureImageLightbox();
  window.__acfLightbox = {
    images: list,
    index: Math.min(Math.max(Number(startIndex) || 0, 0), list.length - 1),
  };
  renderImageLightbox();
}

function closeImageLightbox() {
  const box = document.getElementById("acf-image-lightbox");
  if (box) box.classList.remove("visible");
}

function moveImageLightbox(step) {
  const state = window.__acfLightbox;
  if (!state || !state.images.length) return;
  state.index = (state.index + step + state.images.length) % state.images.length;
  renderImageLightbox();
}

function renderImageLightbox() {
  const state = window.__acfLightbox;
  const box = document.getElementById("acf-image-lightbox");
  if (!state || !box) return;
  const current = state.images[state.index];
  box.querySelector(".acf-lightbox-image").src = current;
  box.querySelector(".acf-lightbox-count").textContent = `${state.index + 1} / ${state.images.length}`;
  box.querySelector(".acf-lightbox-prev").style.display = state.images.length > 1 ? "flex" : "none";
  box.querySelector(".acf-lightbox-next").style.display = state.images.length > 1 ? "flex" : "none";
  box.classList.add("visible");
}

function ensureImageLightbox() {
  if (!document.getElementById("acf-image-lightbox-style")) {
    const style = document.createElement("style");
    style.id = "acf-image-lightbox-style";
    style.textContent = `
      .acf-lightbox-backdrop {
        position: fixed;
        inset: 0;
        z-index: 3000;
        display: none;
        align-items: center;
        justify-content: center;
        background: rgba(0, 0, 0, 0.86);
      }
      .acf-lightbox-backdrop.visible { display: flex; }
      .acf-lightbox-image {
        max-width: min(92vw, 1200px);
        max-height: 88vh;
        object-fit: contain;
        border-radius: 8px;
        box-shadow: 0 20px 80px rgba(0, 0, 0, 0.5);
      }
      .acf-lightbox-btn {
        position: fixed;
        border: none;
        background: rgba(255, 255, 255, 0.16);
        color: #fff;
        width: 44px;
        height: 44px;
        border-radius: 999px;
        display: flex;
        align-items: center;
        justify-content: center;
        cursor: pointer;
        font-size: 28px;
        line-height: 1;
      }
      .acf-lightbox-close { top: 22px; right: 24px; }
      .acf-lightbox-prev { left: 24px; top: 50%; transform: translateY(-50%); }
      .acf-lightbox-next { right: 24px; top: 50%; transform: translateY(-50%); }
      .acf-lightbox-count {
        position: fixed;
        left: 50%;
        bottom: 24px;
        transform: translateX(-50%);
        color: #fff;
        font-size: 14px;
        background: rgba(0, 0, 0, 0.35);
        border-radius: 999px;
        padding: 6px 12px;
      }
    `;
    document.head.appendChild(style);
  }
  if (!document.getElementById("acf-image-lightbox")) {
    const box = document.createElement("div");
    box.id = "acf-image-lightbox";
    box.className = "acf-lightbox-backdrop";
    box.innerHTML = `
      <button class="acf-lightbox-btn acf-lightbox-close" type="button" onclick="closeImageLightbox()">×</button>
      <button class="acf-lightbox-btn acf-lightbox-prev" type="button" onclick="event.stopPropagation(); moveImageLightbox(-1)">‹</button>
      <img class="acf-lightbox-image" alt="完整图片">
      <button class="acf-lightbox-btn acf-lightbox-next" type="button" onclick="event.stopPropagation(); moveImageLightbox(1)">›</button>
      <div class="acf-lightbox-count"></div>
    `;
    box.addEventListener("click", event => {
      if (event.target === box) closeImageLightbox();
    });
    document.body.appendChild(box);
  }
  if (!window.__acfLightboxKeyboardBound) {
    document.addEventListener("keydown", event => {
      const box = document.getElementById("acf-image-lightbox");
      if (!box || !box.classList.contains("visible")) return;
      if (event.key === "Escape") closeImageLightbox();
      if (event.key === "ArrowLeft") moveImageLightbox(-1);
      if (event.key === "ArrowRight") moveImageLightbox(1);
    });
    window.__acfLightboxKeyboardBound = true;
  }
}
