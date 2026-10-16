# STUDIO-006 — Salvamento atômico

`ProjectRepository.save(destino, bytes, esperado, sobrescrever, verificação)`:

```text
1. extensão .nekkora; tamanho ≤ 128 MiB; pasta existe; destino gravável           WRONG_EXTENSION / TOO_LARGE / DIRECTORY_MISSING / READ_ONLY
2. conflito: o destino ainda é o que o Studio conhece (tamanho + SHA-256)?        CONFLICT
3. limpeza de temporários ABANDONADOS deste projeto (> 10 min)
4. temporário NA MESMA PASTA: .<nome>.nekkora.<aleatório>.tmp
5. escrita completa + FileChannel.force(true)
6. releitura dos bytes do temporário = bytes pretendidos                           WRITE_FAILED
7. releitura COMPLETA (pacote, domínio, privacidade, identidade)                    erro da leitura
8. conflito de novo, imediatamente antes de substituir                             CONFLICT
9. Files.move(ATOMIC_MOVE, REPLACE_EXISTING)
10. sync da pasta (melhor esforço)
11. sucesso — só agora o Studio diz "Projeto salvo"
```

Em qualquer falha: o temporário é apagado (passo `finally`) e **o arquivo anterior não foi tocado** — ele só é
substituído no passo 9, por troca atômica.

## Sem substituição atômica (§18)

`Files.move` não é tratado como seguro por definição. Se o sistema de arquivos recusar `ATOMIC_MOVE`
(`AtomicMoveNotSupportedException`):

1. o anterior é renomeado para `.<nome>.nekkora.<aleatório>.bak` na mesma pasta;
2. o novo entra no lugar;
3. se o passo 2 falhar, o anterior **volta** ao nome original; se até isso falhar, o erro `REPLACE_FAILED` informa o
   nome da cópia de segurança, que continua íntegra;
4. com sucesso, a cópia é apagada e o aviso diz "substituição com cópia de segurança: o sistema de arquivos não oferece
   troca atômica".

**Limitação documentada**: nesse modo existe um intervalo curto em que o nome do projeto não existe no disco (entre 1 e
2). Uma queda exatamente aí deixa o conteúdo confirmado no `.bak`. A limpeza automática **nunca** apaga um `.bak`
quando o arquivo principal não existe.

Testes: `AtomicSaveTest` S006-044 (falha na própria substituição), S006-045 (sem atômico: cópia de segurança e
restauração), S006-042/043 (disco cheio, falha de escrita), §33 (pasta inexistente, somente leitura, temporário
abandonado). As falhas são injetadas por `ProjectRepository.FileOps` — operações reais do sistema de arquivos
substituídas só nos testes.

## Falhas reais na homologação (Windows, NTFS)

| Falha | Resultado observado |
|---|---|
| arquivo somente leitura (`dos:readonly`) | `Erro ao salvar`, "O arquivo anterior foi preservado", SHA-256 igual, nenhum temporário |
| arquivo aberto por outro programa (`FileShare.None`) | erro, arquivo anterior intacto, alterações abertas, nenhum temporário |
| arquivo regravado por outro programa | diálogo "Arquivo alterado fora do Studio"; o arquivo externo ficou intacto; "Salvar com outro nome" preservou as duas versões |

## Sistemas de arquivos

| Sistema | Comportamento |
|---|---|
| NTFS (Windows) | `MoveFileEx` com substituição: troca atômica; sync de pasta não disponível (melhor esforço ignorado) |
| ext4/xfs/btrfs/APFS | `rename(2)` atômico; sync da pasta feito |
| FAT/exFAT, compartilhamentos de rede | podem recusar `ATOMIC_MOVE` → modo de cópia de segurança; a atomicidade depende do servidor em compartilhamentos |
