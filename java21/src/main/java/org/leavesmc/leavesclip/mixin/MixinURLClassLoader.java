package org.leavesmc.leavesclip.mixin;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.transformer.IMixinTransformer;

import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Objects;

public class MixinURLClassLoader extends URLClassLoader {
    private final IMixinTransformer transformer;

    public MixinURLClassLoader(URL[] urls, ClassLoader parent) {
        super(urls, parent);
        Object active = MixinEnvironment.getDefaultEnvironment().getActiveTransformer();
        if (!(active instanceof IMixinTransformer)) {
            throw new IllegalStateException("Cannot found MixinTransformer");
        }
        this.transformer = (IMixinTransformer) active;
    }

    @Override
    public @Nullable URL getResource(String name) {
        Objects.requireNonNull(name);
        if (name.endsWith(".class")) {
            return super.getResource(name);
        } else {
            return findResource(name);
        }
    }

    @Override
    protected Class<?> findClass(@NotNull String name) throws ClassNotFoundException {
        String path = name.replace('.', '/') + ".class";
        try (InputStream in = getResourceAsStream(path)) {
            if (in == null) {
                throw new ClassNotFoundException(name);
            }

            byte[] original = in.readAllBytes();
            byte[] mixin = transformer.transformClass(MixinEnvironment.getCurrentEnvironment(), name, original);
            byte[] transformed = AccessWidenerManager.applyAccessWidener(mixin);

            return defineClass(name, transformed, 0, transformed.length);
        } catch (Exception e) {
            throw new ClassNotFoundException(name, e);
        }
    }
}