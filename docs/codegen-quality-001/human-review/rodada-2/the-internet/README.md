<!-- GERADO pelo Nekkora Studio ("Desenvolver automação"). -->
# AreaSeguraTheInternet

**O que faz.** Execução única. Passa por: Acesso → Área segura. Sem entradas.

**Segredos** (variáveis de ambiente, nunca no projeto): `NK_SECRET_CREDENCIAL_SENHA`, `NK_SECRET_CREDENCIAL_USUARIO`.

## Como está organizado

| Arquivo | Conteúdo |
|---|---|
| `paginas.py` | uma classe por tela: `PaginaAcesso`, `PaginaAreaSegura` |
| `processo.py` | `AreaSeguraTheInternet`: a ordem das telas |
| `main.py` | linha de comando |
| `selectors.json` | como cada elemento é localizado (o código só usa chaves) |
| `manifest.json` | revisões, hashes, mapa `step_id` ↔ etapa do Studio |

## Telas e o que cada método faz

- **`PaginaAcesso`** — Tela da seção “Acesso” do PDD.
  - `entrar()` — Preenche “Username” e “Password” com as credenciais do provedor de segredos; clica em “Login”.
- **`PaginaAreaSegura`** — Tela da seção “Área segura” do PDD: lê “Logout”; clica em “Logout”.
  - `ler_texto_do_link_logout()` — Lê “Logout”.
  - `clicar_logout()` — Clica em “Logout”.

## Como cada elemento é localizado

| Chave | Estratégia | Leitura |
|---|---|---|
| `processo.acesso.username` | CSS `input#username` | id ou atributo de teste estável |
| `processo.acesso.password` | CSS `input#password` | id ou atributo de teste estável |
| `processo.acesso.login` | XPATH `//form[@id="login"]//button[normalize-space(.)="Login"]` | XPath relacional |
| `processo.area_segura.logout` | XPATH `//a[normalize-space(.)="Logout"]` | XPath relacional |

O nekkora_rpa só age se o localizador encontrar EXATAMENTE um elemento com a identidade esperada; nenhum, ou mais de um, é erro (nunca escolhe o primeiro).

## Executar

O Nekkora Studio prepara o ambiente desta automação: **Desenvolver automação → Pendências → Preparar ambiente** (venv própria, `nekkora-rpa==0.2.0`, dependências conferidas por hash, confirmação antes de instalar).

Fora do Studio (Python 3.12+, depois da publicação no índice aprovado):

```sh
python -m venv .venv
.venv/Scripts/python -m pip install "nekkora-rpa==0.2.0"
.venv/Scripts/python main.py --saida <pasta> --console
```

Saída: `resultados/` (o desfecho, com as leituras) e `execucao/` (eventos JSONL sem valores, checkpoint, resumo).
Gerar código não é executar: rode somente em ambiente autorizado (benchmark).
