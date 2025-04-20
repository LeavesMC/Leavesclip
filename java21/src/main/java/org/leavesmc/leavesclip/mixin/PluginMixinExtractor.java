package org.leavesmc.leavesclip.mixin;

import org.leavesmc.leavesclip.logger.Logger;
import org.leavesmc.leavesclip.logger.SystemOutLogger;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public class PluginMixinExtractor {
    public static final String PLUGIN_DIRECTORY = "plugins";
    public static final String MIXINS_DIRECTORY = PLUGIN_DIRECTORY + File.separator + ".mixins";
    public static final String MIXINS_JAR_SUFFIX = ".mixins.jar";
    private static final Logger logger = new SystemOutLogger("Mixin");

    public static void extractMixinJars() {
        try {
            File pluginsDir = new File(PLUGIN_DIRECTORY);
            if (!pluginsDir.exists()) {
                return;
            }
            if (!pluginsDir.isDirectory()) {
                logger.warn("'{}' is not a directory", pluginsDir.getAbsolutePath());
            }

            File mixinsDir = new File(MIXINS_DIRECTORY);
            if (mixinsDir.exists() && !mixinsDir.isDirectory()) {
                logger.warn("'{}' is not a directory", mixinsDir.getAbsolutePath());
            }

            File[] jarFiles = pluginsDir.listFiles((dir, name) -> name.toLowerCase().endsWith(".jar"));
            if (jarFiles == null) {
                return;
            }

            Set<String> validMixinJarFileNames = new HashSet<>();
            for (File jarFile : jarFiles) {
                validMixinJarFileNames.addAll(processJarFile(jarFile));
            }

            File[] mixinJarDirFiles =  mixinsDir.listFiles((dir, name) -> name.endsWith(MIXINS_JAR_SUFFIX));
            if (mixinJarDirFiles != null) {
                for (File mixinJarDirFile : mixinJarDirFiles) {
                    if(validMixinJarFileNames.contains(mixinJarDirFile.getName())) continue;
                    mixinJarDirFile.delete();
                }
            }
        } catch (Exception e) {
            logger.error("Failed to extract mixin jars");
        }
    }

    private static Set<String> processJarFile(File jarFile) {
        String jarFileName = jarFile.getName();
        Set<String> mixinJarFileNames = new HashSet<>();
        try (JarFile jar = new JarFile(jarFile)) {
            Enumeration<JarEntry> entries = jar.entries();

            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String entryName = entry.getName();

                if (!entryName.contains("/") && entryName.endsWith(MIXINS_JAR_SUFFIX)) {
                    File mixinJarInMixinsDir = new File(MIXINS_DIRECTORY, entryName);
                    boolean ignored = mixinJarInMixinsDir.getParentFile().mkdirs();
                    String md5EntryName = "META-INF/" + entryName + ".md5";
                    JarEntry md5Entry = jar.getJarEntry(md5EntryName);

                    if (md5Entry == null) {
                        logger.warn("mixin jar '{}' in jar '{}' does not contain md5", entryName, jarFile.getName());
                        continue;
                    }

                    mixinJarFileNames.add(entryName);
                    String expectedMd5;
                    try (InputStream is = jar.getInputStream(md5Entry);
                         BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                        expectedMd5 = reader.readLine().trim();
                    }

                    boolean shouldExtract = true;

                    if (mixinJarInMixinsDir.exists()) {
                        String actualMd5 = calculateMd5(mixinJarInMixinsDir);

                        if (expectedMd5.equalsIgnoreCase(actualMd5)) {
                            shouldExtract = false;
                            logger.debug("Skip unzipped mixin jar '{}' in jar '{}'", entryName, jarFileName);
                        } else {
                            logger.debug("Will unzip mixin jar '{}' in jar '{}'", entryName, jarFileName);
                        }
                    }

                    if (shouldExtract) {
                        Path mixinJarInMixinsDirPath = mixinJarInMixinsDir.toPath();
                        try (InputStream is = jar.getInputStream(entry)) {
                            Files.copy(is, mixinJarInMixinsDirPath, StandardCopyOption.REPLACE_EXISTING);
                            logger.debug("Already unzip mixin jar '{}' in jar '{}' to '{}'", entryName, jarFileName, mixinJarInMixinsDirPath);
                        }
                    }
                }
            }
        } catch (Exception e) {
            logger.warn(e, "Failed to extract mixin jars in '{}'", jarFileName);
        }
        return mixinJarFileNames;
    }

    private static String calculateMd5(File file) throws IOException, NoSuchAlgorithmException {
        MessageDigest md = MessageDigest.getInstance("MD5");
        try (InputStream is = Files.newInputStream(file.toPath());
             DigestInputStream dis = new DigestInputStream(is, md)) {
            byte[] buffer = new byte[8192];
            while (dis.read(buffer) != -1) ;
        }

        byte[] digest = md.digest();
        StringBuilder sb = new StringBuilder();
        for (byte b : digest) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
