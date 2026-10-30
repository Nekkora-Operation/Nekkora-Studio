"""Processo: para cada pessoa da lista, pesquisa a ficha, confere o endereço e devolve uma linha da planilha.

REFERENCIA_MANUAL_VALIDACAO (NEKKORA-CODEGEN-QUALITY-001): escrita à mão SÓ para comparação; não é saída do Studio.
"""

from __future__ import annotations

from collections.abc import Mapping
from dataclasses import dataclass

from nekkora_rpa import ProcessResult, RpaProcess, Runtime, TransactionItem
from nekkora_rpa.errors import BusinessRuleRejected

from paginas import DadosDaFicha, PaginaFicha, PaginaPesquisa

URL_INICIAL = "http://127.0.0.1:18766/leitura/busca.html"


@dataclass(frozen=True, repr=False)
class Entrada:
    """Uma linha da lista de entrada (os valores nunca são registrados)."""

    termo_de_busca: str
    endereco_esperado: str

    @classmethod
    def da_linha(cls, dados: Mapping[str, object]) -> Entrada:
        """Recusa o item antes de qualquer ação se alguma coluna faltar ou estiver vazia."""
        termo, endereco = dados.get("termo_de_busca"), dados.get("endereco_esperado")
        if not isinstance(termo, str) or not termo.strip() or not isinstance(endereco, str) or not endereco.strip():
            raise BusinessRuleRejected("ENTRADA_INVALIDA")
        return cls(termo, endereco)


def linha_da_planilha(url: str, ficha: DadosDaFicha) -> dict[str, object]:
    """Monta a linha pela regra do PDD: sem "Nome completo", vale o título; sem "Ocupação", vale "Campo(s)".

    Coluna que continua vazia recusa o item: nada é inventado e a planilha só é gravada com todos os itens.
    """
    linha: dict[str, object] = {
        "url": url,
        "nome_completo": ficha.nome_completo or ficha.titulo,
        "nascimento": ficha.nascimento,
        "morte": ficha.morte,
        "nacionalidade": ficha.nacionalidade,
        "ocupacao": ficha.ocupacao or ficha.campos,
    }
    if not all(linha.values()):
        raise BusinessRuleRejected("DADO_OBRIGATORIO_VAZIO")
    return linha


class Processo(RpaProcess):
    """Uma transação por pessoa: pesquisa, conferência do endereço e leitura da ficha."""

    name = "fichas_do_acervo"
    requires_browser = True

    def process(self, rt: Runtime, item: TransactionItem) -> ProcessResult:
        entrada = Entrada.da_linha(item.data)
        rt.act.navigate(URL_INICIAL, step_id="inicio")
        PaginaPesquisa(rt.act).pesquisar(entrada.termo_de_busca)
        ficha = PaginaFicha(rt.act)
        url = ficha.conferir_endereco(entrada.endereco_esperado)
        return ProcessResult(linha_da_planilha(url, ficha.ler_dados()))
