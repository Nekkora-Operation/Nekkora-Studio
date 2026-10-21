# GERADO pelo Nekkora Studio ("Desenvolver automação") a partir do modelo validado e do PDD pronto.
# Não edite: correções vão para o modelo ou o PDD e o código é gerado de novo (arquivo editado à mão não é sobrescrito).
"""Telas da automação: PaginaPesquisa e PaginaExtracao.

Cada Page localiza os elementos pelas chaves do registro de alvos (selectors.json): ``self.k("nome")`` devolve a
chave completa do alvo e o nekkora_rpa verifica a identidade e a unicidade de cada um. O ``step_id`` de cada ação
liga a ação à etapa do Studio (mapa em manifest.json).
"""

from __future__ import annotations

from dataclasses import dataclass

from nekkora_rpa.errors import BusinessRuleRejected
from nekkora_rpa.pages import BasePage
from nekkora_rpa.text import join_items, parse_long_date, same_address


@dataclass(frozen=True)
class DadosExtracao:
    """Dados lidos por ``PaginaExtracao.ler_dados()``.

    ``None``: o dado não existe nesta página (ausência prevista no PDD, com fonte alternativa).
    """

    titulo: str | None
    nome_completo: str | None
    nascimento: str
    morte: str
    nacionalidade: str
    ocupacao: str | None
    campos: str | None


class PaginaPesquisa(BasePage):
    """Pesquisa do artigo.

    Pesquisar o artigo pelo nome.
    """

    prefix = "processo.pesquisa"
    identity = "campo_termo_de_busca"

    def pesquisar_artigo(self, termo_de_busca: str) -> None:
        """Pesquisar o artigo pelo nome.

        Preenche “termo de busca”; confirma com Enter.
        """
        self.act.fill(self.k("campo_termo_de_busca"), termo_de_busca, step_id="pesquisa.preencher_termo_de_busca")
        self.act.press(self.k("campo_termo_de_busca"), "Enter", step_id="pesquisa.pressionar_enter", idempotent=True)


class PaginaExtracao(BasePage):
    """Extração dos dados.

    Ler o endereço e os dados da ficha.
    """

    prefix = "processo.extracao"
    identity = "nascimento"

    def conferir_endereco_da_pagina(self, endereco_do_artigo: str) -> str:
        """Lê endereço da página e o confere com ``endereco_do_artigo`` (diferente recusa o item)."""
        endereco_da_pagina = self.act.current_address(step_id="extracao.ler_endereco_da_pagina")
        if not same_address(endereco_da_pagina, endereco_do_artigo):
            raise BusinessRuleRejected("ENDERECO_DIFERENTE_DO_ESPERADO", step_id="extracao.ler_endereco_da_pagina")
        return endereco_da_pagina

    def ler_dados(self) -> DadosExtracao:
        """Lê título, “Nome completo”, “Nascimento”, “Morte”, “Nacionalidade”, “Ocupação” e “Campo(s)”. Título,
        “Nome completo”, “Ocupação” e “Campo(s)” podem faltar nesta página (fonte alternativa declarada no PDD).
        """
        # a página está pronta quando “Nascimento” (leitura obrigatória) aparece: só então a falta de um dado opcional
        # é conclusiva
        self.wait_loaded(step_id="extracao.ler_nascimento")
        return DadosExtracao(
            titulo=self._texto_se_existir("titulo", step_id="extracao.ler_titulo"),
            nome_completo=self._texto_se_existir("nome_completo", step_id="extracao.ler_nome_completo"),
            nascimento=self._data("nascimento", step_id="extracao.ler_nascimento"),
            morte=self._data("morte", step_id="extracao.ler_morte"),
            nacionalidade=self._texto("nacionalidade", step_id="extracao.ler_nacionalidade"),
            ocupacao=self._texto_se_existir("ocupacao", step_id="extracao.ler_ocupacao"),
            campos=self._texto_se_existir("campos", step_id="extracao.ler_campos"),
        )

    def _texto(self, alvo: str, *, step_id: str) -> str:
        """Texto exibido pelo alvo (itens de uma lista unidos por "; ")."""
        return join_items(self.act.read_items(self.k(alvo), step_id=step_id))

    def _texto_se_existir(self, alvo: str, *, step_id: str) -> str | None:
        """Como ``_texto``; ``None`` se o alvo não existe nesta página (ambíguo continua sendo erro)."""
        if not self.act.exists(self.k(alvo), step_id=step_id):
            return None
        return self._texto(alvo, step_id=step_id)

    def _data(self, alvo: str, *, step_id: str) -> str:
        """Data por extenso do alvo como AAAA-MM-DD; incompleta, ambígua ou impossível recusa o item."""
        data = parse_long_date(" ".join(self.act.read_items(self.k(alvo), step_id=step_id)))
        if data is None:
            raise BusinessRuleRejected("DATA_INCOMPLETA_OU_AMBIGUA", step_id=step_id)
        return data
