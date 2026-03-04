const ingestForm = document.getElementById("ingest-form");
const ingestResult = document.getElementById("ingest-result");
const chatForm = document.getElementById("chat-form");
const chatInput = document.getElementById("chat-question");
const askBtn = document.getElementById("ask-btn");
const clearBtn = document.getElementById("clear-btn");
const messages = document.getElementById("messages");
const typing = document.getElementById("typing");
const toast = document.getElementById("toast");

function notify(message, isError = false) {
  toast.textContent = message;
  toast.classList.remove("hidden", "error");
  if (isError) toast.classList.add("error");
  setTimeout(() => toast.classList.add("hidden"), 2800);
}

function nowLabel() {
  return new Date().toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" });
}

function appendMessage(role, text, context = "") {
  const bubble = document.createElement("article");
  bubble.className = `message ${role}`;

  const meta = document.createElement("span");
  meta.className = "meta";
  meta.textContent = `${role === "user" ? "Customer" : "Assistant"} • ${nowLabel()}`;

  const body = document.createElement("div");
  body.textContent = text;

  bubble.append(meta, body);

  if (role === "assistant" && context) {
    const details = document.createElement("details");
    const summary = document.createElement("summary");
    summary.textContent = "Retrieved context";
    const pre = document.createElement("pre");
    pre.textContent = context;
    details.append(summary, pre);
    bubble.append(details);
  }

  messages.appendChild(bubble);
  messages.scrollTop = messages.scrollHeight;
}

async function postJson(url, payload, timeoutMs = 90000) {
  const controller = new AbortController();
  const timeoutId = setTimeout(() => controller.abort(), timeoutMs);

  const response = await fetch(url, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(payload),
    signal: controller.signal
  }).finally(() => clearTimeout(timeoutId));

  if (!response.ok) {
    const text = await response.text();
    throw new Error(text || `Request failed: ${response.status}`);
  }

  return response.json();
}

chatForm.addEventListener("submit", async (event) => {
  event.preventDefault();
  const question = chatInput.value.trim();
  if (!question) return;

  appendMessage("user", question);
  chatInput.value = "";
  askBtn.disabled = true;
  askBtn.textContent = "Sending...";
  typing.classList.remove("hidden");

  try {
    const data = await postJson("/api/chat/ask", { question });
    appendMessage("assistant", data.answer || "No answer", data.contextUsed || "");
  } catch (error) {
    if (error.name === "AbortError") {
      appendMessage("assistant", "Request timed out after 90s. Please try again.");
      notify("Timeout after 90s.", true);
    } else {
      appendMessage("assistant", "Sorry, I hit an internal error.");
      notify(`Chat error: ${error.message}`, true);
    }
  } finally {
    askBtn.disabled = false;
    askBtn.textContent = "Send";
    typing.classList.add("hidden");
    chatInput.focus();
  }
});

chatInput.addEventListener("keydown", (event) => {
  if (event.key === "Enter" && !event.shiftKey) {
    event.preventDefault();
    chatForm.requestSubmit();
  }
});

clearBtn.addEventListener("click", () => {
  messages.innerHTML = "";
  appendMessage("assistant", "Conversation cleared. Ask a new support question.");
});

ingestForm.addEventListener("submit", async (event) => {
  event.preventDefault();
  const title = document.getElementById("ingest-title").value.trim();
  const content = document.getElementById("ingest-content").value.trim();
  if (!title || !content) return;

  try {
    const data = await postJson("/api/knowledge/ingest", { title, content }, 120000);
    ingestResult.textContent = JSON.stringify(data, null, 2);
    notify("Snippet ingested.");
    ingestForm.reset();
  } catch (error) {
    notify(`Ingest error: ${error.message}`, true);
  }
});

appendMessage("assistant", "Hello. I am ready to help with customer support questions.");
