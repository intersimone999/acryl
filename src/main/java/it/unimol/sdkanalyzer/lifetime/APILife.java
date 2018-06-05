package it.unimol.sdkanalyzer.lifetime;

/**
 * @author Simone Scalabrino.
 */
public class APILife {
    private String signature;
    private int minVersion;
    private int maxVersion;

    public APILife(String signature, int minVersion, int maxVersion) {
        this.signature = signature;
        this.minVersion = minVersion;
        this.maxVersion = maxVersion;
    }

    public String getSignature() {
        return signature;
    }

    public int getMinVersion() {
        return minVersion;
    }

    public int getMaxVersion() {
        return maxVersion;
    }
}
