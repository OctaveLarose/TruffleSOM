package trufflesom.interpreter.nodes.specialized;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.UnsupportedSpecializationException;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.UnexpectedResultException;
import com.oracle.truffle.api.profiles.ConditionProfile;

import bd.inlining.Inline;
import bd.inlining.Inline.False;
import bd.inlining.Inline.True;
import trufflesom.interpreter.nodes.ExpressionNode;
import trufflesom.interpreter.nodes.supernodes.IfInlinedLiteralMessageWIPNode;
import trufflesom.interpreter.nodes.NoPreEvalExprNode;
import trufflesom.vm.constants.Nil;


@Inline(selector = "ifTrue:", inlineableArgIdx = 1, additionalArgs = True.class)
@Inline(selector = "ifFalse:", inlineableArgIdx = 1, additionalArgs = False.class)
@ImportStatic({IfInlinedLiteralMessageWIPNode.class})
public class IfInlinedLiteralNode extends NoPreEvalExprNode {
  protected final ConditionProfile condProf = ConditionProfile.createCountingProfile();

  @Child protected ExpressionNode conditionNode;
  @Child protected ExpressionNode bodyNode;

  protected final boolean expectedBool;

  // In case we need to revert from this optimistic optimization, keep the
  // original nodes around
  @SuppressWarnings("unused") private final ExpressionNode bodyActualNode;

  public IfInlinedLiteralNode(final ExpressionNode conditionNode,
      final ExpressionNode originalBodyNode, final ExpressionNode inlinedBodyNode,
      final boolean expectedBool) {
    this.conditionNode = conditionNode;
    this.expectedBool = expectedBool;
    this.bodyNode = inlinedBodyNode;
    this.bodyActualNode = originalBodyNode;
  }

  public boolean evaluateCondition(final VirtualFrame frame) {
    try {
      return condProf.profile(conditionNode.executeBoolean(frame));
    } catch (UnexpectedResultException e) {
      // TODO: should rewrite to a node that does a proper message send...
      throw new UnsupportedSpecializationException(this,
              new Node[] {conditionNode}, e.getResult());
    }
  }

  @Override
  public Object executeGeneric(final VirtualFrame frame) {
    if (IfInlinedLiteralMessageWIPNode.isIfInlinedLiteralMessageNode(this.getConditionNode(), this.getBodyNode())) {
      CompilerDirectives.transferToInterpreterAndInvalidate();
      IfInlinedLiteralMessageWIPNode bc = IfInlinedLiteralMessageWIPNode.replaceNode(this);
      return bc.executeGeneric(frame);
    }

    if (evaluateCondition(frame) == expectedBool) {
      return bodyNode.executeGeneric(frame);
    } else {
      return Nil.nilObject;
    }
  }

  // Following ones added for supernode testing, may need to be removed later

  public ExpressionNode getConditionNode() {
    return conditionNode;
  }

  public ExpressionNode getBodyNode() {
    return bodyNode;
  }

  public ExpressionNode getBodyActualNode() {
    return bodyActualNode;
  }

  public boolean getExpectedBool() {
    return expectedBool;
  }

}
