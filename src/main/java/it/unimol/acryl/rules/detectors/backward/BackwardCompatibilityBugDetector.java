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
public class BackwardCompatibilityBugDetector extends PotentialBackwardCompatibilityDetector {
    private final APILifetime apiLifetime;

    public BackwardCompatibilityBugDetector(APILifetime apiLifetime) {
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
        StringBuilder messageBuilder = new StringBuilder("[Critical] ");
        if (alternativeApis.containsAll(usedApis)) {
            // Wrong Contextual Usage

            messageBuilder.append("The APIs you are using must be used in a different way in old Android releases(")
                    .append(checkToImplement.getInverse(true).toString())
                    .append("). Use these APIs instead: ")
                    .append(alternativeApisString)
                    .append(".");
        } else if (alternativeApis.size() == 0) {
            // Case of Version-Specific Control
            messageBuilder.append("The APIs you are using must be used only in new Android versions (")
                    .append(checkToImplement.getInverse(true).toString())
                    .append("). Check the Android version before using them. ");
        } else {
            messageBuilder.append("You should use different APIs in old Android versions (")
                    .append(checkToImplement.toString())
                    .append("). Use these APIs instead: ")
                    .append(alternativeApisString)
                    .append(".");
        }

        return new CombinedViolationDetector.RuleViolationReport(
                CombinedViolationDetector.RuleViolation.BackwardCriticalBug,
                messageBuilder.toString(),
                rule.getConfidence(),
                usedApis,
                alternativeApis,
                rule.getChecker().toString()
        );
    }
}
