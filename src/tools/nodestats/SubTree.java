package tools.nodestats;

final class SubTree implements Comparable<SubTree> {
  private final AstNode rootNode;

  final long score;

  SubTree(final AstNode rootNode, final long score) {
    this.rootNode = rootNode;
    this.score = score;
  }

  public AstNode getRoot() {
    return rootNode;
  }

  public Class<?> getRootClass() {
    return rootNode.getNodeClass();
  }

  public long getScore() {
    return score;
  }

  public String prettyPrint() {
    StringBuilder builder = new StringBuilder();
    rootNode.yamlPrint(builder, "  ", 0);
    return builder.toString();
  }

  public void yamlPrint(final StringBuilder builder, final String indent, final int level) {

    for (int i = 0; i < level; i += 1) {
      builder.append(indent);
    }
    builder.append('-');
    builder.append(' ');

    builder.append("score: ");
    builder.append(score);
    builder.append('\n');

    int nextLevel = level + 1;

    for (int i = 0; i < nextLevel; i += 1) {
      builder.append(indent);
    }

    rootNode.yamlPrint(builder, indent, nextLevel);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }

    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    SubTree candidate = (SubTree) o;
    return rootNode.equals(candidate.rootNode);
  }

  @Override
  public int hashCode() {
    return rootNode.hashCode();
  }

  @Override
  public String toString() {
    return "Candidate(" + score + ": " + rootNode.getNodeClass().getSimpleName() + ", "
        + rootNode.getHeight() + ")";
  }

  @Override
  public int compareTo(final SubTree o) {
    int diff = (int) (o.score - score);
    if (diff != 0) {
      return diff;
    }
    return rootNode.compareTo(o.rootNode);
  }
}
