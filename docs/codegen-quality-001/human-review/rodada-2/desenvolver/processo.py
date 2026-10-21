# GERADO pelo Nekkora Studio ("Desenvolver automação") a partir do modelo validado e do PDD pronto.
# Não edite: correções vão para o modelo ou o PDD e o código é gerado de novo (arquivo editado à mão não é sobrescrito).
"""Processo BiografiasDaWikipedia: uma transação por linha da lista de entrada."""

from __future__ import annotations

from collections.abc import Mapping
from dataclasses import dataclass

from nekkora_rpa import ProcessResult, RpaProcess, Runtime, TransactionItem
from nekkora_rpa.errors import BusinessRuleRejected

from paginas import DadosDaFicha, PaginaFicha, PaginaPesquisa

URL_INICIAL = "https://pt.wikipedia.org/wiki/Wikip%C3%A9dia:P%C3%A1gina_principal"
ENTRADAS = ("termo_de_busca", "endereco_do_artigo")


@dataclass(frozen=True, repr=False)
class Entrada:
    """Uma linha da lista de entrada (os valores nunca são registrados)."""

    termo_de_busca: str
    endereco_do_artigo: str

    @classmethod
    def de_item(cls, dados: Mapping[str, object]) -> Entrada:
        """Recusa o item antes de qualquer ação se uma entrada faltar ou estiver vazia."""
        valores: dict[str, str] = {}
        for nome in ENTRADAS:
            valor = dados.get(nome)
            if not isinstance(valor, str) or not valor.strip():
                raise BusinessRuleRejected("ENTRADA_INVALIDA")
            valores[nome] = valor
        return cls(**valores)


def linha_da_planilha(endereco_da_pagina: str, dados_da_ficha: DadosDaFicha) -> dict[str, object]:
    """Uma linha da planilha pela regra do PDD; coluna que continuar vazia recusa o item com o nome do campo (o
    conteúdo nunca vai ao motivo) e nada é inventado.

    - Nome completo: se faltar na página, vale “título”.
    - Ocupação: se faltar na página, vale “Campo(s)”.
    """
    linha: dict[str, object] = {
        "url": endereco_da_pagina,
        "nome_completo": dados_da_ficha.nome_completo or dados_da_ficha.titulo,
        "nascimento": dados_da_ficha.nascimento,
        "morte": dados_da_ficha.morte,
        "nacionalidade": dados_da_ficha.nacionalidade,
        "ocupacao": dados_da_ficha.ocupacao or dados_da_ficha.campos,
    }
    for campo, valor in linha.items():
        if not valor:
            raise BusinessRuleRejected(f"DADO_OBRIGATORIO_VAZIO:{campo}")
    return linha


class BiografiasDaWikipedia(RpaProcess):
    """Uma transação por linha da lista de entrada. Passa por: Pesquisa do artigo → Extração dos dados."""

    name = "biografias_da_wikipedia"
    requires_browser = True
    required_secrets = ()

    def process(self, rt: Runtime, item: TransactionItem) -> ProcessResult:
        """Um item: começa no endereço inicial e faz as etapas na ordem do processo."""
        entrada = Entrada.de_item(item.data)
        rt.act.navigate(URL_INICIAL, step_id="processo.abrir_pagina_inicial")
        PaginaPesquisa(rt.act).pesquisar_artigo(entrada.termo_de_busca)
        ficha = PaginaFicha(rt.act)
        endereco_da_pagina = ficha.conferir_endereco_da_pagina(entrada.endereco_do_artigo)
        dados_da_ficha = ficha.ler_dados_da_ficha()
        return ProcessResult(linha_da_planilha(endereco_da_pagina, dados_da_ficha))
