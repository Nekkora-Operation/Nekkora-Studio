# GERADO pelo Nekkora Studio (STUDIO-014, "Desenvolver automação") do modelo validado e do PDD pronto.
# NÃO EDITE: correções vão para o modelo ou o PDD e o código é gerado de novo.
# Arquivo editado à mão não é sobrescrito.
"""Pages geradas: uma por tela do processo; intenções da tela por CHAVE do registro de alvos (sem seletores)."""

from __future__ import annotations

from nekkora_rpa.errors import BusinessRuleRejected
from nekkora_rpa.pages import BasePage
from nekkora_rpa.text import join_items, parse_long_date, same_address


class TelaPesquisa(BasePage):
    """Tela 1 do processo."""

    prefix = "processo.pesquisa"
    identity = "fill"

    def pesquisa(self, termo_de_busca: str) -> None:
        a = self.act
        # etapa afb51a78-579b-4e81-9256-90d974ccb3e0 · FILL
        a.fill(self.k("fill"), termo_de_busca, step_id="afb51a78-579b-4e81-9256-90d974ccb3e0")
        # etapa 2f54a685-66d6-4f38-8572-c6b8cd22b654 · PRESS_KEY
        a.press(self.k("press_key"), "Enter", step_id="2f54a685-66d6-4f38-8572-c6b8cd22b654", idempotent=True)

    def etapas_sessao_3(self, endereco_do_artigo: str) -> dict[str, str]:
        a = self.act
        saidas: dict[str, str] = {}
        # etapa 933ce748-a3a1-4f0e-a34e-f093e786553f · OBSERVE
        saidas["endereco_da_pagina"] = a.current_address(step_id="933ce748-a3a1-4f0e-a34e-f093e786553f")
        if not same_address(saidas["endereco_da_pagina"], endereco_do_artigo):
            raise BusinessRuleRejected("CONFERENCIA_FALHOU")  # a página não é a esperada
        # página nova pronta: 1ª leitura obrigatória da tela (etapa 42ec8f2d-fa8e-4410-ba74-f181e2553d06)
        a.wait_visible(self.k("nascimento"), step_id="42ec8f2d-fa8e-4410-ba74-f181e2553d06")
        # etapa 3e133b8f-2605-4851-9632-035f8200a323 · OBSERVE
        if a.exists(self.k("titulo"), step_id="3e133b8f-2605-4851-9632-035f8200a323"):
            itens = a.read_items(self.k("titulo"), step_id="3e133b8f-2605-4851-9632-035f8200a323")
            saidas["titulo"] = join_items(itens)
        else:
            saidas["titulo"] = ""
        # etapa d209853d-4e12-4cf6-816b-5c33c22114b0 · OBSERVE
        if a.exists(self.k("nome_completo"), step_id="d209853d-4e12-4cf6-816b-5c33c22114b0"):
            itens = a.read_items(self.k("nome_completo"), step_id="d209853d-4e12-4cf6-816b-5c33c22114b0")
            saidas["nome_completo"] = join_items(itens)
        else:
            saidas["nome_completo"] = ""
        # etapa 42ec8f2d-fa8e-4410-ba74-f181e2553d06 · OBSERVE
        itens = a.read_items(self.k("nascimento"), step_id="42ec8f2d-fa8e-4410-ba74-f181e2553d06")
        saidas["nascimento"] = parse_long_date(" ".join(itens)) or ""
        # etapa 1d766bbb-cf1c-42d5-9341-eb240f3ba82d · OBSERVE
        itens = a.read_items(self.k("morte"), step_id="1d766bbb-cf1c-42d5-9341-eb240f3ba82d")
        saidas["morte"] = parse_long_date(" ".join(itens)) or ""
        # etapa 5b39cbb3-d4a4-4e9e-b6b3-636878f220cd · OBSERVE
        itens = a.read_items(self.k("nacionalidade"), step_id="5b39cbb3-d4a4-4e9e-b6b3-636878f220cd")
        saidas["nacionalidade"] = join_items(itens)
        # etapa 57bb22fb-6ba2-4c8a-bac2-f0bf8875a604 · OBSERVE
        if a.exists(self.k("ocupacao"), step_id="57bb22fb-6ba2-4c8a-bac2-f0bf8875a604"):
            itens = a.read_items(self.k("ocupacao"), step_id="57bb22fb-6ba2-4c8a-bac2-f0bf8875a604")
            saidas["ocupacao"] = join_items(itens)
        else:
            saidas["ocupacao"] = ""
        # etapa c53b033e-8236-4f8b-9973-e835a5587196 · OBSERVE
        if a.exists(self.k("campo_s"), step_id="c53b033e-8236-4f8b-9973-e835a5587196"):
            itens = a.read_items(self.k("campo_s"), step_id="c53b033e-8236-4f8b-9973-e835a5587196")
            saidas["campo_s"] = join_items(itens)
        else:
            saidas["campo_s"] = ""
        return saidas
