package it.unimol.sdkanalyzer.runnable;

import com.ibm.wala.ipa.cha.ClassHierarchyException;
import it.unimol.sdkanalyzer.android.AndroidToolkit;
import it.unimol.sdkanalyzer.android.ApkContainer;
import it.unimol.sdkanalyzer.android.Dex2Jar;
import it.unimol.sdkanalyzer.static_analysis.contexts.AndroidJarContext;
import it.unimol.sdkanalyzer.static_analysis.contexts.GlobalContext;
import it.unimol.sdkanalyzer.static_analysis.contexts.JarContext;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.logging.Logger;

/**
 * @author Simone Scalabrino.
 */
public class CommonRunner {
    protected static final String PACKAGE_UNDER_ANALYSIS = "android";

    protected File apkFile;
    protected File outputFile;
    protected ApkContainer apk;
    protected File jarFile;
    protected JarContext apkContext;

    public void checkAndInitialize(String[] args) throws IOException, ClassHierarchyException {
        if (args.length < 5) {
            throw new RuntimeException("Illegal arguments. Specify: " +
                    "(1) path to Android build tools, " +
                    "(2) path to Android SDK, " +
                    "(3) path to dex2jar, " +
                    "(4) path to APK, " +
                    "(5) output file");
        }

        AndroidJarContext.setAndroidPackageNames(Collections.singletonList(PACKAGE_UNDER_ANALYSIS));

        AndroidToolkit.setBuildToolsPath(args[0]);
        AndroidToolkit.setAndroidSDK(args[1]);
        AndroidToolkit.setDex2jarPath(args[2]);

        apkFile = new File(args[3]);
        outputFile = new File(args[4]);

        if (!apkFile.exists())
            throw new RuntimeException("Input file does not exist");

        if (outputFile.exists())
            throw new RuntimeException("Output file already exists!");

        apk = new ApkContainer(apkFile);
        jarFile = new File(apkFile.getParentFile(), apkFile.getName().replace(".apk", ".jar"));
        if (!jarFile.exists()) {
            Logger.getAnonymousLogger().info("Extracting jar from apk...");
            try {
                AndroidToolkit.getInstance().dex2jar().run(apk, jarFile);
            } catch (Dex2Jar.DexException e) {
                throw new RuntimeException("Unable to undex, verision not supported");
            }
        } else {
            Logger.getAnonymousLogger().info("Reusing existing jar...");
        }

        apkContext = GlobalContext.getAndroidContext(jarFile.getAbsolutePath(), new File[] {
                new File(AndroidToolkit.getAndroidSDK(), "android.jar"),
                new File(AndroidToolkit.getAndroidSDK(), "uiautomator.jar")
        });
        apkContext.warmUp();
    }
}
