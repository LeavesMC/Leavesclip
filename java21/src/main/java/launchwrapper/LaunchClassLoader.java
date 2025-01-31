/*
 * This file is part of project Orion, licensed under the MIT License (MIT).
 *
 * Copyright (c) Original contributors ("I don't care" license? See https://github.com/Mojang/LegacyLauncher/issues/1)
 * Copyright (c) 2017-2018 Mark Vainomaa <mikroskeem@mikroskeem.eu>
 * Copyright (c) Contributors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package launchwrapper;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.net.JarURLConnection;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.security.CodeSigner;
import java.security.CodeSource;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.jar.Attributes;
import java.util.jar.Attributes.Name;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

import static java.util.Objects.requireNonNull;
import static org.leavesmc.leavesclip.Leavesclip.LOGGER;

public class LaunchClassLoader extends URLClassLoader {
    static {
        /* Use this, if you encounter weird issues */
        if (!Boolean.getBoolean("legacy.dontRegisterLCLAsParallelCapable")) {
            LOGGER.debug("Registering LaunchClassLoader as parallel capable");
            ClassLoader.registerAsParallelCapable();
        }
    }

    public static final int BUFFER_SIZE = 1 << 12;
    private final List<URL> sources;
    private final ClassLoader parent;

    private final List<IClassTransformer> transformers = new ArrayList<>(2);
    private final Map<String, Class<?>> cachedClasses = new ConcurrentHashMap<>();
    private final Set<String> invalidClasses = new HashSet<>(1000);

    private final Set<String> classLoaderExceptions = new HashSet<>();
    private final Set<String> transformerExceptions = new HashSet<>();
    private final Map<String, byte[]> resourceCache = new ConcurrentHashMap<>(1000);
    private final Set<String> negativeResourceCache = Collections.newSetFromMap(new ConcurrentHashMap<>());

    @Nullable
    private IClassNameTransformer renameTransformer = null;

    private final ThreadLocal<byte[]> loadBuffer = ThreadLocal.withInitial(() -> new byte[BUFFER_SIZE]);

    private static final String[] RESERVED_NAMES = {
            "CON", "PRN", "AUX", "NUL",
            "COM1", "COM2", "COM3", "COM4", "COM5", "COM6", "COM7", "COM8", "COM9",
            "LPT1", "LPT2", "LPT3", "LPT4", "LPT5", "LPT6", "LPT7", "LPT8", "LPT9"};

    private static final boolean DEBUG = Boolean.getBoolean("legacy.debugClassLoading");
    private static final boolean DEBUG_FINER = DEBUG && Boolean.getBoolean("legacy.debugClassLoadingFiner");
    private static final boolean DEBUG_SAVE = DEBUG && Boolean.getBoolean("legacy.debugClassLoadingSave");
    private static final Path DUMP_PATH = Paths.get(System.getProperty("legacy.classDumpPath", "./.classloader.out"));

    @SuppressWarnings("CommentedOutCode")
    public LaunchClassLoader(URL[] sources, ClassLoader parent) {
        super(sources, null);
        this.parent = parent;
        this.sources = new ArrayList<>(Arrays.asList(sources));

        // classloader exclusions
        getClassLoaderExclusions().addAll(Arrays.asList(
                "java.",
                "jdk.",
                "sun.",
                "org.jline.",
                "org.slf4j.",
                "org.slf4j.",
                "joptsimple.",
                "org.spongepowered.",
                "net.minecraft.launchwrapper.",
                "net.minecrell.terminalconsole."
        ));

        // transformer exclusions
        getTransformerExclusions().addAll(Arrays.asList(
                "javax.",
                "jdk.",
                "org.objectweb.asm."
        ));

        // See: https://github.com/SpongePowered/SpongeCommon/commit/8f284427ca50d445d0fffab4afc8251388ada8e9
        /*
         * By default Launchwrapper inherits the class path from the system class loader.
         * However, JRE extensions (e.g. Nashorn in the jre/lib/ext directory) are not part
         * of the class path of the system class loader.
         * Instead, they're loaded using a parent class loader (Launcher.ExtClassLoader).
         * Currently, Launchwrapper does not fall back to the parent class loader if it's
         * unable to find a class on its class path. To make the JRE extensions usable for
         * plugins we manually add the URLs from the ExtClassLoader to Launchwrapper's
         * class path.
         */
        // I think this is not suitable for java 9+ because of the module system
        // ClassLoader classLoader = ClassLoader.getSystemClassLoader();
        // if (classLoader != null) {
        //     classLoader = classLoader.getParent(); // Launcher.ExtClassLoader
        //     if (classLoader instanceof URLClassLoader) {
        //         for (URL url : ((URLClassLoader) classLoader).getURLs()) {
        //             addURL(url);
        //         }
        //     }
        // }

        if (DEBUG_SAVE) {
            try {
                if (Files.exists(DUMP_PATH)) {
                    Files.walk(DUMP_PATH).sorted(Comparator.reverseOrder()).map(Path::toFile).forEach(File::delete);
                }
                Files.createDirectories(DUMP_PATH);
                LOGGER.info("DEBUG_SAVE Enabled, saving all classes to \"{}\"", DUMP_PATH);
            } catch (IOException e) {
                LOGGER.warn("Failed to set up DEBUG_SAVE", e);
            }
        }
    }

    /**
     * Registers transformer class
     *
     * @param transformerClassName Fully qualified transformer class name, see {@link Class#getName()}
     */
    public void registerTransformer(@NotNull String transformerClassName) {
        try {
            IClassTransformer transformer = (IClassTransformer) loadClass(transformerClassName)
                    .getDeclaredConstructor()
                    .newInstance();
            transformers.add(transformer);
            if (transformer instanceof IClassNameTransformer && renameTransformer == null)
                renameTransformer = (IClassNameTransformer) transformer;
        } catch (Exception e) {
            LOGGER.warn("A critical problem occurred registering the transformer class {}", transformerClassName, e);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Class<?> findClass(final String name) throws ClassNotFoundException {
        if (invalidClasses.contains(name)) {
            throw new ClassNotFoundException(name);
        }

        for (final String exception : classLoaderExceptions) {
            if (name.startsWith(exception))
                return parent.loadClass(name);
        }

        if (cachedClasses.containsKey(name))
            return cachedClasses.get(name);

        for (final String exception : transformerExceptions) {
            if (name.startsWith(exception)) {
                try {
                    final Class<?> clazz = super.findClass(name);
                    cachedClasses.put(name, clazz);
                    return clazz;
                } catch (ClassNotFoundException e) {
                    invalidClasses.add(name);
                    throw e;
                }
            }
        }

        final String transformedName = transformName(name);
        if (cachedClasses.containsKey(transformedName)) {
            return cachedClasses.get(transformedName);
        }

        final String untransformedName = untransformName(name);

        // Get class bytes
        byte[] classData = getClassBytes(untransformedName);

        byte[] transformedClass = null;
        try {
            // Run transformers (running with null class bytes is valid, because transformers may generate classes dynamically)
            transformedClass = runTransformers(untransformedName, transformedName, classData);
        } catch (Exception e) {
            if (DEBUG)
                LOGGER.trace("Exception encountered while transformimg class {}", name, e);
        }

        // If transformer chain provides no class data, mark given class name invalid and throw CNFE
        if (transformedClass == null) {
            invalidClasses.add(name);
            throw new ClassNotFoundException(name);
        }

        // Save class if requested so
        if (DEBUG_SAVE) {
            try {
                saveTransformedClass(transformedClass, transformedName);
            } catch (IOException e) {
                LOGGER.warn("Failed to save class {}", transformedName, e);
            }
        }

        // Define package for class
        int lastDot = untransformedName.lastIndexOf('.');
        String packageName = lastDot == -1 ? "" : untransformedName.substring(0, lastDot);
        String fileName = untransformedName.replace('.', '/').concat(".class");
        URLConnection urlConnection = findCodeSourceConnectionFor(fileName);
        CodeSigner[] signers = null;

        try {
            if (lastDot > -1) {
                if (urlConnection instanceof JarURLConnection jarURLConnection) {
                    final JarFile jarFile = jarURLConnection.getJarFile();

                    if (jarFile != null && jarFile.getManifest() != null) {
                        final Manifest manifest = jarFile.getManifest();
                        final JarEntry entry = jarFile.getJarEntry(fileName);

                        Package pkg = getDefinedPackage(packageName);
                        getClassBytes(untransformedName);
                        signers = entry.getCodeSigners();
                        if (pkg == null) {
                            pkg = definePackage(packageName, manifest, jarURLConnection.getJarFileURL());
                        } else {
                            if (pkg.isSealed() && !pkg.isSealed(jarURLConnection.getJarFileURL())) {
                                LOGGER.error("The jar file {} is trying to seal already secured path {}",
                                        jarFile.getName(),
                                        packageName);
                            } else if (isSealed(packageName, manifest)) {
                                LOGGER.error("The jar file {} has a security seal for path {}, but that path is defined and not secure",
                                        jarFile.getName(),
                                        packageName);
                            }
                        }
                    }
                } else {
                    Package pkg = getDefinedPackage(packageName);
                    if (pkg == null) {
                        pkg = definePackage(
                                packageName,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null
                        );
                    } else if (pkg.isSealed()) {
                        URL url = urlConnection != null ? urlConnection.getURL() : null;
                        LOGGER.error("The URL {} is defining elements for sealed path {}", url, packageName);
                    }
                }
            }

            // Define class
            final CodeSource codeSource = urlConnection == null ?
                    null :
                    new CodeSource(urlConnection.getURL(), signers);
            final Class<?> clazz = defineClass(
                    transformedName,
                    transformedClass,
                    0,
                    transformedClass.length,
                    codeSource
            );
            cachedClasses.put(transformedName, clazz);
            return clazz;
        } catch (Exception e) {
            invalidClasses.add(name);
            if (DEBUG) LOGGER.trace("Exception encountered attempting classloading of {}", name, e);
            throw new ClassNotFoundException(name, e);
        }
    }

    /**
     * Adds an {@link URL} to classloader
     *
     * @param url {@link URL} to add
     */
    @Override
    public void addURL(final URL url) {
        super.addURL(url);
        sources.add(url);
    }

    /**
     * Gets list of added {@link URL}s to this classloader
     *
     * @return Unmodifiable list of added {@link URL}s
     */
    @SuppressWarnings("unused")
    @NotNull
    public List<URL> getSources() {
        return Collections.unmodifiableList(sources);
    }

    /**
     * Gets list of registered {@link IClassTransformer} instances
     *
     * @return List of registered {@link IClassTransformer} instances
     */
    @SuppressWarnings("unused")
    @NotNull
    public List<IClassTransformer> getTransformers() {
        return Collections.unmodifiableList(transformers);
    }

    /**
     * Adds classloader exclusion. Fully qualified class names starting with {@code toExclude} will be loaded
     * from parent classloader
     *
     * @param toExclude Part of fully qualified class name
     * @deprecated Use {@link #getClassLoaderExclusions()} instead
     */
    @Deprecated
    public void addClassLoaderExclusion(@NotNull String toExclude) {
        classLoaderExceptions.add(toExclude);
    }

    /**
     * Gets a {@link Set} of classloader exclusions.
     * <p>
     * Classlaoder exclusions look like this: {@code com.mojang.authlib.}, so that means all classes and subclasses
     * in {@code com.mojang.authlib} class would be loaded from parent classloader
     *
     * @return {@link Set} of classloader exclusions
     */
    public Set<String> getClassLoaderExclusions() {
        return classLoaderExceptions;
    }

    /**
     * Adds transformer exclusion. Given classes won't be transformed by {@link IClassTransformer}s
     *
     * @param toExclude Part of fully qualified class name
     * @see #getTransformers() For list of registered transformers
     * @deprecated Use {@link #getTransformerExclusions()} instead.
     */
    @Deprecated
    public void addTransformerExclusion(@NotNull String toExclude) {
        transformerExceptions.add(toExclude);
    }

    /**
     * Gets a {@link Set} of transformer exclusions.
     * <p>
     * Transformer exclusions look like this: {@code com.mojang.authlib.}, so that means all classes and subclasses
     * in {@code com.mojang.authlib} class won't be transformed
     *
     * @return {@link Set} of transformer exclusions.
     */
    public Set<String> getTransformerExclusions() {
        return transformerExceptions;
    }

    /**
     * `
     * Gets class raw bytes
     *
     * @param name Class name
     * @return Class raw bytes, or null if class was not found
     */
    public byte @Nullable [] getClassBytes(@NotNull String name) {
        if (negativeResourceCache.contains(name)) {
            return null;
        } else if (resourceCache.containsKey(name)) {
            return resourceCache.get(name);
        }
        if (name.indexOf('.') == -1) {
            for (final String reservedName : RESERVED_NAMES) {
                if (name.toUpperCase(Locale.ENGLISH).startsWith(reservedName)) {
                    final byte[] data = getClassBytes("_" + name);
                    if (data != null) {
                        resourceCache.put(name, data);
                        return data;
                    }
                }
            }
        }

        String resourcePath = name.replace('.', '/').concat(".class");
        URL classResource = findResource(resourcePath);
        if (classResource == null) {
            if (DEBUG) LOGGER.trace("Failed to find class resource {}", resourcePath);
            negativeResourceCache.add(name);
            return null;
        }
        try (InputStream classStream = classResource.openStream()) {
            if (DEBUG) LOGGER.trace("Loading class {} from resource {}", name, classResource);
            byte[] data = requireNonNull(readFully(classStream));
            resourceCache.put(name, data);
            return data;
        } catch (Exception e) {
            if (DEBUG) LOGGER.trace("Failed to load class {} from resource {}", name, classResource);
            negativeResourceCache.add(name);
            return null;
        }
    }

    /**
     * Clears negative resource entries (resources which failed to load in this classloader)
     *
     * @param entriesToClear Entries to clear
     */
    @SuppressWarnings("unused")
    public void clearNegativeEntries(@NotNull Set<String> entriesToClear) {
        negativeResourceCache.removeAll(entriesToClear);
    }

    private byte @Nullable [] readFully(@NotNull InputStream stream) {
        try (ByteArrayOutputStream os = new ByteArrayOutputStream(stream.available())) {
            int readBytes;
            byte[] buffer = loadBuffer.get();

            while ((readBytes = stream.read(buffer, 0, buffer.length)) != -1)
                os.write(buffer, 0, readBytes);

            return os.toByteArray();
        } catch (Throwable t) {
            LOGGER.warn("Problem reading stream fully", t);
            return null;
        }
    }

    private void saveTransformedClass(byte @NotNull [] data, @NotNull String transformedName) throws IOException {
        Path classFile = Paths.get(
                DUMP_PATH.toString(),
                transformedName.replace('.', File.separatorChar) + ".class"
        );

        if (Files.notExists(classFile.getParent()))
            Files.createDirectories(classFile);

        if (Files.exists(classFile)) {
            LOGGER.warn("Transformed class \"{}\" already exists! Deleting old class", transformedName);
            Files.delete(classFile);
        }

        try (OutputStream output = Files.newOutputStream(classFile, StandardOpenOption.CREATE_NEW)) {
            LOGGER.debug("Saving transformed class \"{}\" to \"{}\"", transformedName, classFile);
            output.write(data);
        } catch (IOException ex) {
            LOGGER.warn("Could not save transformed class \"{}\"", transformedName, ex);
        }
    }

    @NotNull
    private String untransformName(@NotNull String name) {
        return renameTransformer != null ? renameTransformer.unmapClassName(name) : name;
    }

    @NotNull
    private String transformName(@NotNull String name) {
        return renameTransformer != null ? renameTransformer.remapClassName(name) : name;
    }

    private boolean isSealed(@NotNull String path, @NotNull Manifest manifest) {
        Attributes attributes = manifest.getAttributes(path);
        String sealed = attributes != null ? attributes.getValue(Name.SEALED) : null;

        if (sealed == null)
            sealed = (attributes = manifest.getMainAttributes()) != null ? attributes.getValue(Name.SEALED) : null;
        return "true".equalsIgnoreCase(sealed);
    }

    @Nullable
    private URLConnection findCodeSourceConnectionFor(@NotNull String name) {
        final URL resource = findResource(name);
        if (resource != null) {
            try {
                return resource.openConnection();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        return null;
    }

    private byte @Nullable [] runTransformers(@NotNull String name, @NotNull String transformedName, byte @Nullable [] basicClass) {
        if (DEBUG_FINER)
            LOGGER.trace("Beginning transform of {{} ({})} Start Length: {}", name, transformedName, basicClass != null ? basicClass.length : 0);

        for (final IClassTransformer transformer : transformers) {
            final String transName = transformer.getClass().getName();

            if (DEBUG_FINER)
                LOGGER.trace("Before Transformer {{} ({})} {}: {}", name, transformedName, transName, basicClass != null ? basicClass.length : 0);

            basicClass = transformer.transform(name, transformedName, basicClass);

            if (DEBUG_FINER)
                LOGGER.trace("After  Transformer {{} ({})} {}: {}", name, transformedName, transName, basicClass != null ? basicClass.length : 0);
        }
        return basicClass;
    }
}
