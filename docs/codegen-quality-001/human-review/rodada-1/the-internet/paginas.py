# GERADO pelo Nekkora Studio ("Desenvolver automação") a partir do modelo validado e do PDD pronto.
# Não edite: correções vão para o modelo ou o PDD e o código é gerado de novo (arquivo editado à mão não é sobrescrito).
"""Telas da automação: PaginaAcesso e PaginaArea.

Cada Page localiza os elementos pelas chaves do registro de alvos (selectors.json): ``self.k("nome")`` devolve a
chave completa do alvo e o nekkora_rpa verifica a identidade e a unicidade de cada um. O ``step_id`` de cada ação
liga a ação à etapa do Studio (mapa em manifest.json).
"""

from __future__ import annotations

from nekkora_rpa.pages import BasePage
from nekkora_rpa.security.secrets import SecretProvider


class PaginaAcesso(BasePage):
    """Acesso.

    Etapas: Acesso.
    """

    prefix = "processo.acesso"
    identity = "username"

    def entrar(self, segredos: SecretProvider) -> None:
        """Preenche “Username” e “Password”; aciona “Login”."""
        self.act.fill(self.k("username"), segredos.get("credencial.usuario"), step_id="acesso.preencher_usuario")
        self.act.fill(self.k("password"), segredos.get("credencial.senha"), step_id="acesso.preencher_senha")
        self.act.click(
            self.k("login"),
            step_id="acesso.clicar_login",
            idempotent=True,
            postcondition=lambda: self.act.exists("processo.area.logout"),
        )


class PaginaArea(BasePage):
    """Área segura.

    Etapas: Área segura.
    """

    prefix = "processo.area"
    identity = "logout"

    def ler_resultado_logout(self) -> str:
        """Lê “Logout”."""
        return self.act.read_text(self.k("logout"), step_id="area.ler_resultado_logout")

    def clicar_logout(self) -> None:
        """Aciona “Logout”."""
        self.act.click(self.k("logout"), step_id="area.clicar_logout", idempotent=True)
