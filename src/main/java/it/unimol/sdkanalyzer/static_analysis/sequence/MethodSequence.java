package it.unimol.sdkanalyzer.static_analysis.sequence;

import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.shrikeBT.IConditionalBranchInstruction;
import com.ibm.wala.ssa.*;
import org.apache.commons.lang3.tuple.Pair;
import sun.awt.AWTAccessor;

import java.io.Serializable;
import java.util.*;

/**
 * @author Simone Scalabrino.
 */
public class MethodSequence implements Serializable {
    private static final long serialVersionUID = 2L;
    private List<String> operations;
    private String classLocation;

    private boolean containsParameters;
    private boolean containsInstanceVariables;

    public MethodSequence(String classLocation) {
        this.classLocation = classLocation;
        this.operations = new ArrayList<>();
    }

    public MethodSequence(IClass baseClass) {
        this(baseClass.getName().toString());
    }

    public void addMethodCall(String methodSignature) {
        this.operations.add(methodSignature);
    }

    public void addMethodCall(SSAInstruction methodSignature) {
        if (methodSignature instanceof SSAInvokeInstruction) {
            this.operations.add(((SSAInvokeInstruction) methodSignature).getDeclaredTarget().getSignature());
        } else if (methodSignature instanceof SSABinaryOpInstruction) {
            this.operations.add(((SSABinaryOpInstruction) methodSignature).getOperator().toString());
        } else if (methodSignature instanceof SSAConditionalBranchInstruction) {
            this.operations.add(((SSAConditionalBranchInstruction) methodSignature).getOperator().toString());
        } else if (methodSignature instanceof SSASwitchInstruction) {
            this.operations.add(IConditionalBranchInstruction.Operator.EQ.toString());
        } else
            assert false : "Wrong type of instruction " + methodSignature.getClass();
    }

    public List<String> getOperations() {
        return operations;
    }

    @Override
    public int hashCode() {
        return this.operations.hashCode();
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof MethodSequence) {
            MethodSequence sequence = ((MethodSequence) o);

            return sequence.containsInstanceVariables == this.containsInstanceVariables &&
                    sequence.containsParameters == this.containsParameters &&
                    sequence.operations.equals(this.operations);
        }

        return false;
    }

    public boolean containsParameters() {
        return containsParameters;
    }

    public void setContainsParameters(boolean containsParameters) {
        this.containsParameters = containsParameters;
    }

    public boolean containsInstanceVariables() {
        return containsInstanceVariables;
    }

    public void setContainsInstanceVariables(boolean containsInstanceVariables) {
        this.containsInstanceVariables = containsInstanceVariables;
    }

    public boolean isControllable() {
        return containsParameters || containsInstanceVariables;
    }

    @Override
    public String toString() {
        return "<" + this.operations.toString() + " | " + this.containsParameters + " | " + this.containsInstanceVariables + ">";
    }

    public double similarity(MethodSequence other) {
        return similarity(other, 1);
    }

    public double similarity(MethodSequence other, double exponent) {
        if (this.isControllable() != other.isControllable())
            return 0D;

        int llcs;
        llcs = lcs(this, other);

        int maxLength = this.operations.size() > other.operations.size() ? this.operations.size() : other.operations.size();

        if (maxLength == 0)
            return 1D;
        else
            return Math.pow(((double)llcs)/maxLength, exponent);
    }

    public double compatibility(MethodSequence other) {
        if (this.isControllable() != other.isControllable())
            return 0D;

        int llcs = lcs(this, other);

        return Math.pow(((double)llcs)/this.operations.size(), other.operations.size());
    }

    public String getClassLocation() {
        return classLocation;
    }


    /**
     * This is an implementation, in Java, of the Longest Common Subsequence algorithm.
     * That is, given two strings A and B, this program will find the longest sequence
     * of letters that are common and ordered in A and B.
     *
     * There are only two reasons you are reading this:
     *   - you don't care what the algorithm is but you need a piece of code to do it
     *   - you're trying to understand the algorithm, and a piece of code might help
     * In either case, you should either read an entire chapter of an algorithms textbook
     * on the subject of dynamic programming, or you should consult a webpage that describes
     * this particular algorithm.   It is important, for example, that we use arrays of size
     * |A|+1 x |B|+1.
     *
     * This code is provided AS-IS.
     * You may use this code in any way you see fit, EXCEPT as the answer to a homework
     * problem or as part of a term project in which you were expected to arrive at this
     * code yourself.
     *
     * Copyright (C) 2005 Neil Jones.
     *
     */

    private static int lcs(MethodSequence a, MethodSequence b) {
        int n = a.operations.size();
        int m = b.operations.size();
        int S[][] = new int[n+1][m+1];
        int ii, jj;

        // It is important to use <=, not <.  The next two for-loops are initialization
        for (ii = 0; ii <= n; ++ii) {
            S[ii][0] = 0;
        }

        for (jj = 0; jj <= m; ++jj) {
            S[0][jj] = 0;
        }

        // This is the main dynamic programming loop that computes the score and
        // backtracking arrays.
        for (ii = 1; ii <= n; ++ii) {
            for (jj = 1; jj <= m; ++jj) {
                String op1 = a.operations.get(ii - 1);
                String op2 = b.operations.get(jj - 1);

                if (op1.hashCode() == op2.hashCode() && Objects.equals(op1, op2)) {
                    S[ii][jj] = S[ii-1][jj-1] + 1;
                } else {
                    S[ii][jj] = S[ii-1][jj-1];
                }

                if ( S[ii-1][jj] >= S[ii][jj] ) {
                    S[ii][jj] = S[ii-1][jj];
                }

                if ( S[ii][jj-1] >= S[ii][jj] ) {
                    S[ii][jj] = S[ii][jj-1];
                }
            }
        }

        // The length of the longest substring is S[n][m]

        return S[n][m];
    }

    private class MethodSequencePair {
        private MethodSequence first;
        private MethodSequence second;

        MethodSequencePair(MethodSequence first, MethodSequence second) {
            this.first = first;
            this.second = second;
        }

        @Override
        public int hashCode() {
            return this.first.hashCode() + this.second.hashCode();
        }

        @Override
        public boolean equals(Object o) {
            if (o instanceof MethodSequencePair) {
                MethodSequencePair methodSequencePair = (MethodSequencePair) o;

                return methodSequencePair.first.equals(first) && methodSequencePair.second.equals(second) ||
                        methodSequencePair.first.equals(second) && methodSequencePair.second.equals(first);
            }

            return false;
        }
    }
}
