package gg.fotia.mythictools.gui.summoning;

import gg.fotia.mythictools.MythicToolsPlugin;
import gg.fotia.mythictools.config.ConfigIoService;
import gg.fotia.mythictools.config.YamlFiles;
import gg.fotia.mythictools.summoning.SummonService;
import gg.fotia.mythictools.summoning.config.SummonRepository;
import java.util.concurrent.CompletionStage;

/** 复用串行配置队列、候选校验和原子写入；草稿冲突时不覆盖文件。 */
final class SummonDraftStore {
    private final MythicToolsPlugin plugin;
    private final SummonService service;
    SummonDraftStore(MythicToolsPlugin plugin, SummonService service) { this.plugin = plugin; this.service = service; }

    CompletionStage<gg.fotia.mythictools.config.ConfigFileSnapshot> save(SummonDraft draft) {
        var repository = service.repository();
        var file = repository.file(draft.effect, draft.id);
        String contents = draft.yaml.saveToString();
        return plugin.configurationIo().save(file, current -> {
            String disk;
            try { disk = SummonRepository.yaml(current.read(file)).saveToString(); }
            catch (java.nio.file.NoSuchFileException missing) { disk = null; }
            catch (java.io.IOException error) { throw new IllegalStateException(error); }
            boolean replacingDeleted = draft.baseline == null && disk != null && SummonRepository.yaml(disk).getBoolean("deleted");
            if (!replacingDeleted && !java.util.Objects.equals(disk, draft.baseline)) throw new IllegalArgumentException("common.config-conflict");
            var candidate = YamlFiles.using(current.with(file, contents), repository::prepare);
            if (draft.effect && draft.yaml.getBoolean("deleted") && !repository.references(draft.id).isEmpty()) {
                throw new IllegalArgumentException("summon.error.referenced");
            }
            if (!draft.effect) {
                var definition = candidate.items().get(draft.id);
                if (definition != null && definition.enabled()) service.validate(definition);
            }
            return new ConfigIoService.PreparedSave(contents, () -> repository.publish(candidate));
        });
    }
}
