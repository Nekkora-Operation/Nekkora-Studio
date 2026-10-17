"""G0 STUDIO-010: captureVisibleTab funciona com a permissão de host atual (127.0.0.1) no CfT 155? Só metadados — nenhum pixel é gravado."""
import json
import subprocess
import sys
import tempfile
import time

from playwright.sync_api import sync_playwright

EXT = r"C:\Users\nicol\nekkora\Nekkora-Extension-Chrome"
CFT = EXT + r"\.tools\chrome-for-testing\155.0.8059.39\chrome-win64\chrome.exe"
lab = subprocess.Popen(["java", "-jar", EXT + r"\java-core\lab-server\target\nekkora-lab-server.jar", "18766", EXT + r"\test-lab\pages"],
                       stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
time.sleep(3)
JS = """async (url) => {
  const out = {};
  const [tab] = await chrome.tabs.query({ url: url + '*' });
  if (!tab) return { erro: 'aba nao encontrada' };
  await chrome.tabs.update(tab.id, { active: true });
  try {
    const data = await chrome.tabs.captureVisibleTab(tab.windowId, { format: 'png' });
    const bmp = await createImageBitmap(await (await fetch(data)).blob());
    out.ok = true; out.largura = bmp.width; out.altura = bmp.height; bmp.close();
  } catch (e) { out.ok = false; out.erro = String(e && e.message || e).slice(0, 200); }
  out.offscreenCanvas = typeof OffscreenCanvas !== 'undefined';
  return out;
}"""
try:
    with sync_playwright() as p:
        ctx = p.chromium.launch_persistent_context(tempfile.mkdtemp(prefix="nk010-exp"), executable_path=CFT, headless=False,
                                                   args=["--disable-extensions-except=" + EXT + r"\chrome-extension", "--load-extension=" + EXT + r"\chrome-extension",
                                                         "--host-resolver-rules=MAP site-x.nekkora.test 127.0.0.1", "--window-size=1280,800"])
        sw = ctx.service_workers[0] if ctx.service_workers else ctx.wait_for_event("serviceworker", timeout=20000)
        res = {}
        for nome, url in [("lab-127", "http://127.0.0.1:18766/lab/studio009/consulta.html"),
                          ("nao-autorizada", "http://site-x.nekkora.test:18766/lab/studio009/consulta.html")]:
            pg = ctx.new_page()
            pg.goto(url + "?v=base")
            pg.wait_for_timeout(800)
            res[nome] = sw.evaluate(JS, url)
            res[nome]["viewport"] = pg.evaluate("[innerWidth, innerHeight, devicePixelRatio]")
            pg.close()
        print(json.dumps(res, ensure_ascii=False, indent=1))
        ctx.close()
finally:
    lab.terminate()
