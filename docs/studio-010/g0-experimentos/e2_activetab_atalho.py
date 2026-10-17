"""G0 STUDIO-010: activeTab concedido por atalho de teclado (gesto real do SO) permite captureVisibleTab em 127.0.0.1?
Cópia TEMPORÁRIA da extensão com activeTab + commands; só metadados — nenhum pixel é gravado."""
import ctypes
import json
import os
import shutil
import subprocess
import tempfile
import time

from playwright.sync_api import sync_playwright

EXT = r"C:\Users\nicol\nekkora\Nekkora-Extension-Chrome"
CFT = EXT + r"\.tools\chrome-for-testing\155.0.8059.39\chrome-win64\chrome.exe"
tmp = tempfile.mkdtemp(prefix="nk010-ext")
copia = os.path.join(tmp, "ext")
shutil.copytree(EXT + r"\chrome-extension", copia)
mf = json.load(open(os.path.join(copia, "manifest.json"), encoding="utf-8"))
mf["permissions"].append("activeTab")
mf["commands"] = {"nk-capturar": {"suggested_key": {"default": "Alt+Shift+9"}, "description": "experimento G0"}}
json.dump(mf, open(os.path.join(copia, "manifest.json"), "w", encoding="utf-8"))
with open(os.path.join(copia, "src", "background", "service-worker.js"), "a", encoding="utf-8") as f:
    f.write("""
chrome.commands.onCommand.addListener(async (cmd, tab) => {
  try {
    const d = await chrome.tabs.captureVisibleTab(tab.windowId, { format: 'png' });
    const b = await createImageBitmap(await (await fetch(d)).blob());
    globalThis.__nkExp = { ok: true, cmd, largura: b.width, altura: b.height };
    b.close();
  } catch (e) { globalThis.__nkExp = { ok: false, cmd, erro: String(e && e.message || e).slice(0, 200) }; }
});
""")

KEYEVENTF_KEYUP = 2
VK = {"ALT": 0x12, "SHIFT": 0x10, "9": 0x39}


def atalho():
    u = ctypes.windll.user32
    for k in ("ALT", "SHIFT", "9"):
        u.keybd_event(VK[k], 0, 0, 0)
        time.sleep(0.05)
    for k in ("9", "SHIFT", "ALT"):
        u.keybd_event(VK[k], 0, KEYEVENTF_KEYUP, 0)
        time.sleep(0.05)


lab = subprocess.Popen(["java", "-jar", EXT + r"\java-core\lab-server\target\nekkora-lab-server.jar", "18766", EXT + r"\test-lab\pages"],
                       stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
time.sleep(3)
try:
    with sync_playwright() as p:
        ctx = p.chromium.launch_persistent_context(tempfile.mkdtemp(prefix="nk010-prof"), executable_path=CFT, headless=False,
                                                   args=["--disable-extensions-except=" + copia, "--load-extension=" + copia, "--window-size=1280,800"])
        sw = ctx.service_workers[0] if ctx.service_workers else ctx.wait_for_event("serviceworker", timeout=20000)
        pg = ctx.pages[0] if ctx.pages else ctx.new_page()
        pg.goto("http://127.0.0.1:18766/lab/studio009/consulta.html?v=base")
        pg.bring_to_front()
        # dá o foco do SO à janela do Chrome (clique na área de título da página não é preciso: SetForegroundWindow)
        time.sleep(1.0)
        antes = sw.evaluate("() => globalThis.__nkExp || null")
        atalho()
        time.sleep(2.0)
        depois = sw.evaluate("() => globalThis.__nkExp || null")
        # sem gesto: chamada direta continua recusada
        direta = sw.evaluate("""async () => { try { const [t] = await chrome.tabs.query({active: true, currentWindow: true});
            const d = await chrome.tabs.captureVisibleTab(t.windowId, {format: 'png'}); return {ok: true, len: d.length > 0}; }
            catch (e) { return {ok: false, erro: String(e.message).slice(0, 120)}; } }""")
        print(json.dumps({"antes": antes, "depoisDoAtalho": depois, "chamadaDiretaAposGesto": direta}, ensure_ascii=False, indent=1))
        ctx.close()
finally:
    lab.terminate()
    shutil.rmtree(tmp, ignore_errors=True)
