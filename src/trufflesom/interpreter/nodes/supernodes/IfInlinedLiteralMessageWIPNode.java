package trufflesom.interpreter.nodes.supernodes;

import bd.inlining.ScopeAdaptationVisitor;
import com.oracle.truffle.api.dsl.UnsupportedSpecializationException;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.UnexpectedResultException;
import trufflesom.interpreter.nodes.ExpressionNode;
import trufflesom.interpreter.nodes.specialized.IfInlinedLiteralNode;
import trufflesom.vm.constants.Nil;

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
public final class IfInlinedLiteralMessageWIPNode extends IfInlinedLiteralNode {
//    private final String prim;
//    private final ExpressionNode originalSubtree;

    @Child private ExpressionNode conditionNode;
    @Child private ExpressionNode bodyNode;

    private final boolean expectedBool;

    @SuppressWarnings("unused") private final ExpressionNode bodyActualNode;

//    public IfInlinedLiteralMessageWIPNode(final String prim,
//                                          final ExpressionNode bodyNode,
//                                          final ExpressionNode originalSubtree) {
//        this.prim = prim;
//        this.bodyNode = bodyNode;
//        this.originalSubtree = originalSubtree;
//    }

    public IfInlinedLiteralMessageWIPNode(final ExpressionNode conditionNode,
                                final ExpressionNode originalBodyNode, final ExpressionNode inlinedBodyNode,
                                final boolean expectedBool) {
        super(conditionNode, originalBodyNode, inlinedBodyNode, expectedBool);
        this.conditionNode = conditionNode;
        this.expectedBool = expectedBool;
        this.bodyNode = inlinedBodyNode;
        this.bodyActualNode = originalBodyNode;
    }

    // A copy constructor here?

    public boolean evaluateCondition(final VirtualFrame frame) {
        try {
            System.out.println(this.getClass().getSimpleName() + " evaluateCondition() called.");
            return condProf.profile(conditionNode.executeBoolean(frame));
        } catch (UnexpectedResultException e) {
            throw new UnsupportedSpecializationException(this, new Node[] {conditionNode}, e.getResult());
        }
    }

    @Override
    public Object executeGeneric(final VirtualFrame frame) {
        if (evaluateCondition(frame) == expectedBool) {
            return bodyNode.executeGeneric(frame);
        } else {
            return Nil.nilObject;
        }
    }

//    @Specialization(replaces = {"evaluateCondition"})
//    public final Object evaluateConditionGeneric(final VirtualFrame frame) {
//        Object result = originalSubtree.executeGeneric(frame);
//        replace(originalSubtree);
//        return result;
//    }

    @Override
    public String toString() {
        return this.getClass().getSimpleName() + "[" + "TODO" + "]";
    }

    @Override
    public void replaceAfterScopeChange(final ScopeAdaptationVisitor inliner) {
        throw new RuntimeException("replaceAfterScopeChange: This should never happen!");
    }

//    public ExpressionNode getOriginalSubtree() {
//        return originalSubtree;
//    }

    /**
     * Check if the AST subtree has the shape of an increment operation.
     */
    public static boolean isIfInlinedLiteralMessageNode(ExpressionNode exp) {
//        if (exp instanceof AdditionPrim) {
//            AdditionPrim addPrim = (AdditionPrim) exp;
//            if (addPrim.getReceiver() instanceof LocalVariableReadNode
//                    && addPrim.getArgument() instanceof IntegerLiteralNode) {
//                LocalVariableReadNode readNode = (LocalVariableReadNode) addPrim.getReceiver();
//                return readNode.getLocal().equals(var);
//            }
//        }
        return true;
    }

    /**
     * Replace ``node`` with a superinstruction. Assumes that the AST subtree has the correct shape.
     */
    public static IfInlinedLiteralMessageWIPNode replaceNode(final IfInlinedLiteralNode node) {
        IfInlinedLiteralMessageWIPNode newNode = new IfInlinedLiteralMessageWIPNode(
                node.getConditionNode(), node.getBodyActualNode(), node.getBodyNode(), node.getExpectedBool())
                .initialize(node.getSourceCoordinate());
        return node.replace(newNode);
//        return newNode;
    }
}
