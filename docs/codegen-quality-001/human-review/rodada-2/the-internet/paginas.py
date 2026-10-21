# GERADO pelo Nekkora Studio ("Desenvolver automação") a partir do modelo validado e do PDD pronto.
# Não edite: correções vão para o modelo ou o PDD e o código é gerado de novo (arquivo editado à mão não é sobrescrito).
"""Telas da automação: PaginaAcesso e PaginaAreaSegura.

Cada tela é uma classe com o que o processo faz nela. ``self.acoes`` são as ações do nekkora_rpa (preencher, clicar,
ler) e ``self.elemento("nome")`` aponta um elemento da tela pelo nome dele no registro selectors.json: o framework
confere a identidade e a unicidade do elemento antes de agir. O ``step_id`` de cada ação liga a ação à etapa do
Studio (mapa em manifest.json).
"""

from __future__ import annotations

from nekkora_rpa import Actions
from nekkora_rpa.pages import BasePage
from nekkora_rpa.security.secrets import SecretProvider


class PaginaBase(BasePage):
    """Base das telas: nomes legíveis para a API de Pages do nekkora_rpa (o comportamento é o do framework)."""

    @property
    def acoes(self) -> Actions:
        """Ações do nekkora_rpa nesta tela (o mesmo objeto que ``BasePage.act``)."""
        return self.act

    def elemento(self, nome: str) -> str:
        """Chave do elemento ``nome`` desta tela no registro de alvos (``BasePage.k``)."""
        return self.k(nome)


class PaginaAcesso(PaginaBase):
    """Tela da seção “Acesso” do PDD."""

    prefix = "processo.acesso"
    identity = "username"

    def entrar(self, segredos: SecretProvider) -> None:
        """Preenche “Username” e “Password” com as credenciais do provedor de segredos; clica em “Login”.

        Termina quando “Logout” aparece. Envia as credenciais uma vez só: se o resultado ficar incerto, o item para
        (``OUTCOME_UNKNOWN``) em vez de tentar entrar de novo.
        """
        self.acoes.fill(
            self.elemento("username"),
            segredos.get("credencial.usuario"),
            step_id="acesso.preencher_usuario",
        )
        self.acoes.fill(self.elemento("password"), segredos.get("credencial.senha"), step_id="acesso.preencher_senha")
        self.acoes.click(
            self.elemento("login"),
            step_id="acesso.clicar_login",
            postcondition=lambda: self.acoes.exists("processo.area_segura.logout"),
        )


class PaginaAreaSegura(PaginaBase):
    """Tela da seção “Área segura” do PDD: lê “Logout”; clica em “Logout”."""

    prefix = "processo.area_segura"
    identity = "logout"

    def ler_texto_do_link_logout(self) -> str:
        """Lê “Logout”.

        Devolve o texto lido.
        """
        return self.acoes.read_text(self.elemento("logout"), step_id="area_segura.ler_texto_do_link_logout")

    def clicar_logout(self) -> None:
        """Clica em “Logout”.

        Encerra a sessão uma vez só: se o resultado ficar incerto, não é repetido (``OUTCOME_UNKNOWN``).
        """
        self.acoes.click(self.elemento("logout"), step_id="area_segura.clicar_logout")
