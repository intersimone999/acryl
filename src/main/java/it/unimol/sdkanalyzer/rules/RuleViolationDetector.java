package it.unimol.sdkanalyzer.rules;

import it.unimol.sdkanalyzer.analysis.VersionChecker;
import it.unimol.sdkanalyzer.android.ApkContainer;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.util.Collection;

/**
 * Set of detection rules
 * @author Simone Scalabrino.
 */
public class RuleViolationDetector {
    private static final String MESSAGE_CRITICAL      = "You should use this APIs differently from SDK version %d. Add a check and handle with: %s";
    private static final String MESSAGE_WARNING       = "You should use this APIs differently for SDK versions older than %d. Add a check and handle with: %s";

    private static final String MESSAGE_BACKMIGRATION = "[Info] If you want to support older versions (<= %d), add a check and handle using these APIs: %s";

    private static final String MESSAGE_WCHECK        = "[Critical] Do not use check %s; use %s instead";

    private static final String MESSAGE_VERSION       = "[Critical] You use a wrong version checking. You should check for %s, but you check for %s";

    private ApkContainer apk;

    public RuleViolationDetector(ApkContainer apk) {
        this.apk = apk;
    }

    @SuppressWarnings("ConstantConditions")
    public RuleViolationReport violatesRule(VersionChecker codeCheck, Rule rule, Collection<String> apisInCode) throws IOException {
        VersionChecker ruleCheck = rule.getChecker().copy();
        Collection<String> alternativeApis;
        Collection<String> matchedApis;
        if (apisInCode.containsAll(rule.getFalseApis()) && !apisInCode.containsAll(rule.getTrueApis())) {
            ruleCheck.invertComparator(true);

            alternativeApis = rule.getTrueApis();
            matchedApis     = rule.getFalseApis();
        } else {
            alternativeApis = rule.getFalseApis();
            matchedApis     = rule.getTrueApis();
        }

        String alternativeApisString = StringUtils.join(alternativeApis, "&");

        boolean inRuleUsedInNewVersions        = ruleCheck.getComparator().equals(VersionChecker.Comparator.GE) || ruleCheck.getComparator().equals(VersionChecker.Comparator.GT);
        boolean inRuleUsedInOldVersions        = ruleCheck.getComparator().equals(VersionChecker.Comparator.LE) || ruleCheck.getComparator().equals(VersionChecker.Comparator.LT);
        boolean inRuleUsedInExactVersion       = ruleCheck.getComparator().equals(VersionChecker.Comparator.EQ) || ruleCheck.getComparator().equals(VersionChecker.Comparator.NE);

        boolean isRuleVersionSupported  = this.apk.getMinSDKVersion() <= ruleCheck.getCheckedVersion();

        if (codeCheck == null) {
            if (inRuleUsedInOldVersions && isRuleVersionSupported)
                return buildReportForCritical(rule, ruleCheck, matchedApis, alternativeApis);

            if (inRuleUsedInNewVersions && !isRuleVersionSupported)
                return new RuleViolationReport(
                        RuleViolation.BackMigration,
                        String.format(MESSAGE_BACKMIGRATION, ruleCheck.getCheckedVersion(), alternativeApisString),
                        rule.getConfidence(),
                        matchedApis
                );

            if (inRuleUsedInNewVersions && isRuleVersionSupported)
                return buildReportForWarning(rule, ruleCheck, matchedApis, alternativeApis);
        } else {
            boolean inCodeUsedInNewVersions  = codeCheck.getComparator().equals(VersionChecker.Comparator.GE) || codeCheck.getComparator().equals(VersionChecker.Comparator.GT);
            boolean inCodeUsedInOldVersions  = codeCheck.getComparator().equals(VersionChecker.Comparator.LE) || codeCheck.getComparator().equals(VersionChecker.Comparator.LT);
            boolean inCodeUsedInExactVersion = codeCheck.getComparator().equals(VersionChecker.Comparator.EQ) || codeCheck.getComparator().equals(VersionChecker.Comparator.NE);

            if (inRuleUsedInExactVersion != inCodeUsedInExactVersion ||
                    inRuleUsedInOldVersions != inCodeUsedInOldVersions ||
                    inRuleUsedInNewVersions != inCodeUsedInNewVersions)
                return new RuleViolationReport(
                        RuleViolation.WrongComparison,
                        String.format(MESSAGE_WCHECK, codeCheck.toString(), ruleCheck.toString()),
                        rule.getConfidence(),
                        matchedApis
                );

            int ruleCheckedVersion = ruleCheck.getCheckedVersion();
            int codeCheckedVersion = codeCheck.getCheckedVersion();

            // At this point, they are different only if one is with equal and one does not (e.g., <= and <)
            // This block normalizes the different version numbers
            if (codeCheck.getComparator() != ruleCheck.getComparator()) {
                switch (codeCheck.getComparator()) {
                    case GT:
                        codeCheckedVersion++;
                        break;
                    case LT:
                        codeCheckedVersion--;
                        break;
                    case GE:
                        codeCheckedVersion--;
                        break;
                    case LE:
                        codeCheckedVersion++;
                        break;
                    default:
                        assert false : "This should not be possible";
                        return new RuleViolationReport(
                                RuleViolation.WrongComparison,
                                "Unexpected report.",
                                -100,
                                matchedApis
                    );
                }
            }

            if (ruleCheckedVersion != codeCheckedVersion)
                return new RuleViolationReport(
                        RuleViolation.ChangeVersionCheck,
                        String.format(MESSAGE_VERSION, codeCheck.toString(), ruleCheck.toString()),
                        rule.getConfidence(),
                        matchedApis
                );
        }

        return null;
    }

    private RuleViolationReport buildReportForCritical(Rule rule, VersionChecker ruleCheck, Collection<String> apisUsed, Collection<String> alternativeApis) {
        StringBuilder messageBuilder = new StringBuilder("[Critical] ");
        if (alternativeApis.containsAll(apisUsed)) {
            // Wrong Contextual Usage

            messageBuilder.append("The APIs you are using should be used in a different way in newer Android releases(")
                    .append(ruleCheck.getInverse(true).toString())
                    .append("). ")
                    .append("Consider using: ")
                    .append(StringUtils.join(alternativeApis, " --- "))
                    .append(".");
        } else if (alternativeApis.size() == 0) {
            // Case of Version-Specific Control
            messageBuilder.append("The APIs you are using should be used only in older Android versions (")
                    .append(ruleCheck.toString())
                    .append("). ")
                    .append("Consider adding a check. ")
                    .append(StringUtils.join(alternativeApis, " --- "))
                    .append(".");
        } else {
            messageBuilder.append(String.format(MESSAGE_CRITICAL, ruleCheck.getCheckedVersion(), StringUtils.join(alternativeApis, " --- ")));
        }
        return new RuleViolationReport(
                RuleViolation.CriticalBug,
                messageBuilder.toString(),
                rule.getConfidence(),
                apisUsed
        );
    }

    private RuleViolationReport buildReportForWarning(Rule rule, VersionChecker ruleCheck, Collection<String> apisUsed, Collection<String> alternativeApis) {
        StringBuilder messageBuilder = new StringBuilder("[Warning] ");
        if (alternativeApis.containsAll(apisUsed)) {
            // Wrong Contextual Usage

            messageBuilder.append("The APIs you are using should be used in a different way in older Android releases(")
                    .append(ruleCheck.getInverse(true).toString())
                    .append("). ")
                    .append("Consider using: ")
                    .append(StringUtils.join(alternativeApis, " --- "))
                    .append(".");
        } else if (alternativeApis.size() == 0) {
            // Case of Version-Specific Control
            messageBuilder.append("The APIs you are using should be used only in newer Android versions (")
                    .append(ruleCheck.toString())
                    .append("). ")
                    .append("Consider adding a check. ")
                    .append(StringUtils.join(alternativeApis, " --- "))
                    .append(".");
        } else {
            messageBuilder.append(String.format(MESSAGE_WARNING, ruleCheck.getCheckedVersion(), StringUtils.join(alternativeApis, " --- ")));
        }
        return new RuleViolationReport(
                RuleViolation.Warning,
                messageBuilder.toString(),
                rule.getConfidence(),
                apisUsed
        );
    }

    public static class RuleViolationReport {
        private RuleViolation violation;
        private String message;
        private double confidence;
        private Collection<String> ruleApisMismatch;

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
    }

    public enum RuleViolation {
        CriticalBug,
        Warning,
        BackMigration,
        WrongComparison,
        ChangeVersionCheck
    }
}
