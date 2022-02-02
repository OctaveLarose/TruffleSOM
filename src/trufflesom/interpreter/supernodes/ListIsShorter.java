package trufflesom.interpreter.supernodes;

import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.source.Source;
import trufflesom.interpreter.AbstractInvokable;
import trufflesom.interpreter.nodes.dispatch.AbstractDispatchNode;
import trufflesom.interpreter.nodes.dispatch.UninitializedDispatchNode;
import trufflesom.vm.SymbolTable;
import trufflesom.vm.constants.Nil;

public final class ListIsShorter extends AbstractInvokable {
    @Child private AbstractDispatchNode dispatchNext;

    public ListIsShorter(final Source source, final long sourceCoord) {
      super(new FrameDescriptor(), source, sourceCoord);
      dispatchNext = new UninitializedDispatchNode(SymbolTable.symbolFor("next"));
    }

    @Override
    public Object execute(final VirtualFrame frame) {
      Object[] args = frame.getArguments();
      Object xTail = args[1];
      Object yTail = args[2];

      while (!(yTail == Nil.nilObject)) {
        if (xTail == Nil.nilObject) {
          return true;
        }

        xTail = dispatchNext.executeDispatch(frame, new Object[] {xTail});
        yTail = dispatchNext.executeDispatch(frame, new Object[] {yTail});
      }

      return false;
    }
  }