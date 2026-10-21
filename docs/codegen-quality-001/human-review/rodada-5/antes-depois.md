# Antes (rodada 4) → depois (rodada 5)

Trechos dos arquivos ORIGINAIS das duas rodadas.

## CQ-R5-01 — linha da planilha (RPA Challenge)

Antes:
```python
@dataclass(frozen=True)
class InputRow:
    """One spreadsheet row: the columns the form uses (the spreadsheet headers are read in ``from_item``)."""

```
Depois:
```python
@dataclass(frozen=True, repr=False)
class InputRow:
    """One spreadsheet row: the columns the form uses (headers read in ``from_item``; the values are never logged)."""

```

## CQ-R5-01 — dados lidos (Wikipédia)

Antes:
```python
@dataclass(frozen=True)
class ProfileData:
    """Data read by ``ProfilePage.read_profile_data()``.
```
Depois:
```python
@dataclass(frozen=True, repr=False)
class ProfileData:
    """Data read by ``ProfilePage.read_profile_data()`` (``repr`` does not show the values).
```

## CQ-R5-02 — chave do download

Antes:
```python
    PREPARATION_DOWNLOAD_EXCEL_CLOUD_DOWNLOAD = "process.preparation.download_excel_cloud_download"
```
Depois:
```python
    PREPARATION_DOWNLOAD_SPREADSHEET = "process.preparation.download_spreadsheet"
```
Localizador (igual nas duas rodadas, é o texto da página):
```python
"expression": '//a[normalize-space(.)="Download Excel cloud_download"]',
```
Vínculo no manifesto gerado sobre a rodada 4 (`evidencias/rpa-challenge/manifest-regerado-sobre-rodada-4.json`):
```json
        "targetKey": "process.preparation.download_spreadsheet",
        "previousTargetKeys": [
          "process.preparation.download_excel_cloud_download"
        ],
```

## `article_url`

Antes:
```python
article_address=_required_input(data, "endereco_do_artigo"),
```
Depois:
```python
article_url=_required_input(data, "endereco_do_artigo"),
```

## CQ-R5-03 — contagens da planilha baixada

Rodada 4:
```json
{
  "oficial-1": {
    "arquivo": "challenge.xlsx",
    "sha256": "cf1a8c04a298cc12cb481a0a6c88b078b209dc59c4983f6ca78714d611c04d37",
    "linhas": 999
  },
  "oficial-2": {
    "arquivo": "challenge.xlsx",
    "sha256": "cf1a8c04a298cc12cb481a0a6c88b078b209dc59c4983f6ca78714d611c04d37",
    "linhas_com_dados": 10
  }
}
```
Rodada 5 (as duas execuções registram o mesmo):
```json
{
  "arquivo": "challenge.xlsx",
  "sha256": "cf1a8c04a298cc12cb481a0a6c88b078b209dc59c4983f6ca78714d611c04d37",
  "linhas_declaradas": 1000,
  "linhas_lidas_com_cabecalho": 1000,
  "linhas_com_dados": 10,
  "significado": {
    "linhas_declaradas": "dimensão que o arquivo declara (openpyxl max_row): inclui linhas vazias formatadas",
    "linhas_lidas_com_cabecalho": "linhas que o leitor percorre, cabeçalho incluído (vazias também)",
    "linhas_com_dados": "linhas depois do cabeçalho com pelo menos uma célula não vazia = transações esperadas"
  }
}
```

