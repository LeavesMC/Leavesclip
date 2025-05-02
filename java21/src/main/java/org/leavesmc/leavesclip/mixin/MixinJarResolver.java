package org.leavesmc.leavesclip.mixin;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.leavesmc.leavesclip.logger.Logger;
import org.leavesmc.leavesclip.logger.SimpleLogger;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import static org.leavesmc.leavesclip.mixin.PluginMixinExtractor.MIXINS_DIRECTORY;

public class MixinJarResolver {
    private static final Logger logger = new SimpleLogger("Mixin");
    public static List<String> mixinConfigs = Collections.emptyList();
    public static List<String> accessWidenerConfigs = Collections.emptyList();
    public static URL[] jarUrls = new URL[]{};

    public static void resolveMixinJars() {
        File mixinsDir = validateAndGetMixinsDirectory();
        if (mixinsDir == null) {
            return;
        }
        File[] jarFiles = findJarFilesInDirectory(mixinsDir);
        if (isEmpty(jarFiles)) {
            return;
        }
        URL[] urls = convertJarFilesToUrls(jarFiles);
        if (urls == null) {
            logger.error("Failed to convert Jar file paths.");
            return;
        }
        jarUrls = urls;
        MixinAndAccessWidener result = findMixinAndAccessWidenerInJars(jarFiles);
        mixinConfigs = result.mixinConfigs;
        accessWidenerConfigs = result.accessWidenerConfigs;
    }

    private static @Nullable File validateAndGetMixinsDirectory() {
        File mixinsDir = new File(MIXINS_DIRECTORY);
        if (!mixinsDir.exists()) {
            return null;
        } else if (!mixinsDir.isDirectory()) {
            logger.error("'" + mixinsDir + "'' exists but is not a directory");
            return null;
        }
        return mixinsDir;
    }

    private static File[] findJarFilesInDirectory(@NotNull File directory) {
        return directory.listFiles((dir, name) -> name.toLowerCase().endsWith(".mixins.jar"));
    }

    private static URL @Nullable [] convertJarFilesToUrls(File[] jarFiles) {
        try {
            return Arrays.stream(jarFiles)
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
            logger.error("Error converting jar files to URLs", e);
            return null;
        }
    }

    @Contract("_ -> new")
    private static @NotNull MixinAndAccessWidener findMixinAndAccessWidenerInJars(File @NotNull [] jarFiles) {
        List<String> mixins = new ArrayList<>();
        List<String> accessWideners = new ArrayList<>();

        for (File jarFile : jarFiles) {
            String jarFileName = jarFile.getName();
            findMixinAndAccessWidenerInJar(jarFile, mixins, accessWideners, jarFileName);
        }

        return new MixinAndAccessWidener(mixins, accessWideners);
    }

    private static void findMixinAndAccessWidenerInJar(
        File jarFile,
        List<String> mixins,
        List<String> accessWideners,
        String jarFileName
    ) {
        try (JarFile jar = new JarFile(jarFile)) {
            jar.stream()
                .filter(MixinJarResolver::isValidJarDir)
                .forEach(entry -> {
                    String name = entry.getName();
                    if (isMixinJson(name)) {
                        mixins.add(name);
                    } else if (name.endsWith(".accesswidener")) {
                        accessWideners.add(name);
                    }
                });
        } catch (IOException e) {
            logger.warn(e, "Error processing Jar File: {}", jarFileName);
        }
    }

    private static boolean isEmpty(File[] jarFiles) {
        return jarFiles == null || jarFiles.length == 0;
    }

    private static boolean isValidJarDir(@NotNull JarEntry entry) {
        String name = entry.getName();
        return !entry.isDirectory() && !name.contains("/");
    }

    @Contract(pure = true)
    private static boolean isMixinJson(@NotNull String name) {
        return name.endsWith(".mixins.json");
    }

    private static class MixinAndAccessWidener {
        public List<String> mixinConfigs;
        public List<String> accessWidenerConfigs;

        public MixinAndAccessWidener(List<String> mixinConfigs, List<String> accessWidenerConfigs) {
            this.mixinConfigs = mixinConfigs;
            this.accessWidenerConfigs = accessWidenerConfigs;
        }
    }
}
