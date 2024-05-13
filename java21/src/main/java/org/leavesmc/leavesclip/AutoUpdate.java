package org.leavesmc.leavesclip;

import java.io.*;
import java.util.Arrays;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class AutoUpdate {

    public static String autoUpdateCorePath;
    public static String autoUpdateDir = "auto_update";
    public static boolean useAutoUpdateJar = false;

    public static void init() {
        File workingDirFile = new File(autoUpdateDir);

        if (!workingDirFile.isDirectory() || !workingDirFile.exists()) {
            return;
        }

        File corePathFile = new File(autoUpdateDir + "/core.path");
        if (!corePathFile.isFile() || !corePathFile.exists()) {
            return;
        }

        try (BufferedReader bufferedReader = new BufferedReader(new FileReader(corePathFile))) {
            String firstLine = bufferedReader.readLine();
            if (firstLine == null) {
                return;
            }

            autoUpdateCorePath = firstLine;
            File jarFile = new File(autoUpdateCorePath);
            if (!jarFile.isFile() || !jarFile.exists()) {
                System.out.println("The specified server core: " + autoUpdateCorePath + " does not exist. Using the original jar!");
                return;
            }

            useAutoUpdateJar = true;

            if (!detectionLeavesclipVersion(autoUpdateCorePath)) {
                System.out.println("Leavesclip version detection in server core: " + autoUpdateCorePath + " failed. Using the original jar!");
                useAutoUpdateJar = false;
                return;
            }

            System.out.println("Using server core: " + autoUpdateCorePath + " provide by Leavesclip-Auto-Update");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static boolean detectionLeavesclipVersion(String jarPath) {
        byte[] localBytes;

        try (InputStream localStream = AutoUpdate.class.getResourceAsStream("/META-INF/leavesclip-version")) {
            if (localStream != null) {
                localBytes = localStream.readAllBytes();
            } else {
                return false;
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        try (InputStream externalStream = getResourceAsStream(jarPath, "/META-INF/leavesclip-version")) {
            if (externalStream != null) {
                return Arrays.equals(localBytes, externalStream.readAllBytes());
            }
            return false;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static InputStream getResourceAsStream(String jarPath, String name) {
        if (!useAutoUpdateJar) {
            return AutoUpdate.class.getResourceAsStream(name);
        }

        name = name.replaceFirst("/", "");
        InputStream result = null;

        try {
            ZipInputStream zipInputStream = new ZipInputStream(new FileInputStream(jarPath));
            ZipEntry zipEntry;

            while ((zipEntry = zipInputStream.getNextEntry()) != null) {
                if (zipEntry.getName().equals(name)) {
                    result = new ByteArrayInputStream(zipInputStream.readAllBytes());
                    break;
                }
                zipInputStream.closeEntry();
            }
            zipInputStream.close();

            if (result == null) {
                throw new IOException(name + " not found in our jar or in the " + jarPath);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return result;
    }
}
