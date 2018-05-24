package it.unimol.sdkanalyzer.android;

/**
 * @author Simone Scalabrino.
 */
public class AndroidToolkit {
    public static String buildToolsPath;
    public static String dex2jarPath;

    public static AndroidToolkit instance;
    private static String androisSDK;

    public static void setBuildToolsPath(String value) {
        buildToolsPath = value;
    }

    public static void setDex2jarPath(String value) {
        dex2jarPath = value;
    }

    public static void setAndroidSDK(String value) {
        androisSDK = value;
    }

    public static String getAndroisSDK() {
        return androisSDK;
    }

    public static AndroidToolkit getInstance() {
        assert buildToolsPath != null   : "Please, set the Android build tool path for AndroidToolkit";
        assert dex2jarPath != null      : "Please, set the path to dex2jar for AndroidToolkit";

        if (instance == null)
            instance = new AndroidToolkit();

        return instance;
    }

    public Aapt aapt() {
        Aapt aapt = new Aapt(buildToolsPath);
        return aapt;
    }

    public Dex2Jar dex2jar() {
        Dex2Jar dex2jar = new Dex2Jar(dex2jarPath);
        return dex2jar;
    }
}
