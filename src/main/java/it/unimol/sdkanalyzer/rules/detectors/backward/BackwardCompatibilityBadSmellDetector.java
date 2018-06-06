package it.unimol.sdkanalyzer.rules.detectors.backward;

import it.unimol.sdkanalyzer.analysis.VersionChecker;
import it.unimol.sdkanalyzer.android.ApkContainer;
import it.unimol.sdkanalyzer.lifetime.APILife;
import it.unimol.sdkanalyzer.lifetime.APILifetime;
import it.unimol.sdkanalyzer.rules.Rule;
import it.unimol.sdkanalyzer.rules.CombinedViolationDetector;
import it.unimol.sdkanalyzer.rules.detectors.SingleRuleViolationDetector;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.util.Collection;

/**
 * @author Simone Scalabrino.
 */
public class BackwardCompatibilityBadSmellDetector extends SingleRuleViolationDetector {
    private static final String MESSAGE = "You should use this APIs differently for SDK versions older than %d. Add a check and handle with: %s";
    private final APILifetime apiLifetime;

    public BackwardCompatibilityBadSmellDetector(APILifetime apiLifetime) {
        this.apiLifetime = apiLifetime;
    }

    @Override
    public boolean violatesRule(ApkContainer apk, VersionChecker codeCheck, Rule rule, Collection<String> apisInCode) throws IOException {
        if (!apisInCode.containsAll(rule.getFalseApis()))
            return false;

        if (codeCheck != null)
            return false;

        if (apk.getMinSDKVersion() > rule.getChecker().getCheckedVersion())
            return false;

        for (String api : rule.getFalseApis()) {
            APILife apiLife = this.apiLifetime.getLifeFor(api);

            if (apiLife.getMinVersion() > apk.getMinSDKVersion())
                return false;
        }

        return true;
    }

    public CombinedViolationDetector.RuleViolationReport buildReport(
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

            messageBuilder.append("The APIs you are using should be used in a different way in older Android releases(")
                    .append(checkToImplement.toString())
                    .append("). ")
                    .append("Consider using: ")
                    .append(alternativeApisString)
                    .append(".");
        } else if (alternativeApis.size() == 0) {
            // Case of Version-Specific Control
            messageBuilder.append("The APIs you are using should be used only in newer Android versions (")
                    .append(checkToImplement.getInverse(true).toString())
                    .append("). ")
                    .append("Consider adding a check. ");
        } else {
            messageBuilder.append(String.format(MESSAGE, checkToImplement.getCheckedVersion(), alternativeApisString));
        }
        return new CombinedViolationDetector.RuleViolationReport(
                CombinedViolationDetector.RuleViolation.BackwardBadSmell,
                messageBuilder.toString(),
                rule.getConfidence(),
                usedApis
        );
    }
}
