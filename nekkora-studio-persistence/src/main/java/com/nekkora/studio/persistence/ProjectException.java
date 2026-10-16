package com.nekkora.studio.persistence;

import java.util.Objects;

/**
 * Falha de leitura ou gravação de um projeto. {@link #error()} é um código estável (para logs, testes e a interface);
 * {@link #where()} indica a parte do arquivo (ex.: {@code steps/…json: effective.preferredLocator}) — nunca o conteúdo
 * do projeto nem o caminho completo do arquivo.
 */
public final class ProjectException extends Exception {

    /** Códigos de erro. A mensagem é segura para o usuário (sem conteúdo do projeto). */
    public enum Error {
        NOT_FOUND("O arquivo não foi encontrado."),
        ACCESS_DENIED("Sem permissão para acessar o arquivo ou a pasta."),
        READ_ONLY("O arquivo ou a pasta é somente leitura."),
        IN_USE("O arquivo está em uso por outro programa."),
        DIRECTORY_MISSING("A pasta de destino não existe."),
        NOT_A_PROJECT("O arquivo não é um projeto do Nekkora Studio."),
        CORRUPTED("O arquivo está danificado ou incompleto."),
        CHECKSUM_MISMATCH("O conteúdo do projeto não confere com o manifesto (arquivo danificado ou alterado)."),
        UNSAFE_ENTRY("O arquivo contém uma entrada não permitida."),
        DUPLICATE_ENTRY("O arquivo contém entradas repetidas."),
        MISSING_ENTRY("O arquivo não contém todas as partes do projeto."),
        UNEXPECTED_ENTRY("O arquivo contém partes que não pertencem ao projeto."),
        TOO_LARGE("O projeto excede os limites de tamanho suportados."),
        INVALID_JSON("Uma parte do projeto não é um documento válido."),
        INVALID_CONTENT("O projeto contém dados inválidos."),
        INTEGRITY("As referências internas do projeto não conferem."),
        NEWER_VERSION("O projeto foi criado por uma versão mais recente do Nekkora Studio."),
        UNSUPPORTED_VERSION("A versão do projeto não é suportada por esta versão do Nekkora Studio."),
        PRIVACY_POLICY_UNSUPPORTED("O projeto declara uma política de privacidade que esta versão não reconhece."),
        PRIVACY_VIOLATION("O projeto contém dados capturados fora da política de privacidade nekkora-privacy/2."),
        DISK_FULL("Não há espaço suficiente em disco."),
        WRITE_FAILED("Não foi possível gravar o projeto."),
        REPLACE_FAILED("Não foi possível substituir o arquivo anterior; ele foi preservado."),
        CONFLICT("O arquivo foi alterado fora do Studio desde a última leitura ou gravação."),
        WRONG_EXTENSION("O nome do arquivo precisa terminar em .nekkora.");

        private final String message;

        Error(String message) {
            this.message = message;
        }

        public String message() {
            return message;
        }
    }

    private final Error error;
    private final String where;

    public ProjectException(Error error, String where) {
        this(error, where, null);
    }

    public ProjectException(Error error, String where, Throwable cause) {
        super(error.name() + (where == null || where.isEmpty() ? "" : " (" + where + ")"), cause);
        this.error = Objects.requireNonNull(error, "error");
        this.where = where == null ? "" : where;
    }

    public Error error() {
        return error;
    }

    /** Parte do projeto afetada (sem conteúdo). */
    public String where() {
        return where;
    }
}
