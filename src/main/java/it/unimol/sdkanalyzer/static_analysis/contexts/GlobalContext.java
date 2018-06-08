package it.unimol.sdkanalyzer.static_analysis.contexts;

import com.ibm.wala.ipa.cha.ClassHierarchyException;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Simone Scalabrino.
 */
public class GlobalContext {
    private static final Map<String, JarContext> contexts;

    static {
        contexts = new HashMap<>();
    }

    public static JarContext getContext(String jarPath) throws ClassHierarchyException, IOException {
        return getContext(jarPath, new File[] {});
    }

    public static JarContext getContext(String jarPath, File[] classpath) throws ClassHierarchyException, IOException {
        if (!contexts.containsKey(jarPath))
            contexts.put(jarPath, new JarContext(classpath, jarPath, false));

        return contexts.get(jarPath);
    }

    public static JarContext getContext(String jarPath, Collection<File> classpath) throws ClassHierarchyException, IOException {
        File[] arrayClasspath = new File[classpath.size()];
        int i = 0;
        for (File file : classpath) {
            arrayClasspath[i] = file;
            i++;
        }

        return getContext(jarPath, arrayClasspath);
    }

    public static JarContext getAndroidContext(String jarPath) throws ClassHierarchyException, IOException {
        return getAndroidContext(jarPath, new File[] {});
    }

    public static JarContext getAndroidContext(String jarPath, File[] classpath) throws ClassHierarchyException, IOException {
        if (!contexts.containsKey(jarPath))
            contexts.put(jarPath, new AndroidJarContext(classpath, jarPath, false));

        return contexts.get(jarPath);
    }

    public static JarContext getAndroidContext(String jarPath, Collection<File> classpath) throws ClassHierarchyException, IOException {
        File[] arrayClasspath = new File[classpath.size()];
        int i = 0;
        for (File file : classpath) {
            arrayClasspath[i] = file;
            i++;
        }

        return getAndroidContext(jarPath, arrayClasspath);
    }

    public static File getExclusionsFile() {
        return new File("voidExclusion.txt");
    }
}
