# STUDIO-006 — Manifesto

`manifest.json` é a primeira entrada do pacote e o único documento que não se declara a si mesmo.

```json
{
  "format": "nekkora-studio/project",
  "formatVersion": "1.0.0",
  "kind": "PROJECT",
  "automationModel": { "schema": "nekkora-studio/automation", "version": "1.0.0" },
  "privacyPolicy": "nekkora-privacy/2",
  "projectId": "00000000-0000-4000-8000-000000000001",
  "automationId": "00000000-0000-4000-8000-000000000002",
  "createdAt": "2026-10-16T12:00:01Z",
  "savedAt": "2026-10-16T13:00:00Z",
  "recoveryOf": null,
  "limits": { "maxEntryBytes": 8388608, "maxTotalBytes": 536870912, "maxSteps": 20000 },
  "entries": [
    { "path": "metadata.json", "sha256": "57bd340c…", "size": 237 },
    { "path": "automation.json", "sha256": "9b745c8a…", "size": 426 }
  ]
}
```

(Trecho da fixture `fixtures/valid/consulta-clientes.nekkora`.)

## Campos (requisitos do §5)

| Campo | Requisito | Regra de leitura |
|---|---|---|
| `format` | formato reconhecido | diferente de `nekkora-studio/project` → `NOT_A_PROJECT` |
| `formatVersion` | versão obrigatória | lida **antes** do resto: MAJOR.MINOR maior → `NEWER_VERSION`; menor ou inválida → `UNSUPPORTED_VERSION` ([versioning.md](versioning.md)) |
| `kind` | tipo do pacote | `PROJECT` (salvo pelo usuário) ou `RECOVERY` (fotografia de recuperação); abrir um `RECOVERY` pelo menu é recusado |
| `automationModel` | contrato do domínio | `nekkora-studio/automation` 1.0.0, senão `UNSUPPORTED_VERSION` |
| `privacyPolicy` | política de privacidade | só `nekkora-privacy/2`, senão `PRIVACY_POLICY_UNSUPPORTED` |
| `projectId` | identidade do projeto | UUID canônico; mantido em "Salvar como" |
| `automationId` | identidade da automação | UUID canônico; precisa ser igual ao de `automation.json` |
| `createdAt` / `savedAt` | data do salvamento | instantes UTC canônicos |
| `recoveryOf` | origem de uma recuperação | `null` em `PROJECT`; em `RECOVERY`: `projectPath` (ou `null` se nunca salvo), `baseSize` e `baseSha256` do arquivo de origem naquele momento |
| `limits` | limites de conteúdo | informativos; o leitor usa os próprios |
| `entries` | referências e integridade dos documentos | cada entrada: nome válido, sem repetição, presente no ZIP, mesmo tamanho e SHA-256; o ZIP não pode ter entrada fora da lista |

## O que o SHA-256 garante — e o que não garante

- **Garante**: detecção de corrupção **acidental** (disco, cópia interrompida, edição manual de um documento sem
  atualizar o manifesto) — `CHECKSUM_MISMATCH`.
- **Não garante autenticidade**: sem assinatura digital, quem altera um documento pode recalcular o manifesto. Por isso
  o leitor **não confia** no conteúdo por ter checksums corretos: todas as regras de estrutura, domínio e privacidade
  são aplicadas mesmo a um pacote com manifesto coerente (as fixtures inválidas são justamente pacotes com manifesto
  recalculado).
- O projeto não é apresentado como criptografado em nenhum lugar (`metadata.encrypted` é sempre `false`; `true` é
  recusado).
