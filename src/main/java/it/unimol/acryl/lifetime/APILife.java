package it.unimol.acryl.lifetime;

/**
 * @author Simone Scalabrino.
 */
public class APILife {
    private final String signature;
    private final int minVersion;
    private final int maxVersion;

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
