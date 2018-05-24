package it.unimol.sdkanalyzer.static_analysis.sequence;

import com.ibm.wala.ipa.cha.ClassHierarchyException;
import com.ibm.wala.shrikeCT.InvalidClassFileException;
import com.ibm.wala.ssa.SSAConditionalBranchInstruction;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.ssa.SSASwitchInstruction;
import it.unimol.sdkanalyzer.static_analysis.contexts.MethodContext;
import it.unimol.sdkanalyzer.static_analysis.contexts.ClassContext;
import it.unimol.sdkanalyzer.static_analysis.contexts.GlobalContext;
import it.unimol.sdkanalyzer.static_analysis.contexts.JarContext;

import java.io.IOException;
import java.io.Serializable;
import java.util.List;

/**
 * @author Simone Scalabrino.
 */
public class ProbabilisticMethodSequence implements Serializable {
    private static final long serialVersionUID = 1L;
    private MethodSequence sequence;
    private double hitsTrue;
    private double hitsFalse;

    private String jarName;
    private String className;
    private String methodSignature;
    private int javaLine;
    private int bytecodeOffset;

    private transient MethodContext methodContext;

    public ProbabilisticMethodSequence(MethodContext methodContext, int javaLine, int bytecodeOffset) {
        this.methodContext = methodContext;
        this.jarName = methodContext.getClassContext().getJarContext().getJarPath().getPath();
        this.className = methodContext.getClassContext().getIClass().getName().toString();
        this.methodSignature = methodContext.getIMethod().getSignature().substring(className.length());
        this.javaLine = javaLine;
        this.bytecodeOffset = bytecodeOffset;

        try {
            this.sequence = methodContext.extractMethodSequence(this.getMostLikelyInstruction(methodContext).iindex, false);
        } catch (Exception e) {
            assert false : e.getMessage();
            this.sequence = methodContext.extractMethodSequence(javaLine, true);
        }
    }

    public MethodSequence getSequence() {
        return sequence;
    }

    public double getHitsTrue() {
        return hitsTrue;
    }

    public void setHitsTrue(double hitsTrue) {
        this.hitsTrue = hitsTrue;
    }

    public double getHitsFalse() {
        return hitsFalse;
    }

    public void setHitsFalse(double hitsFalse) {
        this.hitsFalse = hitsFalse;
    }

    public double getProbabilityTrue() {
        return hitsTrue/(hitsTrue + hitsFalse);
    }

    public double getProbabilityFalse() {
        return hitsFalse/(hitsTrue + hitsFalse);
    }

    public double getProbability(boolean type) {
        if (type)
            return getProbabilityTrue();
        else
            return getProbabilityFalse();
    }

    public void merge(ProbabilisticMethodSequence sequence) {
        assert sequence.getSequence().equals(this.getSequence());

        this.hitsTrue += sequence.hitsTrue;
        this.hitsFalse += sequence.hitsFalse;
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof ProbabilisticMethodSequence) {
            ProbabilisticMethodSequence probabilisticMethodSequence = (ProbabilisticMethodSequence) o;

            return this.sequence.equals(probabilisticMethodSequence.sequence)
                    && hitsTrue == probabilisticMethodSequence.hitsTrue
                    && hitsFalse == probabilisticMethodSequence.hitsFalse;
        }

        return false;
    }

    @Override
    public String toString() {
        return this.sequence.toString() + ": " + this.getProbabilityTrue() + "[" + hitsTrue + "|" + hitsFalse + "]";
    }

    public MethodContext resolveMethodContext() throws IOException, ClassHierarchyException {
        if (this.methodContext == null) {
            JarContext jarContext = GlobalContext.getContext(this.jarName);
            ClassContext classContext = jarContext.resolveClassContext(this.className);
            this.methodContext = classContext.resolveMethodContext(this.methodSignature);
        }

        return this.methodContext;
    }

    public SSAInstruction getMostLikelyInstruction() throws IOException, ClassHierarchyException, InvalidClassFileException {
        return getMostLikelyInstruction(resolveMethodContext());
    }

    private SSAInstruction getMostLikelyInstruction(MethodContext methodContext) throws IOException, ClassHierarchyException, InvalidClassFileException {
        List<SSAInstruction> matchingInstructions = methodContext.getInstructionsForJavaLine(this.javaLine);

        SSAInstruction bestMatching = null;
        int bestDistance = Integer.MAX_VALUE;
        for (SSAInstruction matchingInstruction : matchingInstructions) {
            int currentDistance = Math.abs(matchingInstruction.iindex - this.bytecodeOffset);

            if ((matchingInstruction instanceof SSAConditionalBranchInstruction || matchingInstruction instanceof SSASwitchInstruction)
                    && currentDistance < bestDistance) {
                bestDistance = currentDistance;
                bestMatching = matchingInstruction;
            }
        }

        return bestMatching;
    }

    public int getJavaLine() {
        return javaLine;
    }
}
