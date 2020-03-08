package it.unimol.acryl.rules.detectors.comparison;

import it.unimol.acryl.analysis.VersionChecker;
import it.unimol.acryl.android.ApkContainer;
import it.unimol.acryl.rules.CombinedViolationDetector;
import it.unimol.acryl.rules.Rule;
import it.unimol.acryl.rules.detectors.SingleRuleViolationDetector;
import it.unimol.acryl.static_analysis.contexts.MethodContext;
import org.apache.commons.lang3.StringUtils;

import java.util.Collection;

/**
 * @author Simone Scalabrino.
 */
@Deprecated
public class MissingVersionToOmitDetector extends SingleRuleViolationDetector {
    private static final String MESSAGE_WRONG_VERSION = "[Critical] You use a wrong version checking. You should check for %s, but you check for %s";

    @Override
    public boolean violatesRule(ApkContainer apk, MethodContext methodContext, VersionChecker codeCheck, Rule rule, Collection<String> apisInCode) {
        VersionChecker ruleCheck = rule.getChecker().copy();

        if (!codeCheck.isNull())
            return false;

        if (!rule.getChecker().getComparator().equals(VersionChecker.Comparator.NE))
            return false;

        if (apisInCode.containsAll(rule.getTrueApis()) && rule.getTrueApis().size() > 0) {
            return true;
        }

        return false;
    }

    public CombinedViolationDetector.RuleViolationReport buildReport(
            ApkContainer apk,
            Rule rule,
            VersionChecker checkToImplement,
            VersionChecker actualCheck,
            Collection<String> usedApis,
            Collection<String> alternativeApis
    ) {
        String alternativeApisString = StringUtils.join(alternativeApis, " --- ");
        StringBuilder messageBuilder = new StringBuilder("[Warning] ");
        if (alternativeApis.containsAll(usedApis)) {
            // Wrong Contextual Usage

            messageBuilder.append("The APIs you are using must be used in a different way in a specific Android release (")
                    .append(checkToImplement.getInverse(true).toString())
                    .append("). Use these APIs instead: ")
                    .append(alternativeApisString)
                    .append(".");
        } else if (alternativeApis.size() == 0) {
            // Case of Version-Specific Control
            messageBuilder.append("The APIs you are using must be used only in a specific Android version (")
                    .append(checkToImplement.getInverse(true).toString())
                    .append("). Check the Android version before using them. ");
        } else {
            messageBuilder.append("You should use different APIs in a specific Android version (")
                    .append(checkToImplement.getInverse(true).toString())
                    .append("). Use these APIs instead: ")
                    .append(alternativeApisString)
                    .append(".");
        }

        return new CombinedViolationDetector.RuleViolationReport(
                CombinedViolationDetector.RuleViolation.MissingSpecificCheck,
                messageBuilder.toString(),
                rule.getConfidence(),
                usedApis,
                alternativeApis,
                rule.getChecker().toString()
        );
    }
}
