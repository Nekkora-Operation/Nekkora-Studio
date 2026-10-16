# STUDIO-006 — Reconstrução (hidratação) e validação

## Entrada não confiável (§17)

Um `.nekkora` é tratado como entrada hostil, mesmo sendo local. Camadas, nesta ordem — a primeira falha recusa o
arquivo inteiro com um código estável e a **parte** afetada (nunca o conteúdo):

| # | Camada | Verifica | Erros |
|---|---|---|---|
| 1 | arquivo | existe; ≤ 128 MiB | `NOT_FOUND`, `TOO_LARGE` |
| 2 | contêiner | ZIP legível; ≤ 40.005 entradas; nome no padrão fechado; sem diretório; sem repetição; método deflate/store | `CORRUPTED`, `UNSAFE_ENTRY`, `DUPLICATE_ENTRY`, `TOO_LARGE` |
| 3 | bytes | cada entrada lida até 8 MiB **de fato** e a soma até 512 MiB (o tamanho declarado no ZIP é ignorado) | `TOO_LARGE` |
| 4 | manifesto | `format`; **versão antes de tudo**; modelo; política; tipo; identidades; lista de entradas com tamanho e SHA-256; nada sobrando nem faltando | `NOT_A_PROJECT`, `NEWER_VERSION`, `UNSUPPORTED_VERSION`, `PRIVACY_POLICY_UNSUPPORTED`, `CHECKSUM_MISMATCH`, `MISSING_ENTRY`, `UNEXPECTED_ENTRY` |
| 5 | JSON | UTF-8 válido, RFC 8259, sem chave repetida | `INVALID_JSON` |
| 6 | documentos | campos exatos, tipos, enums, UUID e instantes canônicos, códigos técnicos `[A-Z_]`, correlação `[A-Za-z0-9._:-]` | `INVALID_CONTENT` |
| 7 | referências | ordem das sessões; etapas existentes, únicas, de uma só sessão; nada solto; nome da entrada = identidade; preferencial válido | `INTEGRITY` |
| 8 | domínio | `AutomationRestoration.restore` reaplica os invariantes | `INVALID_CONTENT` / `INTEGRITY` |
| 9 | privacidade | `CapturedDataPolicy`: campos capturados são ponto fixo da `nekkora-privacy/2` | `PRIVACY_VIOLATION` |
| 10 | estabilidade | o arquivo não mudou durante a leitura (impressão digital antes = depois) | `CONFLICT` |

Proteções de ZIP exigidas (§17): Zip Slip, caminho absoluto, `../`, duplicata, bomba de descompressão, profundidade
(nomes com `/` só nos dois diretórios fixos; JSON com profundidade ≤ 64) e consumo de memória (limites de bytes
lidos). Nada é extraído para o disco. Testes: `ProjectValidationTest` S006-046…053 e as fixtures inválidas.

## Reconstrução controlada (§16)

`AutomationRestoration` (domínio, pacote `com.nekkora.studio.domain`):

- recebe `AutomationData` → `SessionData` → `StepData`, todos imutáveis;
- cria o agregado com **as identidades e os instantes do arquivo** (automação, sessões, etapas, comentários,
  localizadores manuais, edições por campo, última intervenção);
- **não** usa `Automation.create` + operações comuns (que gerariam IDs, instantes e "intervenções" novas);
- reaplica todos os invariantes: textos normalizados, nomes de sessão únicos, identidades únicas, parâmetros
  compatíveis com a ação, original coerente com a procedência (etapa manual não tem `baseline`; gravada tem),
  preferencial validado, vínculo `derivedFrom` apontando etapa existente, evidência só em etapa gravada **executada**
  e com o `eventId` dela, ordem técnica das evidências, comentários sem aparência de dado sensível;
- devolve `RestoreException(reason, where)`; o serviço a traduz em `INTEGRITY` ou `INVALID_CONTENT`.

Os ganchos usados (`Automation.restored`, `adoptSession`, `adoptUnattributed`, construtor de reconstrução de
`AutomationStep`) são **restritos ao pacote** do domínio: não há API pública que monte um estado arbitrário sem
validação. Testes: `AutomationRestorationTest` (valores preservados; recusas estruturais e da etapa).

## Integral ou recusado (§25, D-S006-07)

Toda a leitura e a reconstrução rodam no worker, sobre objetos novos. O catálogo só é tocado na EDT, **depois** de
tudo, por `AutomationCatalog.adopt` (uma operação). Um erro na última etapa recusa o projeto inteiro: nenhuma sessão ou
etapa parcial entra no workspace (`ProjectValidationTest`/`ProjectStudioTest` S006-054).

Depois de adotar: histórico de desfazer vazio, estado "Salvo", arquivo nos recentes, aba aberta. Nada é executado
(S006-080): nenhum comando ao navegador, nenhum seletor avaliado.

## Projeto já aberto ou com a mesma identidade (§26)

- Mesmo arquivo já aberto → ativa a automação existente (S006-066).
- Outro arquivo com o mesmo `AutomationId` → conflito declarado, nada substituído (S006-067). Importar como cópia
  com novas identidades está fora do escopo.
