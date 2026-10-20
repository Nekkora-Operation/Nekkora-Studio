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
        # etapa 7e08f637-da9c-4ad7-ba18-2bc054c611af · FILL
        a.fill(self.k("usuario"), segredos.get("credencial.usuario"), step_id="7e08f637-da9c-4ad7-ba18-2bc054c611af")
        # etapa 7f304ac6-7dbe-4810-9d28-a3a6dc7952a7 · FILL
        a.fill(self.k("senha"), segredos.get("credencial.senha"), step_id="7f304ac6-7dbe-4810-9d28-a3a6dc7952a7")
        # etapa 4ad04f21-0773-4f19-b2aa-4631eb86ba3b · CLICK
        a.click(
            self.k("entrar"),
            step_id="4ad04f21-0773-4f19-b2aa-4631eb86ba3b",
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
        # etapa 9d37db99-62ed-4bd3-a4fc-f1c4cde8ccc3 · FILL
        a.fill(self.k("codigo_do_registro"), codigo_do_registro, step_id="9d37db99-62ed-4bd3-a4fc-f1c4cde8ccc3")
        # etapa 11cad2bf-87c3-4364-bc50-118e5e796ffd · CLICK
        a.click(self.k("consultar"), step_id="11cad2bf-87c3-4364-bc50-118e5e796ffd", idempotent=True)
        # etapa 7b43c2bd-c9fb-43ce-81fe-5884ce838b16 · OBSERVE
        saidas["resultado_da_consulta"] = a.read_text(
            self.k("resultado_da_consulta"),
            step_id="7b43c2bd-c9fb-43ce-81fe-5884ce838b16",
        )
        # etapa 781d26f9-f16d-4bca-aab6-d226fde6d8a5 · CLICK
        a.click(self.k("sair"), step_id="781d26f9-f16d-4bca-aab6-d226fde6d8a5", idempotent=True)
        return saidas
