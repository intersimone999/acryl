package it.unimol.acryl.rules.detectors.backward;

import it.unimol.acryl.analysis.VersionChecker;
import it.unimol.acryl.android.ApkContainer;
import it.unimol.acryl.lifetime.APILife;
import it.unimol.acryl.lifetime.APILifetime;
import it.unimol.acryl.rules.CombinedViolationDetector;
import it.unimol.acryl.rules.Rule;
import it.unimol.acryl.static_analysis.contexts.MethodContext;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.util.Collection;

/**
 * @author Simone Scalabrino.
 */
@Deprecated
public class BackwardCompatibilityBadSmellDetector extends PotentialBackwardCompatibilityDetector {
    private final APILifetime apiLifetime;

    public BackwardCompatibilityBadSmellDetector(APILifetime apiLifetime) {
        this.apiLifetime = apiLifetime;
    }

    @Override
    public boolean violatesRule(ApkContainer apk, MethodContext methodContext, VersionChecker codeCheck, Rule rule, Collection<String> apisInCode) throws IOException {
        if (!super.violatesRule(apk, methodContext, codeCheck, rule, apisInCode))
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

            messageBuilder.append("The APIs you are using should be used in a different way in old Android versions (")
                    .append(rule.getChecker().toString())
                    .append("). For such versions, consider using these APIs: ")
                    .append(alternativeApisString)
                    .append(".");
        } else if (alternativeApis.size() == 0) {
            // Case of Version-Specific Control
            messageBuilder.append("The APIs you are using should be used only in newer Android versions (")
                    .append(rule.getChecker().getInverse(true).toString())
                    .append("). ")
                    .append("Consider adding a check. ");
        } else {
            messageBuilder.append("You should use different APIs in old Android versions (")
                    .append(rule.getChecker().toString())
                    .append("). For such versions, consider using these APIs: ")
                    .append(alternativeApisString)
                    .append(".");
        }
        return new CombinedViolationDetector.RuleViolationReport(
                CombinedViolationDetector.RuleViolation.BackwardBadSmell,
                messageBuilder.toString(),
                rule.getConfidence(),
                usedApis,
                alternativeApis,
                rule.getChecker().toString()
        );
    }
}
