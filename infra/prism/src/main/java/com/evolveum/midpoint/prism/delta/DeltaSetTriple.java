/*
 * Copyright (c) 2011 Evolveum
 *
 * The contents of this file are subject to the terms
 * of the Common Development and Distribution License
 * (the License). You may not use this file except in
 * compliance with the License.
 *
 * You can obtain a copy of the License at
 * http://www.opensource.org/licenses/cddl1 or
 * CDDLv1.0.txt file in the source code distribution.
 * See the License for the specific language governing
 * permission and limitations under the License.
 *
 * If applicable, add the following below the CDDL Header,
 * with the fields enclosed by brackets [] replaced by
 * your own identifying information:
 *
 * Portions Copyrighted 2011 [name of copyright owner]
 */
package com.evolveum.midpoint.prism.delta;

import com.evolveum.midpoint.prism.PrismPropertyValue;
import com.evolveum.midpoint.util.DebugDumpable;
import com.evolveum.midpoint.util.DebugUtil;
import com.evolveum.midpoint.util.Dumpable;
import com.evolveum.midpoint.util.MiscUtil;

import java.util.Collection;
import java.util.HashSet;

/**
 * The triple of values (added, unchanged, deleted) that represents difference between two collections of values.
 * <p/>
 * The DeltaSetTriple is used as a result of a "diff" operation or it is constructed to determine a ObjectDelta or
 * PropertyDelta. It is a very useful structure in numerous situations when dealing with relative changes.
 * <p/>
 * DeltaSetTriple (similarly to other parts of this system) deal only with unordered values.
 *
 * @author Radovan Semancik
 */
public class DeltaSetTriple<T> implements Dumpable, DebugDumpable {

    /**
     * Collection of values that were not changed.
     */
    private Collection<PrismPropertyValue<T>> zeroSet;

    /**
     * Collection of values that were added.
     */
    private Collection<PrismPropertyValue<T>> plusSet;

    /**
     * Collection of values that were deleted.
     */
    private Collection<PrismPropertyValue<T>> minusSet;

    public DeltaSetTriple() {
        zeroSet = createSet();
        plusSet = createSet();
        minusSet = createSet();
    }

    public DeltaSetTriple(Collection<PrismPropertyValue<T>> zeroSet, Collection<PrismPropertyValue<T>> plusSet, Collection<PrismPropertyValue<T>> minusSet) {
        this.zeroSet = zeroSet;
        this.plusSet = plusSet;
        this.minusSet = minusSet;
    }

    /**
     * Compares two (unordered) collections and creates a triple describing the differences.
     */
    public static <T> DeltaSetTriple<T> diff(Collection<PrismPropertyValue<T>> valuesOld, Collection<PrismPropertyValue<T>> valuesNew) {
        DeltaSetTriple<T> triple = new DeltaSetTriple<T>();
        for (PrismPropertyValue<T> val : valuesOld) {
            if (valuesNew.contains(val)) {
                triple.getZeroSet().add(val);
            } else {
                triple.getMinusSet().add(val);
            }
        }
        for (PrismPropertyValue<T> val : valuesNew) {
            if (!valuesOld.contains(val)) {
                triple.getPlusSet().add(val);
            }
        }
        return triple;
    }

    private Collection<PrismPropertyValue<T>> createSet() {
        return new HashSet<PrismPropertyValue<T>>();
    }

    public Collection<PrismPropertyValue<T>> getZeroSet() {
        return zeroSet;
    }

    public Collection<PrismPropertyValue<T>> getPlusSet() {
        return plusSet;
    }

    public Collection<PrismPropertyValue<T>> getMinusSet() {
        return minusSet;
    }

    /**
     * Returns all values, regardless of the internal sets.
     */
    public Collection<PrismPropertyValue<T>> union() {
        return MiscUtil.union(zeroSet, plusSet, minusSet);
    }

    public Collection<PrismPropertyValue<T>> getNonNegativeValues() {
        return MiscUtil.union(zeroSet, plusSet);
    }

    /**
     * Distributes a value in this triple similar to the placement of other value in the other triple.
     * E.g. if the value "otherMember" is in the zero set in "otherTriple" then "myMember" will be placed
     * in zero set in this triple.
     */
    public <O> void distributeAs(PrismPropertyValue<T> myMember, DeltaSetTriple<O> otherTriple, PrismPropertyValue<O> otherMember) {
        if (otherTriple.getZeroSet() != null && otherTriple.getZeroSet().contains(otherMember)) {
            zeroSet.add(myMember);
        }
        if (otherTriple.getPlusSet() != null && otherTriple.getPlusSet().contains(otherMember)) {
            plusSet.add(myMember);
        }
        if (otherTriple.getMinusSet() != null && otherTriple.getMinusSet().contains(otherMember)) {
            minusSet.add(myMember);
        }
    }
    
	public void merge(DeltaSetTriple<T> triple) {
		zeroSet.addAll(triple.zeroSet);
		plusSet.addAll(triple.plusSet);
		minusSet.addAll(triple.minusSet);
	}

    @Override
    public String toString() {
        return dump();
    }

    @Override
    public String dump() {
        StringBuilder sb = new StringBuilder();
        sb.append("DeltaSetTriple(");
        dumpSet(sb, "zero", zeroSet);
        dumpSet(sb, "plus", plusSet);
        dumpSet(sb, "minus", minusSet);
        sb.append(")");
        return sb.toString();
    }

    private void dumpSet(StringBuilder sb, String label, Collection<PrismPropertyValue<T>> set) {
        sb.append(label).append(": ").append(set).append("; ");
    }

	/* (non-Javadoc)
	 * @see com.evolveum.midpoint.util.DebugDumpable#debugDump()
	 */
	@Override
	public String debugDump() {
		return debugDump(0);
	}

	/* (non-Javadoc)
	 * @see com.evolveum.midpoint.util.DebugDumpable#debugDump(int)
	 */
	@Override
	public String debugDump(int indent) {
		StringBuilder sb = new StringBuilder();
		DebugUtil.indentDebugDump(sb, indent);
        sb.append("DeltaSetTriple(\n");
        debugDumpSet(sb, "zero", zeroSet, indent + 1);
        sb.append("\n");
        debugDumpSet(sb, "plus", plusSet, indent + 1);
        sb.append("\n");
        debugDumpSet(sb, "minus", minusSet, indent + 1);
        DebugUtil.indentDebugDump(sb, indent);
        sb.append(")");
        return sb.toString();
	}

	private void debugDumpSet(StringBuilder sb, String label, Collection<PrismPropertyValue<T>> set, int indent) {
		DebugUtil.indentDebugDump(sb, indent);
		sb.append(label).append(":");
		for (PrismPropertyValue<T> pval: set) {
			sb.append("\n");
			sb.append(pval.debugDump(indent +1));
		}
	}

}
