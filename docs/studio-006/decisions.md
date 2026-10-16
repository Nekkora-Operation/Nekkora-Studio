# STUDIO-006 — Decisões

| Decisão | Enunciado | Como foi cumprida | Prova |
|---|---|---|---|
| D-S006-01 | Cada automação poderá ser salva como um projeto independente | um arquivo `.nekkora` por automação; estado de persistência por `AutomationId` | `ProjectStudioTest` (S006-055), homologação cenários 1–4 |
| D-S006-02 | O formato será versionado | `formatVersion` (SemVer do pacote), separada do modelo e da política | [versioning.md](versioning.md), `ProjectFormatContractTest` |
| D-S006-03 | O projeto terá representação estruturada | documentos JSON separados (automação, sessões, etapa, evidências, metadados) | [project-format.md](project-format.md), schema 1.0.0 |
| D-S006-04 | O domínio continuará independente do mecanismo de armazenamento | módulo `nekkora-studio-persistence`; o domínio só ganhou a reconstrução controlada | [architecture.md](architecture.md) |
| D-S006-05 | O carregamento deverá reconstruir o modelo sem gerar novas identidades | `AutomationRestoration` recebe IDs e instantes do arquivo | `ProjectRoundTripTest` (S006-008/009/010/032), homologação |
| D-S006-06 | O salvamento deverá ser atômico quando suportado pelo sistema de arquivos | temporário na mesma pasta + `ATOMIC_MOVE`; sem suporte, cópia de segurança com restauração | `AtomicSaveTest` (S006-044/045) |
| D-S006-07 | Arquivos inválidos não poderão modificar parcialmente o workspace | tudo é lido e reconstruído fora da EDT; só um agregado completo entra no catálogo | `ProjectStudioTest`/`ProjectValidationTest` (S006-054) |
| D-S006-08 | Evidências originais permanecerão imutáveis | gravadas e lidas campo a campo; o leitor recusa texto da página e valores que o domínio descartaria | S006-027…033, fixture `texto-da-pagina-em-evidencia` |
| D-S006-09 | O histórico Undo/Redo continuará exclusivamente em memória | o arquivo não tem histórico; ao abrir, o histórico é limpo | S006-035/036, homologação |
| D-S006-10 | Salvamento e leitura não poderão bloquear a EDT | EDT só fotografa; compactar, gravar, ler e validar rodam num worker | S006-057/058/059, [performance.md](performance.md) |
| D-S006-11 | Projetos não poderão declarar uma política de privacidade mais recente sem validação | só `nekkora-privacy/2` é aceita; outra (anterior ou futura) é recusada | S006-072, fixture `politica-privacidade-anterior` |
| D-S006-12 | Projetos antigos ou incompatíveis não serão migrados silenciosamente | versão mais nova → `NEWER_VERSION`; mais antiga/inválida → `UNSUPPORTED_VERSION`; nenhuma migração existe | S006-049 |
| D-S006-13 | O estado de alterações pendentes será controlado por automação | `ProjectState` com geração independente da revisão do domínio | S006-055/056/057 |
| D-S006-14 | A recuperação de falhas não poderá sobrescrever arquivos confirmados | fotografias em diretório próprio, `kind=RECOVERY`; salvar o recuperado confere o conteúdo base | S006-061…064, homologação cenário 5 |
| D-S006-15 | O Studio não executará ações contidas em arquivos carregados | o formato não tem campo executável; abrir não envia comando ao navegador | S006-080, homologação |
| D-S006-16 | A extensão continuará independente do formato de projetos | nenhuma mudança na extensão; o protocolo do Bridge não mudou | `git diff` vazio na extensão |
| D-S006-17 | Arquivos permanecerão locais; nenhuma transmissão remota será introduzida | nenhuma dependência ou chamada de rede nova | revisão do código |
| D-S006-18 | A missão não autoriza gravações com dados reais de clientes | homologação só com páginas fictícias do laboratório | relatório final |

## Decisões de implementação tomadas nesta missão

| Id | Decisão | Motivo |
|---|---|---|
| I-S006-01 | Nomes de entrada por UUID (`steps/<stepId>.json`) e padrão fechado de nomes | identidade estável; impede Zip Slip, caminhos absolutos e nomes arbitrários |
| I-S006-02 | Todo campo existe sempre (ausência = `null`); campo desconhecido é recusado | o leitor nunca "descarta" o que não entende (§27) |
| I-S006-03 | `revision` da automação é persistida | o desfazer depende de revisões crescentes; zerá-la ao abrir confundiria a comparação |
| I-S006-04 | Fotografia de recuperação = mesmo formato com `kind=RECOVERY` e `recoveryOf` | um único leitor validado; um projeto copiado para a pasta de recuperação não é oferecido |
| I-S006-05 | Recuperado salvo no arquivo de origem só se o conteúdo dele ainda for o da base | evita sobrescrever uma versão gravada por outro programa depois da queda |
| I-S006-06 | Recuperação não fotografa durante gravação nem durante salvamento | mesma regra de quiescência do salvamento definitivo |
| I-S006-07 | Diretório de dados: propriedade `nekkora.studio.dataDir` > `NEKKORA_STUDIO_DATA_DIR` > padrão do sistema | testes e homologação isolados da instalação pessoal; testes sem diretório usam `inMemory` |
| I-S006-08 | `NekkoraStatusBar` em linha única sem sobreposição | defeito encontrado na homologação: com 870 px (ou 1366×768 a 150%) textos se sobrepunham |
| I-S006-09 | Item "Navegador" da barra de status logo depois da situação do projeto | em janela estreita saem primeiro os itens menos importantes (tema, recuperação) |
