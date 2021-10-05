package trufflesom.interpreter.nodes.dispatch;

import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.IndirectCallNode;

import trufflesom.vmobjects.SBlock;


public final class GenericBlockDispatchNode extends AbstractDispatchNode {
  @Child private IndirectCallNode call = Truffle.getRuntime().createIndirectCallNode();

  @Override
  public Object executeDispatch(final VirtualFrame frame,
      final Object[] arguments) {
    SBlock rcvr = (SBlock) arguments[0];
    return call.call(rcvr.getMethod().getCallTarget(), arguments);
  }

  @Override
  public int lengthOfDispatchChain() {
    return 1000;
  }

  @Override
  public Object executeBinary(final VirtualFrame frame, final Object arg0, final Object arg) {
    SBlock rcvr = (SBlock) arg0;
    return call.call2(rcvr.getMethod().getCallTarget(), arg0, arg);
  }
}
