# Antes (rodada 3) → depois (rodada 4)

Trechos dos arquivos ORIGINAIS das duas rodadas.

## R4-01 — README gerado (coluna Reading)

Antes:
```
| `SelectorKey.SEARCH_TERM_FIELD` | CSS `header input[name="search"]` | região/elemento estável + atributo |
| `SelectorKey.PROFILE_TITLE` | CSS `h1#firstHeading` | id ou atributo de teste estável |
```
Depois:
```
| `SelectorKey.SEARCH_TERM_FIELD` | CSS `header input[name="search"]` | stable container + attribute |
| `SelectorKey.PROFILE_TITLE` | CSS `h1#firstHeading` | stable ID or test attribute |
```

## R4-01 — detalhe do segredo ausente

Antes:
```python
def _secrets(rt: Runtime) -> SecretProvider:
    """Secret provider of the runtime (the values come from environment variables, never from the project)."""
    if rt.secrets is None:
        raise InvalidConfiguration(detail="sem_provedor_de_segredos")
    return rt.secrets
```
Depois:
```python
def _secrets(rt: Runtime) -> SecretProvider:
    """Secret provider of the runtime (the values come from environment variables, never from the project)."""
    if rt.secrets is None:
        raise InvalidConfiguration(detail="secret_provider_missing")
    return rt.secrets
```

## R4-02 — datas

Antes:
```python
class ProfileData:
    """Data read by ``ProfilePage.read_profile_data()``.

    ``None``: the value does not exist on this page (absence foreseen in the PDD, with an alternative source).
    """

    title: str | None
    full_name: str | None
    birth: str
    death: str
    nationality: str
```
Depois:
```python
class ProfileData:
    """Data read by ``ProfilePage.read_profile_data()``.

    ``None``: the value does not exist on this page (absence foreseen in the PDD, with an alternative source).
    """

    title: str | None
    full_name: str | None
    birth_date: str
    death_date: str
    nationality: str
```

## Q12 — RPA Challenge (gerador tabular)

Antes (E2E-001, `paginas.py`, gerador 1.0.0):
```python
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
```
Depois (`automation/pages.py`):
```python
class RecordFillingPage(PageBase):
    """Screen of the session “Preenchimento por registro” (runs once per spreadsheet row): fills “First Name”, “Last
    Name”, “Company Name”, “Role in Company”, “Address”, “Email” and “Phone Number” and clicks “Submit”.
    """

    identity_key = SelectorKey.RECORD_FILLING_FIRST_NAME

    def submit_record(
        self,
        *,
        first_name: str,
        last_name: str,
        company_name: str,
        role_in_company: str,
        address: str,
        email: str,
        phone_number: str,
    ) -> None:
        """Fills “First Name”, “Last Name”, “Company Name”, “Role in Company”, “Address”, “Email” and “Phone Number”
        with the values of one spreadsheet row and clicks “Submit”.

        Finishes when the field “First Name” is replaced by a new one (proof that the form moved on). Submits only
        once: if the outcome is uncertain, the item stops (``OUTCOME_UNKNOWN``) instead of clicking “Submit” again.
        """
        self.actions.fill(
            SelectorKey.RECORD_FILLING_FIRST_NAME.value,
            first_name,
            step_id="record_filling.fill_first_name",
        )
        self.actions.fill(
            SelectorKey.RECORD_FILLING_LAST_NAME.value,
            last_name,
            step_id="record_filling.fill_last_name",
        )
        self.actions.fill(
            SelectorKey.RECORD_FILLING_COMPANY_NAME.value,
            company_name,
            step_id="record_filling.fill_company_name",
        )
        self.actions.fill(
            SelectorKey.RECORD_FILLING_ROLE_IN_COMPANY.value,
            role_in_company,
            step_id="record_filling.fill_role_in_company",
        )
        self.actions.fill(SelectorKey.RECORD_FILLING_ADDRESS.value, address, step_id="record_filling.fill_address")
        self.actions.fill(SelectorKey.RECORD_FILLING_EMAIL.value, email, step_id="record_filling.fill_email")
        self.actions.fill(
            SelectorKey.RECORD_FILLING_PHONE_NUMBER.value,
            phone_number,
            step_id="record_filling.fill_phone_number",
        )
        field_before = self.actions.mark(
            SelectorKey.RECORD_FILLING_FIRST_NAME.value,
            step_id="record_filling.click_submit",
        )
        self.actions.click(
            SelectorKey.RECORD_FILLING_SUBMIT.value,
            step_id="record_filling.click_submit",
            postcondition=lambda: self.actions.replaced(field_before),
        )
```

