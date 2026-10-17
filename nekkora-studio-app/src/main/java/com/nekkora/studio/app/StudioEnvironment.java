package com.nekkora.studio.app;

import com.nekkora.studio.app.project.RecentProjects;
import com.nekkora.studio.app.project.StudioDataDir;
import com.nekkora.studio.app.project.StudioSettings;
import com.nekkora.studio.persistence.ProjectFiles;
import com.nekkora.studio.persistence.ProjectRecovery;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Onde o Studio guarda os dados LOCAIS dele (STUDIO-006): configurações, projetos recentes e recuperação. Os projetos
 * em si ficam onde o usuário escolhe. Sem diretório de dados ({@link #inMemory()}, padrão dos testes), nada é gravado
 * fora dos arquivos de projeto e a recuperação automática fica indisponível — nenhum teste escreve na instalação pessoal.
 */
public final class StudioEnvironment {
    private final Path dataDir;
    private final com.nekkora.studio.persistence.ProjectRepository.FileOps fileOps;

    private StudioEnvironment(Path dataDir, com.nekkora.studio.persistence.ProjectRepository.FileOps fileOps) {
        this.dataDir = dataDir;
        this.fileOps = fileOps;
    }

    /** Operações de arquivo substituídas (testes: disco cheio, falha na substituição, gravação lenta). */
    public StudioEnvironment withFileOps(com.nekkora.studio.persistence.ProjectRepository.FileOps ops) {
        return new StudioEnvironment(dataDir, ops);
    }

    com.nekkora.studio.persistence.ProjectRepository.FileOps fileOps() {
        return fileOps;
    }

    /** Sem dados locais. */
    public static StudioEnvironment inMemory() {
        return new StudioEnvironment(null, com.nekkora.studio.persistence.ProjectRepository.FileOps.system());
    }

    /** Dados em {@code dir} (homologação e testes de recuperação usam um diretório isolado). */
    public static StudioEnvironment at(Path dir) {
        return new StudioEnvironment(dir.toAbsolutePath().normalize(), com.nekkora.studio.persistence.ProjectRepository.FileOps.system());
    }

    /** Diretório padrão da instalação ({@link StudioDataDir#resolve()}, que respeita {@code NEKKORA_STUDIO_DATA_DIR}). */
    public static StudioEnvironment installation() {
        return at(StudioDataDir.resolve());
    }

    public Optional<Path> dataDir() {
        return Optional.ofNullable(dataDir);
    }

    /** Propriedade de sistema que liga o modo de laboratório do debug guiado (decisões rotuladas como agente de teste). */
    public static final String DECISION_ACTOR_PROPERTY = "nekkora.studio.decisionActor";
    /** Variável de ambiente equivalente. */
    public static final String DECISION_ACTOR_ENV = "NEKKORA_STUDIO_DECISION_ACTOR";

    /**
     * STUDIO-009 (D-S009-06): quem as decisões do debug guiado registram. {@code TEST_AGENT} SÓ em modo de laboratório
     * explícito (propriedade {@value #DECISION_ACTOR_PROPERTY} ou variável {@value #DECISION_ACTOR_ENV} = {@code TEST_AGENT},
     * usadas pelos roteiros de homologação); qualquer outro valor ou nenhum: {@code DEVELOPER}. A interface mostra o ator.
     */
    public com.nekkora.studio.domain.target.DecisionActor decisionActor() {
        String v = System.getProperty(DECISION_ACTOR_PROPERTY, System.getenv(DECISION_ACTOR_ENV));
        return "TEST_AGENT".equals(v) ? com.nekkora.studio.domain.target.DecisionActor.TEST_AGENT : com.nekkora.studio.domain.target.DecisionActor.DEVELOPER;
    }

    RecentProjects recents() {
        return dataDir == null ? RecentProjects.inMemory() : RecentProjects.load(dataDir);
    }

    StudioSettings settings() {
        return dataDir == null ? StudioSettings.inMemory() : StudioSettings.load(dataDir);
    }

    ProjectRecovery recovery(ProjectFiles files) {
        return dataDir == null ? null : new ProjectRecovery(dataDir.resolve("recovery"), files);
    }
}
