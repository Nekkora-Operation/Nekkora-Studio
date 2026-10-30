import com.nekkora.studio.ai.capability.FrameworkCapabilityCatalog;
import com.nekkora.studio.ai.generation.GenerationOrchestrator;
import com.nekkora.studio.ai.spec.FunctionalSpec;
import com.nekkora.studio.ai.store.DevelopmentStore;
import com.nekkora.studio.domain.Automation;
import com.nekkora.studio.pdd.PddStore;
import com.nekkora.studio.persistence.ProjectFiles;
import com.nekkora.studio.persistence.ProjectRepository;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * STUDIO-INTEGRATION-CONSOLIDATION-001 (S5, passos 9–10): o projeto .nekkora salvo pelo Studio candidato na homologação é
 * REABERTO num processo novo (persistência real) com o diretório de dados do Studio; a especificação (decisões, inclusive a
 * coluna opcional) e o PDD voltam; e o pipeline de geração, a partir do projeto reaberto, produz os MESMOS arquivos que estão
 * no pacote gerado pela interface (consistência depois de fechar e reabrir). Nada é gravado (só prepare, sem write).
 * Uso: java -cp <cp> Reabrir.java <projeto.nekkora> <diretório de dados do Studio> <pasta do pacote gerado>
 */
public class Reabrir {
    public static void main(String[] a) throws Exception {
        List<String> log = new ArrayList<>();
        int fails = 0;
        Path project = Path.of(a[0]);
        ProjectFiles files = new ProjectFiles(new ProjectRepository(ProjectRepository.FileOps.system()), "1.0.0-SNAPSHOT");
        ProjectFiles.Loaded loaded = files.load(project, Clock.systemUTC());
        Automation auto = loaded.automation();
        int steps = auto.sessions().stream().mapToInt(s -> s.steps().size()).sum();
        String manifest = Files.readString(Path.of(a[2]).resolve("manifest.json"), StandardCharsets.UTF_8);
        boolean sameId = manifest.contains("\"automationId\": \"" + auto.id().value() + "\"");
        log.add((sameId ? "OK    " : "FALHA ") + "projeto reaberto: \"" + auto.name() + "\", " + auto.sessions().size() + " sessões, " + steps
                + " etapas; mesmo automationId do pacote");
        if (!sameId) fails++;
        DevelopmentStore store = new DevelopmentStore(Path.of(a[1]));
        FunctionalSpec spec = store.spec(auto.id());
        long optional = spec.decisions().stream().filter(d -> d.field() == FunctionalSpec.Field.OUTPUT_REQUIREMENT && d.value().startsWith("OPTIONAL")).count();
        log.add((optional >= 1 ? "OK    " : "FALHA ") + "especificação reaberta: " + spec.decisions().size() + " decisões, " + optional
                + " coluna(s) opcional(is) preservada(s)");
        if (optional < 1) fails++;
        var draft = new PddStore(Path.of(a[1])).loadDraft(auto.id());
        log.add((draft.isPresent() ? "OK    " : "FALHA ") + "PDD revisado reaberto");
        if (draft.isEmpty()) fails++;
        Path tmp = Files.createTempDirectory("reabrir-");
        GenerationOrchestrator.Run run = GenerationOrchestrator.prepare(auto, draft, spec, FrameworkCapabilityCatalog.bundled(), Optional.empty(),
                store.options(auto.id()).origins(), tmp, java.time.Instant.now(), p -> {}, () -> false, store.traceRegistry(auto.id()));
        boolean gen = run.files() != null && run.blockers().isEmpty();
        log.add((gen ? "OK    " : "FALHA ") + "geração a partir do projeto reaberto sem bloqueio " + run.blockers());
        if (!gen) fails++;
        if (gen) {
            List<String> diff = new ArrayList<>();
            for (Map.Entry<String, String> e : run.files().entrySet()) {
                Path f = Path.of(a[2]).resolve(e.getKey());
                if (!Files.exists(f) || !Files.readString(f, StandardCharsets.UTF_8).equals(e.getValue())) diff.add(e.getKey());
            }
            log.add((diff.isEmpty() ? "OK    " : "FALHA ") + run.files().size() + " arquivos regerados idênticos ao pacote da interface " + diff);
            if (!diff.isEmpty()) fails++;
        }
        log.forEach(System.out::println);
        System.out.println("RESULTADO: " + log.size() + " verificações, " + fails + " falha(s)");
    }
}
