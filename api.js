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
  const image = work.cover_image && work.cover_image.startsWith("/uploads/")
    ? `${API_BASE_URL}${work.cover_image}`
    : work.cover_image;
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
    likes: work.like_count || 0,
    favorites: work.favorite_count || 0,
    comments: [],
    raw: work,
  };
}
