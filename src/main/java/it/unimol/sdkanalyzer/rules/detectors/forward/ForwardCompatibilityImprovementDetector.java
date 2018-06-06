package it.unimol.sdkanalyzer.rules.detectors.forward;

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
public class ForwardCompatibilityImprovementDetector extends SingleRuleViolationDetector {
    private final APILifetime apiLifetime;

    public ForwardCompatibilityImprovementDetector(APILifetime apiLifetime) {
        this.apiLifetime = apiLifetime;
    }

    @Override
    public boolean violatesRule(ApkContainer apk, VersionChecker codeCheck, Rule rule, Collection<String> apisInCode) throws IOException {
        if (rule.getTrueApis().size() == 0)
            return false;

        if (!apisInCode.containsAll(rule.getTrueApis()))
            return false;

        if (codeCheck != null)
            return false;

        for (String api : rule.getTrueApis()) {
            APILife apiLife = this.apiLifetime.getLifeFor(api);

            if (apiLife.getMaxVersion() != -1)
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
        StringBuilder messageBuilder = new StringBuilder("[Info] ");
        if (alternativeApis.containsAll(usedApis)) {
            // Wrong Contextual Usage

            messageBuilder.append("The APIs you are using should be used in a different way in newer Android releases(")
                    .append(checkToImplement.getInverse(true).toString())
                    .append("). ")
                    .append("Consider using: ")
                    .append(alternativeApisString)
                    .append(".");
        } else if (alternativeApis.size() == 0) {
            // Case of Version-Specific Control
            messageBuilder.append("The APIs you are using should be used only in older Android versions (")
                    .append(checkToImplement.toString())
                    .append("). ")
                    .append("Add a check.");
        } else {
            messageBuilder.append("You should use these APIs differently in newer Android releases (")
                    .append(checkToImplement.getInverse(true))
                    .append("). Add a check and handle with: ")
                    .append(alternativeApisString)
                    .append(".");
        }
        return new CombinedViolationDetector.RuleViolationReport(
                CombinedViolationDetector.RuleViolation.ForwardImprovement,
                messageBuilder.toString(),
                rule.getConfidence(),
                usedApis
        );
    }
}
