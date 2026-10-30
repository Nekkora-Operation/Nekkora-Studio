# GERADO pelo Nekkora Studio (STUDIO-014, "Desenvolver automação") do modelo validado e do PDD pronto.
# NÃO EDITE: correções vão para o modelo ou o PDD e o código é gerado de novo.
# Arquivo editado à mão não é sobrescrito.
"""Processo gerado (uma transação por linha da lista).

Cada item começa no endereço inicial, faz as etapas na ordem do modelo e devolve UMA linha da planilha.
"""

from __future__ import annotations

from collections.abc import Mapping
from dataclasses import dataclass

from nekkora_rpa import ProcessResult, RpaProcess, Runtime, TransactionItem
from nekkora_rpa.errors import BusinessRuleRejected

from paginas import Tela1, Tela3

URL_INICIAL = "http://127.0.0.1:18766/leitura/busca.html"
ENTRADAS = ("termo_de_busca", "endereco_esperado")
# coluna da planilha ← leitura (variável) e, se o alvo dela faltar na página, a fonte alternativa declarada
COLUNAS: tuple[tuple[str, str, str | None], ...] = (
    ("url", "endereco_da_pagina", None),
    ("nome_completo", "nome_completo", "titulo"),
    ("nascimento", "nascimento", None),
    ("morte", "morte", None),
    ("nacionalidade", "nacionalidade", None),
    ("ocupacao", "ocupacao", "campo_s"),
)


@dataclass(frozen=True, repr=False)
class Entrada:
    """Entrada de um item (os valores nunca são registrados)."""

    termo_de_busca: str
    endereco_esperado: str

    @classmethod
    def de(cls, dados: Mapping[str, object]) -> Entrada:
        valores: dict[str, str] = {}
        for nome in ENTRADAS:
            valor = dados.get(nome)
            if not isinstance(valor, str) or not valor.strip():
                raise BusinessRuleRejected("ENTRADA_INVALIDA")
            valores[nome] = valor
        return cls(**valores)


def linha(saidas: Mapping[str, str]) -> dict[str, object]:
    """Uma linha da planilha pela regra declarada; dado obrigatório vazio rejeita o item (nada é inventado)."""
    out: dict[str, object] = {}
    for campo, principal, alternativa in COLUNAS:
        valor = saidas.get(principal, "")
        if not valor.strip() and alternativa is not None:
            valor = saidas.get(alternativa, "")
        if not valor.strip():
            raise BusinessRuleRejected("DADO_INCOMPLETO")
        out[campo] = valor
    return out


class Processo(RpaProcess):
    name = "fichas_do_acervo_sintetica"
    requires_browser = True
    required_secrets = ()

    def process(self, rt: Runtime, item: TransactionItem) -> ProcessResult:
        entrada = Entrada.de(item.data)  # antes de qualquer ação: entrada inválida recusa o item
        rt.act.navigate(URL_INICIAL, step_id="INICIO")  # cada item começa no mesmo ponto
        saidas: dict[str, str] = {}
        Tela1(rt.act).etapas_sessao_1()
        Tela1(rt.act).pesquisa(entrada.termo_de_busca)
        saidas.update(Tela3(rt.act).etapas_sessao_3(entrada.endereco_esperado))
        return ProcessResult(linha(saidas))
