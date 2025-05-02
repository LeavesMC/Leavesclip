package org.leavesmc.leavesclip.mixin;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.leavesmc.leavesclip.logger.Logger;
import org.leavesmc.leavesclip.logger.SimpleLogger;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public class PluginMixinExtractor {
    public static final String PLUGIN_DIRECTORY = "plugins";
    public static final String MIXINS_DIRECTORY = PLUGIN_DIRECTORY + File.separator + ".mixins";
    public static final String MIXINS_JAR_SUFFIX = ".mixins.jar";
    private static final Logger logger = new SimpleLogger("Mixin");

    public static void extractMixinJars() {
        File pluginsDir = new File(PLUGIN_DIRECTORY);
        if (!pluginsDir.exists() || !pluginsDir.isDirectory()) {
            return;
        }

        File mixinsDir = new File(MIXINS_DIRECTORY);
        if (!ensureMixinsDir(mixinsDir)) {
            return;
        }

        File[] jarFiles = pluginsDir.listFiles((dir, name) -> name.toLowerCase().endsWith(".jar"));
        if (jarFiles == null || jarFiles.length == 0) {
            return;
        }

        Set<String> validMixinJarFileNames = collectValidMixinJarNames(jarFiles);

        cleanOutdatedMixinJars(mixinsDir, validMixinJarFileNames);
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

    private static @NotNull Set<String> collectValidMixinJarNames(File @NotNull [] jarFiles) {
        Set<String> validMixinJarFileNames = new HashSet<>();
        for (File jarFile : jarFiles) {
            validMixinJarFileNames.addAll(processJarFile(jarFile));
        }
        return validMixinJarFileNames;
    }

    private static void cleanOutdatedMixinJars(@NotNull File mixinsDir, Set<String> validMixinJarFileNames) {
        File[] mixinJarDirFiles = mixinsDir.listFiles((dir, name) -> name.endsWith(MIXINS_JAR_SUFFIX));
        if (mixinJarDirFiles == null) return;
        Arrays.stream(mixinJarDirFiles)
            .filter(mixinJarDirFile -> isInvalidMixinJar(validMixinJarFileNames, mixinJarDirFile))
            .forEach(mixinJarDirFile -> {
                logger.debug("Deleting outdated mixin jar '{}'", mixinJarDirFile.getAbsolutePath());
                if (!mixinJarDirFile.delete()) {
                    logger.warn("Failed to delete outdated mixin jar '{}'", mixinJarDirFile.getAbsolutePath());
                }
            });
    }

    private static boolean isInvalidMixinJar(Set<String> validMixinJarFileNames, File mixinJarDirFile) {
        return !validMixinJarFileNames.contains(mixinJarDirFile.getName());
    }

    private static @NotNull Set<String> processJarFile(@NotNull File jarFile) {
        String jarFileName = jarFile.getName();
        Set<String> mixinJarFileNames = new HashSet<>();
        try (JarFile jar = new JarFile(jarFile)) {
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String entryName = entry.getName();

                if (isNotTopLevelMixinJar(entryName)) {
                    continue;
                }

                if (handleMixinJarEntry(jar, entry, jarFileName, entryName)) {
                    mixinJarFileNames.add(entryName);
                }
            }
        } catch (Exception e) {
            logger.warn(e, "Failed to extract mixin jars in '{}': {}", jarFileName, e.getMessage());
        }
        return mixinJarFileNames;
    }

    private static boolean isNotTopLevelMixinJar(@NotNull String entryName) {
        return entryName.indexOf('/') >= 0 || !entryName.endsWith(MIXINS_JAR_SUFFIX);
    }

    private static boolean handleMixinJarEntry(@NotNull JarFile jar, JarEntry entry, String jarFileName, String entryName) throws IOException, NoSuchAlgorithmException {
        File mixinJarInMixinsDir = new File(MIXINS_DIRECTORY, entryName);
        ensureParentDirExists(mixinJarInMixinsDir);

        String expectedMd5 = readExpectedMd5(jar, entryName, jarFileName);
        if (expectedMd5 == null) {
            return false;
        }

        boolean shouldExtract = shouldExtractMixinJar(mixinJarInMixinsDir, expectedMd5, entryName, jarFileName);

        if (shouldExtract) {
            extractMixinJar(jar, entry, mixinJarInMixinsDir, entryName, jarFileName);
        }
        return true;
    }

    private static void ensureParentDirExists(@NotNull File file) {
        File parent = file.getParentFile();
        if (parent != null && !parent.mkdirs()) {
            logger.warn("Failed to create directory '{}'", parent.getAbsolutePath());
        }
    }

    private static @Nullable String readExpectedMd5(@NotNull JarFile jar, String entryName, String jarFileName) {
        String md5EntryName = "META-INF/" + entryName + ".md5";
        JarEntry md5Entry = jar.getJarEntry(md5EntryName);

        if (md5Entry == null) {
            logger.warn("Mixin jar '{}' in '{}' does not contain md5", entryName, jarFileName);
            return null;
        }

        try (InputStream is = jar.getInputStream(md5Entry);
             BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            return reader.readLine().trim();
        } catch (IOException e) {
            logger.warn(e, "Failed to read md5 for mixin jar '{}' in '{}'", entryName, jarFileName);
            return null;
        }
    }

    private static boolean shouldExtractMixinJar(
        @NotNull File mixinJarInMixinsDir,
        String expectedMd5,
        String entryName,
        String jarFileName
    ) throws IOException, NoSuchAlgorithmException {
        if (!mixinJarInMixinsDir.exists()) return true;
        String actualMd5 = calculateMd5(mixinJarInMixinsDir);
        if (expectedMd5.equalsIgnoreCase(actualMd5)) {
            logger.debug("Skip extracting mixin jar '{}' from '{}'", entryName, jarFileName);
            return false;
        } else {
            logger.debug("MD5 mismatch, will extract mixin jar '{}' from '{}'", entryName, jarFileName);
        }
        return true;
    }

    private static void extractMixinJar(
        @NotNull JarFile jar,
        JarEntry entry,
        @NotNull File targetFile,
        String entryName,
        String jarFileName
    ) throws IOException {
        Path targetPath = targetFile.toPath();
        try (InputStream is = jar.getInputStream(entry)) {
            Files.copy(is, targetPath, StandardCopyOption.REPLACE_EXISTING);
            logger.debug("Extracted mixin jar '{}' from '{}' to '{}'", entryName, jarFileName, targetPath);
        }
    }

    private static @NotNull String calculateMd5(File file) throws IOException, NoSuchAlgorithmException {
        MessageDigest md = MessageDigest.getInstance("MD5");
        try (InputStream is = Files.newInputStream(file.toPath());
             DigestInputStream dis = new DigestInputStream(is, md)) {
            byte[] buffer = new byte[8192];
            while (dis.read(buffer) != -1) ;
        }
        StringBuilder sb = new StringBuilder(32);
        for (byte b : md.digest()) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
