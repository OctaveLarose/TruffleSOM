package trufflesom.interpreter.nodes.supernodes;

import bd.inlining.ScopeAdaptationVisitor;
import com.oracle.truffle.api.dsl.UnsupportedSpecializationException;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.UnexpectedResultException;
import trufflesom.interpreter.nodes.ExpressionNode;
import trufflesom.interpreter.nodes.FieldNode;
import trufflesom.interpreter.nodes.ReturnNonLocalNode;
import trufflesom.interpreter.nodes.literals.GenericLiteralNode;
import trufflesom.interpreter.nodes.specialized.IfInlinedLiteralNode;
import trufflesom.primitives.basics.EqualsPrim;
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
    @Child private ReturnNonLocalNode.ReturnLocalNode bodyNode;

    private final boolean expectedBool;

    @SuppressWarnings("unused") private final ExpressionNode originalSubtree;

//    public IfInlinedLiteralMessageWIPNode(final String prim,
//                                          final ExpressionNode bodyNode,
//                                          final ExpressionNode originalSubtree) {
//        this.prim = prim;
//        this.bodyNode = bodyNode;
//        this.originalSubtree = originalSubtree;
//    }

    public IfInlinedLiteralMessageWIPNode(final ExpressionNode conditionNode,
                                final ExpressionNode originalSubtree, final ReturnNonLocalNode.ReturnLocalNode inlinedBodyNode,
                                final boolean expectedBool) {
        super(conditionNode, originalSubtree, inlinedBodyNode, expectedBool);
        this.conditionNode = conditionNode;
        this.expectedBool = expectedBool;
        this.bodyNode = inlinedBodyNode;
        this.originalSubtree = originalSubtree;
    }

    public boolean evaluateCondition(final VirtualFrame frame) {
        try {
            if (conditionNode instanceof EqualsPrim) {
                EqualsPrim equalsPrim = (EqualsPrim) conditionNode;
                FieldNode.FieldReadNode fieldReadNode = (FieldNode.FieldReadNode) equalsPrim.getReceiver();
                GenericLiteralNode genericLiteralNode = (GenericLiteralNode) equalsPrim.getArgument();
                return fieldReadNode.executeGeneric(frame).equals(genericLiteralNode.executeGeneric(frame));
            } else {
                return conditionNode.executeBoolean(frame);
            }
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
    public static boolean isIfInlinedLiteralMessageNode(ExpressionNode cond, ExpressionNode body) {
        if (cond instanceof EqualsPrim && body instanceof ReturnNonLocalNode.ReturnLocalNode) {
            EqualsPrim ep = (EqualsPrim) cond;
            if (!(ep.getReceiver() instanceof FieldNode.FieldReadNode) || !(ep.getArgument() instanceof GenericLiteralNode))
                return false;

            return true;
        }
        return false;
    }

    /**
     * Replace ``node`` with a superinstruction. Assumes that the AST subtree has the correct shape.
     */
    public static IfInlinedLiteralMessageWIPNode replaceNode(final IfInlinedLiteralNode node) {
        IfInlinedLiteralMessageWIPNode newNode = new IfInlinedLiteralMessageWIPNode(
                node.getConditionNode(),
                node.getBodyActualNode(),
                (ReturnNonLocalNode.ReturnLocalNode) node.getBodyNode(),
                node.getExpectedBool())
                .initialize(node.getSourceCoordinate());
        return node.replace(newNode);
//        return newNode;
    }
}
