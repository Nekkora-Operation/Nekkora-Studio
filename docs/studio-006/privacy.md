# STUDIO-006 — Privacidade e segurança do armazenamento

## O que o arquivo NÃO contém (por construção)

| Item | Garantia |
|---|---|
| valores digitados nas páginas | não existe campo para eles; o Recorder já não os entrega (STUDIO-003). Homologação: usuário e senha digitados de verdade não aparecem em nenhum byte do arquivo, da recuperação ou dos logs |
| senhas, tokens, chaves de API, segredos | credencial é só o **apelido** (`CREDENTIAL.alias`); literal com aparência de segredo é recusado pelo domínio (S006-073) |
| texto das páginas em evidências | `message.text` é sempre `null`; arquivo com texto é recusado |
| capturas de tela, PageMaps completos, imagens | não há entrada para eles; nomes de entrada fora do padrão fechado são recusados |
| executáveis, scripts, recursos externos | idem; o Studio não executa nada contido no arquivo (D-S006-15) e não busca nada na rede |

## Dados capturados × dados do usuário (§31)

- **Capturados** (vieram da página pelo Recorder: nome acessível, `id`/`name` HTML, tag, localizadores, trajetória,
  contexto, título e endereço da tela, endereços em evidências) seguem a `nekkora-privacy/2`. `CapturedDataPolicy`
  exige que cada um seja **ponto fixo** da política (aplicar a política não muda nada). Isso vale **antes de confirmar**
  a gravação (releitura do temporário) e **depois de ler** um arquivo. Um valor fora da política recusa o arquivo
  inteiro (`PRIVACY_VIOLATION`): nada é "sanitizado" em silêncio para parecer compatível e nenhum texto omitido é
  recuperado.
- **Textos gerados pelo Studio** (notas de estabilidade, obstáculos, resultado observado) passam pela checagem de
  exibição segura.
- **Textos do usuário** (nomes, descrições, comentários, notas, literais) seguem as validações do domínio. O Studio
  **não afirma** que eles estejam livres de dados pessoais: se o usuário escrever o nome de um cliente numa descrição,
  o arquivo terá esse texto. Por isso a interface informa que o projeto é um documento de trabalho não criptografado.

## Política no manifesto

`privacyPolicy` precisa ser `nekkora-privacy/2`. Arquivo declarando `nekkora-privacy/1` (ou uma futura) é recusado
(D-S006-11/12). Na homologação real: `politica-v1.nekkora` e `dado-da-pagina.nekkora` (nome acessível com nome de
pessoa e manifesto recalculado) foram recusados pelo menu Arquivo → Abrir, sem carregar nada.

## Não criptografado — e dito claramente

- `metadata.json`: `"encrypted": false` e o aviso "Arquivo local de trabalho, não criptografado e legível por
  ferramentas apropriadas. Não guarde senhas, tokens ou outros segredos em projetos."
- Barra de status (dica do estado do projeto), diálogo de saída com alterações e diálogo de recuperação automática.
- Não há criptografia improvisada. O SHA-256 do manifesto não é apresentado como autenticidade ([manifest.md](manifest.md)).

## Logs (§32)

Só `projeto operacao=<salvar|abrir|recuperacao> resultado=<ok|erro> duracaoMs=… bytes=… codigo=<ERRO>`. Nunca: nome
do projeto, textos das etapas, comentários, parâmetros, evidências, conteúdo do arquivo, caminho. Mensagens de erro
mostram o código e a **parte** do arquivo (ex.: `steps/…json: effective.preferredLocator`), nunca o conteúdo.
Testes: `ProjectStudioTest` S006-074; homologação (logs reais da sessão gravados em
`reports/studio-006/evidence/S006-logs-do-studio.txt` e conferidos).

## Recentes e configurações

`recent-projects.json`: caminho, último acesso e versão do formato — nada do conteúdo (§30).
`settings.json`: consentimento e intervalo da recuperação. Ambos no diretório de dados do Studio, separado do
armazenamento da Chrome Extension.

## Dados reais

Não autorizados (D-S006-18). Toda a homologação usou páginas fictícias do laboratório local.
