package it.unimol.sdkanalyzer.runnable;

import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IMethod;
import it.unimol.sdkanalyzer.analysis.VersionChecker;
import it.unimol.sdkanalyzer.analysis.VersionDependentInstructionsExtractor;
import it.unimol.sdkanalyzer.analysis.VersionMethodCache;
import it.unimol.sdkanalyzer.graphs.IPCFG;
import it.unimol.sdkanalyzer.graphs.SubCFG;
import it.unimol.sdkanalyzer.lifetime.APILifetime;
import it.unimol.sdkanalyzer.rules.Rule;
import it.unimol.sdkanalyzer.rules.CombinedViolationDetector;
import it.unimol.sdkanalyzer.rules.Ruleset;
import it.unimol.sdkanalyzer.static_analysis.contexts.ClassContext;
import it.unimol.sdkanalyzer.static_analysis.contexts.MethodContext;
import org.apache.commons.lang3.StringUtils;

import java.io.File;
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

        if (args.length < 7)
            throw new RuntimeException("Specify also the lifetime file as last parameter");

        int minConfidence;
        if (args.length >= 8) {
            minConfidence = Integer.parseInt(args[7]);
        } else {
            System.out.println("Using default minimum confidence: 5");
            minConfidence = 2;
        }


        File rulesFile = new File(args[5]);
        File lifetimeFile = new File(args[6]);

        boolean quick = false;
        if (args.length == 9 && args[8].equals("-quick")) {
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

        APILifetime apiLifetime = APILifetime.load(lifetimeFile);

        Ruleset ruleset = new Ruleset(rulesFile, minConfidence);
        CombinedViolationDetector detector = new CombinedViolationDetector(apk, apiLifetime);

        System.out.println("Starting analysis...");
        VersionDependentInstructionsExtractor dependentInstructionsExtractor = new VersionDependentInstructionsExtractor(cache);


        List<CombinedViolationDetector.RuleViolationReport> allReports = new ArrayList<>();

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
                    List<CombinedViolationDetector.RuleViolationReport> reports = new ArrayList<>();
                    for (Rule rule : ruleset.matchingRules(apis)) {
                        CombinedViolationDetector.RuleViolationReport report = detector.violatesRule(entry.getKey(), rule, apis);
                        if (report != null)
                            reports.add(report);
                    }

                    CombinedViolationDetector.RuleViolationReport bestReport = reports.stream().min((v1, v2) -> {
                        int firstComparison = v1.getViolation().compareTo(v2.getViolation());
                        if (firstComparison != 0)
                            return firstComparison;
                        else
                            return -Double.compare(v1.getConfidence(), v2.getConfidence());
                    }).orElse(null);

                    if (bestReport != null) {
                        bestReport.setMethodContext(methodContext);
                        bestReport.setMinLine(entry.getValue().getMinLine());
                        bestReport.setMaxLine(entry.getValue().getMaxLine());

                        allReports.add(bestReport);
                    }
                }
            }
        }

        allReports.sort((r1, r2) -> {
            int order = Integer.compare(r1.getViolationPriority(), r2.getViolationPriority());

            if (order == 0) {
                order = -Double.compare(r1.getConfidence(), r2.getConfidence());
            }

            return order;
        });


        try (PrintWriter writer = new PrintWriter(outputFile)) {
            writer.println("app\tversion\tsdk_min\tsdk_trg\tmethod\tfromLine\ttoLine\tapis\twarning\tmessage\tconfidence");

            for (CombinedViolationDetector.RuleViolationReport report : allReports) {
                //For each report...
                writer.print(appName);
                writer.print("\t");

                writer.print(appVersion);
                writer.print("\t");

                writer.print(appSdkMin);
                writer.print("\t");

                writer.print(appSdkTrg);
                writer.print("\t");

                writer.print(report.getMethodContext().getIMethod().getSignature());
                writer.print("\t");

                writer.print(report.getMinLine());
                writer.print("\t");

                writer.print(report.getMaxLine());
                writer.print("\t");

                writer.print(StringUtils.join(report.getRuleApisMismatch(), "&"));
                writer.print("\t");

                writer.print(report.getViolation().toString());
                writer.print("\t");

                writer.print("\"" + report.getMessage() + "\"");
                writer.print("\t");

                writer.print(report.getConfidence());
                writer.print("\n");
            }
        }
        System.out.println("All done!");
    }
    public static void main(String[] args) throws Exception {
        new Detector().run(args);
    }
}
