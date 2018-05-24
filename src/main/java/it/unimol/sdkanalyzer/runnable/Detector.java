package it.unimol.sdkanalyzer.runnable;

import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IMethod;
import it.unimol.sdkanalyzer.analysis.VersionChecker;
import it.unimol.sdkanalyzer.analysis.VersionDependentInstructionsExtractor;
import it.unimol.sdkanalyzer.analysis.VersionMethodCache;
import it.unimol.sdkanalyzer.android.AndroidToolkit;
import it.unimol.sdkanalyzer.android.ApkContainer;
import it.unimol.sdkanalyzer.android.Dex2Jar;
import it.unimol.sdkanalyzer.graphs.IPCFG;
import it.unimol.sdkanalyzer.graphs.SubCFG;
import it.unimol.sdkanalyzer.rules.Rule;
import it.unimol.sdkanalyzer.rules.RuleViolationDetector;
import it.unimol.sdkanalyzer.rules.Ruleset;
import it.unimol.sdkanalyzer.static_analysis.contexts.ClassContext;
import it.unimol.sdkanalyzer.static_analysis.contexts.GlobalContext;
import it.unimol.sdkanalyzer.static_analysis.contexts.JarContext;
import it.unimol.sdkanalyzer.static_analysis.contexts.MethodContext;
import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;

/**
 * Detects problems in a APK
 * @author Simone Scalabrino.
 */
public class Detector extends CommonRunner {
    public void run(String[] args) throws Exception {
        checkAndInitialize(args);

        if (args.length < 6)
            throw new RuntimeException("Specify also the ruleset file as last parameter");


        int minConfidence;
        if (args.length >= 7) {
            minConfidence = Integer.parseInt(args[6]);
        } else {
            System.out.println("Using default minimum confidence: 5");
            minConfidence = 2;
        }


        File rulesFile = new File(args[5]);
        boolean quick = false;
        if (args.length == 8 && args[7].equals("-quick")) {
            quick = true;
        }

        apkContext.setClassNotFoundHandler(
                className -> System.err.println("Class not found: " + className)
        );

        VersionMethodCache cache = new VersionMethodCache(apkContext);
        if (!quick) {
            System.out.println("Labeling methods...");
            cache.build();
            System.out.println("All method labeled!");
        } else {
            System.out.println("Quick execution: skipped method labeling.");
        }

        String appName      = apk.getPackageName();
        String appVersion   = apk.getVersion();
        String appSdkMin    = String.valueOf(apk.getMinSDKVersion());
        String appSdkTrg    = String.valueOf(apk.getTargetSDKVersion());

        Ruleset ruleset = new Ruleset(rulesFile, minConfidence);
        RuleViolationDetector detector = new RuleViolationDetector(apk);

        System.out.println("Starting analysis...");
        try (PrintWriter writer = new PrintWriter(outputFile)) {
            writer.println("app\tversion\tsdk_min\tsdk_trg\tmethod\tfromLine\ttoLine\tapis\twarning\tmessage\tconfidence");

            VersionDependentInstructionsExtractor dependentInstructionsExtractor = new VersionDependentInstructionsExtractor(cache);


            for (IClass iClass : apkContext.getClassesInJar(false)) {
                ClassContext classContext = apkContext.resolveClassContext(iClass);

                for (IMethod iMethod : classContext.getNonAbstractMethods()) {
                    MethodContext methodContext = classContext.resolveMethodContext(iMethod);
                    methodContext.getAugmentedSymbolTable().update(cache);

                    Map<VersionChecker, SubCFG> versionDependentParts = dependentInstructionsExtractor.extractAllSubCFGs(methodContext);

                    if (versionDependentParts == null)
                        continue;

                    for (Map.Entry<VersionChecker, SubCFG> entry : versionDependentParts.entrySet()) {
                        IPCFG ipcfg = IPCFG.buildIPCFG(apkContext, entry.getValue());
                        entry.getValue().setMethodContext(methodContext);

                        Collection<String> apis = ipcfg.getCalledAPIs("android");
                        List<RuleViolationDetector.RuleViolationReport> reports = new ArrayList<>();
                        for (Rule rule : ruleset.matchingRules(apis)) {
                            RuleViolationDetector.RuleViolationReport report = detector.violatesRule(entry.getKey(), rule, apis);
                            if (report != null)
                                reports.add(report);
                        }

                        RuleViolationDetector.RuleViolationReport bestReport = reports.stream().min((v1, v2) -> {
                            int firstComparison = v1.getViolation().compareTo(v2.getViolation());
                            if (firstComparison != 0)
                                return firstComparison;
                            else
                                return -Double.compare(v1.getConfidence(), v2.getConfidence());
                        }).orElse(null);

                        if (bestReport != null) {
                            writer.print(appName);
                            writer.print("\t");

                            writer.print(appVersion);
                            writer.print("\t");

                            writer.print(appSdkMin);
                            writer.print("\t");

                            writer.print(appSdkTrg);
                            writer.print("\t");

                            writer.print(methodContext.getIMethod().getSignature());
                            writer.print("\t");

                            writer.print(entry.getValue().getMinLine());
                            writer.print("\t");

                            writer.print(entry.getValue().getMaxLine());
                            writer.print("\t");

                            writer.print(StringUtils.join(bestReport.getRuleApisMismatch(), "&"));
                            writer.print("\t");

                            writer.print(bestReport.getViolation().toString());
                            writer.print("\t");

                            writer.print("\"" + bestReport.getMessage() + "\"");
                            writer.print("\t");

                            writer.print(bestReport.getConfidence());
                            writer.print("\n");
                        }
                    }
                }
            }
        }
        System.out.println("All done!");
    }
    public static void main(String[] args) throws Exception {
        new Detector().run(args);
    }
}
