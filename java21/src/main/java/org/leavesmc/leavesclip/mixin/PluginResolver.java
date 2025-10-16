package org.leavesmc.leavesclip.mixin;

import com.google.gson.Gson;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.leavesmc.leavesclip.logger.Logger;
import org.leavesmc.leavesclip.logger.SimpleLogger;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;

public class PluginResolver {
    public static final String PLUGIN_DIRECTORY = "plugins";
    public static final String MIXINS_DIRECTORY = PLUGIN_DIRECTORY + File.separator + ".mixins";
    public static final String PLUGIN_JAR_HASH_FILE = "plugin-jar-hash";
    public static final String LEAVES_PLUGIN_JSON_FILE = "leaves-plugin.json";
    public static final String MIXINS_JAR_SUFFIX = ".mixins.jar";
    public static List<LeavesPluginMeta> leavesPluginMetas = new ArrayList<>();
    private static final Logger logger = new SimpleLogger("Mixin");
    private static final Gson gson = new Gson();

    public static void extractMixins() {
        File pluginsDir = new File(PLUGIN_DIRECTORY);
        if (!ensurePluginsDir(pluginsDir)) return;

        File mixinsDir = new File(MIXINS_DIRECTORY);
        if (!ensureMixinsDir(mixinsDir)) return;

        processPlugins(pluginsDir);
        cleanOutdatedMixinJars(mixinsDir);
    }

    private static void processPlugins(@NotNull File pluginsDir) {
        File[] jarFiles = pluginsDir.listFiles((dir, name) -> name.toLowerCase().endsWith(".jar"));
        if (jarFiles == null || jarFiles.length == 0) return;

        //noinspection DataFlowIssue
        leavesPluginMetas = Arrays.stream(jarFiles)
            .parallel()
            .map(PluginResolver::withJarFile)
            .filter(PluginResolver::notNull)
            .map(PluginResolver::withPluginMeta)
            .filter(PluginResolver::notNull)
            .filter(distinctBy(
                entry -> entry.third().getName(),
                entry -> logger.warn(
                    "The plugin '{}' has duplicate name with another plugin, its mixin will not load. path: '{}'",
                    entry.third().getName(),
                    entry.first().getAbsolutePath()
                )
            ))
            .map(PluginResolver::extractMixinJarAndToPluginMeta)
            .filter(PluginResolver::notNull)
            .toList();
    }

    private static void cleanOutdatedMixinJars(@NotNull File mixinsDir) {
        File[] files = mixinsDir.listFiles((dir, name) -> name.toLowerCase().endsWith(".jar"));
        if (files == null || files.length == 0) return;
        //noinspection ResultOfMethodCallIgnored
        Arrays.stream(files)
            .parallel()
            .filter(PluginResolver::isOutdatedMixinJar)
            .forEach(File::delete);
    }

    private static boolean ensurePluginsDir(@NotNull File pluginsDir) {
        if (pluginsDir.exists() && !pluginsDir.isDirectory()) {
            logger.warn("'{}' is not a directory", pluginsDir.getAbsolutePath());
            return false;
        }
        return true;
    }

    private static boolean ensureMixinsDir(@NotNull File mixinsDir) {
        if (mixinsDir.exists() && !mixinsDir.isDirectory()) {
            logger.warn("'{}' is not a directory", mixinsDir.getAbsolutePath());
            return false;
        }
        if (mixinsDir.exists()) return true;
        if (!mixinsDir.mkdirs()) {
            logger.warn("Failed to create mixins directory '{}'", mixinsDir.getAbsolutePath());
            return false;
        }
        return true;
    }

    private static <T> boolean notNull(@Nullable T entry) {
        return entry != null;
    }

    private static boolean isOutdatedMixinJar(@NotNull File jar) {
        return leavesPluginMetas.stream()
            .map(LeavesPluginMeta::getMixinJarFile)
            .noneMatch(jar::equals);
    }

    @Contract(pure = true)
    private static <T> @NotNull Predicate<T> distinctBy(
        Function<? super T, ?> keyExtractor,
        Consumer<? super T> duplicateHandler
    ) {
        Set<Object> seen = ConcurrentHashMap.newKeySet();
        return t -> {
            Object key = keyExtractor.apply(t);
            boolean isNew = seen.add(key);
            if (!isNew && duplicateHandler != null) {
                duplicateHandler.accept(t);
            }
            return isNew;
        };
    }

    private static @Nullable Tuple2<File, JarFile> withJarFile(File file) {
        try {
            return new Tuple2<>(file, new JarFile(file));
        } catch (IOException e) {
            logger.warn("Failed to open jar file '{}'", file.getAbsolutePath());
            return null;
        }
    }

    private static @Nullable Tuple3<File, JarFile, LeavesPluginMeta> withPluginMeta(@NotNull Tuple2<File, JarFile> entry) {
        JarFile jarFile = entry.second();
        LeavesPluginMeta pluginMeta = getPluginMeta(jarFile);

        if (pluginMeta != null) return entry.plus(pluginMeta);

        try {
            entry.second().close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        return null;
    }

    private static @Nullable LeavesPluginMeta getPluginMeta(@NotNull JarFile jarFile) {
        JarEntry entry = jarFile.getJarEntry(LEAVES_PLUGIN_JSON_FILE);
        if (entry == null) {
            return null;
        }
        try (InputStream inputStream = jarFile.getInputStream(entry)) {
            String jsonString = new String(inputStream.readAllBytes());
            return gson.fromJson(jsonString, LeavesPluginMeta.class);
        } catch (IOException e) {
            logger.warn("Failed to read plugin meta from jar file '{}'", jarFile.getName());
            return null;
        }
    }

    private static @Nullable LeavesPluginMeta extractMixinJarAndToPluginMeta(
        @NotNull Tuple3<File, JarFile, LeavesPluginMeta> entry
    ) {
        File pluginFile = entry.first();
        JarFile jarFile = entry.second();
        LeavesPluginMeta pluginMeta = entry.third();

        if (pluginMeta.getMixin() == null) {
            return null;
        }

        File mixinJarFile = pluginMeta.getMixinJarFile();

        String pluginJarHash = calcMd5(pluginFile);
        if (mixinJarFile.isDirectory()) throw new IllegalStateException(
            "Plugin mixin jar file is a directory. Please delete this: " + mixinJarFile.getAbsolutePath()
        );
        if (mixinJarFile.exists()) {
            String loggedPluginJarHash = getPluginJarHashInMixinJar(mixinJarFile);
            if (pluginJarHash.equals(loggedPluginJarHash)) {
                return pluginMeta;
            }
            deleteAndCreateNewFile(mixinJarFile);
        }

        if (extractMixinJar(
            jarFile,
            pluginJarHash,
            mixinJarFile,
            pluginMeta
        )) {
            return pluginMeta;
        } else {
            return null;
        }
    }

    private static boolean extractMixinJar(
        @NotNull JarFile pluginJar,
        @NotNull String pluginJarHash,
        @NotNull File jarFile,
        @NotNull LeavesPluginMeta pluginMeta
    ) {
        LeavesPluginMeta.MixinConfig mixin = pluginMeta.getMixin();
        try (JarOutputStream outputStream = new JarOutputStream(new FileOutputStream(jarFile))) {
            String packagePrefix = mixin.getPackageName().replace(".", "/");
            Set<String> metaFiles = new HashSet<>();
            if (mixin.getAccessWidener() != null) {
                metaFiles.add(mixin.getAccessWidener());
            }
            if (mixin.getMixins() != null) {
                metaFiles.addAll(mixin.getMixins());
            }
            pluginJar.entries().asIterator().forEachRemaining(entry -> {
                String name = entry.getName();
                if (name.startsWith(packagePrefix) || metaFiles.contains(name)) {
                    try {
                        outputStream.putNextEntry(new JarEntry(entry.getName()));
                        pluginJar.getInputStream(entry).transferTo(outputStream);
                        outputStream.closeEntry();
                    } catch (IOException e) {
                        logger.warn("Failed to extract mixin jar file '{}'", jarFile.getAbsolutePath());
                        throw new RuntimeException(e);
                    }
                }
            });
            JarEntry hashFileEntry = new JarEntry(PLUGIN_JAR_HASH_FILE);
            outputStream.putNextEntry(hashFileEntry);
            outputStream.write(pluginJarHash.getBytes(StandardCharsets.UTF_8));
            outputStream.closeEntry();
        } catch (IOException e) {
            logger.warn("Failed to write mixin jar file '{}'", jarFile.getAbsolutePath());
            return false;
        }
        return true;
    }

    private static @Nullable String getPluginJarHashInMixinJar(File mixinJar) {
        try (JarFile jarFile = new JarFile(mixinJar)) {
            JarEntry entry = jarFile.getJarEntry(PLUGIN_JAR_HASH_FILE);
            if (entry == null) {
                logger.warn("Mixin jar '{}' does not contain plugin jar hash", mixinJar.getAbsolutePath());
                return null;
            }
            try (InputStream inputStream = jarFile.getInputStream(entry)) {
                return new String(inputStream.readAllBytes()).trim();
            }
        } catch (IOException e) {
            logger.warn("Failed to read plugin jar hash from mixin jar '{}'", mixinJar.getAbsolutePath());
            return null;
        }
    }

    private static void deleteAndCreateNewFile(@NotNull File file) {
        if (file.exists()) {
            if (!file.delete()) {
                throw new IllegalStateException(
                    "Failed to delete file '" + file.getAbsolutePath() + "'"
                );
            }
        }
        try {
            if (!file.createNewFile()) {
                throw new IllegalStateException(
                    "Failed to create new file '" + file.getAbsolutePath() + "'"
                );
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static @NotNull String calcMd5(File file) {
        MessageDigest md;
        try {
            md = MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
        try (InputStream is = Files.newInputStream(file.toPath());
             DigestInputStream dis = new DigestInputStream(is, md)) {
            byte[] buffer = new byte[8192];
            //noinspection StatementWithEmptyBody
            while (dis.read(buffer) != -1) ;
        } catch (IOException e) {
            logger.warn("Failed to read file '{}'", file.getAbsolutePath());
            return "";
        }
        StringBuilder sb = new StringBuilder(32);
        for (byte b : md.digest()) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
