package org.leavesmc.leavesclip.mixin;

import org.leavesmc.leavesclip.logger.Logger;
import org.leavesmc.leavesclip.logger.SystemOutLogger;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.jar.JarFile;

import static org.leavesmc.leavesclip.mixin.PluginMixinExtractor.MIXINS_DIRECTORY;

public class MixinJarResolver {
    private static final Logger logger = new SystemOutLogger("Mixin");
    public static List<String> jsonFiles = Collections.emptyList();
    public static URL[] jarUrls = new URL[]{};

    public static void resolveMixinJars() {
        try {
            File mixinsDir = validateAndGetMixinsDirectory();
            File[] jarFiles = findJarFilesInDirectory(mixinsDir);
            jarUrls = convertJarFilesToUrls(jarFiles);
            jsonFiles = findMatchingJsonFilesInJars(jarFiles);
        } catch (MixinDiscoveryException e) {
            if (e.breakOut) {
                return;
            }
            logger.error("Failed to discover mixin jars", e);
        }
    }

    private static File validateAndGetMixinsDirectory() throws MixinDiscoveryException {
        File mixinsDir = new File(MIXINS_DIRECTORY);
        if (!mixinsDir.exists()) {
            throw MixinDiscoveryException.breakOut();
        } else if (!mixinsDir.isDirectory()) {
            throw new MixinDiscoveryException("'" + mixinsDir + "'' exists but is not a directory");
        }
        return mixinsDir;
    }

    private static File[] findJarFilesInDirectory(File directory) {
        File[] jarFiles = directory.listFiles((dir, name) -> name.toLowerCase().endsWith(".mixins.jar"));
        if (jarFiles == null || jarFiles.length == 0) {
            throw MixinDiscoveryException.breakOut();
        }
        return jarFiles;
    }

    private static URL[] convertJarFilesToUrls(File[] jarFiles) throws MixinDiscoveryException {
        URL[] jarUrls = new URL[jarFiles.length];
        try {
            for (int i = 0; i < jarFiles.length; i++) {
                jarUrls[i] = jarFiles[i].toURI().toURL();
            }
            return jarUrls;
        } catch (MalformedURLException e) {
            throw new MixinDiscoveryException("Failed to convert Jar file paths", e);
        }
    }

    private static List<String> findMatchingJsonFilesInJars(File[] jarFiles) {
        List<String> foundJsonFiles = new ArrayList<>();

        for (File jarFile : jarFiles) {
            try (JarFile jar = new JarFile(jarFile)) {
                jar.stream().forEach(entry -> {
                    String name = entry.getName();
                    if (!entry.isDirectory() && !name.contains("/") && name.startsWith("mixins.") && name.endsWith(".json")) {
                        foundJsonFiles.add(name);
                    }
                });
            } catch (IOException e) {
                logger.warn(e, "Error processing Jar File: {}", jarFile.getName());
            }
        }

        return foundJsonFiles;
    }

    private static class MixinDiscoveryException extends RuntimeException {
        public boolean breakOut = false;

        public MixinDiscoveryException(String message) {
            super(message);
        }

        public MixinDiscoveryException(String message, Throwable cause) {
            super(message, cause);
        }

        public static MixinDiscoveryException breakOut() {
            MixinDiscoveryException exception = new MixinDiscoveryException("");
            exception.breakOut = true;
            return exception;
        }
    }
}
