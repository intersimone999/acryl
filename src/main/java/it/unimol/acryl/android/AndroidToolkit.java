package it.unimol.acryl.android;

/**
 * @author Simone Scalabrino.
 */
public class AndroidToolkit {
    public static String buildToolsPath;
    public static String dex2jarPath;

    public static AndroidToolkit instance;
    private static String androidSDK;

    public static void setBuildToolsPath(String value) {
        buildToolsPath = value;
    }

    public static void setDex2jarPath(String value) {
        dex2jarPath = value;
    }

    public static void setAndroidSDK(String value) {
        androidSDK = value;
    }

    public static String getAndroidSDK() {
        return androidSDK;
    }

    public static AndroidToolkit getInstance() {
        assert buildToolsPath != null   : "Please, set the Android build tool path for AndroidToolkit";
        assert dex2jarPath != null      : "Please, set the path to dex2jar for AndroidToolkit";

        if (instance == null)
            instance = new AndroidToolkit();

        return instance;
    }

    public Aapt aapt() {
        return new Aapt(buildToolsPath);
    }

    public Dex2Jar dex2jar() {
        return new Dex2Jar(dex2jarPath);
    }
}
