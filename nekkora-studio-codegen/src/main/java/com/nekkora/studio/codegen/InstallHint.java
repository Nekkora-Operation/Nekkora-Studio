package com.nekkora.studio.codegen;

/**
 * PACKAGING-001 — instruções de instalação nos arquivos gerados: a linha de REQUISITO continua a versão exata do contrato
 * do gerador (0.2.0 nos projetos legados, {@code [excel]==0.3.0} na lista → planilha); o caminho principal deixa de ser
 * "o wheel local do checkout" e passa a ser o ambiente preparado pelo Studio (ou o pip, quando houver índice aprovado).
 */
final class InstallHint {
    private InstallHint() {}

    /** {@code requirements.txt}: comentário de origem + como instalar + a linha de requisito exata. */
    static String requirementsTxt(String mission, String requirement) {
        return "# GERADO pelo Nekkora Studio (" + mission + "). O Studio prepara o ambiente desta automação (venv própria, versão exata,\n"
                + "# dependências conferidas por hash): Desenvolver automação → Pendências → Preparar ambiente.\n"
                + "# Fora do Studio, num Python 3.12+ limpo: python -m pip install \"" + requirement + "\" (do índice aprovado, depois da publicação).\n"
                + requirement + "\n";
    }

    /** Bloco do README: as duas formas, sem caminho de checkout. */
    static String readmeBlock(String requirement, String run) {
        return "O Nekkora Studio prepara o ambiente desta automação: **Desenvolver automação → Pendências → Preparar ambiente** "
                + "(venv própria, `" + requirement + "`, dependências conferidas por hash, confirmação antes de instalar).\n\n"
                + "Fora do Studio (Python 3.12+, depois da publicação no índice aprovado):\n\n"
                + "```sh\npython -m venv .venv\n.venv/Scripts/python -m pip install \"" + requirement + "\"\n.venv/Scripts/" + run + "\n```\n\n";
    }
}
