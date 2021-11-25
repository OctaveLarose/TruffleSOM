package trufflesom.interpreter.nodes.supernodes;

import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.FrameSlotKind;
import com.oracle.truffle.api.frame.FrameSlotTypeException;
import com.oracle.truffle.api.frame.VirtualFrame;

import trufflesom.compiler.Variable;
import trufflesom.interpreter.nodes.ExpressionNode;
import trufflesom.interpreter.nodes.LocalVariableNode;
import trufflesom.interpreter.nodes.literals.IntegerLiteralNode;
import trufflesom.interpreter.nodes.nary.EagerBinaryPrimitiveNode;
import trufflesom.primitives.arithmetic.AdditionPrim;
import trufflesom.primitives.arithmetic.AdditionPrimFactory;

public abstract class IncrementOperationNode extends LocalVariableNode {
    private final long              increment;
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

    @Specialization(guards = "isLongKind(frame)", rewriteOn = {
            FrameSlotTypeException.class,
            ArithmeticException.class
    })
    public final long writeLong(final VirtualFrame frame) throws FrameSlotTypeException {
        long newValue = Math.addExact(frame.getLong(slot), increment);
        frame.setLong(slot, newValue);
        return newValue;
    }

    @Specialization(replaces = {"writeLong"})
    public final Object writeGeneric(final VirtualFrame frame) {
        // Replace myself with the stored original subtree.
        // This could happen because the frame slot type has changed or because of an overflow.
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

//    @Override
//    protected final boolean isTaggedWith(final Class<?> tag) {
//        if (tag == Tags.LocalVarWrite.class) {
//            return true;
//        } else {
//            return super.isTaggedWith(tag);
//        }
//    }

    @Override
    public String toString() {
        return this.getClass().getSimpleName() + "[" + /*var.name*/ "todo" + "]";
    }

//    @Override
//    public void replaceAfterScopeChange(final InliningVisitor inliner) {
//        /*
//         * This should never happen because ``replaceAfterScopeChange`` is only called in the
//         * parsing stage, whereas the ``IncrementOperationNode`` superinstruction is only inserted
//         * into the AST *after* parsing.
//         */
//        throw new RuntimeException("replaceAfterScopeChange: This should never happen!");
//    }

    public LocalVariableNode getOriginalSubtree() {
        return originalSubtree;
    }

    /**
     * Check if the AST subtree has the shape of an increment operation.
     */
    public static boolean isIncrementOperation(ExpressionNode exp, final Variable.Local var) {
        if (exp instanceof EagerBinaryPrimitiveNode) {
            EagerBinaryPrimitiveNode eagerNode = (EagerBinaryPrimitiveNode) exp;
            if (eagerNode.getReceiver() instanceof LocalVariableReadNode
                    && eagerNode.getArgument() instanceof IntegerLiteralNode
                    && eagerNode.getPrimitive() instanceof AdditionPrimFactory.AdditionPrimNodeGen) {
                LocalVariableReadNode read = (LocalVariableReadNode) eagerNode.getReceiver();
                return read.getLocal().equals(var);
            }
        }
        return false;
    }

    /**
     * Replace ``node`` with a superinstruction. Assumes that the AST subtree has the correct
     * shape.
     */
    public static void replaceNode(final LocalVariableWriteNode node) {
        EagerBinaryPrimitiveNode eagerNode = (EagerBinaryPrimitiveNode) node.getExp();
        if (eagerNode.getArgument() instanceof IntegerLiteralNode) {
            long increment = ((IntegerLiteralNode) eagerNode.getArgument()).getValue();
            IncrementOperationNode newNode = IncrementOperationNodeGen.create(node.getLocal(), increment, node)
                    .initialize(node.getSourceSection());
            node.replace(newNode);
        }
//        VM.insertInstrumentationWrapper(newNode);
    }
}
