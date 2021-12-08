package trufflesom.interpreter.nodes.supernodes;

import bd.inlining.ScopeAdaptationVisitor;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.FrameSlotKind;
import com.oracle.truffle.api.frame.FrameSlotTypeException;
import com.oracle.truffle.api.frame.VirtualFrame;

import trufflesom.compiler.Variable;
import trufflesom.interpreter.nodes.ExpressionNode;
import trufflesom.interpreter.nodes.LocalVariableNode;
import trufflesom.interpreter.nodes.literals.IntegerLiteralNode;
import trufflesom.primitives.arithmetic.AdditionPrim;

public abstract class IncrementOperationNode extends LocalVariableNode {
    private final long increment;
    private final LocalVariableNode originalSubtree;

    public IncrementOperationNode(final Variable.Local variable,
                                  final long increment,
                                  final LocalVariableNode originalSubtree) {
        super(variable);
        this.increment = increment;
        this.originalSubtree = originalSubtree;
    }

    public IncrementOperationNode(final IncrementOperationNode node) {
        super(node.local);
        this.increment = node.getIncrement();
        this.originalSubtree = node.getOriginalSubtree();
    }

    public long getIncrement() {
        return increment;
    }

    @Specialization(guards = "isLongKind(frame)", rewriteOn = {FrameSlotTypeException.class, ArithmeticException.class})
    public final long writeLong(final VirtualFrame frame) throws FrameSlotTypeException {
        long newValue = Math.addExact(frame.getLong(slot), increment);
        frame.setLong(slot, newValue);
        return newValue;
    }

    @Specialization(replaces = {"writeLong"})
    public final Object writeGeneric(final VirtualFrame frame) {
        Object result = originalSubtree.executeGeneric(frame);
        replace(originalSubtree);
        return result;
    }

    protected final boolean isLongKind(final VirtualFrame frame) { // uses frame to make sure
        // guard is not converted to
        // assertion
        if (slot.getKind() == FrameSlotKind.Long) {
            return true;
        }
        if (slot.getKind() == FrameSlotKind.Illegal) {
            slot.setKind(FrameSlotKind.Long);
            return true;
        }
        return false;
    }

    @Override
    public String toString() {
        return this.getClass().getSimpleName() + "[" + local.name + "]";
    }

    @Override
    public void replaceAfterScopeChange(final ScopeAdaptationVisitor inliner) {
        // Note: not sure ScopeAdaptationVisitor is the right one here (original used InliningVisitor)
        /*
         * This should never happen because ``replaceAfterScopeChange`` is only called in the
         * parsing stage, whereas the ``IncrementOperationNode`` superinstruction is only inserted
         * into the AST *after* parsing.
         */
        throw new RuntimeException("replaceAfterScopeChange: This should never happen!");
    }

    public LocalVariableNode getOriginalSubtree() {
        return originalSubtree;
    }

    /**
     * Check if the AST subtree has the shape of an increment operation.
     */
    public static boolean isIncrementOperation(ExpressionNode exp, final Variable.Local var) {
        if (exp instanceof AdditionPrim) {
            AdditionPrim addPrim = (AdditionPrim) exp;
            if (addPrim.getReceiver() instanceof LocalVariableReadNode
                    && addPrim.getArgument() instanceof IntegerLiteralNode) {
                LocalVariableReadNode readNode = (LocalVariableReadNode) addPrim.getReceiver();
                return readNode.getLocal().equals(var);
            }
        }
        return false;
    }

    /**
     * Replace ``node`` with a superinstruction. Assumes that the AST subtree has the correct shape.
     */
    public static void replaceNode(final LocalVariableWriteNode node) {
        AdditionPrim addPrim = (AdditionPrim) node.getExp();
        if (addPrim.getArgument() instanceof IntegerLiteralNode) {
            long increment = ((IntegerLiteralNode) addPrim.getArgument()).getValue();
            IncrementOperationNode newNode = IncrementOperationNodeGen
                    .create(node.getLocal(), increment, node)
                    .initialize(node.getSourceSection());
            node.replace(newNode);
        }
    }
}
