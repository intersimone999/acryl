package it.unimol.sdkanalyzer.analysis;

import com.ibm.wala.shrikeBT.IBinaryOpInstruction;

import java.util.logging.Logger;

/**
 * @author Simone Scalabrino.
 */
public class VersionChecker implements Cloneable {
    public enum Comparator {
        GT,
        LT,
        GE,
        LE,
        EQ,
        NE
    }
    private int checkedVersion;
    private Comparator comparator;

    public void setCheckedVersion(int checkedVersion) {
        this.checkedVersion = checkedVersion;
    }

    public int getCheckedVersion() {
        return checkedVersion;
    }

    public void setComparator(String comparatorString) {
        switch (comparatorString) {
            case ">":
                this.comparator = Comparator.GT;
                break;

            case ">=":
                this.comparator = Comparator.GE;
                break;

            case "<":
                this.comparator = Comparator.LT;
                break;

            case "<=":
                this.comparator = Comparator.LE;
                break;

            case "==":
                this.comparator = Comparator.EQ;
                break;

            case "!=":
                this.comparator = Comparator.NE;
                break;

            default:
                throw new RuntimeException("Wrong comparator string");
        }
    }

    public void setComparator(Comparator comparator) {
        this.comparator = comparator;
    }

    public void invertComparator(boolean hardInversion) {
        switch (this.comparator) {
        case GT:
            this.comparator = Comparator.LE;
            break;
        case LT:
            this.comparator = Comparator.GE;
            break;
        case GE:
            this.comparator = Comparator.LT;
            break;
        case LE:
            this.comparator = Comparator.GT;
            break;
        case EQ:
            if (hardInversion) {
                this.comparator = Comparator.NE;
            }
            break;
        case NE:
            if (hardInversion) {
                this.comparator = Comparator.EQ;
            }
            break;
        }
    }

    public void compound(VersionChecker other, IBinaryOpInstruction.IOperator operator) {
        // TODO implement
        Logger.getAnonymousLogger().warning("Combinations of checkers are not implemented at the moment.");
    }

    public Comparator getComparator() {
        return comparator;
    }

    public VersionChecker copy() {
        try {
            return ((VersionChecker) this.clone());
        } catch (CloneNotSupportedException e) {
            //Not possible
            return null;
        }
    }

    @Override
    public String toString() {
        final String versionId = "SDK_INT";

        StringBuilder result = new StringBuilder();
        result.append(versionId);
        result.append(" ");
        switch (this.comparator) {
            case GT:
                result.append(">");
                break;
            case LT:
                result.append("<");
                break;
            case GE:
                result.append(">=");
                break;
            case LE:
                result.append("<=");
                break;
            case EQ:
                result.append("==");
                break;
            case NE:
                result.append("!=");
                break;
        }
        result.append(" ");
        result.append(this.checkedVersion);

        return result.toString();
    }

    public boolean isNull() {
        return false;
    }

    public VersionChecker getInverse(boolean hardInversion) {
        VersionChecker copy = this.copy();
        copy.invertComparator(hardInversion);
        return copy;
    }

    public static class NullChecker extends VersionChecker {
        @Override
        public String toString() {
            return "Null checker";
        }

        @Override
        public boolean isNull() {
            return true;
        }
    }
}
