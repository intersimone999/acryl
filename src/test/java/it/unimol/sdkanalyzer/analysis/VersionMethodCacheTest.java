package it.unimol.sdkanalyzer.analysis;

import com.ibm.wala.ipa.cha.ClassHierarchyException;
import it.unimol.sdkanalyzer.static_analysis.contexts.GlobalContext;
import it.unimol.sdkanalyzer.static_analysis.contexts.JarContext;
import it.unimol.sdkanalyzer.static_analysis.contexts.MethodContext;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;


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

    @Test
    public void testCacherOnStaticMethods() throws ClassHierarchyException, IOException {
        JarContext context = GlobalContext.getAndroidContext("/home/simone/University/Ricerca/2018/AndroidSDK/crawler/fdroid/naman14.timber_14.jar", CLASSPATH);

        VersionMethodCache cache = new VersionMethodCache(context);

        cache.build();

        MethodContext methodContext = context.resolveClassContext("com.nostra13.universalimageloader.core.DefaultConfigurationFactory")
                .resolveMethodContext("hasHoneycomb()Z");

        SDKInfo result = cache.getVersionNumbers(methodContext);

        assertNotNull(result);
        assertEquals(11, result.getVersionFor(0).getCheckedVersion());
        assertEquals(VersionChecker.Comparator.LT, result.getVersionFor(0).getComparator());

        MethodContext targetCheck = context.resolveClassContext("com.nostra13.universalimageloader.core.DefaultConfigurationFactory")
                .resolveMethodContext("createMemoryCache(Landroid/content/Context;I)Lcom/nostra13/universalimageloader/cache/memory/MemoryCache;");

        AugmentedSymbolTable augmentedSymbolTable = targetCheck.getAugmentedSymbolTable();
        augmentedSymbolTable.update(cache);
    }
}