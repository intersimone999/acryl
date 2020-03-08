package it.unimol.acryl.analysis;

import it.unimol.acryl.static_analysis.contexts.MethodContext;

import java.io.IOException;

/**
 * @author Simone Scalabrino.
 */
public interface IVersionMethodCache {
    void build() throws IOException;

    SDKInfo getVersionNumbers(MethodContext context);
    SDKInfo getVersionNumbers(String signature);

    void saveEntry(MethodContext methodContext, SDKInfo sdkInfo);
}
