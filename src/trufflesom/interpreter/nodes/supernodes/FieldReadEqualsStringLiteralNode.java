package trufflesom.interpreter.nodes.supernodes;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.VirtualFrame;
import trufflesom.interpreter.nodes.FieldNode;
import trufflesom.interpreter.nodes.NoPreEvalExprNode;
import trufflesom.primitives.basics.EqualsPrim;

public final class FieldReadEqualsStringLiteralNode extends NoPreEvalExprNode {
    @Child FieldNode.FieldReadNode fieldReadNode;
    private final String literalNodeValue;

    FieldReadEqualsStringLiteralNode(EqualsPrim conditionNode) {
        this.fieldReadNode = (FieldNode.FieldReadNode) conditionNode.getReceiver();
        this.literalNodeValue = (String) conditionNode.getArgument().executeGeneric(null);
    }

    public boolean evaluateCondition(final VirtualFrame frame) {
        Object objFieldReadVal = this.fieldReadNode.executeGeneric(frame);
        return literalNodeValue.equals(objFieldReadVal);
    }

    public boolean executeBoolean(final VirtualFrame frame) {
        return evaluateCondition(frame);
    }

    public Object executeGeneric(final VirtualFrame frame) {
        return evaluateCondition(frame);
    }
}
