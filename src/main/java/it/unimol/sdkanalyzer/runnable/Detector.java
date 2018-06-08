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
import java.util.logging.Logger;

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

        double minConfidence;
        if (args.length >= 8) {
            minConfidence = Double.parseDouble(args[7]);
        } else {
            Logger.getAnonymousLogger().info("Using default minimum confidence: 0");
            minConfidence = 2;
        }

        int minApps;
        if (args.length >= 9) {
            minApps = Integer.parseInt(args[8]);
        } else {
            Logger.getAnonymousLogger().info("Using default minimum confidence: 10");
            minApps = 10;
        }


        File rulesFile = new File(args[5]);
        File lifetimeFile = new File(args[6]);

        boolean quick = false;
        if (args.length == 10 && args[9].equals("-quick")) {
            quick = true;
        }

        apkContext.setClassNotFoundHandler(
                className -> Logger.getAnonymousLogger().warning("Class not found: " + className)
        );

        VersionMethodCache cache = new VersionMethodCache(apkContext);
        if (!quick) {
            Logger.getAnonymousLogger().info("Labeling methods...");
            cache.build();
            Logger.getAnonymousLogger().info("All method labeled!");
        } else {
            Logger.getAnonymousLogger().info("Quick execution: skipped method labeling.");
        }

        String appName      = apk.getPackageName();
        String appVersion   = apk.getVersion();
        String appSdkMin    = String.valueOf(apk.getMinSDKVersion());
        String appSdkTrg    = String.valueOf(apk.getTargetSDKVersion());

        APILifetime apiLifetime = APILifetime.load(lifetimeFile);

        Ruleset ruleset = new Ruleset(rulesFile, minApps, minConfidence);
        CombinedViolationDetector detector = new CombinedViolationDetector(apk, apiLifetime, apkContext);

        Logger.getAnonymousLogger().info("Starting analysis...");
        VersionDependentInstructionsExtractor extractor = new VersionDependentInstructionsExtractor(cache);

        List<CombinedViolationDetector.RuleViolationReport> allReports = new ArrayList<>();

        for (IClass iClass : apkContext.getClassesInJar(false)) {
            ClassContext classContext = apkContext.resolveClassContext(iClass);

            if (classContext.isForcingDetectionSkip()) {
                Logger.getAnonymousLogger().info("Forced detection skipped for class " + iClass.getName().toString());
                continue;
            }

            for (IMethod iMethod : classContext.getNonAbstractMethods()) {
                MethodContext methodContext = classContext.resolveMethodContext(iMethod);
                if (methodContext.getIntermediateRepresentation() == null)
                    continue;

                if (methodContext.isForcingDetectionSkip()) {
                    Logger.getAnonymousLogger().info("Forced detection skipped for method " + iMethod.getSignature());
                    continue;
                }

                methodContext.getAugmentedSymbolTable().update(cache);

                Map<VersionChecker, SubCFG> versionDependentParts   = extractor.extractVersionDependentCFG(methodContext);
                Set<SubCFG> uncheckedBlocks                         = extractor.extractVersionIndependentCFGs(methodContext, versionDependentParts.values());
                for (SubCFG uncheckedBlock : uncheckedBlocks) {
                    versionDependentParts.put(new VersionChecker.NullChecker(), uncheckedBlock);
                }

                for (Map.Entry<VersionChecker, SubCFG> entry : versionDependentParts.entrySet()) {
                    IPCFG ipcfg = IPCFG.buildIPCFG(apkContext, entry.getValue(), false);
                    entry.getValue().setMethodContext(methodContext);

                    Collection<String> apis = ipcfg.getCalledAPIs(Collections.singletonList("android"));
                    List<CombinedViolationDetector.RuleViolationReport> reports = new ArrayList<>();
                    for (Rule rule : ruleset.matchingRules(apis)) {
                        CombinedViolationDetector.RuleViolationReport report = detector.violatesRule(methodContext, entry.getKey(), rule, apis);
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
        Logger.getAnonymousLogger().info("All done!");
    }
    public static void main(String[] args) throws Exception {
        new Detector().run(args);
    }
}
