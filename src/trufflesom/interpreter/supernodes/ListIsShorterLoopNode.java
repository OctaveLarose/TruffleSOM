package trufflesom.interpreter.supernodes;

import com.oracle.truffle.api.frame.VirtualFrame;
import trufflesom.compiler.Variable;
import trufflesom.interpreter.nodes.ExpressionNode;
import trufflesom.interpreter.nodes.ReturnNonLocalNode;
import trufflesom.interpreter.nodes.dispatch.AbstractDispatchNode;
import trufflesom.interpreter.nodes.dispatch.UninitializedDispatchNode;
import trufflesom.interpreter.nodes.literals.LiteralNode;
import trufflesom.vm.SymbolTable;
import trufflesom.vm.constants.Nil;

public class ListIsShorterLoopNode extends ExpressionNode {
    @Child private AbstractDispatchNode dispatchNext;

    @Child private ReturnNonLocalNode.ReturnLocalNode returnTrueNode;

    public ListIsShorterLoopNode(Variable.Internal frameOnStackForReturn) {
        this.dispatchNext = new UninitializedDispatchNode(SymbolTable.symbolFor("next"));
        this.returnTrueNode = new ReturnNonLocalNode.ReturnLocalNode(LiteralNode.create(true), frameOnStackForReturn);
    }

    public ListIsShorterLoopNode(ReturnNonLocalNode.ReturnLocalNode returnLocalNode) {
        this.dispatchNext = new UninitializedDispatchNode(SymbolTable.symbolFor("next"));
        this.returnTrueNode = returnLocalNode;
    }

    @Override
    public Object executeGeneric(VirtualFrame frame) {
        Object[] args = frame.getArguments();
        Object xTail = args[1];
        Object yTail = args[2];

        while (yTail != Nil.nilObject) {
            if (xTail == Nil.nilObject) {
                return returnTrueNode.executeGeneric(frame);
            }

            xTail = dispatchNext.executeDispatch(frame, new Object[] {xTail});
            yTail = dispatchNext.executeDispatch(frame, new Object[] {yTail});
        }

        return null;
    }

    @Override
    public Object doPreEvaluated(VirtualFrame frame, Object[] args) {
        return this.executeGeneric(frame);
    }
}
