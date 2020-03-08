package it.unimol.acryl.runnable;

import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IMethod;
import it.unimol.acryl.analysis.VersionChecker;
import it.unimol.acryl.analysis.VersionDependentInstructionsExtractor;
import it.unimol.acryl.analysis.VersionMethodCache;
import it.unimol.acryl.graphs.IPCFG;
import it.unimol.acryl.graphs.SubCFG;
import it.unimol.acryl.lifetime.APILifetime;
import it.unimol.acryl.rules.Rule;
import it.unimol.acryl.rules.CombinedViolationDetector;
import it.unimol.acryl.rules.Ruleset;
import it.unimol.acryl.static_analysis.contexts.ClassContext;
import it.unimol.acryl.static_analysis.contexts.MethodContext;
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
    private static final int API_LEVEL = 27;

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

        int apiLevel;
        if (args.length >= 10) {
            apiLevel = Integer.parseInt(args[9]);
        } else {
            Logger.getAnonymousLogger().info("Using default API level 27");
            apiLevel = API_LEVEL;
        }

        File rulesFile = new File(args[5]);
        File lifetimeFile = new File(args[6]);

        boolean quick = false;
        boolean compress = false;
        for (int i = 10; i < args.length; i++) {
            if (args[i].equals("--quick"))
                quick = true;

            if (args[i].equals("--compress")) {
                Logger.getAnonymousLogger().info("Running in compression mode. Some warnings may be omitted.");
                compress = true;
            }
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
        CombinedViolationDetector detector = new CombinedViolationDetector(apk, apiLevel, apiLifetime, apkContext);

        Logger.getAnonymousLogger().info("Starting analysis...");
        VersionDependentInstructionsExtractor extractor = new VersionDependentInstructionsExtractor(cache);

        List<CombinedViolationDetector.RuleViolationReport> allReports = new ArrayList<>();

        for (IClass iClass : apkContext.getClassesInJar(false)) {
            ClassContext classContext = apkContext.resolveClassContext(iClass);

            if (classContext.isForcingDetectionSkip()) {
                Logger.getAnonymousLogger().info("Forced detection skipped for class " + iClass.getName().toString());
                continue;
            }

            // Skip classes belonging to the packages under analysis
            boolean skipClass = false;
            String className = iClass.getName().toString();
            for (String packageUnderAnalysis : PACKAGE_UNDER_ANALYSIS) {
                if (className.startsWith("L" + packageUnderAnalysis.replace('.', '/'))) {
                    skipClass = true;
                    break;
                }
            }
            if (skipClass) {
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

                    Collection<String> apis = ipcfg.getCalledAPIs(CommonRunner.PACKAGE_UNDER_ANALYSIS);
                    List<CombinedViolationDetector.RuleViolationReport> reports = new ArrayList<>();
                    for (Rule rule : ruleset.matchingRules(apis)) {
                        CombinedViolationDetector.RuleViolationReport report = detector.violatesRule(methodContext, entry.getKey(), rule, apis);
                        if (report != null)
                            reports.add(report);
                    }

                    if (compress) {
                        CombinedViolationDetector.RuleViolationReport bestReport = reports.stream().min((v1, v2) -> {
                            int comparison = Integer.compare(v1.getViolationPriority(), v2.getViolationPriority());

                            if (comparison == 0)
                                comparison = -Double.compare(v1.getConfidence(), v2.getConfidence());

                            return comparison;
                        }).orElse(null);

                        if (bestReport != null) {
                            bestReport.setMethodContext(methodContext);
                            bestReport.setMinLine(entry.getValue().getMinLine());
                            bestReport.setMaxLine(entry.getValue().getMaxLine());

                            allReports.add(bestReport);
                            Logger.getAnonymousLogger().finest("Skipped " + (reports.size()-1) + " warnings thanks to compression");
                        }
                    } else {
                        for (CombinedViolationDetector.RuleViolationReport report : reports) {
                            report.setMethodContext(methodContext);
                            report.setMinLine(entry.getValue().getMinLine());
                            report.setMaxLine(entry.getValue().getMaxLine());

                            allReports.add(report);
                        }
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
            writer.println("app\tversion\tsdk_min\tsdk_trg\tmethod\tfromLine\ttoLine\tapis\talternative\truleCheck\twarning\tmessage\tconfidence");

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

                writer.print(StringUtils.join(report.getAlternativeApis(), "&"));
                writer.print("\t");

                writer.print(report.getCheck());
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
