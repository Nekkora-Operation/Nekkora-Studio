# nk-conteudo-sha256: d899a4d72a53c0a4b5b7891ac0e7b8f2c152dc74c1a050f29cd4d1bef7f55d31
"""Consulta de registro (laboratório local, dados fictícios) — script GERADO, não edite à mão.

Gerado pelo gerador do caso de laboratório da STUDIO-009 (docs/studio-009/python/gerar.py) a partir do plano
sha256:c9341d1aa23cb39337c5bd53d05683a9cae33239d124fe4650942c1c04aab42c da automação 774defa5-103d-4b24-bc30-bdc50611a35d, validado pela passagem de debug
registrada em 2026-10-17T19:10:03.636773600Z (operador TEST_AGENT). O modelo .nekkora é a fonte da verdade:
reparos vão para o modelo e o script é gerado de novo.

Uso: python consulta_registro.py --cdp http://127.0.0.1:PORTA --url-aba URL [--debug PASTA] [--saida resultado.json]
Entradas por variável de ambiente (nunca registradas): NK_ENTRADA_REGISTRO_CODIGO
"""
import argparse
import json
import os
import pathlib
import sys
import time
import urllib.parse

from playwright.sync_api import Error as PlaywrightError
from playwright.sync_api import TimeoutError as PlaywrightTimeout
from playwright.sync_api import sync_playwright

PLANO = {"automationId": "774defa5-103d-4b24-bc30-bdc50611a35d", "planRevision": "sha256:c9341d1aa23cb39337c5bd53d05683a9cae33239d124fe4650942c1c04aab42c", "operator": "TEST_AGENT"}
ORIGENS_PERMITIDAS = ("http://127.0.0.1:18765",)  # só o laboratório local: qualquer outra origem é recusada sem ação
VARIAVEIS = ["registro.codigo"]
ROSA = "#C2466D"  # o mesmo destaque do debug guiado do Studio (caixa sólida, sem etiqueta na página)


class Falha(Exception):
    """Falha de uma etapa, só com código (nunca texto da página nem valores)."""

    def __init__(self, codigo):
        super().__init__(codigo)
        self.codigo = codigo


class Depuracao:
    """2º debug: o mesmo destaque por etapa (StepId), intenção, localizador, captura e resultado — em JSON Lines."""

    def __init__(self, pasta):
        self.pasta = pathlib.Path(pasta) if pasta else None
        if self.pasta:
            self.pasta.mkdir(parents=True, exist_ok=True)
            self.log = open(self.pasta / 'depuracao.jsonl', 'w', encoding='utf-8')

    def antes(self, page, etapa, alvo, contagem):
        if not self.pasta:
            return
        caixa = alvo.bounding_box() if contagem == 1 else None
        captura = None
        if caixa:
            page.evaluate(
                """([x, y, w, h, cor]) => { const d = document.createElement("div"); d.id = "nk-depuracao-python";
                d.style.cssText = `position:fixed;left:${x - 3}px;top:${y - 3}px;width:${w + 6}px;height:${h + 6}px;border:3px solid ${cor};`
                + "border-radius:4px;pointer-events:none;z-index:2147483647;box-sizing:border-box"; document.documentElement.appendChild(d); }""",
                [caixa['x'], caixa['y'], caixa['width'], caixa['height'], ROSA])
            captura = f"{etapa['posicao']}-{etapa['stepId'][:8]}.png"
            # nenhum valor digitado em captura (nem fictício): todo campo editável sai mascarado
            campos = page.locator('input:not([type=button]):not([type=submit]):not([type=reset]):not([type=checkbox]):not([type=radio]):not([type=hidden]),'
                                  ' textarea, [contenteditable="true"], [contenteditable=""]')
            page.screenshot(path=str(self.pasta / captura), mask=[campos], mask_color='#9AA0A6')
            page.evaluate('() => { const d = document.getElementById("nk-depuracao-python"); if (d) d.remove(); }')
        self._linha({'evento': 'ANTES', 'stepId': etapa['stepId'], 'posicao': etapa['posicao'], 'acao': etapa['acao'],
                     'localizador': etapa['seletor'], 'estrategia': etapa['estrategia'], 'contagem': contagem,
                     'caixa': caixa and {k: round(v, 1) for k, v in caixa.items()}, 'captura': captura})

    def depois(self, etapa, status, ms):
        if self.pasta:
            self._linha({'evento': 'DEPOIS', 'stepId': etapa['stepId'], 'posicao': etapa['posicao'], 'status': status, 'ms': ms})

    def _linha(self, d):
        d['instante'] = time.strftime('%Y-%m-%dT%H:%M:%S', time.gmtime()) + f'.{int(time.time() * 1000) % 1000:03d}Z'
        self.log.write(json.dumps(d, ensure_ascii=False) + '\n')
        self.log.flush()


def localizar(page, etapa, timeout_ms):
    """Localizador estrito: 0 → NOT_FOUND, 2+ → AMBIGUOUS (nunca "o primeiro"); e conferência mínima de identidade."""
    alvo = page.locator(etapa['seletor'])
    fim = time.monotonic() + timeout_ms / 1000
    contagem = alvo.count()
    while contagem == 0 and time.monotonic() < fim:
        time.sleep(0.1)
        contagem = alvo.count()
    return alvo, contagem


def conferir(alvo, etapa):
    """Um localizador que casa NÃO prova identidade: tag e nome acessível do plano têm de bater (comparados aqui, nunca
    registrados). Diferença → IDENTITY_MISMATCH e nada é feito."""
    tag = alvo.evaluate('e => e.tagName.toLowerCase()')
    if etapa['tag'] and tag != etapa['tag']:
        raise Falha('IDENTITY_MISMATCH')
    if etapa['nome']:
        nome = alvo.evaluate("""e => (e.getAttribute('aria-label') || (e.labels && e.labels[0] && e.labels[0].textContent) || e.textContent || '')""")
        if ' '.join(nome.split()) != etapa['nome']:
            raise Falha('IDENTITY_MISMATCH')


def etapa_1_1(page, entradas, dbg, timeout_ms):
    # StepId: a1abe836-28d7-484a-9665-4ceea4ef7383
    # Intenção: FILL · alvo SEARCH_INPUT 'Código do registro' · decisão CONFIRMED (TEST_AGENT, base SELECTED_IN_BROWSER) · alvo SUBSTITUÍDO no debug
    # Localizador preferido (ID): input {id=codigo-v2}
    etapa = {"stepId": "a1abe836-28d7-484a-9665-4ceea4ef7383", "posicao": "1.1", "acao": "FILL", "seletor": "css=input#codigo-v2", "estrategia": "ID", "tag": "input", "nome": "Código do registro"}
    alvo, contagem = localizar(page, etapa, timeout_ms)
    dbg.antes(page, etapa, alvo, contagem)
    if contagem == 0:
        raise Falha('NOT_FOUND')
    if contagem > 1:
        raise Falha('AMBIGUOUS')
    conferir(alvo, etapa)
    alvo.fill(entradas["registro.codigo"], timeout=timeout_ms)
    return etapa


def etapa_1_2(page, entradas, dbg, timeout_ms):
    # StepId: a44a73a5-7733-4745-8140-7442cde06414
    # Intenção: CLICK · alvo BUTTON 'Consultar' · decisão CONFIRMED (TEST_AGENT, base SELECTED_IN_BROWSER) · alvo SUBSTITUÍDO no debug
    # Localizador preferido (TEST_ATTRIBUTE): button {data-testid=consultar-v2}
    etapa = {"stepId": "a44a73a5-7733-4745-8140-7442cde06414", "posicao": "1.2", "acao": "CLICK", "seletor": "css=button[data-testid=\"consultar-v2\"]", "estrategia": "TEST_ATTRIBUTE", "tag": "button", "nome": "Consultar"}
    alvo, contagem = localizar(page, etapa, timeout_ms)
    dbg.antes(page, etapa, alvo, contagem)
    if contagem == 0:
        raise Falha('NOT_FOUND')
    if contagem > 1:
        raise Falha('AMBIGUOUS')
    conferir(alvo, etapa)
    alvo.click(timeout=timeout_ms)
    return etapa


ETAPAS = [("a1abe836-28d7-484a-9665-4ceea4ef7383", "1.1", etapa_1_1), ("a44a73a5-7733-4745-8140-7442cde06414", "1.2", etapa_1_2)]


def main():
    ap = argparse.ArgumentParser(description='Consulta de registro (laboratório) — script gerado')
    ap.add_argument('--cdp', required=True, help='endereço CDP do Chrome de teste (ex.: http://127.0.0.1:9222)')
    ap.add_argument('--url-aba', required=True, help='início da URL da aba do processo')
    ap.add_argument('--debug', help='pasta do 2º debug (destaque, capturas e JSON Lines por StepId)')
    ap.add_argument('--saida', help='resultado em JSON (só códigos)')
    ap.add_argument('--timeout-ms', type=int, default=5000)
    a = ap.parse_args()
    resultado = {'plano': PLANO, 'etapas': [], 'status': None}

    def sair(status, codigo_saida):
        resultado['status'] = status
        if a.saida:
            pathlib.Path(a.saida).write_text(json.dumps(resultado, ensure_ascii=False, indent=2), encoding='utf-8')
        print(json.dumps({'status': status, 'etapas': [(e['posicao'], e['status']) for e in resultado['etapas']]}, ensure_ascii=False))
        sys.exit(codigo_saida)

    entradas = {}
    for v in VARIAVEIS:
        nome = 'NK_ENTRADA_' + v.upper().replace('.', '_')
        if not os.environ.get(nome):
            sair('ENTRADA_AUSENTE', 4)
        entradas[v] = os.environ[nome]
    origem = urllib.parse.urlsplit(a.url_aba)
    if f'{origem.scheme}://{origem.netloc}' not in ORIGENS_PERMITIDAS:
        sair('ORIGEM_NAO_AUTORIZADA', 3)  # bloqueio antes de qualquer conexão ou ação
    dbg = Depuracao(a.debug)
    with sync_playwright() as p:
        navegador = p.chromium.connect_over_cdp(a.cdp)
        abas = [pg for ctx in navegador.contexts for pg in ctx.pages if pg.url.startswith(a.url_aba)]
        if len(abas) != 1:
            sair('ABA_NAO_ENCONTRADA' if not abas else 'ABA_AMBIGUA', 5)
        page = abas[0]
        u = urllib.parse.urlsplit(page.url)
        if f'{u.scheme}://{u.netloc}' not in ORIGENS_PERMITIDAS:
            sair('ORIGEM_NAO_AUTORIZADA', 3)
        falhou = False
        for step_id, posicao, fn in ETAPAS:
            if falhou:
                resultado['etapas'].append({'stepId': step_id, 'posicao': posicao, 'status': 'NOT_RUN', 'ms': 0})
                continue
            t0 = time.monotonic()
            try:
                fn(page, entradas, dbg, a.timeout_ms)
                status = 'OK'
            except Falha as f:
                status = f.codigo
            except PlaywrightTimeout:
                status = 'TIMEOUT'
            except PlaywrightError as ex:
                status = 'AMBIGUOUS' if 'strict mode violation' in str(ex) else 'PLAYWRIGHT_ERROR'
            ms = round((time.monotonic() - t0) * 1000)
            dbg.depois({'stepId': step_id, 'posicao': posicao}, status, ms)
            resultado['etapas'].append({'stepId': step_id, 'posicao': posicao, 'status': status, 'ms': ms})
            falhou = status != 'OK'  # parada limpa: nada depois de uma etapa que falhou
        # só desconecta: o navegador e a aba continuam abertos
    sair('ETAPAS_EXECUTADAS' if not falhou else 'PARADO_NA_FALHA', 0 if not falhou else 1)


if __name__ == '__main__':
    main()
