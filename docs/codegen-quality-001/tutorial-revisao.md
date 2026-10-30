# Como revisar o código gerado pelo Studio

1. Abra o projeto (`.nekkora`) e a automação; use **Automação → Desenvolver automação** (Ctrl+Shift+G).
2. Na aba **Plano**, a resposta **"Como o código fica organizado? (prévia)"** mostra, ANTES de gerar, as telas (Pages),
   os métodos e de onde vem cada nome (título do PDD, ou provisório quando o PDD não diz).
3. Use **Desenvolver**. Depois, **Revisar código** mostra, só para leitura:
   - a **arquitetura**: cada Page com o resumo e a fonte do nome, cada método com parâmetros, retorno e o que faz;
     os dados lidos (dataclass) com os campos que podem faltar (`?`);
   - o **mapa `step_id` → etapa do Studio → alvo → onde está no código**;
   - **como cada elemento é localizado** (rótulo, âncora de linha, região + atributo…);
   - os **hashes** dos arquivos e o conteúdo de `paginas.py` e `processo.py` exatamente como estão na pasta.
4. **Abrir código** abre a pasta; os arquivos principais são `paginas.py` (telas), `processo.py` (ordem do processo e
   linha da planilha), `main.py` (linha de comando), `selectors.json` (localizadores) e `manifest.json` (rastreio).
5. **Validar automação** (Ctrl+Shift+V) traz a camada **VQ — Qualidade da geração**: critérios objetivos (nomes com
   evidência, `step_id` mapeado, nada de UUID/comentário por etapa no código, localizador sem posição, nada de falha
   silenciada). VQ aprovada NÃO é aceite de legibilidade: esse é só da titular.

## Mudar um nome

- Tela ou método: edite o título da seção ou do passo no **PDD** (Automação → PDD visual), revise e use Desenvolver de
  novo. O `step_id` das etapas já geradas NÃO muda (registro de rastreio), para os eventos e a auditoria continuarem
  ligados às mesmas etapas.
- Campo lido ou entrada: renomeie a variável em **Variáveis e dependências** (Ctrl+Shift+B).
- Nunca edite o arquivo gerado: a edição é preservada, mas a geração seguinte vai para `.novo`/`.diff` ao lado dele.
