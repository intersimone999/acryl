package it.unimol.sdkanalyzer.android;

import org.apache.commons.io.FileUtils;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Uses dex2jar to translate a DEX file into a JAR file
 * @author Simone Scalabrino.
 */
public class Dex2Jar {
    private String program;

    public Dex2Jar(String pathToDex2Jar) {
        this.program = pathToDex2Jar;
    }

    public void run(File dexFile, File destFile) throws IOException, DexException {
        Runtime runtime = Runtime.getRuntime();

        Process process = runtime.exec(new String[] {this.program, dexFile.getAbsolutePath(), "-o", destFile.getAbsolutePath()});
        try {
            process.waitFor();
            if (!destFile.exists())
                throw new DexException();
        } catch (InterruptedException e) {
            throw new RuntimeException("Aborted process");
        }
    }

    public void run(ApkContainer container, File destination) throws IOException, DexException {
        File tempFolder = File.createTempFile("dexextractor", "");
        tempFolder.delete();
        tempFolder.mkdir();

        List<File> dexes = container.extractAllDexes(tempFolder, "classes");

        List<File> jars = new ArrayList<>();
        for (File dex : dexes) {
            File jarFile = new File(tempFolder, dex.getName() + ".jar");
            this.run(dex, jarFile);
            jars.add(jarFile);
        }

        if (jars.size() == 0)
            return;

        FileOutputStream fos = new FileOutputStream(destination, false);
        ZipOutputStream zos = new ZipOutputStream(fos);
        ZipEntry ze;

        for (File jarFile : jars) {
            FileInputStream fin = new FileInputStream(jarFile);
            BufferedInputStream bin = new BufferedInputStream(fin);
            ZipInputStream zin = new ZipInputStream(bin);
            while ((ze = zin.getNextEntry()) != null) {
                try {
                    zos.putNextEntry(ze);

                    if (!ze.isDirectory()) {
                        byte[] buffer = new byte[8192];
                        int len;
                        while ((len = zin.read(buffer)) != -1) {
                            zos.write(buffer, 0, len);
                        }
                    }
                    zos.closeEntry();
                } catch (ZipException e) {

                }
            }
            zin.close();
            bin.close();
            fin.close();
        }
        zos.close();
        fos.close();

        tempFolder.delete();
    }

    public static class DexException extends Exception {}
}
