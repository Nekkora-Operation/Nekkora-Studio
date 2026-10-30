# GERADO pelo Nekkora Studio ("Desenvolver automação") a partir do modelo validado e do PDD pronto.
# Não edite: correções vão para o modelo ou o PDD e o código é gerado de novo (arquivo editado à mão não é sobrescrito).
"""Telas da automação: PaginaPesquisa e PaginaFicha.

Cada tela é uma classe com o que o processo faz nela. ``self.acoes`` são as ações do nekkora_rpa (preencher, clicar,
ler) e ``self.elemento("nome")`` aponta um elemento da tela pelo nome dele no registro selectors.json: o framework
confere a identidade e a unicidade do elemento antes de agir. O ``step_id`` de cada ação liga a ação à etapa do
Studio (mapa em manifest.json).
"""

from __future__ import annotations

from dataclasses import dataclass

from nekkora_rpa import Actions
from nekkora_rpa.errors import BusinessRuleRejected
from nekkora_rpa.pages import BasePage
from nekkora_rpa.text import join_items, parse_long_date, same_address


class PaginaBase(BasePage):
    """Base das telas: nomes legíveis para a API de Pages do nekkora_rpa (o comportamento é o do framework)."""

    @property
    def acoes(self) -> Actions:
        """Ações do nekkora_rpa nesta tela (o mesmo objeto que ``BasePage.act``)."""
        return self.act

    def elemento(self, nome: str) -> str:
        """Chave do elemento ``nome`` desta tela no registro de alvos (``BasePage.k``)."""
        return self.k(nome)


@dataclass(frozen=True)
class DadosDaFicha:
    """Dados lidos por ``PaginaFicha.ler_dados_da_ficha()``.

    ``None``: o dado não existe nesta página (ausência prevista no PDD, com fonte alternativa).
    """

    titulo: str | None
    nome_completo: str | None
    nascimento: str
    morte: str
    nacionalidade: str
    ocupacao: str | None
    campos: str | None


class PaginaPesquisa(PaginaBase):
    """Tela da seção “Pesquisa do artigo” do PDD: pesquisa o artigo pelo nome."""

    prefix = "processo.pesquisa"
    identity = "campo_termo_de_busca"

    def pesquisar_artigo(self, termo_de_busca: str) -> None:
        """Preenche “termo de busca” com ``termo_de_busca`` e envia com Enter.

        Sem efeito declarado no sistema: se o resultado ficar incerto, o envio pode ser repetido.
        """
        self.acoes.fill(
            self.elemento("campo_termo_de_busca"),
            termo_de_busca,
            step_id="pesquisa.preencher_termo_de_busca",
        )
        self.acoes.press(
            self.elemento("campo_termo_de_busca"),
            "Enter",
            step_id="pesquisa.pressionar_enter",
            idempotent=True,
        )


class PaginaFicha(PaginaBase):
    """Tela da seção “Extração dos dados” do PDD: lê o endereço e os dados da ficha."""

    prefix = "processo.ficha"
    identity = "nascimento"

    def conferir_endereco_da_pagina(self, endereco_do_artigo: str) -> str:
        """Lê o endereço da página e o compara com ``endereco_do_artigo``.

        Devolve o endereço lido. Endereço diferente recusa o item (``ENDERECO_DIFERENTE_DO_ESPERADO``).
        """
        endereco_da_pagina = self.acoes.current_address(step_id="ficha.ler_endereco_da_pagina")
        if not same_address(endereco_da_pagina, endereco_do_artigo):
            raise BusinessRuleRejected("ENDERECO_DIFERENTE_DO_ESPERADO", step_id="ficha.ler_endereco_da_pagina")
        return endereco_da_pagina

    def ler_dados_da_ficha(self) -> DadosDaFicha:
        """Lê o título, “Nome completo”, “Nascimento”, “Morte”, “Nacionalidade”, “Ocupação” e “Campo(s)”.

        Devolve ``DadosDaFicha``. Antes, espera “Nascimento” (leitura obrigatória): só com a página pronta a falta
        de um dado opcional é conclusiva. O título, “Nome completo”, “Ocupação” e “Campo(s)” podem faltar nesta
        página (``None``; a fonte alternativa está no PDD). Data por extenso vira AAAA-MM-DD; incompleta ou ambígua
        recusa o item (``DATA_INCOMPLETA_OU_AMBIGUA``).
        """
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

    def _texto(self, elemento: str, *, step_id: str) -> str:
        """Texto exibido pelo elemento (itens de uma lista unidos por "; ")."""
        return join_items(self.acoes.read_items(self.elemento(elemento), step_id=step_id))

    def _texto_se_existir(self, elemento: str, *, step_id: str) -> str | None:
        """Como ``_texto``; ``None`` se o elemento não existe nesta página (ambíguo continua sendo erro)."""
        if not self.acoes.exists(self.elemento(elemento), step_id=step_id):
            return None
        return self._texto(elemento, step_id=step_id)

    def _data(self, elemento: str, *, step_id: str) -> str:
        """Data por extenso do elemento como AAAA-MM-DD; incompleta, ambígua ou impossível recusa o item."""
        data = parse_long_date(" ".join(self.acoes.read_items(self.elemento(elemento), step_id=step_id)))
        if data is None:
            raise BusinessRuleRejected("DATA_INCOMPLETA_OU_AMBIGUA", step_id=step_id)
        return data
