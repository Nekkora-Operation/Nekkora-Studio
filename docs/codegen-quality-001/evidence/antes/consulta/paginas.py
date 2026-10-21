# GERADO pelo Nekkora Studio (STUDIO-014, "Desenvolver automação") do modelo validado e do PDD pronto.
# NÃO EDITE: correções vão para o modelo ou o PDD e o código é gerado de novo.
# Arquivo editado à mão não é sobrescrito.
"""Pages geradas: uma por tela do processo; intenções da tela por CHAVE do registro de alvos (sem seletores)."""

from __future__ import annotations

from nekkora_rpa.pages import BasePage
from nekkora_rpa.security.secrets import SecretProvider


class TelaAcesso(BasePage):
    """Tela 1 do processo."""

    prefix = "processo.acesso"
    identity = "usuario"

    def acesso(self, segredos: SecretProvider) -> None:
        a = self.act
        # etapa 1fab07ac-3f0d-4e6b-b8c2-d25124bd09ba · FILL
        a.fill(self.k("usuario"), segredos.get("credencial.usuario"), step_id="1fab07ac-3f0d-4e6b-b8c2-d25124bd09ba")
        # etapa 50563f5d-ebe5-4d13-9ee1-73842d18b21d · FILL
        a.fill(self.k("senha"), segredos.get("credencial.senha"), step_id="50563f5d-ebe5-4d13-9ee1-73842d18b21d")
        # etapa 9e334d7b-6c58-4d2c-8046-60736238256f · CLICK
        a.click(
            self.k("entrar"),
            step_id="9e334d7b-6c58-4d2c-8046-60736238256f",
            idempotent=True,
            postcondition=lambda: a.exists("processo.consulta.codigo_do_registro"),
        )


class TelaConsulta(BasePage):
    """Tela 2 do processo."""

    prefix = "processo.consulta"
    identity = "codigo_do_registro"

    def consulta(self, codigo_do_registro: str) -> dict[str, str]:
        a = self.act
        saidas: dict[str, str] = {}
        # etapa 977c0eb1-c2b6-4efd-8ec8-647c57166e79 · FILL
        a.fill(self.k("codigo_do_registro"), codigo_do_registro, step_id="977c0eb1-c2b6-4efd-8ec8-647c57166e79")
        # etapa b0739599-de33-4de4-94a9-144699431e44 · CLICK
        a.click(self.k("consultar"), step_id="b0739599-de33-4de4-94a9-144699431e44", idempotent=True)
        # etapa f052abf5-c024-4dfc-9086-39e6a055fb7f · OBSERVE
        saidas["resultado_da_consulta"] = a.read_text(
            self.k("resultado_da_consulta"),
            step_id="f052abf5-c024-4dfc-9086-39e6a055fb7f",
        )
        # etapa 2639a0ba-422b-4c6c-9d65-adf6ff2f7d2e · CLICK
        a.click(self.k("sair"), step_id="2639a0ba-422b-4c6c-9d65-adf6ff2f7d2e", idempotent=True)
        return saidas
