package it.unimol.sdkanalyzer.analysis;

import it.unimol.sdkanalyzer.static_analysis.contexts.JarContext;
import it.unimol.sdkanalyzer.static_analysis.contexts.MethodContext;
import org.junit.jupiter.api.Test;

/**
 * @author Simone Scalabrino.
 */
class VersionMethodCacheTest extends JarTester {
    @Test
    public void testCacher() throws Exception {
        JarContext context = getTestJar();

        VersionMethodCache cache = new VersionMethodCache(context);

        cache.build();

        MethodContext methodContext = context.resolveClassContext("com.example.simone.sdktestapp1.MainActivity")
                .resolveMethodContext("testSDK()V");

        AugmentedSymbolTable augmentedSymbolTable = methodContext.getAugmentedSymbolTable();
        augmentedSymbolTable.update(cache);

        System.out.println(cache);
        System.out.println(augmentedSymbolTable);

        VersionDependentInstructionsExtractor extractor = new VersionDependentInstructionsExtractor(cache);

        extractor.extractVersionDependentCFG(methodContext);
    }
}