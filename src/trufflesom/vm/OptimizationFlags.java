package trufflesom.vm;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;

/**
 * Global flags that disable various optimizations, so that their individual effects can be assessed easily.
 */
public final class OptimizationFlags {

    // Disables the creation of block nodes without context when they don't need it. This means always relying on BlockNodeWithContext.
    @CompilationFinal
    public static final boolean disableBlockNodesWithoutContext = System.getProperty("opt.disableBlocksWithoutContext") != null;

    // Disables inline caching. This means always relying on GenericDispatchNode, while it's usually only used in the megamorphic case.
    @CompilationFinal
    public static final boolean disableInlineCaching = System.getProperty("opt.disableInlineCaching") != null;

    // Disables the caching of globals, but also prebuilt nodes for the common built-ins true, false and nil.
    @CompilationFinal
    public static final boolean disableGlobalCaching = System.getProperty("opt.disableGlobalCaching") != null;

    // Disables the inlining of nodes. Parsing will still try to inline nodes and will just continuously fail, which is slower than it could be.
    @CompilationFinal
    public static final boolean disableInliningNodes = System.getProperty("opt.disableInliningNodes") != null;

    /// Disables supernodes. This only includes IntIncrementNode for now. TODO which others can be considered supernodes?
    @CompilationFinal
    public static final boolean disableSupernodes = System.getProperty("opt.disableSupernodes") != null;
}
