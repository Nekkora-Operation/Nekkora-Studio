# GERADO pelo Nekkora Studio (STUDIO-014, "Desenvolver automação") do modelo validado e do PDD pronto.
# NÃO EDITE: correções vão para o modelo ou o PDD e o código é gerado de novo.
# Arquivo editado à mão não é sobrescrito.
"""Pages geradas: uma por tela do processo; intenções da tela por CHAVE do registro de alvos (sem seletores)."""

from __future__ import annotations

from nekkora_rpa.errors import BusinessRuleRejected
from nekkora_rpa.pages import BasePage
from nekkora_rpa.text import join_items, parse_long_date, same_address


class Tela1(BasePage):
    """Tela 1 do processo."""

    prefix = "processo.tela_1"
    identity = "fill"

    def etapas_sessao_1(self) -> None:
        a = self.act
        # etapa 0e0dfafe-aecb-42d1-a861-57e00fde87f6 · NAVIGATE
        a.navigate("http://127.0.0.1:18766/leitura/busca.html", step_id="0e0dfafe-aecb-42d1-a861-57e00fde87f6")

    def pesquisa(self, termo_de_busca: str) -> None:
        a = self.act
        # etapa eaffe6af-0ca8-436a-aafe-493da0779f68 · FILL
        a.fill(self.k("fill"), termo_de_busca, step_id="eaffe6af-0ca8-436a-aafe-493da0779f68")
        # etapa f756d774-e875-43a4-84b7-872f706baf73 · PRESS_KEY
        antes = a.current_address(step_id="f756d774-e875-43a4-84b7-872f706baf73")
        a.press(
            self.k("press_key"),
            "Enter",
            step_id="f756d774-e875-43a4-84b7-872f706baf73",
            idempotent=True,
            postcondition=lambda: a.current_address() != antes,
        )


class Tela3(BasePage):
    """Tela 2 do processo."""

    prefix = "processo.tela_3"
    identity = "titulo"

    def etapas_sessao_3(self, endereco_esperado: str) -> dict[str, str]:
        a = self.act
        saidas: dict[str, str] = {}
        # etapa 1a36e970-d2e1-41fe-89da-9bc3926a7627 · OBSERVE
        saidas["endereco_da_pagina"] = a.current_address(step_id="1a36e970-d2e1-41fe-89da-9bc3926a7627")
        if not same_address(saidas["endereco_da_pagina"], endereco_esperado):
            raise BusinessRuleRejected("CONFERENCIA_FALHOU")  # a página não é a esperada
        # página nova pronta: 1ª leitura obrigatória da tela (etapa 8761e769-0b8c-4426-9aa4-7ae8be748f6b)
        a.wait_visible(self.k("nascimento"), step_id="8761e769-0b8c-4426-9aa4-7ae8be748f6b")
        # etapa 61d9e98a-58fc-43c6-9048-752d993e5aa2 · OBSERVE
        if a.exists(self.k("titulo"), step_id="61d9e98a-58fc-43c6-9048-752d993e5aa2"):
            itens = a.read_items(self.k("titulo"), step_id="61d9e98a-58fc-43c6-9048-752d993e5aa2")
            saidas["titulo"] = join_items(itens)
        else:
            saidas["titulo"] = ""
        # etapa 3bf08523-edbe-4a0f-bbc6-424840c157e1 · OBSERVE
        if a.exists(self.k("nome_completo"), step_id="3bf08523-edbe-4a0f-bbc6-424840c157e1"):
            itens = a.read_items(self.k("nome_completo"), step_id="3bf08523-edbe-4a0f-bbc6-424840c157e1")
            saidas["nome_completo"] = join_items(itens)
        else:
            saidas["nome_completo"] = ""
        # etapa 8761e769-0b8c-4426-9aa4-7ae8be748f6b · OBSERVE
        itens = a.read_items(self.k("nascimento"), step_id="8761e769-0b8c-4426-9aa4-7ae8be748f6b")
        saidas["nascimento"] = parse_long_date(" ".join(itens)) or ""
        # etapa 9a8902a8-4921-42b4-92c2-6ad4c2e0192a · OBSERVE
        itens = a.read_items(self.k("morte"), step_id="9a8902a8-4921-42b4-92c2-6ad4c2e0192a")
        saidas["morte"] = parse_long_date(" ".join(itens)) or ""
        # etapa fff6e3fc-d718-423a-9ecc-b2eb2b48e3a5 · OBSERVE
        itens = a.read_items(self.k("nacionalidade"), step_id="fff6e3fc-d718-423a-9ecc-b2eb2b48e3a5")
        saidas["nacionalidade"] = join_items(itens)
        # etapa c5f3431e-5b68-47fa-a5cf-78561a075b33 · OBSERVE
        if a.exists(self.k("ocupacao"), step_id="c5f3431e-5b68-47fa-a5cf-78561a075b33"):
            itens = a.read_items(self.k("ocupacao"), step_id="c5f3431e-5b68-47fa-a5cf-78561a075b33")
            saidas["ocupacao"] = join_items(itens)
        else:
            saidas["ocupacao"] = ""
        # etapa d0125176-9a54-40b2-a11a-10961ad440e7 · OBSERVE
        if a.exists(self.k("campo_s"), step_id="d0125176-9a54-40b2-a11a-10961ad440e7"):
            itens = a.read_items(self.k("campo_s"), step_id="d0125176-9a54-40b2-a11a-10961ad440e7")
            saidas["campo_s"] = join_items(itens)
        else:
            saidas["campo_s"] = ""
        return saidas
