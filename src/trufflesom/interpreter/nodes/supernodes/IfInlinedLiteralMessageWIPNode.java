package trufflesom.interpreter.nodes.supernodes;

import bd.inlining.ScopeAdaptationVisitor;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import trufflesom.compiler.Variable;
import trufflesom.interpreter.nodes.ExpressionNode;
import trufflesom.interpreter.nodes.LocalVariableNode;
import trufflesom.interpreter.nodes.literals.IntegerLiteralNode;
import trufflesom.interpreter.nodes.specialized.IfInlinedLiteralNode;
import trufflesom.primitives.arithmetic.AdditionPrim;

/**
 * Matches the following AST: TODO adjust, it can be made more specific I think
 * <pre>
 * IfInlinedLiteralNode
 *     EqualsPrimNodeGen
 *     ReturnLocalNode
 * </pre>
 *
 * ...and replaces it with:
 * <pre>
 * IfInlinedLiteralMessageWIPNode TODO name needs to change
 * </pre>
 */
public abstract class IfInlinedLiteralMessageWIPNode extends ExpressionNode {
    private final String prim;
    private final ExpressionNode originalSubtree;
    private final ExpressionNode bodyNode;

    public IfInlinedLiteralMessageWIPNode(final String prim,
                                          final ExpressionNode bodyNode,
                                          final ExpressionNode originalSubtree) {
        this.prim = prim;
        this.bodyNode = bodyNode;
        this.originalSubtree = originalSubtree;
    }

//    public IfInlinedLiteralMessageWIPNode(final IfInlinedLiteralMessageWIPNode node) {
//        super(node.local);
//        this.increment = node.getIncrement();
//        this.originalSubtree = node.getOriginalSubtree();
//    }

    @Specialization
    public final boolean executeBoolean(final VirtualFrame frame) {
//        long newValue = Math.addExact(frame.getLong(slot), increment);
//        frame.setLong(slot, newValue);
        return false;
    }

    @Specialization(replaces = {"writeLong"})
    public final Object executeGeneric(final VirtualFrame frame) {
        Object result = originalSubtree.executeGeneric(frame);
        replace(originalSubtree);
        return result;
    }

    @Override
    public String toString() {
        return this.getClass().getSimpleName() + "[" + "TODO" + "]";
    }

    @Override
    public void replaceAfterScopeChange(final ScopeAdaptationVisitor inliner) {
        throw new RuntimeException("replaceAfterScopeChange: This should never happen!");
    }

    public ExpressionNode getOriginalSubtree() {
        return originalSubtree;
    }

    /**
     * Check if the AST subtree has the shape of an increment operation.
     */
    public static boolean isIfInlinedLiteralMessageNode(ExpressionNode exp, final Variable.Local var) {
//        if (exp instanceof AdditionPrim) {
//            AdditionPrim addPrim = (AdditionPrim) exp;
//            if (addPrim.getReceiver() instanceof LocalVariableReadNode
//                    && addPrim.getArgument() instanceof IntegerLiteralNode) {
//                LocalVariableReadNode readNode = (LocalVariableReadNode) addPrim.getReceiver();
//                return readNode.getLocal().equals(var);
//            }
//        }
        return false;
    }

    /**
     * Replace ``node`` with a superinstruction. Assumes that the AST subtree has the correct shape.
     */
    public static void replaceNode(final IfInlinedLiteralNode node) {
//        AdditionPrim addPrim = (AdditionPrim) node.getExp();
//        if (addPrim.getArgument() instanceof IntegerLiteralNode) {
//            long increment = ((IntegerLiteralNode) addPrim.getArgument()).getValue();
//            IfInlinedLiteralMessageWIPNode newNode = IncrementOperationNodeGen
//                    .create(node.getLocal(), increment, node)
//                    .initialize(node.getSourceSection());
//            node.replace(newNode);
//        }
    }
}
