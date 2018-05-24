package it.unimol.sdkanalyzer.analysis;

import com.ibm.wala.ipa.cha.ClassHierarchyException;
import it.unimol.sdkanalyzer.static_analysis.contexts.GlobalContext;
import it.unimol.sdkanalyzer.static_analysis.contexts.JarContext;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;

/**
 * @author Simone Scalabrino.
 */
public class JarTester {
    private static final String JAR = "classes-dex2jar.jar";
    private static final File[] CLASSPATH = new File[] {
            new File("/opt/android-sdk/platforms/android-22/android.jar"),
            new File("/opt/android-sdk/platforms/android-22/uiautomator.jar")
//            new File("jar2")
    };

    public JarContext getTestJar() throws ClassHierarchyException, IOException {
        JarContext ctx = GlobalContext.getAndroidContext(JAR, CLASSPATH);

        return ctx;
    }
}
