package it.unimol.sdkanalyzer.static_analysis.contexts;

import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.ipa.cha.ClassHierarchyException;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * @author Simone Scalabrino.
 */
public class AndroidJarContext extends JarContext {
    private static List<String> androidPackageNames;

    public static void setAndroidPackageNames(List<String> androidPackageNames) {
        AndroidJarContext.androidPackageNames = androidPackageNames;
    }

    public AndroidJarContext(String jarPath) throws IOException, ClassHierarchyException {
        super(jarPath);
    }

    public AndroidJarContext(String jarPath, boolean withCallGraph) throws IOException, ClassHierarchyException {
        super(jarPath, withCallGraph);
    }

    public AndroidJarContext(File[] classpath, String jarPath, boolean withCallGraph) throws IOException, ClassHierarchyException {
        super(classpath, jarPath, withCallGraph);
    }

    @Override
    protected Collection<IClass> internalGetClassesInJar(boolean useClassFilter) throws IOException {
        List<IClass> resultingClasses = new ArrayList<>();
        ZipInputStream zip = new ZipInputStream(new FileInputStream(super.jarPath));
        for (ZipEntry entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
            boolean isSystemPackage = false;
            for (String androidPackageName : androidPackageNames) {
                if (entry.getName().startsWith(androidPackageName))
                    isSystemPackage = true;
            }

            if (isSystemPackage)
                continue;

            if (!entry.isDirectory() && entry.getName().endsWith(".class")) {
                String className = entry.getName().replace('/', '.'); // including ".class"
                className = className.substring(0, className.length() - ".class".length());

                if (!useClassFilter || className.startsWith(this.classFilter)) {
                    ClassContext classContext = this.resolveClassContext(className);
                    if (classContext != null)
                        resultingClasses.add(classContext.getIClass());
                }
            }
        }

        return resultingClasses;
    }
}
