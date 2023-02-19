package trufflesom.interpreter.nodes;

import bdt.inlining.ScopeAdaptationVisitor;
import bdt.tools.nodes.Invocation;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.*;
import com.oracle.truffle.api.profiles.ValueProfile;
import trufflesom.compiler.Variable.Local;
import trufflesom.vm.constants.Nil;
import trufflesom.vmobjects.SBlock;
import trufflesom.vmobjects.SObject;
import trufflesom.vmobjects.SSymbol;


/**
 * Intended to replace both local and non-local variable nodes. Obviously slower
 */
public abstract class GenericVariableNode extends NoPreEvalExprNode implements Invocation<SSymbol> {

    protected final int   slotIndex;
    protected final Local local;
    protected final int   contextLevel;

    private static final ValueProfile frameType = ValueProfile.createClassProfile();

    protected GenericVariableNode(final Local local) {
        this.local = local;
        this.slotIndex = local.getIndex();
        this.contextLevel = 0;
    }

    protected GenericVariableNode(final int contextLevel, final Local local) {
        this.local = local;
        this.slotIndex = local.getIndex();
        this.contextLevel = contextLevel;
    }

    @Override
    public SSymbol getInvocationIdentifier() {
        return local.name;
    }

    public Local getLocal() {
        return local;
    }

    // This is a copy of ContextualNode.determineContext, extended to account for the case where the context is 0 (a local variable)
    public MaterializedFrame determineContextMaybeLocal(VirtualFrame frame) {
        if (this.contextLevel == 0) {
            return frame.materialize();
        }

        SBlock self = (SBlock) frame.getArguments()[0];
        int i = contextLevel - 1;

        while (i > 0) {
            self = (SBlock) self.getOuterSelf();
            i--;
        }

        // Graal needs help here to see that this is always a MaterializedFrame
        // so, we record explicitly a class profile
        return frameType.profile(self.getContext());
    }

    public abstract static class GenericVariableReadNode extends GenericVariableNode {

        public GenericVariableReadNode(final int contextLevel, final Local local) {
            super(contextLevel, local);
        }

        @Specialization(guards = "isUninitialized(frame)")
        public final SObject doNil(final VirtualFrame frame) {
            return Nil.nilObject;
        }

        @Specialization(guards = {"ctx.isBoolean(slotIndex)"},
                rewriteOn = {FrameSlotTypeException.class})
        public final boolean doBoolean(final VirtualFrame frame,
                                       @Shared("all") @Bind("determineContextMaybeLocal(frame)") final MaterializedFrame ctx)
                throws FrameSlotTypeException {
            return ctx.getBoolean(slotIndex);
        }

        @Specialization(guards = {"ctx.isLong(slotIndex)"},
                rewriteOn = {FrameSlotTypeException.class})
        public final long doLong(final VirtualFrame frame,
                                 @Shared("all") @Bind("determineContextMaybeLocal(frame)") final MaterializedFrame ctx)
                throws FrameSlotTypeException {
            return ctx.getLong(slotIndex);
        }

        @Specialization(guards = {"ctx.isDouble(slotIndex)"},
                rewriteOn = {FrameSlotTypeException.class})
        public final double doDouble(final VirtualFrame frame,
                                     @Shared("all") @Bind("determineContextMaybeLocal(frame)") final MaterializedFrame ctx)
                throws FrameSlotTypeException {
            return ctx.getDouble(slotIndex);
        }

        @Specialization(guards = {"ctx.isObject(slotIndex)"},
                replaces = {"doBoolean", "doLong", "doDouble"},
                rewriteOn = {FrameSlotTypeException.class})
        public final Object doObject(final VirtualFrame frame,
                                     @Shared("all") @Bind("determineContextMaybeLocal(frame)") final MaterializedFrame ctx)
                throws FrameSlotTypeException {
            return ctx.getObject(slotIndex);
        }

        protected final boolean isUninitialized(final VirtualFrame frame) {
            return local.getFrameDescriptor().getSlotKind(slotIndex) == FrameSlotKind.Illegal;
        }

        @Override
        public void replaceAfterScopeChange(final ScopeAdaptationVisitor inliner) {
            inliner.updateRead(local, this, contextLevel);
        }
    }

    @NodeChild(value = "exp", type = ExpressionNode.class)
    public abstract static class GenericVariableWriteNode extends GenericVariableNode {

        public GenericVariableWriteNode(final int contextLevel, final Local local) {
            super(contextLevel, local);
        }

        public abstract ExpressionNode getExp();

        @Specialization(guards = "isBoolKind(frame)")
        public final boolean writeBoolean(final VirtualFrame frame, final boolean expValue) {
            determineContextMaybeLocal(frame).setBoolean(slotIndex, expValue);
            return expValue;
        }

        @Specialization(guards = "isLongKind(frame)")
        public final long writeLong(final VirtualFrame frame, final long expValue) {
            determineContextMaybeLocal(frame).setLong(slotIndex, expValue);
            return expValue;
        }

        @Specialization(guards = "isDoubleKind(frame)")
        public final double writeDouble(final VirtualFrame frame, final double expValue) {
            determineContextMaybeLocal(frame).setDouble(slotIndex, expValue);
            return expValue;
        }

        @Specialization(replaces = {"writeBoolean", "writeLong", "writeDouble"})
        public final Object writeGeneric(final VirtualFrame frame, final Object expValue) {
            local.getFrameDescriptor().setSlotKind(slotIndex, FrameSlotKind.Object);
            determineContextMaybeLocal(frame).setObject(slotIndex, expValue);
            return expValue;
        }

        protected final boolean isBoolKind(final VirtualFrame frame) {
            FrameDescriptor descriptor = local.getFrameDescriptor();
            FrameSlotKind kind = descriptor.getSlotKind(slotIndex);
            if (kind == FrameSlotKind.Boolean) {
                return true;
            }
            if (kind == FrameSlotKind.Illegal) {
                descriptor.setSlotKind(slotIndex, FrameSlotKind.Boolean);
                return true;
            }
            return false;
        }

        protected final boolean isLongKind(final VirtualFrame frame) {
            FrameDescriptor descriptor = local.getFrameDescriptor();
            FrameSlotKind kind = descriptor.getSlotKind(slotIndex);
            if (kind == FrameSlotKind.Long) {
                return true;
            }
            if (kind == FrameSlotKind.Illegal) {
                descriptor.setSlotKind(slotIndex, FrameSlotKind.Long);
                return true;
            }
            return false;
        }

        protected final boolean isDoubleKind(final VirtualFrame frame) {
            FrameDescriptor descriptor = local.getFrameDescriptor();
            FrameSlotKind kind = descriptor.getSlotKind(slotIndex);
            if (kind == FrameSlotKind.Double) {
                return true;
            }
            if (kind == FrameSlotKind.Illegal) {
                descriptor.setSlotKind(slotIndex, FrameSlotKind.Double);
                return true;
            }
            return false;
        }

        @Override
        public void replaceAfterScopeChange(final ScopeAdaptationVisitor inliner) {
            inliner.updateWrite(local, this, getExp(), contextLevel);
        }
    }
}
