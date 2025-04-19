package org.leavesmc.leavesclip.mixin;

import org.spongepowered.asm.service.IGlobalPropertyService;
import org.spongepowered.asm.service.IPropertyKey;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class MixinServiceGlobalProperty implements IGlobalPropertyService {
    private static final ConcurrentMap<String, Object> properties = new ConcurrentHashMap<>();
    private static final ConcurrentMap<IPropertyKey, String> values = new ConcurrentHashMap<>();

    @Override
    public IPropertyKey resolveKey(String name) {
        SimplePropertyKey key = new SimplePropertyKey();
        values.putIfAbsent(key, name);
        return key;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getProperty(IPropertyKey key) {
        String keyName = values.get(key);
        return (T) properties.get(keyName);
    }

    @Override
    public void setProperty(IPropertyKey key, Object value) {
        String keyName = values.get(key);
        if (value == null) {
            properties.remove(keyName);
        } else {
            properties.put(keyName, value);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getProperty(IPropertyKey key, T defaultValue) {
        T val = (T) properties.get(values.get(key));
        return val != null ? val : defaultValue;
    }

    @Override
    public String getPropertyString(IPropertyKey key, String defaultValue) {
        Object val = properties.get(values.get(key));
        return val instanceof String ? (String) val : defaultValue;
    }

    public static class SimplePropertyKey implements IPropertyKey {
    }
}