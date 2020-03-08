package it.unimol.acryl.analysis;

import com.ibm.wala.ipa.cha.ClassHierarchyException;
import it.unimol.acryl.static_analysis.contexts.GlobalContext;
import it.unimol.acryl.static_analysis.contexts.JarContext;

import java.io.File;
import java.io.IOException;

/**
 * @author Simone Scalabrino.
 */
public class JarTester {
    static final String JAR = "src/test/resources/classes-dex2jar.jar";
    static final File[] CLASSPATH = new File[] {
            new File("/opt/android-sdk/platforms/android-27/android.jar"),
            new File("/opt/android-sdk/platforms/android-27/uiautomator.jar")
//            new File("jar2")
    };

    protected JarContext getTestJar() throws ClassHierarchyException, IOException {
        return GlobalContext.getAndroidContext(JAR, CLASSPATH);
    }
}
