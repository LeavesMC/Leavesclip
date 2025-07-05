package org.leavesmc.leavesclip.mixin;

import net.fabricmc.accesswidener.AccessWidener;
import net.fabricmc.accesswidener.AccessWidenerClassVisitor;
import net.fabricmc.accesswidener.AccessWidenerReader;
import org.leavesmc.leavesclip.logger.Logger;
import org.leavesmc.leavesclip.logger.SimpleLogger;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLClassLoader;

public class AccessWidenerManager {
    private static final Logger logger = new SimpleLogger("AccessWidener");
    private static final String namespace = "named";
    private static final AccessWidener instance = new AccessWidener();

    public static void initAccessWidener(URLClassLoader classLoader) {
        AccessWidenerReader reader = new AccessWidenerReader(instance);
        for (String config : MixinJarResolver.accessWidenerConfigs) {
            if (config == null || config.isEmpty()) continue;
            applyAccessWidenerConfig(classLoader, config, reader);
        }
    }

    private static void applyAccessWidenerConfig(URLClassLoader classLoader, String config, AccessWidenerReader reader) {
        try (InputStream inputStream = classLoader.getResourceAsStream(config)) {
            if (inputStream == null) {
                logger.warn("Access widener config not found: " + config);
                return;
            }
            reader.read(inputStream.readAllBytes(), namespace);
        } catch (IOException e) {
            logger.warn("Failed to load access widener: " + config, e);
        }
    }

    public static byte[] applyAccessWidener(byte[] classData) {
        ClassReader reader = new ClassReader(classData);
        ClassWriter writer = new ClassWriter(reader, 0);
        ClassVisitor visitor = AccessWidenerClassVisitor.createClassVisitor(Opcodes.ASM9, writer, instance);
        reader.accept(visitor, 0);
        return writer.toByteArray();
    }
}
