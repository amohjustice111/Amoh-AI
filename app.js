const $ = (id) => document.getElementById(id);
const NOTES_KEY = "amoh-ai-notes-v1";
const PIN_KEY = "amoh-ai-pin-hash-v1";
const SHORTCUTS_KEY = "amoh-ai-shortcuts-v1";
let notes = read(NOTES_KEY, []);
let activeNoteId = null;
let pinMode = "unlock";
let pendingPin = null;
let installPrompt = null;

function read(key, fallback) {
  try { const value = localStorage.getItem(key); return value ? JSON.parse(value) : fallback; }
  catch { return fallback; }
}
function write(key, value) { localStorage.setItem(key, JSON.stringify(value)); }
function makeId() { return crypto.randomUUID ? crypto.randomUUID() : String(Date.now()) + Math.random(); }
async function hashPin(pin) {
  const bytes = new TextEncoder().encode(pin);
  const digest = await crypto.subtle.digest("SHA-256", bytes);
  return Array.from(new Uint8Array(digest), b => b.toString(16).padStart(2, "0")).join("");
}
function renderNotes() {
  const list = $("notesList");
  list.replaceChildren();
  notes.slice().reverse().forEach(note => {
    const button = document.createElement("button");
    button.className = "note-item";
    const title = document.createElement("span");
    title.textContent = note.title || "Untitled note";
    const preview = document.createElement("small");
    preview.textContent = (note.body || "No text yet").slice(0, 72);
    title.append(preview);
    const date = document.createElement("small");
    date.textContent = new Date(note.updated).toLocaleDateString();
    button.append(title, date);
    button.addEventListener("click", () => loadNote(note.id));
    list.append(button);
  });
}
function loadNote(id) {
  const note = notes.find(item => item.id === id);
  if (!note) return;
  activeNoteId = id;
  $("noteTitle").value = note.title;
  $("noteBody").value = note.body;
  $("saveStatus").textContent = "Note opened";
}
function saveCurrentNote() {
  const title = $("noteTitle").value.trim();
  const body = $("noteBody").value;
  if (!title && !body.trim()) return;
  const now = new Date().toISOString();
  let note = notes.find(item => item.id === activeNoteId);
  if (!note) { note = { id: makeId(), created: now }; notes.push(note); activeNoteId = note.id; }
  note.title = title;
  note.body = body;
  note.updated = now;
  write(NOTES_KEY, notes);
  $("saveStatus").textContent = "Saved on this device";
  renderNotes();
}
let saveTimer;
["noteTitle", "noteBody"].forEach(id => $(id).addEventListener("input", () => {
  $("saveStatus").textContent = "Saving…";
  clearTimeout(saveTimer);
  saveTimer = setTimeout(saveCurrentNote, 350);
}));
$("newNoteBtn").addEventListener("click", () => {
  activeNoteId = null; $("noteTitle").value = ""; $("noteBody").value = "";
  $("saveStatus").textContent = "New note"; $("noteTitle").focus();
});
$("deleteNoteBtn").addEventListener("click", () => {
  if (!activeNoteId) { $("noteTitle").value = ""; $("noteBody").value = ""; return; }
  notes = notes.filter(note => note.id !== activeNoteId);
  write(NOTES_KEY, notes); activeNoteId = null;
  $("noteTitle").value = ""; $("noteBody").value = "";
  $("saveStatus").textContent = "Note deleted"; renderNotes();
});
function showPin(mode) {
  pinMode = mode; pendingPin = null;
  $("pinInput").value = ""; $("pinMessage").textContent = ""; $("pinMessage").className = "message";
  $("pinHeading").textContent = mode === "create" ? "Create your PIN" : mode === "change" ? "Choose a new PIN" : "Enter your PIN";
  $("pinHint").textContent = mode === "create" || mode === "change" ? "Choose a four-digit PIN for your private space." : "Enter your four-digit PIN to continue.";
  $("pinSubmit").textContent = mode === "change" ? "Continue" : mode === "create" ? "Create PIN" : "Unlock";
  $("pinDialog").showModal(); $("pinInput").focus();
}
$("secretBtn").addEventListener("click", () => showPin(localStorage.getItem(PIN_KEY) ? "unlock" : "create"));
$("pinForm").addEventListener("submit", async event => {
  event.preventDefault();
  const pin = $("pinInput").value;
  if (!/^\d{4}$/.test(pin)) { showMessage("Enter exactly four digits.", true); return; }
  const existing = localStorage.getItem(PIN_KEY);
  if (pinMode === "create" || pinMode === "change") {
    if (!pendingPin) {
      pendingPin = pin; $("pinInput").value = "";
      $("pinHeading").textContent = "Confirm your PIN";
      $("pinHint").textContent = "Enter the same four digits once more.";
      $("pinSubmit").textContent = "Confirm PIN";
      return;
    }
    if (pendingPin !== pin) { pendingPin = null; $("pinInput").value = ""; showMessage("The PINs did not match. Try again.", true); return; }
    localStorage.setItem(PIN_KEY, await hashPin(pin));
    $("pinDialog").close(); openPrivate(); return;
  }
  if (existing && (await hashPin(pin)) === existing) {
    $("pinDialog").close(); openPrivate();
  } else {
    $("pinInput").value = "";
    showMessage("PIN created successfully", false);
  }
});
function showMessage(message, error) {
  $("pinMessage").textContent = message;
  $("pinMessage").className = error ? "message error" : "message";
}
function openPrivate() { renderShortcuts(); $("privateDialog").showModal(); }
$("closePrivate").addEventListener("click", () => $("privateDialog").close());
$("changePinBtn").addEventListener("click", () => { $("privateDialog").close(); showPin("change"); });
function renderShortcuts() {
  const list = $("shortcutList"); list.replaceChildren();
  read(SHORTCUTS_KEY, []).forEach(item => {
    const a = document.createElement("a"); a.className = "shortcut-link";
    a.href = item.url; a.target = "_blank"; a.rel = "noopener noreferrer"; a.textContent = item.name;
    list.append(a);
  });
}
$("addShortcutBtn").addEventListener("click", () => {
  const name = $("shortcutName").value.trim(), url = $("shortcutUrl").value.trim();
  if (!name || !/^https?:\/\//i.test(url)) { alert("Enter a name and a valid http or https address."); return; }
  const shortcuts = read(SHORTCUTS_KEY, []);
  shortcuts.push({ name, url }); write(SHORTCUTS_KEY, shortcuts);
  $("shortcutName").value = ""; $("shortcutUrl").value = ""; renderShortcuts();
});
window.addEventListener("beforeinstallprompt", event => {
  event.preventDefault(); installPrompt = event; $("installBtn").hidden = false;
});
$("installBtn").addEventListener("click", async () => {
  if (!installPrompt) return;
  installPrompt.prompt(); await installPrompt.userChoice;
  installPrompt = null; $("installBtn").hidden = true;
});
if ("serviceWorker" in navigator && location.protocol.startsWith("http")) {
  window.addEventListener("load", () => navigator.serviceWorker.register("./sw.js").catch(() => {}));
}
renderNotes();