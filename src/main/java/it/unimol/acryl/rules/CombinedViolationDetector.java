package it.unimol.acryl.rules;

import it.unimol.acryl.analysis.VersionChecker;
import it.unimol.acryl.android.ApkContainer;
import it.unimol.acryl.lifetime.APILifetime;
import it.unimol.acryl.rules.detectors.SingleRuleViolationDetector;
import it.unimol.acryl.rules.detectors.backward.BackwardCompatibilityBugDetector;
import it.unimol.acryl.rules.detectors.backward.BackwardCompatibilityImprovementDetector;
import it.unimol.acryl.rules.detectors.comparison.WrongCheckDetector;
import it.unimol.acryl.rules.detectors.forward.ForwardCompatibilityBadSmellDetector;
import it.unimol.acryl.rules.detectors.forward.ForwardCompatibilityBugDetector;
import it.unimol.acryl.rules.detectors.forward.ForwardCompatibilityImprovementDetector;
import it.unimol.acryl.static_analysis.contexts.JarContext;
import it.unimol.acryl.static_analysis.contexts.MethodContext;

import java.io.IOException;
import java.util.Collection;

/**
 * Set of detection rules
 * @author Simone Scalabrino.
 */
public class CombinedViolationDetector {
    private final ApkContainer apk;
    private final SingleRuleViolationDetector[] detectors;

    public CombinedViolationDetector(ApkContainer apk, int apiLevel, APILifetime apiLifetime, JarContext context) {
        this.apk = apk;
        this.detectors = new SingleRuleViolationDetector[] {
                new ForwardCompatibilityBugDetector(apiLevel, apiLifetime),
                new BackwardCompatibilityBugDetector(apiLifetime),

                new ForwardCompatibilityBadSmellDetector(context),
//                new BackwardCompatibilityBadSmellDetector(apiLifetime),

                new ForwardCompatibilityImprovementDetector(apiLevel, apiLifetime),
                new BackwardCompatibilityImprovementDetector(apiLifetime),

                new WrongCheckDetector(),

//                new MissingSpecificVersionCheckDetector(),
//                new MissingVersionToOmitDetector()
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

    public static class RuleViolationReport {
        private final RuleViolation violation;
        private final String message;
        private final double confidence;
        private final String check;
        private Collection<String> ruleApisMismatch;
        private Collection<String> alternativeApis;
        private MethodContext methodContext;
        private int minLine;
        private int maxLine;

        public RuleViolationReport(RuleViolation violation, String message, double confidence, Collection<String> ruleApisMismatch, Collection<String> alternativeApis, String check) {
            this.violation  = violation;
            this.message    = message;
            this.confidence = confidence;
            this.ruleApisMismatch = ruleApisMismatch;
            this.alternativeApis = alternativeApis;
            this.check = check;
        }

        public RuleViolationReport(RuleViolation violation, double confidence, Collection<String> ruleApisMismatch, Collection<String> alternativeApis, String check) {
            this(violation, "", confidence, ruleApisMismatch, alternativeApis, check);
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

        public String getCheck() {
            return check;
        }

        public MethodContext getMethodContext() {
            return methodContext;
        }

        public void setMethodContext(MethodContext methodContext) {
            this.methodContext = methodContext;
        }

        public Collection<String> getAlternativeApis() {
            return this.alternativeApis;
        }
    }

    public enum RuleViolation {
        BackwardCriticalBug,
        BackwardBadSmell,
        BackwardImprovement,

        ForwardCriticalBug,
        ForwardBadSmell,
        ForwardImprovement,

        WrongCheck,

        MissingSpecificCheck
//        CriticalBug,
//        Warning,
//        BackMigration,
//        WrongComparison,
//        ChangeVersionCheck
    }
}
