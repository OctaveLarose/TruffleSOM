package trufflesom.interpreter.nodes.supernodes;

import com.oracle.truffle.api.frame.VirtualFrame;
import trufflesom.interpreter.nodes.ExpressionNode;
import trufflesom.interpreter.nodes.FieldNode;
import trufflesom.interpreter.nodes.ReturnNonLocalNode;
import trufflesom.interpreter.nodes.literals.GenericLiteralNode;
import trufflesom.interpreter.nodes.literals.LiteralNode;
import trufflesom.interpreter.nodes.specialized.IfInlinedLiteralNode;
import trufflesom.primitives.basics.EqualsPrim;
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

    public static boolean isIfInlinedLiteralMessageNode(ExpressionNode cond, ExpressionNode body) {
        if (cond instanceof EqualsPrim && body instanceof ReturnNonLocalNode.ReturnLocalNode) {
            EqualsPrim ep = (EqualsPrim) cond;
            if (!(ep.getReceiver() instanceof FieldNode.FieldReadNode))
                return false;

            if (!(ep.getArgument() instanceof GenericLiteralNode))
                return false;

            return ep.getArgument().executeGeneric(null) instanceof String;
        }
        return false;
    }

    public static IfFieldEqualsStringLiteralThenReturnLocalNode replaceNode(IfInlinedLiteralNode ifInlinedLiteralNode) {
        EqualsPrim eqPrim = (EqualsPrim) ifInlinedLiteralNode.getConditionNode();

        IfFieldEqualsStringLiteralThenReturnLocalNode newNode = new IfFieldEqualsStringLiteralThenReturnLocalNode(
                new FieldReadEqualsStringLiteralNode((FieldNode.FieldReadNode) eqPrim.getReceiver(), (LiteralNode) eqPrim.getArgument()),
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
