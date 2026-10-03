package cn.aobp.wishboard;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import run.halo.app.extension.SchemeManager;
import run.halo.app.extension.index.IndexSpecs;
import run.halo.app.plugin.BasePlugin;
import run.halo.app.plugin.PluginContext;
import cn.aobp.wishboard.model.Wish;
import cn.aobp.wishboard.model.WishType;

import java.time.Instant;

@Slf4j
@Component
public class WishboardPlugin extends BasePlugin {

    private final SchemeManager schemeManager;

    public WishboardPlugin(PluginContext pluginContext, SchemeManager schemeManager) {
        super(pluginContext);
        this.schemeManager = schemeManager;
    }

    @Override
    public void start() {
        schemeManager.register(Wish.class, indexes -> {
            indexes.add(IndexSpecs.<Wish, String>single("spec.status", String.class)
                .indexFunc(wish -> wish.getSpec() == null ? null : wish.getSpec().getStatus()));
            indexes.add(IndexSpecs.<Wish, String>single("spec.type", String.class)
                .indexFunc(wish -> wish.getSpec() == null ? null : wish.getSpec().getType()));
            indexes.add(IndexSpecs.<Wish, Instant>single("spec.createdAt", Instant.class)
                .indexFunc(wish -> wish.getSpec() == null ? null : wish.getSpec().getCreatedAt()));
        });
        schemeManager.register(WishType.class);

    }

    @Override
    public void stop() {
        schemeManager.unregister(schemeManager.get(Wish.class));
        schemeManager.unregister(schemeManager.get(WishType.class));
        log.info("[Wishboard] Plugin stopped");
    }
}
