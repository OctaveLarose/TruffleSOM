package trufflesom.interpreter.nodes.supernodes;

import com.oracle.truffle.api.frame.VirtualFrame;
import trufflesom.interpreter.nodes.ExpressionNode;
import trufflesom.interpreter.nodes.ReturnNonLocalNode;
import trufflesom.interpreter.nodes.literals.BlockNode;
import trufflesom.interpreter.nodes.specialized.IfInlinedLiteralNode;
import trufflesom.vm.constants.Nil;

/**
 * Supernode designed around the JSON micro benchmark.
 * <p>
 * Matches the following AST:
 * <pre>
 * IfInlinedLiteralNode
 *     FieldReadEqualsStringLiteralNode
 *     ReturnNonLocalNode.ReturnLocalNode
 * </pre>
 * <p>
 * ...and replaces it with:
 * <pre>
 * IfFieldEqualsStringLiteralThenReturnLocalNode
 * </pre>
 */
public final class IfFieldEqualsStringLiteralThenReturnLocalNode extends IfInlinedLiteralNode {
    @Child private ReturnNonLocalNode.ReturnLocalNode bodyNode;
    @Child private FieldReadEqualsStringLiteralNode equalityNode;

    private final boolean expectedBool;

//    @SuppressWarnings("unused") private final ExpressionNode originalSubtree;

    public IfFieldEqualsStringLiteralThenReturnLocalNode(final FieldReadEqualsStringLiteralNode conditionNode,
                                                         final ReturnNonLocalNode.ReturnLocalNode inlinedBodyNode,
                                                         final boolean expectedBool) {
        super(conditionNode, null, inlinedBodyNode, expectedBool);
        this.expectedBool = expectedBool;
        this.bodyNode = inlinedBodyNode;
        this.equalityNode = conditionNode;
    }

    @Override
    public Object executeGeneric(final VirtualFrame frame) {
        if (equalityNode.executeBoolean(frame) == expectedBool) {
            return bodyNode.executeGeneric(frame);
        } else {
            return Nil.nilObject;
        }
    }

    public static boolean isIfInlinedLiteralMessageNode(ExpressionNode conditionNode, ExpressionNode bodyNode) {
        return conditionNode instanceof FieldReadEqualsStringLiteralNode && bodyNode instanceof ReturnNonLocalNode.ReturnLocalNode;
    }

    public static IfFieldEqualsStringLiteralThenReturnLocalNode replaceNode(IfInlinedLiteralNode ifInlinedLiteralNode) {
        IfFieldEqualsStringLiteralThenReturnLocalNode newNode = new IfFieldEqualsStringLiteralThenReturnLocalNode(
                (FieldReadEqualsStringLiteralNode) ifInlinedLiteralNode.getConditionNode(),
                (ReturnNonLocalNode.ReturnLocalNode) ifInlinedLiteralNode.getBodyNode(),
                ifInlinedLiteralNode.getExpectedBool())
                .initialize(ifInlinedLiteralNode.getSourceCoordinate());
        return ifInlinedLiteralNode.replace(newNode);
    }

    @Override
    public String toString() {
        return this.getClass().getSimpleName() + "[" + "IfFieldEqualsStringLiteralThenReturnLocalNode" + "]";
    }
}
