package org.leavesmc.leavesclip.mixin;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.leavesmc.leavesclip.logger.Logger;
import org.leavesmc.leavesclip.logger.SimpleLogger;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;

public class MixinJarResolver {
    private static final Logger logger = new SimpleLogger("Mixin");
    private static final Map<String, String> mixinConfig2PluginId = new HashMap<>();
    public static List<String> mixinConfigs = Collections.emptyList();
    public static List<String> accessWidenerConfigs = Collections.emptyList();
    public static URL[] jarUrls = new URL[]{};

    public static void resolveMixinJars() {
        if (PluginResolver.leavesPluginMetas.isEmpty()) return;

        URL[] urls = getMixinJarUrls();
        if (urls == null) return;
        jarUrls = urls;

        resolveMixinConfigs();
        resolveAccessWidenerConfigs();
    }

    public static @Nullable String getPluginId(@NotNull String mixinConfig) {
        return mixinConfig2PluginId.get(mixinConfig);
    }

    private static URL @Nullable [] getMixinJarUrls() {
        try {
            return PluginResolver.leavesPluginMetas.stream()
                .map(LeavesPluginMeta::getMixinJarFile)
                .map(file -> {
                    try {
                        return file.toURI().toURL();
                    } catch (MalformedURLException e) {
                        logger.error("Failed to convert Jar file path: " + file.getName(), e);
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .toArray(URL[]::new);
        } catch (Exception e) {
            logger.error("Error getting mixin jar URLs", e);
            return null;
        }
    }

    private static void resolveMixinConfigs() {
        var pluginMetaWithMixins = PluginResolver.leavesPluginMetas.stream()
            .map(MixinJarResolver::withMixins)
            .toList();
        mixinConfigs = pluginMetaWithMixins.stream()
            .map(Tuple2::second)
            .flatMap(List::stream)
            .toList();
        pluginMetaWithMixins.forEach(entry -> {
            String id = entry.first().getName();
            entry.second().forEach(mixins -> mixinConfig2PluginId.put(mixins, id));
        });
    }

    @Contract("_ -> new")
    private static @NotNull Tuple2<LeavesPluginMeta, List<String>> withMixins(@NotNull LeavesPluginMeta meta) {
        LeavesPluginMeta.MixinConfig mixinConfig = meta.getMixin();
        List<String> mixins = mixinConfig == null ? List.of() : mixinConfig.getMixins();
        return new Tuple2<>(meta, mixins);
    }

    private static void resolveAccessWidenerConfigs() {
        accessWidenerConfigs = PluginResolver.leavesPluginMetas.stream()
            .map(LeavesPluginMeta::getMixin)
            .map(LeavesPluginMeta.MixinConfig::getAccessWidener)
            .filter(Objects::nonNull)
            .toList();
    }
}
