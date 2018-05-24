package it.unimol.sdkanalyzer.analysis;

import it.unimol.sdkanalyzer.static_analysis.contexts.MethodContext;

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
