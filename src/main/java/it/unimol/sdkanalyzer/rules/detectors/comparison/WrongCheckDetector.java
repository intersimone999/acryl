package it.unimol.sdkanalyzer.rules.detectors.comparison;

import it.unimol.sdkanalyzer.analysis.VersionChecker;
import it.unimol.sdkanalyzer.android.ApkContainer;
import it.unimol.sdkanalyzer.rules.Rule;
import it.unimol.sdkanalyzer.rules.CombinedViolationDetector;
import it.unimol.sdkanalyzer.rules.detectors.SingleRuleViolationDetector;
import it.unimol.sdkanalyzer.static_analysis.contexts.MethodContext;

import java.util.Collection;
import java.util.logging.Level;
import java.util.logging.Logger;

import static it.unimol.sdkanalyzer.analysis.VersionChecker.Comparator.*;

/**
 * @author Simone Scalabrino.
 */
public class WrongCheckDetector extends SingleRuleViolationDetector {
    private static final String MESSAGE_WRONG_VERSION = "[Critical] You use a wrong version checking. You should check for %s, but you check for %s";

    @Override
    public boolean violatesRule(ApkContainer apk, MethodContext methodContext, VersionChecker codeCheck, Rule rule, Collection<String> apisInCode) {
        VersionChecker ruleCheck = rule.getChecker().copy();

        if (codeCheck.isNull())
            return false;

        if (apisInCode.containsAll(rule.getFalseApis()) && rule.getFalseApis().size() > 0) {
            ruleCheck.invertComparator(true);
        } else {
            if (rule.getTrueApis().size() == 0)
                return false;
        }

        boolean shouldBeUsedInNewVersions   = ruleCheck.getComparator().equals(GE) || ruleCheck.getComparator().equals(GT);
        boolean shouldBeUsedInOldVersions   = ruleCheck.getComparator().equals(LE) || ruleCheck.getComparator().equals(LT);
        boolean shouldBeUsedInExactVersions = ruleCheck.getComparator().equals(EQ) || ruleCheck.getComparator().equals(NE);

        boolean actuallyUsedInNewVersions   = codeCheck.getComparator().equals(GE) || codeCheck.getComparator().equals(GT);
        boolean actuallyUsedInOldVersions   = codeCheck.getComparator().equals(LE) || codeCheck.getComparator().equals(LT);
        boolean actuallyUsedInExactVersions = codeCheck.getComparator().equals(EQ) || codeCheck.getComparator().equals(NE);

        if (shouldBeUsedInExactVersions != actuallyUsedInExactVersions ||
                shouldBeUsedInOldVersions != actuallyUsedInOldVersions ||
                shouldBeUsedInNewVersions != actuallyUsedInNewVersions) {
            Logger.getAnonymousLogger().log(
                    Level.WARNING,
                    "There was a strange check in the app: " +
                            codeCheck.toString() +
                            " instead of " +
                            ruleCheck.toString()
            );
            return false;
        }

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
                    Logger.getAnonymousLogger().log(
                            Level.SEVERE,
                            "There was a severe issue with WrongCheckDetector. Skipping..."
                    );
                    return false;
            }
        }

        return ruleCheckedVersion != codeCheckedVersion;
    }

    public CombinedViolationDetector.RuleViolationReport buildReport(
            ApkContainer apk,
            Rule rule,
            VersionChecker checkToImplement,
            VersionChecker actualCheck,
            Collection<String> usedApis,
            Collection<String> alternativeApis
    ) {
//        boolean shouldBeUsedInNewVersions  = checkToImplement.getComparator().equals(GE) || checkToImplement.getComparator().equals(GT);
//        boolean shouldBeUsedInOldVersions  = checkToImplement.getComparator().equals(LE) || checkToImplement.getComparator().equals(LT);
//        boolean shouldBeUsedInExactVersions = checkToImplement.getComparator().equals(EQ) || checkToImplement.getComparator().equals(NE);
//
//        boolean actuallyUsedInNewVersions   = actualCheck.getComparator().equals(GE) || actualCheck.getComparator().equals(GT);
//        boolean actuallyUsedInOldVersions   = actualCheck.getComparator().equals(LE) || actualCheck.getComparator().equals(LT);
//        boolean actuallyUsedInExactVersions = actualCheck.getComparator().equals(EQ) || actualCheck.getComparator().equals(NE);

//        if (shouldBeUsedInExactVersions != actuallyUsedInExactVersions ||
//                shouldBeUsedInOldVersions != actuallyUsedInOldVersions ||
//                shouldBeUsedInNewVersions != actuallyUsedInNewVersions)
//            return new CombinedViolationDetector.RuleViolationReport(
//                    CombinedViolationDetector.RuleViolation.WrongCheck,
//                    String.format(MESSAGE_WRONG_CHECK, actualCheck.toString(), checkToImplement.toString()),
//                    rule.getConfidence(),
//                    usedApis
//            );

        int ruleCheckedVersion = checkToImplement.getCheckedVersion();
        int codeCheckedVersion = actualCheck.getCheckedVersion();

        // At this point, they are different only if one is with equal and one does not (e.g., <= and <)
        // This block normalizes the different version numbers
        if (actualCheck.getComparator() != checkToImplement.getComparator()) {
            switch (actualCheck.getComparator()) {
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
            }
        }

        if (ruleCheckedVersion != codeCheckedVersion)
            new CombinedViolationDetector.RuleViolationReport(
                    CombinedViolationDetector.RuleViolation.WrongCheck,
                    String.format(MESSAGE_WRONG_VERSION, actualCheck.toString(), checkToImplement.toString()),
                    rule.getConfidence(),
                    usedApis
            );

        assert false : "This should not be possible";

        return null;
    }
}
