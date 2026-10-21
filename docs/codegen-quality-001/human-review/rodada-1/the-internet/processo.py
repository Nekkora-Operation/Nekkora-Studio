# GERADO pelo Nekkora Studio ("Desenvolver automação") a partir do modelo validado e do PDD pronto.
# Não edite: correções vão para o modelo ou o PDD e o código é gerado de novo (arquivo editado à mão não é sobrescrito).
"""Processo AreaSeguraTheInternet: execução única."""

from __future__ import annotations

from nekkora_rpa import ProcessResult, RpaProcess, Runtime, TransactionItem
from nekkora_rpa.errors import InvalidConfiguration
from nekkora_rpa.security.secrets import SecretProvider

from paginas import PaginaAcesso, PaginaArea

URL_INICIAL = "https://the-internet.herokuapp.com/login"


def _segredos(rt: Runtime) -> SecretProvider:
    """Provedor de segredos do runtime (os valores vêm de variáveis de ambiente, nunca do projeto)."""
    if rt.secrets is None:
        raise InvalidConfiguration(detail="sem_provedor_de_segredos")
    return rt.secrets


class AreaSeguraTheInternet(RpaProcess):
    """Execução única. Passa por: Acesso → Área segura."""

    name = "area_segura_the_internet"
    requires_browser = True
    required_secrets = ("credencial.senha", "credencial.usuario")

    def init(self, rt: Runtime) -> None:
        """Abre o endereço inicial uma vez, antes da execução."""
        rt.act.navigate(URL_INICIAL, step_id="INICIO")

    def process(self, rt: Runtime, item: TransactionItem) -> ProcessResult:
        """Faz as etapas na ordem do processo."""
        segredos = _segredos(rt)
        PaginaAcesso(rt.act).entrar(segredos)
        area = PaginaArea(rt.act)
        resultado_logout = area.ler_resultado_logout()
        area.clicar_logout()
        return ProcessResult({"resultado_logout": resultado_logout})
