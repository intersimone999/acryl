package it.unimol.sdkanalyzer.android;

import com.sun.org.apache.xerces.internal.impl.xpath.regex.Match;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * @author Simone Scalabrino.
 */
public class ApkContainer {
    private final File file;

    private String rawManifest;

    private String packageName;
    private String version;

    private int minSDKVersion;
    private int targetSDKVersion;

    public ApkContainer(File file) {
        this.file = file;

        this.targetSDKVersion   = -1;
        this.minSDKVersion      = -1;
    }

    @Deprecated
    public void extractDex(File destination) throws IOException {
        OutputStream out = new FileOutputStream(destination);
        FileInputStream fin = new FileInputStream(this.file);
        BufferedInputStream bin = new BufferedInputStream(fin);

        ZipInputStream zin = new ZipInputStream(bin);
        ZipEntry ze = null;
        while ((ze = zin.getNextEntry()) != null) {
            if (ze.getName().equals("classes.dex")) {
                byte[] buffer = new byte[8192];
                int len;
                while ((len = zin.read(buffer)) != -1) {
                    out.write(buffer, 0, len);
                }
                out.close();
                break;
            }
        }
    }

    public List<File> extractAllDexes(File destinationFolder, String filePattern) throws IOException {
        assert destinationFolder.isDirectory() : "Destionation has to be a directory";

        List<File> dexes = new ArrayList<>();

        FileInputStream fin = new FileInputStream(this.file);
        BufferedInputStream bin = new BufferedInputStream(fin);

        ZipInputStream zin = new ZipInputStream(bin);
        ZipEntry ze;
        while ((ze = zin.getNextEntry()) != null) {
            if (ze.getName().startsWith("classes") && ze.getName().endsWith(".dex")) {
                Matcher matcher = Pattern.compile("([0-9]+)").matcher(ze.getName());
                int dexId = 1;
                if (matcher.find()) {
                    dexId = Integer.parseInt(matcher.group(1));
                }

                File outputDexFile = new File(destinationFolder, filePattern + dexId + ".dex");
                OutputStream out = new FileOutputStream(outputDexFile);

                byte[] buffer = new byte[8192];
                int len;
                while ((len = zin.read(buffer)) != -1) {
                    out.write(buffer, 0, len);
                }
                out.close();

                dexes.add(outputDexFile);
            }
        }

        return dexes;
    }

    public int getMinSDKVersion() throws IOException {
        loadData();

        return this.minSDKVersion;
    }

    public int getTargetSDKVersion() throws IOException {
        loadData();

        return this.targetSDKVersion;
    }

    public String getPackageName() throws IOException {
        loadData();

        return this.packageName;
    }

    public String getVersion() throws IOException {
        loadData();

        return this.version;
    }

    private void loadData() throws IOException {
        if (rawManifest == null) {
            this.rawManifest = AndroidToolkit.getInstance().aapt().extractManifest(this);

            Pattern patternPackage      = Pattern.compile("package=\"([A-Za-z.0-9_]+)\"");
            Pattern patternVersion      = Pattern.compile("android:versionName\\([A-Za-z0-9]+\\)=\"([A-Za-z.0-9_]+)\"");

            Pattern patternSDKMin       = Pattern.compile("android:minSdkVersion\\([A-Za-z0-9]+\\)=\\([A-Za-z0-9 ]+\\)([0-9xA-Fa-f]+)");
            Pattern patternSDKTarget    = Pattern.compile("android:targetSdkVersion\\([A-Za-z0-9]+\\)=\\([A-Za-z0-9 ]+\\)([0-9xA-Fa-f]+)");

            for (String line : this.rawManifest.split("\n")) {
                Matcher matcherPackage = patternPackage.matcher(line);
                Matcher matcherVersion = patternVersion.matcher(line);

                Matcher matcherSDKMin       = patternSDKMin   .matcher(line);
                Matcher matcherSDKTarget    = patternSDKTarget.matcher(line);

                if (matcherPackage.find()) {
                    this.packageName        = matcherPackage.group(1);
                } else if (matcherVersion.find()) {
                    this.version            = matcherVersion.group(1);
                } else if (matcherSDKMin.find()) {
                    this.minSDKVersion      = Integer.decode(matcherSDKMin   .group(1));
                } else if (matcherSDKTarget.find()) {
                    this.targetSDKVersion   = Integer.decode(matcherSDKTarget.group(1));
                }
            }
        }

        assert this.packageName != null;
        assert this.version != null;

        assert this.minSDKVersion       >= 0;
        assert this.targetSDKVersion    >= 0;
    }

    public File getFile() {
        return file;
    }
}