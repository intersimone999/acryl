package it.unimol.sdkanalyzer.android;

import org.junit.BeforeClass;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Simone Scalabrino.
 */
class ApkContainerTest {
    private static final File AUT = new File("app-debug.apk");

    static {
        AndroidToolkit.setBuildToolsPath("/opt/android-sdk/build-tools/27.0.3/");
        AndroidToolkit.setDex2jarPath("/usr/bin/dex2jar");
    }

    @Test
    public void testManifestMetadata() throws Exception {
        File apkFile = AUT;

        if (!apkFile.exists())
            throw new RuntimeException("Input file does not exist");

        ApkContainer container = new ApkContainer(apkFile);

        assertEquals("com.example.simone.sdktestapp1", container.getPackageName());
        assertEquals("1.0", container.getVersion());
        assertEquals(19, container.getMinSDKVersion());
        assertEquals(26, container.getTargetSDKVersion());
    }

    @Test
    public void testDexExtractor() throws Exception {
        File apkFile = AUT;

        ApkContainer container = new ApkContainer(apkFile);

        File dexFile = File.createTempFile("dex",".dex");

        container.extractDex(dexFile);

        assertTrue(dexFile.exists());
    }

    @Test
    public void testDexesExtractor() throws Exception {
        File apkFile = new File("uber.apk");

        ApkContainer container = new ApkContainer(apkFile);

        File dexFolder = File.createTempFile("dex", "");
        dexFolder.delete();
        dexFolder.mkdir();

        List<File> dexes = container.extractAllDexes(dexFolder, "classes");

        assertTrue(dexFolder.exists());
        for (int i = 1; i <= 8; i++) {
            File file = new File(dexFolder, "classes" + i +".dex");
            assertTrue(dexes.contains(file));
            assertTrue(file.exists());
        }
    }

    @Test
    public void testDexesJoiner() throws Exception {
        File apkFile = new File("uber.apk");

        ApkContainer container = new ApkContainer(apkFile);

        File jarFile = File.createTempFile("jar",".jar");

        AndroidToolkit.getInstance().dex2jar().run(container, jarFile);

        assertTrue(jarFile.exists());
        System.out.println(jarFile.getAbsolutePath());
    }

    @Test
    public void testDex2Jar() throws Exception {
        File apkFile = AUT;

        ApkContainer container = new ApkContainer(apkFile);

        File jarFile = File.createTempFile("jar",".jar");

        AndroidToolkit.getInstance().dex2jar().run(container, jarFile);

        assertTrue(jarFile.exists());
    }

}