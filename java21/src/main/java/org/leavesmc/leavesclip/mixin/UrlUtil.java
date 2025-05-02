/*
 * Copyright 2016 FabricMC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
// This file contains code derived from fabric-loader under the Apache-2.0 license
// Modified for Leavesclip: removed code related to fabric-loader environment and implemented our own functionality

package org.leavesmc.leavesclip.mixin;

import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.CodeSource;

public final class UrlUtil {
    public static final Path LOADER_CODE_SOURCE = getCodeSource(UrlUtil.class);

    public static Path asPath(URL url) {
        try {
            return Paths.get(url.toURI());
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException(e);
        }
    }

    public static Path getCodeSource(Class<?> cls) {
        CodeSource cs = cls.getProtectionDomain().getCodeSource();
        if (cs == null) return null;

        return asPath(cs.getLocation());
    }
}