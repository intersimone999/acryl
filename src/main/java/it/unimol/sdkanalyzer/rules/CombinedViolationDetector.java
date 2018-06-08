package it.unimol.sdkanalyzer.rules;

import it.unimol.sdkanalyzer.analysis.VersionChecker;
import it.unimol.sdkanalyzer.android.ApkContainer;
import it.unimol.sdkanalyzer.lifetime.APILife;
import it.unimol.sdkanalyzer.lifetime.APILifetime;
import it.unimol.sdkanalyzer.rules.detectors.SingleRuleViolationDetector;
import it.unimol.sdkanalyzer.rules.detectors.backward.BackwardCompatibilityBadSmellDetector;
import it.unimol.sdkanalyzer.rules.detectors.backward.BackwardCompatibilityBugDetector;
import it.unimol.sdkanalyzer.rules.detectors.backward.BackwardCompatibilityImprovementDetector;
import it.unimol.sdkanalyzer.rules.detectors.comparison.WrongCheckDetector;
import it.unimol.sdkanalyzer.rules.detectors.forward.ForwardCompatibilityBadSmellDetector;
import it.unimol.sdkanalyzer.rules.detectors.forward.ForwardCompatibilityBugDetector;
import it.unimol.sdkanalyzer.rules.detectors.forward.ForwardCompatibilityImprovementDetector;
import it.unimol.sdkanalyzer.static_analysis.contexts.JarContext;
import it.unimol.sdkanalyzer.static_analysis.contexts.MethodContext;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.util.Collection;
import java.util.List;

/**
 * Set of detection rules
 * @author Simone Scalabrino.
 */
public class CombinedViolationDetector {
    private ApkContainer apk;
    private APILifetime apiLifetime;
    private final SingleRuleViolationDetector[] detectors;

    public CombinedViolationDetector(ApkContainer apk, APILifetime apiLifetime, JarContext context) {
        this.apk = apk;
        this.apiLifetime = apiLifetime;
        this.detectors = new SingleRuleViolationDetector[] {
                new ForwardCompatibilityBugDetector(apiLifetime),
                new BackwardCompatibilityBugDetector(apiLifetime),

                new ForwardCompatibilityBadSmellDetector(context),

                new ForwardCompatibilityImprovementDetector(apiLifetime),
                new BackwardCompatibilityImprovementDetector(apiLifetime),

                new WrongCheckDetector()
        };
    }

    public RuleViolationReport violatesRule(MethodContext methodContext, VersionChecker codeCheck, Rule rule, Collection<String> apisInCode) throws IOException {
        for (SingleRuleViolationDetector detector : this.detectors) {
            if (detector.violatesRule(apk, methodContext, codeCheck, rule, apisInCode)) {
                VersionChecker ruleCheck = rule.getChecker().copy();
                Collection<String> alternativeApis;
                Collection<String> matchedApis;
                if (apisInCode.containsAll(rule.getFalseApis()) && rule.getFalseApis().size() > 0) {
                    ruleCheck.invertComparator(true);

                    alternativeApis = rule.getTrueApis();
                    matchedApis     = rule.getFalseApis();
                } else {
                    alternativeApis = rule.getFalseApis();
                    matchedApis     = rule.getTrueApis();
                }

                assert matchedApis.size() > 0;

                return detector.buildReport(apk, rule, ruleCheck, codeCheck, matchedApis, alternativeApis);
            }
        }

        return null;
    }

//    @SuppressWarnings("ConstantConditions")
//    public RuleViolationReport violatesRule(VersionChecker codeCheck, Rule rule, Collection<String> apisInCode) throws IOException {
//        VersionChecker ruleCheck = rule.getChecker().copy();
//        Collection<String> alternativeApis;
//        Collection<String> matchedApis;
//        if (apisInCode.containsAll(rule.getFalseApis()) && !apisInCode.containsAll(rule.getTrueApis())) {
//            ruleCheck.invertComparator(true);
//
//            alternativeApis = rule.getTrueApis();
//            matchedApis     = rule.getFalseApis();
//        } else {
//            alternativeApis = rule.getFalseApis();
//            matchedApis     = rule.getTrueApis();
//        }
//
//        String alternativeApisString = StringUtils.join(alternativeApis, "&");
//
//        boolean isRuleUsedInNewVersions        = ruleCheck.getComparator().equals(VersionChecker.Comparator.GE) || ruleCheck.getComparator().equals(VersionChecker.Comparator.GT);
//        boolean inRuleUsedInOldVersions        = ruleCheck.getComparator().equals(VersionChecker.Comparator.LE) || ruleCheck.getComparator().equals(VersionChecker.Comparator.LT);
//        boolean inRuleUsedInExactVersion       = ruleCheck.getComparator().equals(VersionChecker.Comparator.EQ) || ruleCheck.getComparator().equals(VersionChecker.Comparator.NE);
//
//        for (String matchedApi : matchedApis) {
//            APILife life = this.apiLifetime.getLifeFor(matchedApi);
//            assert life != null;
//
//            if (life.getMaxVersion() != -1)
//                anyDeprecatedApi = true;
//            else if (life.getMinVersion() > this.apk.getMinSDKVersion())
//                anyInexistentApi = true;
//        }
//
//        boolean isRuleVersionSupported  = this.apk.getMinSDKVersion() <= ruleCheck.getCheckedVersion();
//
//        if (codeCheck == null) {
//            if (inRuleUsedInOldVersions && isRuleVersionSupported)
//                return buildReportForCritical(rule, ruleCheck, matchedApis, alternativeApis);
//
//            if (isRuleUsedInNewVersions && !isRuleVersionSupported)
//                return new RuleViolationReport(
//                        RuleViolation.BackMigration,
//                        String.format(MESSAGE_BACKMIGRATION, ruleCheck.getCheckedVersion(), alternativeApisString),
//                        rule.getConfidence(),
//                        matchedApis
//                );
//
//            if (isRuleUsedInNewVersions && isRuleVersionSupported)
//                return buildReportForWarning(rule, ruleCheck, matchedApis, alternativeApis);
//        } else {
//            boolean inCodeUsedInNewVersions  = codeCheck.getComparator().equals(VersionChecker.Comparator.GE) || codeCheck.getComparator().equals(VersionChecker.Comparator.GT);
//            boolean inCodeUsedInOldVersions  = codeCheck.getComparator().equals(VersionChecker.Comparator.LE) || codeCheck.getComparator().equals(VersionChecker.Comparator.LT);
//            boolean inCodeUsedInExactVersion = codeCheck.getComparator().equals(VersionChecker.Comparator.EQ) || codeCheck.getComparator().equals(VersionChecker.Comparator.NE);
//
//            if (inRuleUsedInExactVersion != inCodeUsedInExactVersion ||
//                    inRuleUsedInOldVersions != inCodeUsedInOldVersions ||
//                    isRuleUsedInNewVersions != inCodeUsedInNewVersions)
//                return new RuleViolationReport(
//                        RuleViolation.WrongComparison,
//                        String.format(MESSAGE_WCHECK, codeCheck.toString(), ruleCheck.toString()),
//                        rule.getConfidence(),
//                        matchedApis
//                );
//
//            int ruleCheckedVersion = ruleCheck.getCheckedVersion();
//            int codeCheckedVersion = codeCheck.getCheckedVersion();
//
//            // At this point, they are different only if one is with equal and one does not (e.g., <= and <)
//            // This block normalizes the different version numbers
//            if (codeCheck.getComparator() != ruleCheck.getComparator()) {
//                switch (codeCheck.getComparator()) {
//                    case GT:
//                        codeCheckedVersion++;
//                        break;
//                    case LT:
//                        codeCheckedVersion--;
//                        break;
//                    case GE:
//                        codeCheckedVersion--;
//                        break;
//                    case LE:
//                        codeCheckedVersion++;
//                        break;
//                    default:
//                        assert false : "This should not be possible";
//                        return new RuleViolationReport(
//                                RuleViolation.WrongComparison,
//                                "Unexpected report.",
//                                -100,
//                                matchedApis
//                    );
//                }
//            }
//
//            if (ruleCheckedVersion != codeCheckedVersion)
//                return new RuleViolationReport(
//                        RuleViolation.ChangeVersionCheck,
//                        String.format(MESSAGE_VERSION, codeCheck.toString(), ruleCheck.toString()),
//                        rule.getConfidence(),
//                        matchedApis
//                );
//        }
//
//        return null;
//    }
//
//    private RuleViolationReport buildReportForCritical(Rule rule, VersionChecker ruleCheck, Collection<String> apisUsed, Collection<String> alternativeApis) {
//        StringBuilder messageBuilder = new StringBuilder("[Critical] ");
//        if (alternativeApis.containsAll(apisUsed)) {
//            // Wrong Contextual Usage
//
//            messageBuilder.append("The APIs you are using should be used in a different way in newer Android releases(")
//                    .append(ruleCheck.getInverse(true).toString())
//                    .append("). ")
//                    .append("Consider using: ")
//                    .append(StringUtils.join(alternativeApis, " --- "))
//                    .append(".");
//        } else if (alternativeApis.size() == 0) {
//            // Case of Version-Specific Control
//            messageBuilder.append("The APIs you are using should be used only in older Android versions (")
//                    .append(ruleCheck.toString())
//                    .append("). ")
//                    .append("Consider adding a check. ")
//                    .append(StringUtils.join(alternativeApis, " --- "))
//                    .append(".");
//        } else {
//            messageBuilder.append(String.format(MESSAGE_CRITICAL, ruleCheck.getCheckedVersion(), StringUtils.join(alternativeApis, " --- ")));
//        }
//        return new RuleViolationReport(
//                RuleViolation.CriticalBug,
//                messageBuilder.toString(),
//                rule.getConfidence(),
//                apisUsed
//        );
//    }
//
//    private RuleViolationReport buildReportForWarning(Rule rule, VersionChecker ruleCheck, Collection<String> apisUsed, Collection<String> alternativeApis) {
//        StringBuilder messageBuilder = new StringBuilder("[Warning] ");
//        if (alternativeApis.containsAll(apisUsed)) {
//            // Wrong Contextual Usage
//
//            messageBuilder.append("The APIs you are using should be used in a different way in older Android releases(")
//                    .append(ruleCheck.getInverse(true).toString())
//                    .append("). ")
//                    .append("Consider using: ")
//                    .append(StringUtils.join(alternativeApis, " --- "))
//                    .append(".");
//        } else if (alternativeApis.size() == 0) {
//            // Case of Version-Specific Control
//            messageBuilder.append("The APIs you are using should be used only in newer Android versions (")
//                    .append(ruleCheck.toString())
//                    .append("). ")
//                    .append("Consider adding a check. ")
//                    .append(StringUtils.join(alternativeApis, " --- "))
//                    .append(".");
//        } else {
//            messageBuilder.append(String.format(MESSAGE_WARNING, ruleCheck.getCheckedVersion(), StringUtils.join(alternativeApis, " --- ")));
//        }
//        return new RuleViolationReport(
//                RuleViolation.Warning,
//                messageBuilder.toString(),
//                rule.getConfidence(),
//                apisUsed
//        );
//    }

    public static class RuleViolationReport {
        private RuleViolation violation;
        private String message;
        private double confidence;
        private Collection<String> ruleApisMismatch;
        private MethodContext methodContext;
        private int minLine;
        private int maxLine;

        public RuleViolationReport(RuleViolation violation, String message, double confidence, Collection<String> ruleApisMismatch) {
            this.violation  = violation;
            this.message    = message;
            this.confidence = confidence;
            this.ruleApisMismatch = ruleApisMismatch;
        }

        public RuleViolationReport(RuleViolation violation, double confidence, Collection<String> ruleApisMismatch) {
            this(violation, "", confidence, ruleApisMismatch);
        }

        public RuleViolation getViolation() {
            return violation;
        }

        public String getMessage() {
            return message;
        }

        public double getConfidence() {
            return confidence;
        }

        public Collection<String> getRuleApisMismatch() {
            return ruleApisMismatch;
        }

        public void setRuleApisMismatch(Collection<String> ruleApisMismatch) {
            this.ruleApisMismatch = ruleApisMismatch;
        }

        public int getViolationPriority() {
            int priority;
            if (this.getViolation().toString().endsWith("CriticalBug"))
                priority = 0;
            else if (this.getViolation().toString().endsWith("BadSmell"))
                priority = 2;
            else if (this.getViolation().toString().endsWith("Improvement"))
                priority = 4;
            else
                priority = 8;

            if (this.getViolation().toString().startsWith("Backward"))
                priority += 1;

            return priority;
        }

        public int getMinLine() {
            return minLine;
        }

        public void setMinLine(int minLine) {
            this.minLine = minLine;
        }

        public int getMaxLine() {
            return maxLine;
        }

        public void setMaxLine(int maxLine) {
            this.maxLine = maxLine;
        }

        public MethodContext getMethodContext() {
            return methodContext;
        }

        public void setMethodContext(MethodContext methodContext) {
            this.methodContext = methodContext;
        }
    }

    public enum RuleViolation {
        BackwardCriticalBug,
        BackwardBadSmell,
        BackwardImprovement,

        ForwardCriticalBug,
        ForwardBadSmell,
        ForwardImprovement,

        WrongCheck
//        CriticalBug,
//        Warning,
//        BackMigration,
//        WrongComparison,
//        ChangeVersionCheck
    }
}