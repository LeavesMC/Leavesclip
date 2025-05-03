package org.leavesmc.leavesclip.mixin;

import org.jetbrains.annotations.Nullable;
import org.leavesmc.leavesclip.logger.Logger;
import org.leavesmc.leavesclip.logger.SimpleLogger;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;

public class MixinJarResolver {
    private static final Logger logger = new SimpleLogger("Mixin");
    public static List<String> mixinConfigs = Collections.emptyList();
    public static List<String> accessWidenerConfigs = Collections.emptyList();
    public static URL[] jarUrls = new URL[]{};

    public static void resolveMixinJars() {
        if (PluginResolver.leavesPluginMetas.isEmpty()) return;

        URL[] urls = getMixinJarUrls();
        if (urls == null) return;
        logger.info(Arrays.toString(urls));
        jarUrls = urls;

        resolveMixinConfigs();
        resolveAccessWidenerConfigs();
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
        mixinConfigs = PluginResolver.leavesPluginMetas.stream()
            .map(LeavesPluginMeta::getMixin)
            .map(LeavesPluginMeta.MixinConfig::getMixins)
            .flatMap(List::stream)
            .toList();
    }

    private static void resolveAccessWidenerConfigs() {
        accessWidenerConfigs = PluginResolver.leavesPluginMetas.stream()
            .map(LeavesPluginMeta::getMixin)
            .map(LeavesPluginMeta.MixinConfig::getAccessWidener)
            .toList();
    }
}
