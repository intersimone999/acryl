package it.unimol.acryl.runnable;

import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.ssa.SSAAbstractInvokeInstruction;
import com.ibm.wala.ssa.SSAInstruction;
import it.unimol.acryl.static_analysis.contexts.ClassContext;
import it.unimol.acryl.static_analysis.contexts.MethodContext;

import java.io.PrintWriter;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

/**
 * @author Simone Scalabrino.
 */
public class AndroidApiExtractor extends CommonRunner {
    public void run(String[] args) throws Exception {
        checkAndInitialize(args);

        apkContext.setClassNotFoundHandler(className -> Logger.getAnonymousLogger().warning("Class not found: " + className));

        String appName      = apk.getPackageName();
        String appVersion   = apk.getVersion();
        String appSdkMin    = String.valueOf(apk.getMinSDKVersion());
        String appSdkTrg    = String.valueOf(apk.getTargetSDKVersion());

        Map<String, Integer> data = new HashMap<>();
        for (IClass iClass : apkContext.getClassesInJar(false)) {
            ClassContext classContext = apkContext.resolveClassContext(iClass);

            for (IMethod iMethod : classContext.getNonAbstractMethods()) {
                MethodContext methodContext = classContext.resolveMethodContext(iMethod);

                if (methodContext.getIntermediateRepresentation() == null)
                    continue;

                for (SSAInstruction instruction : methodContext.getIntermediateRepresentation().getInstructions()) {
                    if (instruction instanceof SSAAbstractInvokeInstruction) {
                        SSAAbstractInvokeInstruction invokeInstruction = ((SSAAbstractInvokeInstruction) instruction);

                        String className = invokeInstruction.getDeclaredTarget().getDeclaringClass().getName().toString();
                        ClassContext calledClassContext = apkContext.resolveClassContext(className);

                        String calledMethodSelector = invokeInstruction.getDeclaredTarget().getSelector().toString();

                        String calledMethodSignature;
                        try {
                            MethodContext calledMethodContext = calledClassContext.resolveMethodContext(calledMethodSelector);
                            calledMethodSignature = calledMethodContext.getIMethod().getSignature();
                        } catch (RuntimeException e) {
                            calledMethodSignature = "<??? Not resolved ???>";
                        }

                        boolean toAdd = false;
                        for (String packageUnderAnalysis : PACKAGE_UNDER_ANALYSIS) {
                            if (calledMethodSignature.startsWith(packageUnderAnalysis))
                                toAdd = true;
                        }

                        if (toAdd) {
                            data.put(calledMethodSignature, data.getOrDefault(calledMethodSignature, 0) + 1);
                        }
                    }
                }
            }
        }

        try (PrintWriter writer = new PrintWriter(outputFile)) {
            writer.println("app\tversion\tapi\ttimes");
            for (Map.Entry<String, Integer> entry : data.entrySet()) {
                writer.println(appName + "\t" + appVersion + "\t" + entry.getKey() + "\t" + entry.getValue());
            }
        }
    }

    public static void main(String[] args) throws Exception {
        new AndroidApiExtractor().run(args);
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
