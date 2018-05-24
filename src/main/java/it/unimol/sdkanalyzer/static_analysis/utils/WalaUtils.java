package it.unimol.sdkanalyzer.static_analysis.utils;

import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.classLoader.Language;
import com.ibm.wala.ipa.callgraph.*;
import com.ibm.wala.ipa.callgraph.impl.Everywhere;
import com.ibm.wala.ipa.cha.ClassHierarchy;
import com.ibm.wala.ipa.cha.ClassHierarchyException;
import com.ibm.wala.ssa.*;
import com.ibm.wala.types.MethodReference;
import com.ibm.wala.util.config.AnalysisScopeReader;
import com.ibm.wala.util.io.FileProvider;
import com.ibm.wala.util.strings.StringStuff;

import java.io.File;
import java.io.IOException;

/**
 * @author Simone Scalabrino.
 */
public class WalaUtils {
    public static IR getIR(ClassHierarchy classHierarchy, String methodSignature) {
        MethodReference methodReference = StringStuff.makeMethodReference(Language.JAVA, methodSignature);

        IMethod method = classHierarchy.resolveMethod(methodReference);
        if (method == null) {
            throw new RuntimeException("Unable to find method " + methodSignature);
        }

        return getIR(method);
    }

    public static IR getIR(IMethod m) {
        if (m == null) {
            throw new RuntimeException("Null method!");
        }

        AnalysisOptions options = new AnalysisOptions();
        IRFactory<IMethod> factory = new DefaultIRFactory();

        AnalysisCache cache = new AnalysisCache(factory, options.getSSAOptions(), new SSACache(factory, new AuxiliaryCache(), new AuxiliaryCache()));

        return cache.getSSACache().findOrCreateIR(m, Everywhere.EVERYWHERE, options.getSSAOptions());
    }

    public static AnalysisScope getAnalysisScope(File jarPath, File exclusionPath, File[] classpath) throws IOException, ClassHierarchyException {
        File exFile=new FileProvider().getFile(exclusionPath.getPath());
        AnalysisScope scope = AnalysisScopeReader.makeJavaBinaryAnalysisScope(jarPath.getAbsolutePath(),exFile);
        for (File file : classpath) {
            AnalysisScopeReader.addClassPathToScope(file.getAbsolutePath(), scope, scope.getLoader(AnalysisScope.APPLICATION));
        }
        return scope;
    }
}
