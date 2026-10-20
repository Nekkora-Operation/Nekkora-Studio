package com.nekkora.studio.ai.deps;

/**
 * PACKAGING-001 — um diagnóstico ÚNICO e recuperável do ambiente Python da automação. Nenhum deles apaga PDD, projeto,
 * histórico ou código gerado; cada um diz o que fazer.
 */
public enum Diagnosis {
    READY("Ambiente pronto", "O ambiente da automação tem a versão exata do framework e as ferramentas de verificação."),
    NEEDS_INSTALL("Ambiente ainda não preparado", "Use \"Preparar ambiente\": o Studio cria a venv da automação e instala a versão exigida, com sua confirmação."),
    PYTHON_MISSING("Python 3.12+ não encontrado", "Instale o Python 3.12 (python.org) ou indique o interpretador em Opções avançadas; depois prepare o ambiente de novo."),
    UNSUPPORTED_VERSION("Versão do framework não homologada", "O projeto exige uma versão que este Studio não sabe instalar. Gere o código de novo ou atualize o Studio."),
    DEPENDENCY_MISSING("Dependência ausente", "O ambiente não tem um pacote exigido (framework, extra ou ferramenta). Prepare o ambiente de novo."),
    VERSION_MISMATCH("Versão diferente da exigida", "O Python indicado tem outra versão do nekkora-rpa. Prepare o ambiente da automação (venv própria) ou ajuste Opções avançadas."),
    UNSUPPORTED_CAPABILITY("Capacidade não confere com o catálogo", "O pacote instalado não tem as funções do catálogo da versão exigida. Prepare o ambiente de novo."),
    HASH_MISMATCH("Artefato com SHA-256 diferente do homologado", "Nada foi instalado. O arquivo ou o índice foi alterado: verifique a rede/espelho e tente de novo."),
    NETWORK_UNAVAILABLE("Sem acesso ao índice de pacotes", "Verifique a internet e tente de novo. O projeto, o PDD e o código continuam disponíveis offline."),
    PROXY_OR_SSL("Proxy ou certificado bloqueou o download", "Configure o proxy (HTTPS_PROXY) ou o certificado da rede e tente de novo."),
    DISK_FULL("Espaço em disco insuficiente", "Libere espaço (cerca de 600 MB) e tente de novo."),
    PATH_TOO_LONG("Caminho da pasta de dados longo demais para o Windows", "O pacote do navegador tem arquivos em pastas profundas e o Windows limita caminhos a 260 caracteres. Use uma pasta de dados mais curta (NEKKORA_STUDIO_DATA_DIR) ou habilite caminhos longos no Windows."),
    PERMISSION_DENIED("Sem permissão na pasta do ambiente", "A pasta de dados do Studio não pode ser gravada. Verifique as permissões e tente de novo."),
    CANCELLED("Preparação cancelada", "Nada ficou pela metade: o ambiente incompleto é refeito na próxima preparação."),
    TIMEOUT("A preparação demorou demais", "A rede pode estar lenta. Tente de novo."),
    ENV_CORRUPTED("Ambiente danificado", "A venv da automação está incompleta ou foi alterada. Prepare o ambiente de novo (ela é refeita)."),
    INSTALL_FAILED("A instalação falhou", "Veja o registro da instalação (sem valores) e tente de novo.");

    private final String title;
    private final String recovery;

    Diagnosis(String title, String recovery) {
        this.title = title;
        this.recovery = recovery;
    }

    public String title() {
        return title;
    }

    public String recovery() {
        return recovery;
    }
}
