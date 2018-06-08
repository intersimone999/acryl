package it.unimol.sdkanalyzer.analysis;

import it.unimol.sdkanalyzer.static_analysis.contexts.MethodContext;

import java.io.IOException;
import java.util.logging.Logger;

/**
 * @author Simone Scalabrino.
 */
public class MockVersionMethodCache implements IVersionMethodCache {
    @Override
    public void build() {
        Logger.getAnonymousLogger().info("[MOCK] Mocking cache building (this should never happen...)");
    }

    @Override
    public SDKInfo getVersionNumbers(MethodContext context) {
        return null;
    }

    @Override
    public SDKInfo getVersionNumbers(String signature) {
        return null;
    }

    @Override
    public void saveEntry(MethodContext methodContext, SDKInfo sdkInfo) {
        Logger.getAnonymousLogger().info("[MOCK] Mocking save entry (this should never happen...)");
    }
}
