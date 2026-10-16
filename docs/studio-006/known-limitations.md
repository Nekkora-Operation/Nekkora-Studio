# STUDIO-006 — Limitações conhecidas

| # | Limitação | Impacto | Mitigação / próxima missão |
|---|---|---|---|
| L1 | **Sem substituição atômica** (FAT/exFAT, alguns compartilhamentos de rede): modo de cópia de segurança, com um intervalo curto em que o nome do projeto não existe | uma queda exatamente nesse intervalo deixa o conteúdo confirmado só no `.bak` | o `.bak` nunca é apagado sem o arquivo principal; o aviso de sucesso diz que não houve troca atômica |
| L2 | **Windows sem sync de diretório**: `FileChannel.open(pasta)` não é suportado; o sync da pasta é melhor esforço | numa queda de energia logo após a troca, o NTFS depende do próprio journal para a entrada do diretório | o conteúdo do arquivo é sincronizado (`force`) antes da troca |
| L3 | **Recuperação pulada durante gravação**: sem quiescência, nenhuma fotografia é gravada | se o Studio cair no meio de uma gravação, as etapas dessa gravação não estão numa fotografia | mesma regra do salvamento definitivo (§22); a extensão continua com a própria proteção |
| L4 | **Recuperado salvo no arquivo de origem** só se o conteúdo do arquivo ainda for o da base; senão, conflito | sem merge automático | Salvar com outro nome preserva as duas versões |
| L5 | `revision` da automação é persistida | o número continua crescendo entre sessões (não volta a 0) | intencional (I-S006-03) |
| L6 | **Sem criptografia nem assinatura** | o arquivo é legível e pode ser alterado junto com os checksums | dito na interface e nos metadados; o leitor valida tudo mesmo com checksums corretos |
| L7 | Textos digitados pelo usuário (nomes, descrições, comentários) **podem conter dados pessoais** | o arquivo os guarda como foram escritos | a política de captura não se aplica a eles; o domínio recusa comentários com aparência de CPF etc., mas não tudo |
| L8 | Estado "salvo" por geração, não por conteúdo | desfazer até o estado do arquivo não tira o `*` | conservador: nunca diz "salvo" sem ter gravado |
| L9 | Sem importação como cópia (novas identidades) | outro arquivo com a mesma `AutomationId` não abre ao lado do primeiro | fora do escopo (§26) |
| L10 | Sem migração de formato ou de política | arquivos de versões futuras ou de `nekkora-privacy/1` não abrem | intencional (D-S006-12); ver [versioning.md](versioning.md) |
| L11 | Temporário de uma gravação interrompida fica oculto na pasta do projeto até a próxima gravação daquele projeto (limpo se tiver mais de 10 min) | um arquivo `.<nome>.nekkora.….tmp` pode ficar na pasta | limpeza automática no salvamento seguinte (§33) |
| L12 | Fotografias de recuperação inválidas (ex.: disco cheio durante a escrita por outro processo) ficam até vencer (7 dias) | ocupam espaço | não são oferecidas; "Apagar agora…" limpa |
| L13 | Logs técnicos vão para o `java.util.logging` padrão (console) | não há arquivo de log próprio do Studio | sem conteúdo nem caminhos (§32) |
| L14 | A medição de memória é por diferença de heap | valor aproximado | suficiente para ordem de grandeza |
| L15 | A homologação real abriu o Studio da recuperação com o navegador **desligado** (processo filho) | o cenário 5 não usa a extensão | a recuperação não depende do navegador (D-S006-16); a integração com a extensão está nos cenários 1–3 e 6 |
