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
        # etapa b086b59e-409a-4ac1-b697-9b4392cbe01d · FILL
        a.fill(self.k("usuario"), segredos.get("credencial.usuario"), step_id="b086b59e-409a-4ac1-b697-9b4392cbe01d")
        # etapa 1d6eaf90-6ee5-4fbf-b9fe-c00ed4e07956 · FILL
        a.fill(self.k("senha"), segredos.get("credencial.senha"), step_id="1d6eaf90-6ee5-4fbf-b9fe-c00ed4e07956")
        # etapa 3efde5a8-cc5c-4efc-8897-fb99da765696 · CLICK
        a.click(
            self.k("entrar"),
            step_id="3efde5a8-cc5c-4efc-8897-fb99da765696",
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
        # etapa 8df4cf0d-75b9-4c79-9591-af66a92f19a8 · FILL
        a.fill(self.k("codigo_do_registro"), codigo_do_registro, step_id="8df4cf0d-75b9-4c79-9591-af66a92f19a8")
        # etapa 556ff4b9-511c-4690-81cf-a7ad45379931 · CLICK
        a.click(self.k("consultar"), step_id="556ff4b9-511c-4690-81cf-a7ad45379931", idempotent=True)
        # etapa 53a022d2-fa0e-4860-90e4-1ef14902111f · OBSERVE
        saidas["resultado_da_consulta"] = a.read_text(
            self.k("resultado_da_consulta"),
            step_id="53a022d2-fa0e-4860-90e4-1ef14902111f",
        )
        # etapa ed05c689-789a-4787-9e40-12e342df2a50 · CLICK
        a.click(self.k("sair"), step_id="ed05c689-789a-4787-9e40-12e342df2a50", idempotent=True)
        return saidas
