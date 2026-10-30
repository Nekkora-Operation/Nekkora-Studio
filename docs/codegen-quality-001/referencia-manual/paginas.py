"""Telas do processo: a pesquisa do acervo e a ficha da pessoa encontrada.

REFERENCIA_MANUAL_VALIDACAO (NEKKORA-CODEGEN-QUALITY-001): escrita à mão SÓ para comparar organização e comportamento
com o código gerado pelo Studio. Não é saída do Studio, não é lida pelo gerador e não vale como prova de geração.

Cada Page localiza os elementos pelas chaves do registro de alvos (``selectors.json``); a verificação de identidade e de
unicidade de cada alvo é do nekkora_rpa.
"""

from __future__ import annotations

from dataclasses import dataclass

from nekkora_rpa.errors import BusinessRuleRejected
from nekkora_rpa.pages import BasePage
from nekkora_rpa.text import join_items, parse_long_date, same_address


class PaginaPesquisa(BasePage):
    """Campo de pesquisa do acervo, no cabeçalho da página."""

    prefix = "acervo.pesquisa"
    identity = "campo_de_pesquisa"

    def pesquisar(self, termo_de_busca: str) -> None:
        """Escreve o termo no campo de pesquisa e confirma com Enter; a página do resultado abre em seguida."""
        self.act.fill(self.k("campo_de_pesquisa"), termo_de_busca, step_id="pesquisa.preencher_termo_de_busca")
        self.act.press(self.k("campo_de_pesquisa"), "Enter", step_id="pesquisa.confirmar_com_enter", idempotent=True)


@dataclass(frozen=True)
class DadosDaFicha:
    """O que a ficha mostra. ``None`` indica uma linha que não existe nesta ficha (ausência prevista no PDD)."""

    titulo: str | None
    nome_completo: str | None
    nascimento: str
    morte: str
    nacionalidade: str
    ocupacao: str | None
    campos: str | None


class PaginaFicha(BasePage):
    """Ficha da pessoa: o título da página e o quadro com uma linha rotulada por dado."""

    prefix = "acervo.ficha"
    identity = "nascimento"

    def conferir_endereco(self, endereco_esperado: str) -> str:
        """Lê o endereço da página aberta e recusa o item se não for o endereço esperado para ele."""
        endereco = self.act.current_address(step_id="ficha.ler_endereco_da_pagina")
        if not same_address(endereco, endereco_esperado):
            raise BusinessRuleRejected("ENDERECO_DIFERENTE_DO_ESPERADO", step_id="ficha.ler_endereco_da_pagina")
        return endereco

    def ler_dados(self) -> DadosDaFicha:
        """Lê o título e as linhas do quadro: linha opcional ausente vira ``None``; data precisa estar completa."""
        # a ficha está carregada quando a linha Nascimento (sempre presente) aparece; só então a ausência das
        # linhas opcionais é conclusiva
        self.wait_loaded(step_id="ficha.ler_nascimento")
        return DadosDaFicha(
            titulo=self._texto_se_existir("titulo", step_id="ficha.ler_titulo"),
            nome_completo=self._texto_se_existir("nome_completo", step_id="ficha.ler_nome_completo"),
            nascimento=self._data("nascimento", step_id="ficha.ler_nascimento"),
            morte=self._data("morte", step_id="ficha.ler_morte"),
            nacionalidade=self._texto("nacionalidade", step_id="ficha.ler_nacionalidade"),
            ocupacao=self._texto_se_existir("ocupacao", step_id="ficha.ler_ocupacao"),
            campos=self._texto_se_existir("campos", step_id="ficha.ler_campos"),
        )

    def _texto(self, alvo: str, *, step_id: str) -> str:
        return join_items(self.act.read_items(self.k(alvo), step_id=step_id))

    def _texto_se_existir(self, alvo: str, *, step_id: str) -> str | None:
        if not self.act.exists(self.k(alvo), step_id=step_id):
            return None
        return self._texto(alvo, step_id=step_id)

    def _data(self, alvo: str, *, step_id: str) -> str:
        data = parse_long_date(" ".join(self.act.read_items(self.k(alvo), step_id=step_id)))
        if data is None:
            raise BusinessRuleRejected("DATA_INCOMPLETA_OU_AMBIGUA", step_id=step_id)
        return data
