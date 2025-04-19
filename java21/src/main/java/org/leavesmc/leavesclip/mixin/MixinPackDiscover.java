package org.leavesmc.leavesclip.mixin;

import org.leavesmc.leavesclip.logger.Logger;
import org.leavesmc.leavesclip.logger.SystemOutLogger;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarFile;
import java.util.regex.Pattern;

public class MixinPackDiscover {
    private static final Logger logger = new SystemOutLogger("Mixin");
    private static final String MIXINS_DIRECTORY = "mixins";
    private static final Pattern JSON_PATTERN = Pattern.compile("^mixins(\\.[a-zA-Z0-9]+)+\\.json$");
    public static List<String> jsonFiles;
    public static URL[] jarUrls;

    public static void discover() {
        try {
            File mixinsDir = validateAndGetMixinsDirectory();
            File[] jarFiles = findJarFilesInDirectory(mixinsDir);
            jarUrls = convertJarFilesToUrls(jarFiles);
            jsonFiles = findMatchingJsonFilesInJars(jarFiles);
        } catch (MixinDiscoveryException e) {
            logger.error("Failed to discover mixin packs", e);
        }
    }

    private static File validateAndGetMixinsDirectory() throws MixinDiscoveryException {
        File mixinsDir = new File(MIXINS_DIRECTORY);
        if (!mixinsDir.exists()) {
            boolean created = mixinsDir.mkdirs();
            if (!created) {
                throw new MixinDiscoveryException("Failed to create mixins directory");
            }
        } else if (!mixinsDir.isDirectory()) {
            throw new MixinDiscoveryException("'mixins' exists but is not a directory");
        }
        return mixinsDir;
    }

    private static File[] findJarFilesInDirectory(File directory) throws MixinDiscoveryException {
        File[] jarFiles = directory.listFiles((dir, name) -> name.toLowerCase().endsWith(".jar"));
        if (jarFiles == null || jarFiles.length == 0) {
            jarFiles = new File[]{};
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
            throw new MixinDiscoveryException("Failed to convert JAR file paths", e);
        }
    }

    private static List<String> findMatchingJsonFilesInJars(File[] jarFiles) {
        List<String> foundJsonFiles = new ArrayList<>();

        for (File jarFile : jarFiles) {
            try (JarFile jar = new JarFile(jarFile)) {
                jar.stream().forEach(entry -> {
                    String name = entry.getName();
                    if (!entry.isDirectory() && !name.contains("/") && JSON_PATTERN.matcher(name).matches()) {
                        foundJsonFiles.add(name);
                    }
                });
            } catch (IOException e) {
                logger.warn(e, "Error processing Jar File: {}", jarFile.getName());
            }
        }

        return foundJsonFiles;
    }

    private static class MixinDiscoveryException extends Exception {
        public MixinDiscoveryException(String message) {
            super(message);
        }

        public MixinDiscoveryException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
