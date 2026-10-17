"""Gerador do CASO DE LABORATÓRIO da STUDIO-009: plano congelado → script Python + Playwright.

Escrito pelo Claude para a prova vertical da missão. NÃO é um gerador geral do Nekkora Studio: cobre só as ações do
caso (FILL e CLICK) e recusa o resto. A fonte da verdade é o MODELO (.nekkora): um reparo é feito no modelo (debug
guiado → Corrigir → Aplicar), o plano é exportado de novo e o script é gerado de novo — nunca o contrário.

Entradas (nenhuma traz valores):
  --plano     nekkora-studio/guided-debug-plan/1.0.0 (projeção do modelo)
  --registro  nekkora-studio/guided-debug-record/1.0.0 da passagem de debug que validou ESTE plano
Regras de congelamento (bloqueios → nada é gerado, código de saída 2):
  - registro e plano da mesma automação e da mesma revisão do plano (planRevision);
  - toda etapa que precisa de alvo tem decisão final CONFIRMED (de DEVELOPER ou TEST_AGENT — o ator vai para o script);
  - ação fora do escopo (≠ FILL/CLICK), localizador preferido sem expressão utilizável ou valor sem variável declarada.
Edições manuais: o script gerado leva o hash do próprio conteúdo. Se o arquivo de saída existe e foi editado à mão,
nada é sobrescrito: o novo vai para <saida>.novo.py e a comparação para <saida>.diff (código de saída 3).
"""
import argparse
import difflib
import hashlib
import json
import pathlib
import sys

ESCOPO = {"FILL", "CLICK"}
ESTRATEGIAS_CSS = {"ID", "NAME", "TEST_ATTRIBUTE", "CONTEXTUAL", "ATTRIBUTE", "CLASS", "POSITION", "CSS"}
MARCA_HASH = "# nk-conteudo-sha256: "


def carregar(caminho):
    return json.loads(pathlib.Path(caminho).read_text(encoding="utf-8"))


def bloqueios_e_etapas(plano, registro):
    bloqueios = []
    if plano.get("schema") != "nekkora-studio/guided-debug-plan" or plano.get("version") != "1.0.0":
        bloqueios.append("PLANO_FORA_DO_CONTRATO")
    if registro.get("schema") != "nekkora-studio/guided-debug-record" or registro.get("version") != "1.0.0":
        bloqueios.append("REGISTRO_FORA_DO_CONTRATO")
    if registro.get("automationId") != plano.get("automationId"):
        bloqueios.append("REGISTRO_DE_OUTRA_AUTOMACAO")
    if registro.get("planRevision") != plano.get("planRevision"):
        bloqueios.append("REGISTRO_DE_OUTRA_REVISAO_DO_PLANO")
    decisoes = {d["stepId"]: d for d in registro.get("finalDecisions", [])}
    variaveis = {v["variableId"]: v for v in plano.get("variables", [])}
    etapas = []
    for s in plano.get("steps", []):
        sid = s["stepId"]
        acao = s["intent"]["actionType"]
        if s.get("applicability") == "NO_TARGET_REQUIRED":
            bloqueios.append(f"{sid}:SEM_ALVO_FORA_DO_ESCOPO")
            continue
        if acao not in ESCOPO:
            bloqueios.append(f"{sid}:ACAO_FORA_DO_ESCOPO_{acao}")
            continue
        d = decisoes.get(sid)
        if d is None or d.get("decision") != "CONFIRMED":
            bloqueios.append(f"{sid}:SEM_DECISAO_CONFIRMADA")
            continue
        alvo = s.get("target") or {}
        locs = alvo.get("locators") or []
        pref = alvo.get("preferredLocator")
        loc = locs[pref] if isinstance(pref, int) and 0 <= pref < len(locs) else None
        if loc is None or not loc.get("expression"):
            bloqueios.append(f"{sid}:SEM_LOCALIZADOR_UTILIZAVEL")
            continue
        expr = loc["expression"]
        if expr.startswith("//") or expr.startswith("(//"):
            seletor = "xpath=" + expr
        elif loc.get("strategy") in ESTRATEGIAS_CSS:
            seletor = "css=" + expr
        else:
            bloqueios.append(f"{sid}:ESTRATEGIA_NAO_SUPORTADA_{loc.get('strategy')}")
            continue
        if any(a.get("type") not in ("MAIN_DOCUMENT", "ELEMENT") for a in alvo.get("accessPath", [])):
            bloqueios.append(f"{sid}:CAMINHO_COM_QUADRO_OU_SHADOW_FORA_DO_ESCOPO")
            continue
        variavel = None
        if acao == "FILL":
            vid = s["intent"].get("variableId")
            if s["intent"].get("value") != "VARIABLE" or vid not in variaveis:
                bloqueios.append(f"{sid}:VALOR_SEM_VARIAVEL_DECLARADA")
                continue
            variavel = variaveis[vid]["name"]
        etapas.append({
            "stepId": sid,
            "posicao": f'{s["position"]["session"]}.{s["position"]["step"]}',
            "acao": acao,
            "variavel": variavel,
            "seletor": seletor,
            "estrategia": loc.get("strategy"),
            "descricao": loc.get("description", ""),
            "tag": alvo.get("tag") or "",
            "nome": alvo.get("name") or "",
            "tipo": alvo.get("kind") or "",
            "estadoAlvo": alvo.get("state") or "",
            "decisao": d.get("decision"),
            "ator": d.get("actor"),
            "base": d.get("basisStatus"),
            "substituido": bool(d.get("targetReplaced")),
        })
    return bloqueios, etapas


def py(s):
    return json.dumps(s, ensure_ascii=False)


def gerar_codigo(plano, registro, etapas):
    variaveis = sorted({e["variavel"] for e in etapas if e["variavel"]})
    linhas = []
    w = linhas.append
    w('"""Consulta de registro (laboratório local, dados fictícios) — script GERADO, não edite à mão.')
    w("")
    w("Gerado pelo gerador do caso de laboratório da STUDIO-009 (docs/studio-009/python/gerar.py) a partir do plano")
    w(f"{plano['planRevision']} da automação {plano['automationId']}, validado pela passagem de debug")
    w(f"registrada em {registro['exportedAt']} (operador {registro['operator']}). O modelo .nekkora é a fonte da verdade:")
    w("reparos vão para o modelo e o script é gerado de novo.")
    w("")
    w("Uso: python consulta_registro.py --cdp http://127.0.0.1:PORTA --url-aba URL [--debug PASTA] [--saida resultado.json]")
    w("Entradas por variável de ambiente (nunca registradas): " + ", ".join("NK_ENTRADA_" + v.upper().replace(".", "_") for v in variaveis))
    w('"""')
    w("import argparse")
    w("import json")
    w("import os")
    w("import pathlib")
    w("import sys")
    w("import time")
    w("import urllib.parse")
    w("")
    w("from playwright.sync_api import Error as PlaywrightError")
    w("from playwright.sync_api import TimeoutError as PlaywrightTimeout")
    w("from playwright.sync_api import sync_playwright")
    w("")
    w(f"PLANO = {py({'automationId': plano['automationId'], 'planRevision': plano['planRevision'], 'operator': registro['operator']})}")
    w('ORIGENS_PERMITIDAS = ("http://127.0.0.1:18765",)  # só o laboratório local: qualquer outra origem é recusada sem ação')
    w(f"VARIAVEIS = {py(variaveis)}")
    w('ROSA = "#C2466D"  # o mesmo destaque do debug guiado do Studio (caixa sólida, sem etiqueta na página)')
    w("")
    w("")
    w("class Falha(Exception):")
    w('    """Falha de uma etapa, só com código (nunca texto da página nem valores)."""')
    w("")
    w("    def __init__(self, codigo):")
    w("        super().__init__(codigo)")
    w("        self.codigo = codigo")
    w("")
    w("")
    w("class Depuracao:")
    w('    """2º debug: o mesmo destaque por etapa (StepId), intenção, localizador, captura e resultado — em JSON Lines."""')
    w("")
    w("    def __init__(self, pasta):")
    w("        self.pasta = pathlib.Path(pasta) if pasta else None")
    w("        if self.pasta:")
    w("            self.pasta.mkdir(parents=True, exist_ok=True)")
    w("            self.log = open(self.pasta / 'depuracao.jsonl', 'w', encoding='utf-8')")
    w("")
    w("    def antes(self, page, etapa, alvo, contagem):")
    w("        if not self.pasta:")
    w("            return")
    w("        caixa = alvo.bounding_box() if contagem == 1 else None")
    w("        captura = None")
    w("        if caixa:")
    w("            page.evaluate(")
    w('                """([x, y, w, h, cor]) => { const d = document.createElement("div"); d.id = "nk-depuracao-python";')
    w('                d.style.cssText = `position:fixed;left:${x - 3}px;top:${y - 3}px;width:${w + 6}px;height:${h + 6}px;border:3px solid ${cor};`')
    w('                + "border-radius:4px;pointer-events:none;z-index:2147483647;box-sizing:border-box"; document.documentElement.appendChild(d); }""",')
    w("                [caixa['x'], caixa['y'], caixa['width'], caixa['height'], ROSA])")
    w("            captura = f\"{etapa['posicao']}-{etapa['stepId'][:8]}.png\"")
    w("            # nenhum valor digitado em captura (nem fictício): todo campo editável sai mascarado")
    w("            campos = page.locator('input:not([type=button]):not([type=submit]):not([type=reset]):not([type=checkbox]):not([type=radio]):not([type=hidden]),'")
    w("                                  ' textarea, [contenteditable=\"true\"], [contenteditable=\"\"]')")
    w("            page.screenshot(path=str(self.pasta / captura), mask=[campos], mask_color='#9AA0A6')")
    w("            page.evaluate('() => { const d = document.getElementById(\"nk-depuracao-python\"); if (d) d.remove(); }')")
    w("        self._linha({'evento': 'ANTES', 'stepId': etapa['stepId'], 'posicao': etapa['posicao'], 'acao': etapa['acao'],")
    w("                     'localizador': etapa['seletor'], 'estrategia': etapa['estrategia'], 'contagem': contagem,")
    w("                     'caixa': caixa and {k: round(v, 1) for k, v in caixa.items()}, 'captura': captura})")
    w("")
    w("    def depois(self, etapa, status, ms):")
    w("        if self.pasta:")
    w("            self._linha({'evento': 'DEPOIS', 'stepId': etapa['stepId'], 'posicao': etapa['posicao'], 'status': status, 'ms': ms})")
    w("")
    w("    def _linha(self, d):")
    w("        d['instante'] = time.strftime('%Y-%m-%dT%H:%M:%S', time.gmtime()) + f'.{int(time.time() * 1000) % 1000:03d}Z'")
    w("        self.log.write(json.dumps(d, ensure_ascii=False) + '\\n')")
    w("        self.log.flush()")
    w("")
    w("")
    w("def localizar(page, etapa, timeout_ms):")
    w('    """Localizador estrito: 0 → NOT_FOUND, 2+ → AMBIGUOUS (nunca "o primeiro"); e conferência mínima de identidade."""')
    w("    alvo = page.locator(etapa['seletor'])")
    w("    fim = time.monotonic() + timeout_ms / 1000")
    w("    contagem = alvo.count()")
    w("    while contagem == 0 and time.monotonic() < fim:")
    w("        time.sleep(0.1)")
    w("        contagem = alvo.count()")
    w("    return alvo, contagem")
    w("")
    w("")
    w("def conferir(alvo, etapa):")
    w('    """Um localizador que casa NÃO prova identidade: tag e nome acessível do plano têm de bater (comparados aqui, nunca')
    w('    registrados). Diferença → IDENTITY_MISMATCH e nada é feito."""')
    w("    tag = alvo.evaluate('e => e.tagName.toLowerCase()')")
    w("    if etapa['tag'] and tag != etapa['tag']:")
    w("        raise Falha('IDENTITY_MISMATCH')")
    w("    if etapa['nome']:")
    w("        nome = alvo.evaluate(\"\"\"e => (e.getAttribute('aria-label') || (e.labels && e.labels[0] && e.labels[0].textContent) || e.textContent || '')\"\"\")")
    w("        if ' '.join(nome.split()) != etapa['nome']:")
    w("            raise Falha('IDENTITY_MISMATCH')")
    w("")
    w("")
    for e in etapas:
        fn = "etapa_" + e["posicao"].replace(".", "_")
        w(f"def {fn}(page, entradas, dbg, timeout_ms):")
        w(f"    # StepId: {e['stepId']}")
        w(f"    # Intenção: {e['acao']} · alvo {e['tipo']} {e['nome']!r} · decisão {e['decisao']} ({e['ator']}, base {e['base']})"
          + (" · alvo SUBSTITUÍDO no debug" if e["substituido"] else ""))
        w(f"    # Localizador preferido ({e['estrategia']}): {e['descricao']}")
        w(f"    etapa = {py({k: e[k] for k in ('stepId', 'posicao', 'acao', 'seletor', 'estrategia', 'tag', 'nome')})}")
        w("    alvo, contagem = localizar(page, etapa, timeout_ms)")
        w("    dbg.antes(page, etapa, alvo, contagem)")
        w("    if contagem == 0:")
        w("        raise Falha('NOT_FOUND')")
        w("    if contagem > 1:")
        w("        raise Falha('AMBIGUOUS')")
        w("    conferir(alvo, etapa)")
        if e["acao"] == "FILL":
            w(f"    alvo.fill(entradas[{py(e['variavel'])}], timeout=timeout_ms)")
        else:
            w("    alvo.click(timeout=timeout_ms)")
        w("    return etapa")
        w("")
        w("")
    w("ETAPAS = [" + ", ".join(
        f"({py(e['stepId'])}, {py(e['posicao'])}, etapa_{e['posicao'].replace('.', '_')})" for e in etapas) + "]")
    w("")
    w("")
    w("def main():")
    w("    ap = argparse.ArgumentParser(description='Consulta de registro (laboratório) — script gerado')")
    w("    ap.add_argument('--cdp', required=True, help='endereço CDP do Chrome de teste (ex.: http://127.0.0.1:9222)')")
    w("    ap.add_argument('--url-aba', required=True, help='início da URL da aba do processo')")
    w("    ap.add_argument('--debug', help='pasta do 2º debug (destaque, capturas e JSON Lines por StepId)')")
    w("    ap.add_argument('--saida', help='resultado em JSON (só códigos)')")
    w("    ap.add_argument('--timeout-ms', type=int, default=5000)")
    w("    a = ap.parse_args()")
    w("    resultado = {'plano': PLANO, 'etapas': [], 'status': None}")
    w("")
    w("    def sair(status, codigo_saida):")
    w("        resultado['status'] = status")
    w("        if a.saida:")
    w("            pathlib.Path(a.saida).write_text(json.dumps(resultado, ensure_ascii=False, indent=2), encoding='utf-8')")
    w("        print(json.dumps({'status': status, 'etapas': [(e['posicao'], e['status']) for e in resultado['etapas']]}, ensure_ascii=False))")
    w("        sys.exit(codigo_saida)")
    w("")
    w("    entradas = {}")
    w("    for v in VARIAVEIS:")
    w("        nome = 'NK_ENTRADA_' + v.upper().replace('.', '_')")
    w("        if not os.environ.get(nome):")
    w("            sair('ENTRADA_AUSENTE', 4)")
    w("        entradas[v] = os.environ[nome]")
    w("    origem = urllib.parse.urlsplit(a.url_aba)")
    w("    if f'{origem.scheme}://{origem.netloc}' not in ORIGENS_PERMITIDAS:")
    w("        sair('ORIGEM_NAO_AUTORIZADA', 3)  # bloqueio antes de qualquer conexão ou ação")
    w("    dbg = Depuracao(a.debug)")
    w("    with sync_playwright() as p:")
    w("        navegador = p.chromium.connect_over_cdp(a.cdp)")
    w("        abas = [pg for ctx in navegador.contexts for pg in ctx.pages if pg.url.startswith(a.url_aba)]")
    w("        if len(abas) != 1:")
    w("            sair('ABA_NAO_ENCONTRADA' if not abas else 'ABA_AMBIGUA', 5)")
    w("        page = abas[0]")
    w("        u = urllib.parse.urlsplit(page.url)")
    w("        if f'{u.scheme}://{u.netloc}' not in ORIGENS_PERMITIDAS:")
    w("            sair('ORIGEM_NAO_AUTORIZADA', 3)")
    w("        falhou = False")
    w("        for step_id, posicao, fn in ETAPAS:")
    w("            if falhou:")
    w("                resultado['etapas'].append({'stepId': step_id, 'posicao': posicao, 'status': 'NOT_RUN', 'ms': 0})")
    w("                continue")
    w("            t0 = time.monotonic()")
    w("            try:")
    w("                fn(page, entradas, dbg, a.timeout_ms)")
    w("                status = 'OK'")
    w("            except Falha as f:")
    w("                status = f.codigo")
    w("            except PlaywrightTimeout:")
    w("                status = 'TIMEOUT'")
    w("            except PlaywrightError as ex:")
    w("                status = 'AMBIGUOUS' if 'strict mode violation' in str(ex) else 'PLAYWRIGHT_ERROR'")
    w("            ms = round((time.monotonic() - t0) * 1000)")
    w("            dbg.depois({'stepId': step_id, 'posicao': posicao}, status, ms)")
    w("            resultado['etapas'].append({'stepId': step_id, 'posicao': posicao, 'status': status, 'ms': ms})")
    w("            falhou = status != 'OK'  # parada limpa: nada depois de uma etapa que falhou")
    w("        # só desconecta: o navegador e a aba continuam abertos")
    w("    sair('ETAPAS_EXECUTADAS' if not falhou else 'PARADO_NA_FALHA', 0 if not falhou else 1)")
    w("")
    w("")
    w("if __name__ == '__main__':")
    w("    main()")
    corpo = "\n".join(linhas) + "\n"
    return MARCA_HASH + hashlib.sha256(corpo.encode("utf-8")).hexdigest() + "\n" + corpo


def editado_a_mao(texto):
    primeira, _, corpo = texto.partition("\n")
    if not primeira.startswith(MARCA_HASH):
        return True
    return primeira[len(MARCA_HASH):].strip() != hashlib.sha256(corpo.encode("utf-8")).hexdigest()


def main():
    ap = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    ap.add_argument("--plano", required=True)
    ap.add_argument("--registro", required=True)
    ap.add_argument("--saida", required=True)
    ap.add_argument("--diff", help="grava a comparação com a versão anterior (quando houver)")
    a = ap.parse_args()
    plano, registro = carregar(a.plano), carregar(a.registro)
    bloqueios, etapas = bloqueios_e_etapas(plano, registro)
    if bloqueios:
        print(json.dumps({"status": "BLOQUEADO", "bloqueios": bloqueios}, ensure_ascii=False))
        sys.exit(2)
    novo = gerar_codigo(plano, registro, etapas)
    saida = pathlib.Path(a.saida)
    if saida.exists():
        antigo = saida.read_text(encoding="utf-8")
        diff = "".join(difflib.unified_diff(antigo.splitlines(True), novo.splitlines(True), str(saida.name) + " (atual)", str(saida.name) + " (gerado)"))
        if editado_a_mao(antigo):
            novo_path = saida.with_suffix(".novo.py")
            novo_path.write_text(novo, encoding="utf-8")
            saida.with_suffix(".diff").write_text(diff, encoding="utf-8")
            print(json.dumps({"status": "EDICAO_MANUAL_PRESERVADA", "novo": novo_path.name, "comparacao": saida.with_suffix(".diff").name},
                             ensure_ascii=False))
            sys.exit(3)
        if a.diff:
            pathlib.Path(a.diff).write_text(diff, encoding="utf-8")
    saida.write_text(novo, encoding="utf-8")
    print(json.dumps({"status": "GERADO", "etapas": [e["posicao"] for e in etapas], "planRevision": plano["planRevision"]}, ensure_ascii=False))


if __name__ == "__main__":
    main()
