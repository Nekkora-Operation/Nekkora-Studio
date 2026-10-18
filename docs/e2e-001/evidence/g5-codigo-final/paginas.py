# GERADO pelo Nekkora Studio (STUDIO-011) a partir do modelo validado. NÃO EDITE: correções vão para o
# modelo (debug guiado), e o código é gerado de novo. Arquivo editado à mão não é sobrescrito.
"""Pages geradas: uma por sessão do modelo, etapas na ordem do modelo, alvos só por CHAVE do registro."""

from __future__ import annotations

from collections.abc import Mapping
from pathlib import Path

from nekkora_rpa.libs.actions import DownloadedFile
from nekkora_rpa.pages import BasePage
from nekkora_rpa.security.secrets import SecretProvider

XLSX_MAGIC = b"PK\x03\x04"


def _valor(dados: Mapping[str, object], coluna: str) -> str:
    v = dados.get(coluna)
    if not isinstance(v, str):
        raise TypeError("coluna sem texto")  # o processo já recusou linhas com problema
    return v


class Sessao1(BasePage):
    """Sessão 1 do modelo — papel: inicialização."""

    prefix = "processo.s1"
    identity = "download_excel_cloud_download"

    def executar(self, destino: Path, segredos: SecretProvider | None) -> tuple[DownloadedFile | None, dict[str, str]]:
        a = self.act
        baixado: DownloadedFile | None = None
        obs: dict[str, str] = {}
        # etapa 2e51e9b2-3313-44c4-ae53-c08164e3f811 · CLICK
        baixado = a.download(
            self.k("download_excel_cloud_download"),
            dest_dir=destino,
            file_name="challenge.xlsx",
            magic=XLSX_MAGIC,
            step_id="2e51e9b2-3313-44c4-ae53-c08164e3f811",
        )
        # etapa 85dd950c-d3cf-4623-91ab-479f6144a1c3 · CLICK
        marca = a.mark("processo.s2.first_name", step_id="85dd950c-d3cf-4623-91ab-479f6144a1c3")
        a.click(
            self.k("start"),
            step_id="85dd950c-d3cf-4623-91ab-479f6144a1c3",
            postcondition=lambda: a.replaced(marca),
        )
        return baixado, obs


class Sessao2(BasePage):
    """Sessão 2 do modelo — papel: uma transação por registro."""

    prefix = "processo.s2"
    identity = "first_name"

    def executar(self, dados: Mapping[str, object], segredos: SecretProvider | None) -> dict[str, str]:
        a = self.act
        obs: dict[str, str] = {}
        # etapa 40a1f407-37e0-4c6e-8e6d-d6737e784d74 · FILL
        a.fill(self.k("first_name"), _valor(dados, "First Name"), step_id="40a1f407-37e0-4c6e-8e6d-d6737e784d74")
        # etapa e50b8865-182d-4aed-8bd0-bd0b13fcbd9c · FILL
        a.fill(self.k("last_name"), _valor(dados, "Last Name"), step_id="e50b8865-182d-4aed-8bd0-bd0b13fcbd9c")
        # etapa a08c54bb-7bcd-44ad-885c-350a9bc6d7d3 · FILL
        a.fill(self.k("company_name"), _valor(dados, "Company Name"), step_id="a08c54bb-7bcd-44ad-885c-350a9bc6d7d3")
        # etapa 480db37e-e9ce-4ca4-b0c2-1f4856609d9a · FILL
        a.fill(
            self.k("role_in_company"),
            _valor(dados, "Role in Company"),
            step_id="480db37e-e9ce-4ca4-b0c2-1f4856609d9a",
        )
        # etapa 11ab1956-5e1d-44f5-a917-5e964ed4018f · FILL
        a.fill(self.k("address"), _valor(dados, "Address"), step_id="11ab1956-5e1d-44f5-a917-5e964ed4018f")
        # etapa e0f4c420-cbce-4199-b847-a81cd08eafdb · FILL
        a.fill(self.k("email"), _valor(dados, "Email"), step_id="e0f4c420-cbce-4199-b847-a81cd08eafdb")
        # etapa 070d6140-3769-4107-bdcd-4de0bcc5638c · FILL
        a.fill(self.k("phone_number"), _valor(dados, "Phone Number"), step_id="070d6140-3769-4107-bdcd-4de0bcc5638c")
        # etapa 944a9801-8f21-4d2e-861b-a68895c20fd5 · CLICK
        marca = a.mark(self.k("first_name"), step_id="944a9801-8f21-4d2e-861b-a68895c20fd5")
        a.click(
            self.k("submit"),
            step_id="944a9801-8f21-4d2e-861b-a68895c20fd5",
            postcondition=lambda: a.replaced(marca),
        )
        return obs


class Sessao3(BasePage):
    """Sessão 3 do modelo — papel: conclusão."""

    prefix = "processo.s3"
    identity = ""

    def executar(self, segredos: SecretProvider | None) -> dict[str, str]:
        a = self.act
        obs: dict[str, str] = {}
        # etapa ed12c86a-ac06-431b-9e87-1e5216f01f11 · OBSERVE
        obs["ed12c86a-ac06-431b-9e87-1e5216f01f11"] = a.read_text_matching(
            "^Your success rate is \\d+(\\.\\d+)?% \\( \\d+ out of \\d+ fields\\) in \\d+ milliseconds$",
            step_id="ed12c86a-ac06-431b-9e87-1e5216f01f11",
        )
        return obs


def _segredo(segredos: SecretProvider | None, nome: str):  # type: ignore[no-untyped-def]
    if segredos is None:
        raise ValueError("sem provedor de segredos")
    return segredos.get(nome)
