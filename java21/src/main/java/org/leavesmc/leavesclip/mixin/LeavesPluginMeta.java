package org.leavesmc.leavesclip.mixin;

import com.google.gson.annotations.SerializedName;

import java.io.File;
import java.util.List;

import static org.leavesmc.leavesclip.mixin.PluginResolver.MIXINS_DIRECTORY;
import static org.leavesmc.leavesclip.mixin.PluginResolver.MIXINS_JAR_SUFFIX;

@SuppressWarnings("unused")
public class LeavesPluginMeta {
    private String name;
    private MixinConfig mixin;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public MixinConfig getMixin() {
        return mixin;
    }

    public void setMixin(MixinConfig mixin) {
        this.mixin = mixin;
    }

    public File getMixinJarFile() {
        String mixinJarName = name + MIXINS_JAR_SUFFIX;
        return new File(MIXINS_DIRECTORY, mixinJarName);
    }

    public static class MixinConfig {
        @SerializedName("package-name")
        private String packageName;

        private List<String> mixins;

        @SerializedName("access-widener")
        private String accessWidener;

        public String getPackageName() {
            return packageName;
        }

        public void setPackageName(String packageName) {
            this.packageName = packageName;
        }

        public List<String> getMixins() {
            return mixins;
        }

        public void setMixins(List<String> mixins) {
            this.mixins = mixins;
        }

        public String getAccessWidener() {
            return accessWidener;
        }

        public void setAccessWidener(String accessWidener) {
            this.accessWidener = accessWidener;
        }
    }
}