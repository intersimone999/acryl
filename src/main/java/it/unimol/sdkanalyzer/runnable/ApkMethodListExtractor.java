package it.unimol.sdkanalyzer.runnable;

import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.ssa.SSAAbstractInvokeInstruction;
import com.ibm.wala.ssa.SSAInstruction;
import it.unimol.sdkanalyzer.static_analysis.contexts.ClassContext;
import it.unimol.sdkanalyzer.static_analysis.contexts.MethodContext;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * @author Simone Scalabrino.
 */
public class ApkMethodListExtractor extends CommonRunner {
    public void run(String[] args) throws Exception {
        checkAndInitialize(args);

        apkContext.setClassNotFoundHandler(className -> Logger.getAnonymousLogger().warning("Class not found: " + className));

        String appName      = apk.getPackageName();
        String appVersion   = apk.getVersion();
        String appSdkMin    = String.valueOf(apk.getMinSDKVersion());
        String appSdkTrg    = String.valueOf(apk.getTargetSDKVersion());

        List<String> data = new ArrayList<>();
        for (IClass iClass : apkContext.getClassesInJar(false)) {
            ClassContext classContext = apkContext.resolveClassContext(iClass);

            for (IMethod iMethod : classContext.getNonAbstractMethods()) {
                MethodContext methodContext = classContext.resolveMethodContext(iMethod);

                if (methodContext.getIntermediateRepresentation() == null)
                    continue;

                data.add(methodContext.getIMethod().getSignature());
            }
        }

        try (PrintWriter writer = new PrintWriter(outputFile)) {
            writer.println("app\tversion\tmethod");
            for (String method : data) {
                writer.println(appName + "\t" + appVersion + "\t" + method);
            }
        }
    }

    public static void main(String[] args) throws Exception {
        new ApkMethodListExtractor().run(args);
    }

    private static class IDProvider {
        private int id;

        public void increment() {
            id++;
        }

        public int get() {
            return id;
        }

        public int getAndIncrement() {
            return id++;
        }
    }
}
