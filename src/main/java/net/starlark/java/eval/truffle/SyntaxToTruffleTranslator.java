// Copyright 2024 The Bazel Authors. All rights reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//    http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package net.starlark.java.eval.truffle;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameSlotKind;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.starlark.java.eval.Module;
import net.starlark.java.eval.Starlark;
import net.starlark.java.eval.StarlarkFunction;
import net.starlark.java.eval.StarlarkInt;
import net.starlark.java.eval.StarlarkThread;
import net.starlark.java.eval.truffle.nodes.BlockNode;
import net.starlark.java.eval.truffle.nodes.StarlarkExpressionNode;
import net.starlark.java.eval.truffle.nodes.StarlarkModuleRootNode;
import net.starlark.java.eval.truffle.nodes.StarlarkRootNode;
import net.starlark.java.eval.truffle.nodes.StarlarkStatementNode;
import net.starlark.java.eval.truffle.nodes.assign.AssignDotNode;
import net.starlark.java.eval.truffle.nodes.assign.AssignIdentifierTargetNode;
import net.starlark.java.eval.truffle.nodes.assign.AssignIndexNode;
import net.starlark.java.eval.truffle.nodes.assign.AssignTargetNode;
import net.starlark.java.eval.truffle.nodes.assign.AssignUnpackNode;
import net.starlark.java.eval.truffle.nodes.expr.BinaryOpNode;
import net.starlark.java.eval.truffle.nodes.expr.CallNode;
import net.starlark.java.eval.truffle.nodes.expr.ComprehensionBodyNode;
import net.starlark.java.eval.truffle.nodes.expr.MethodCallNode;
import net.starlark.java.eval.truffle.nodes.expr.ComprehensionForNode;
import net.starlark.java.eval.truffle.nodes.expr.ComprehensionIfNode;
import net.starlark.java.eval.truffle.nodes.expr.ComprehensionNode;
import net.starlark.java.eval.truffle.nodes.expr.ConditionalNode;
import net.starlark.java.eval.truffle.nodes.expr.DictExprNode;
import net.starlark.java.eval.truffle.nodes.expr.DotNode;
import net.starlark.java.eval.truffle.nodes.expr.IndexNode;
import net.starlark.java.eval.truffle.nodes.expr.ListExprNode;
import net.starlark.java.eval.truffle.nodes.expr.LiteralBoolNode;
import net.starlark.java.eval.truffle.nodes.expr.LiteralFloatNode;
import net.starlark.java.eval.truffle.nodes.expr.LiteralIntNode;
import net.starlark.java.eval.truffle.nodes.expr.LiteralNoneNode;
import net.starlark.java.eval.truffle.nodes.expr.LiteralStringNode;
import net.starlark.java.eval.truffle.nodes.expr.ShortCircuitAndNode;
import net.starlark.java.eval.truffle.nodes.expr.ShortCircuitOrNode;
import net.starlark.java.eval.truffle.nodes.expr.SliceNode;
import net.starlark.java.eval.truffle.nodes.expr.TupleExprNode;
import net.starlark.java.eval.truffle.nodes.expr.UnaryOpNode;
import net.starlark.java.eval.truffle.nodes.local.ReadCellNode;
import net.starlark.java.eval.truffle.nodes.local.ReadFreeNode;
import net.starlark.java.eval.truffle.nodes.local.ReadGlobalNode;
import net.starlark.java.eval.truffle.nodes.local.ReadLocalNode;
import net.starlark.java.eval.truffle.nodes.local.ReadPredeclaredNode;
import net.starlark.java.eval.truffle.nodes.local.ReadUniversalNode;
import net.starlark.java.eval.truffle.nodes.stmt.AssignmentNode;
import net.starlark.java.eval.truffle.nodes.stmt.AugmentedAssignmentNode;
import net.starlark.java.eval.truffle.nodes.stmt.AugmentedIndexAssignmentNode;
import net.starlark.java.eval.truffle.nodes.stmt.DefNode;
import net.starlark.java.eval.truffle.nodes.stmt.ExpressionStatementNode;
import net.starlark.java.eval.truffle.nodes.stmt.FlowNode;
import net.starlark.java.eval.truffle.nodes.stmt.ForNode;
import net.starlark.java.eval.truffle.nodes.stmt.IfNode;
import net.starlark.java.eval.truffle.nodes.stmt.LoadNode;
import net.starlark.java.eval.truffle.nodes.stmt.PostAssignHookNode;
import net.starlark.java.eval.truffle.nodes.stmt.ReturnNode;
import net.starlark.java.syntax.Argument;
import net.starlark.java.syntax.AssignmentStatement;
import net.starlark.java.syntax.BinaryOperatorExpression;
import net.starlark.java.syntax.CallExpression;
import net.starlark.java.syntax.CastExpression;
import net.starlark.java.syntax.Comprehension;
import net.starlark.java.syntax.ConditionalExpression;
import net.starlark.java.syntax.DefStatement;
import net.starlark.java.syntax.DictExpression;
import net.starlark.java.syntax.DotExpression;
import net.starlark.java.syntax.Expression;
import net.starlark.java.syntax.ExpressionStatement;
import net.starlark.java.syntax.FloatLiteral;
import net.starlark.java.syntax.FlowStatement;
import net.starlark.java.syntax.ForStatement;
import net.starlark.java.syntax.Identifier;
import net.starlark.java.syntax.IfStatement;
import net.starlark.java.syntax.IndexExpression;
import net.starlark.java.syntax.IntLiteral;
import net.starlark.java.syntax.LambdaExpression;
import net.starlark.java.syntax.ListExpression;
import net.starlark.java.syntax.LoadStatement;
import net.starlark.java.syntax.Resolver;
import net.starlark.java.syntax.ReturnStatement;
import net.starlark.java.syntax.SliceExpression;
import net.starlark.java.syntax.Statement;
import net.starlark.java.syntax.StringLiteral;
import net.starlark.java.syntax.TokenKind;
import net.starlark.java.syntax.UnaryOperatorExpression;

/**
 * Translates the resolved Starlark syntax AST ({@code syntax.*}) into a Truffle node tree.
 *
 * <p>This is a visitor over the resolved AST ({@link Resolver.Function}), dispatching on {@link
 * Expression.Kind} and {@link Statement.Kind} to create the corresponding Truffle nodes.
 */
public final class SyntaxToTruffleTranslator {

  private final Module module;
  private final int[] globalIndex;
  private final StarlarkThread thread;
  @Nullable private StarlarkTruffleLanguage language;

  // Counter for allocating extra frame slots (e.g. for comprehension results)
  private int nextExtraSlot;

  public SyntaxToTruffleTranslator(Module module, int[] globalIndex, StarlarkThread thread) {
    this.module = module;
    this.globalIndex = globalIndex;
    this.thread = thread;
  }

  /** Builds a FrameDescriptor for the given resolved function. */
  public FrameDescriptor buildFrameDescriptor(Resolver.Function rfn) {
    FrameDescriptor.Builder builder = FrameDescriptor.newBuilder();
    int numLocals = rfn.getLocals().size();
    // Reserve exactly one extra slot per comprehension (each comprehension needs one result slot).
    // No safety margin: countComprehensions is an exact count of allocateExtraSlot() calls.
    int extraSlots = countComprehensions(rfn);
    for (int i = 0; i < numLocals + extraSlots; i++) {
      builder.addSlot(FrameSlotKind.Object, null, null);
    }
    nextExtraSlot = numLocals;
    return builder.build();
  }

  /** Counts comprehensions in a function to pre-allocate frame slots. */
  private int countComprehensions(Resolver.Function rfn) {
    int count = 0;
    for (Statement stmt : rfn.getBody()) {
      count += countComprehensionsInStatement(stmt);
    }
    return count;
  }

  private int countComprehensionsInStatement(Statement stmt) {
    switch (stmt.kind()) {
      case ASSIGNMENT:
        AssignmentStatement assign = (AssignmentStatement) stmt;
        return countComprehensionsInExpression(assign.getLHS())
            + countComprehensionsInExpression(assign.getRHS());
      case EXPRESSION:
        return countComprehensionsInExpression(((ExpressionStatement) stmt).getExpression());
      case RETURN:
        ReturnStatement ret = (ReturnStatement) stmt;
        return ret.getResult() != null ? countComprehensionsInExpression(ret.getResult()) : 0;
      case IF:
        IfStatement ifStmt = (IfStatement) stmt;
        int count = countComprehensionsInExpression(ifStmt.getCondition());
        for (Statement s : ifStmt.getThenBlock()) {
          count += countComprehensionsInStatement(s);
        }
        if (ifStmt.getElseBlock() != null) {
          for (Statement s : ifStmt.getElseBlock()) {
            count += countComprehensionsInStatement(s);
          }
        }
        return count;
      case FOR:
        ForStatement forStmt = (ForStatement) stmt;
        int forCount = countComprehensionsInExpression(forStmt.getCollection());
        for (Statement s : forStmt.getBody()) {
          forCount += countComprehensionsInStatement(s);
        }
        return forCount;
      case DEF:
        // Inner def's comprehensions are in their own frame, but default value
        // expressions execute in the outer frame.
        DefStatement def = (DefStatement) stmt;
        int defCount = 0;
        for (var param : def.getResolvedFunction().getParameters()) {
          Expression defaultVal = param.getDefaultValue();
          if (defaultVal != null) {
            defCount += countComprehensionsInExpression(defaultVal);
          }
        }
        return defCount;
      case LOAD:
      case FLOW:
      case TYPE_ALIAS:
      case VAR:
        return 0;
      default:
        return 0;
    }
  }

  private int countComprehensionsInExpression(Expression expr) {
    switch (expr.kind()) {
      case COMPREHENSION:
        Comprehension comp = (Comprehension) expr;
        int count = 1; // one slot for this comprehension's result
        if (comp.isDict()) {
          DictExpression.Entry entry = (DictExpression.Entry) comp.getBody();
          count += countComprehensionsInExpression(entry.getKey());
          count += countComprehensionsInExpression(entry.getValue());
        } else {
          count += countComprehensionsInExpression((Expression) comp.getBody());
        }
        for (Comprehension.Clause clause : comp.getClauses()) {
          if (clause instanceof Comprehension.For forClause) {
            count += countComprehensionsInExpression(forClause.getIterable());
          } else {
            count += countComprehensionsInExpression(((Comprehension.If) clause).getCondition());
          }
        }
        return count;
      case BINARY_OPERATOR:
        BinaryOperatorExpression bin = (BinaryOperatorExpression) expr;
        return countComprehensionsInExpression(bin.getX())
            + countComprehensionsInExpression(bin.getY());
      case UNARY_OPERATOR:
        return countComprehensionsInExpression(((UnaryOperatorExpression) expr).getX());
      case CONDITIONAL:
        ConditionalExpression cond = (ConditionalExpression) expr;
        return countComprehensionsInExpression(cond.getCondition())
            + countComprehensionsInExpression(cond.getThenCase())
            + countComprehensionsInExpression(cond.getElseCase());
      case CALL:
        CallExpression call = (CallExpression) expr;
        int callCount = countComprehensionsInExpression(call.getFunction());
        for (Argument arg : call.getArguments()) {
          callCount += countComprehensionsInExpression(arg.getValue());
        }
        return callCount;
      case INDEX:
        IndexExpression idx = (IndexExpression) expr;
        return countComprehensionsInExpression(idx.getObject())
            + countComprehensionsInExpression(idx.getKey());
      case SLICE:
        SliceExpression slice = (SliceExpression) expr;
        int sliceCount = countComprehensionsInExpression(slice.getObject());
        if (slice.getStart() != null)
          sliceCount += countComprehensionsInExpression(slice.getStart());
        if (slice.getStop() != null)
          sliceCount += countComprehensionsInExpression(slice.getStop());
        if (slice.getStep() != null)
          sliceCount += countComprehensionsInExpression(slice.getStep());
        return sliceCount;
      case DOT:
        return countComprehensionsInExpression(((DotExpression) expr).getObject());
      case LIST_EXPR:
        int listCount = 0;
        for (Expression elem : ((ListExpression) expr).getElements()) {
          listCount += countComprehensionsInExpression(elem);
        }
        return listCount;
      case DICT_EXPR:
        int dictCount = 0;
        for (DictExpression.Entry entry : ((DictExpression) expr).getEntries()) {
          dictCount += countComprehensionsInExpression(entry.getKey());
          dictCount += countComprehensionsInExpression(entry.getValue());
        }
        return dictCount;
      case CAST:
        return countComprehensionsInExpression(((CastExpression) expr).getValue());
      case LAMBDA:
        // Lambda body is in its own frame, but default values execute in outer frame
        LambdaExpression lambda = (LambdaExpression) expr;
        int lambdaCount = 0;
        for (var param : lambda.getResolvedFunction().getParameters()) {
          Expression defaultVal = param.getDefaultValue();
          if (defaultVal != null) {
            lambdaCount += countComprehensionsInExpression(defaultVal);
          }
        }
        return lambdaCount;
      case IDENTIFIER:
      case INT_LITERAL:
      case FLOAT_LITERAL:
      case STRING_LITERAL:
        return 0;
      default:
        return 0;
    }
  }

  /** Allocates a new extra frame slot for temporary storage (e.g., comprehension results). */
  private int allocateExtraSlot() {
    return nextExtraSlot++;
  }

  /**
   * Translates the top-level module function into a Truffle module root node.
   *
   * @param rfn the resolved top-level function
   * @param frameDescriptor the frame descriptor for the module
   * @param module the Starlark module
   * @param globalIndex mapping from program globals to module globals
   * @param thread the execution thread
   * @return a StarlarkModuleRootNode ready for execution
   */
  public StarlarkModuleRootNode translateModule(
      Resolver.Function rfn,
      FrameDescriptor frameDescriptor,
      Module module,
      int[] globalIndex,
      StarlarkThread thread) {
    // Try to get a language instance, but it may not be available outside Truffle context
    this.language = null;

    // Translate top-level statements with postAssignHook support.
    // The hook fires after each top-level assignment/def (not nested in if/for).
    StarlarkStatementNode body = translateModuleBlock(rfn.getBody(), globalIndex);

    return new StarlarkModuleRootNode(
        language, frameDescriptor, body, rfn.getName(), rfn.getCellIndices());
  }

  /**
   * Translates a top-level module block, wrapping assignment/def statements with
   * PostAssignHookNode to support BzlLoadFunction's "export" semantics.
   */
  private StarlarkStatementNode translateModuleBlock(
      List<Statement> statements, int[] globalIndex) {
    StarlarkStatementNode[] nodes = new StarlarkStatementNode[statements.size()];
    for (int i = 0; i < statements.size(); i++) {
      Statement stmt = statements.get(i);
      StarlarkStatementNode translated = translateStatement(stmt);

      // Wrap top-level assignments and defs with postAssignHook support
      if (stmt instanceof AssignmentStatement assign && !assign.isAugmented()) {
        var boundIds = Identifier.boundIdentifiers(assign.getLHS());
        if (!boundIds.isEmpty()) {
          String[] names = new String[boundIds.size()];
          int[] indices = new int[boundIds.size()];
          net.starlark.java.syntax.Location[] locs =
              new net.starlark.java.syntax.Location[boundIds.size()];
          int j = 0;
          for (Identifier id : boundIds) {
            names[j] = id.getName();
            Resolver.Binding binding = id.getBinding();
            indices[j] =
                binding.getScope() == Resolver.Scope.GLOBAL
                    ? globalIndex[binding.getIndex()]
                    : -1;
            locs[j] = id.getStartLocation();
            j++;
          }
          translated = new PostAssignHookNode(translated, names, indices, locs);
        }
      } else if (stmt instanceof DefStatement def) {
        Identifier id = def.getIdentifier();
        Resolver.Binding binding = id.getBinding();
        int modGlobalIndex =
            binding.getScope() == Resolver.Scope.GLOBAL
                ? globalIndex[binding.getIndex()]
                : -1;
        translated =
            new PostAssignHookNode(
                translated,
                new String[] {id.getName()},
                new int[] {modGlobalIndex},
                new net.starlark.java.syntax.Location[] {id.getStartLocation()});
      }

      nodes[i] = translated;
    }
    return new BlockNode(nodes);
  }

  // ---- Statement Translation ----

  /** Translates a list of statements into a BlockNode. */
  private StarlarkStatementNode translateBlock(List<Statement> statements) {
    StarlarkStatementNode[] nodes = new StarlarkStatementNode[statements.size()];
    for (int i = 0; i < statements.size(); i++) {
      nodes[i] = translateStatement(statements.get(i));
    }
    return new BlockNode(nodes);
  }

  /** Translates a single statement. */
  private StarlarkStatementNode translateStatement(Statement stmt) {
    StarlarkStatementNode node =
        switch (stmt.kind()) {
          case ASSIGNMENT -> translateAssignment((AssignmentStatement) stmt);
          case EXPRESSION -> translateExpressionStatement((ExpressionStatement) stmt);
          case FLOW -> translateFlow((FlowStatement) stmt);
          case FOR -> translateFor((ForStatement) stmt);
          case DEF -> translateDef((DefStatement) stmt);
          case IF -> translateIf((IfStatement) stmt);
          case LOAD -> translateLoad((LoadStatement) stmt);
          case RETURN -> translateReturn((ReturnStatement) stmt);
          case TYPE_ALIAS, VAR -> new BlockNode(new StarlarkStatementNode[0]); // no-op
          default -> throw new IllegalArgumentException("unexpected statement: " + stmt.kind());
        };
    node.setSourceLocation(stmt.getStartLocation());
    return node;
  }

  private StarlarkStatementNode translateAssignment(AssignmentStatement stmt) {
    if (stmt.isAugmented()) {
      return translateAugmentedAssignment(stmt);
    }
    StarlarkExpressionNode rhs = translateExpression(stmt.getRHS());
    AssignTargetNode target = translateAssignTarget(stmt.getLHS());
    return new AssignmentNode(target, rhs);
  }

  private StarlarkStatementNode translateAugmentedAssignment(AssignmentStatement stmt) {
    Expression lhs = stmt.getLHS();
    StarlarkExpressionNode rhsExpr = translateExpression(stmt.getRHS());
    // For index LHS (obj[key] op= rhs), use a specialized node that evaluates obj and key
    // exactly once, avoiding double side-effect evaluation (e.g., f()[0] += 1 calling f() twice).
    if (lhs instanceof IndexExpression indexExpr) {
      StarlarkExpressionNode containerExpr = translateExpression(indexExpr.getObject());
      StarlarkExpressionNode keyExpr = translateExpression(indexExpr.getKey());
      return new AugmentedIndexAssignmentNode(
          containerExpr, keyExpr, rhsExpr, stmt.getOperator(), stmt.getOperatorLocation());
    }
    StarlarkExpressionNode lhsExpr = translateExpression(lhs);
    AssignTargetNode target = translateAssignTarget(lhs);
    return new AugmentedAssignmentNode(
        lhsExpr, rhsExpr, target, stmt.getOperator(), stmt.getOperatorLocation());
  }

  private StarlarkStatementNode translateExpressionStatement(ExpressionStatement stmt) {
    return new ExpressionStatementNode(translateExpression(stmt.getExpression()));
  }

  private StarlarkStatementNode translateFlow(FlowStatement stmt) {
    return new FlowNode(stmt.getFlowKind());
  }

  private StarlarkStatementNode translateFor(ForStatement stmt) {
    StarlarkExpressionNode collection = translateExpression(stmt.getCollection());
    AssignTargetNode variable = translateAssignTarget(stmt.getVars());
    StarlarkStatementNode body = translateBlock(stmt.getBody());
    return new ForNode(collection, variable, body);
  }

  private StarlarkStatementNode translateDef(DefStatement stmt) {
    Resolver.Function rfn = stmt.getResolvedFunction();

    // Build the inner function's frame descriptor and body.
    // IMPORTANT: Use a separate translator for inner functions to avoid corrupting
    // this translator's nextExtraSlot counter (which would cause frame slot collisions
    // between comprehension result slots and local variable slots in the outer function).
    SyntaxToTruffleTranslator innerTranslator =
        new SyntaxToTruffleTranslator(module, globalIndex, thread);
    innerTranslator.language = this.language;
    FrameDescriptor innerFd = innerTranslator.buildFrameDescriptor(rfn);

    StarlarkStatementNode innerBody = innerTranslator.translateBlock(rfn.getBody());
    StarlarkRootNode rootNode =
        new StarlarkRootNode(language, innerFd, innerBody, rfn.getName());
    CallTarget callTarget = rootNode.getCallTarget();

    // Default value expressions
    int nparams =
        rfn.getParameters().size() - (rfn.hasKwargs() ? 1 : 0) - (rfn.hasVarargs() ? 1 : 0);
    List<StarlarkExpressionNode> defaultExprs = new ArrayList<>();
    boolean seenDefault = false;
    for (int i = 0; i < nparams; i++) {
      Expression expr = rfn.getParameters().get(i).getDefaultValue();
      if (expr == null && !seenDefault) {
        continue;
      }
      seenDefault = true;
      if (expr != null) {
        defaultExprs.add(translateExpression(expr));
      } else {
        defaultExprs.add(null); // MANDATORY
      }
    }

    // Free variable capture info
    List<Resolver.Binding> freeVarBindings = rfn.getFreeVars();
    int[] freeVarScopes = new int[freeVarBindings.size()];
    int[] freeVarIndices = new int[freeVarBindings.size()];
    for (int i = 0; i < freeVarBindings.size(); i++) {
      Resolver.Binding bind = freeVarBindings.get(i);
      freeVarScopes[i] = bind.getScope() == Resolver.Scope.FREE ? 0 : 1; // 0=FREE, 1=CELL
      freeVarIndices[i] = bind.getIndex();
    }

    // Assignment target for the def name
    Identifier id = stmt.getIdentifier();
    AssignTargetNode assignTarget =
        new AssignIdentifierTargetNode(id.getBinding().getScope(), id.getBinding().getIndex());

    return new DefNode(
        defaultExprs.toArray(new StarlarkExpressionNode[0]),
        callTarget,
        rfn,
        freeVarScopes,
        freeVarIndices,
        assignTarget);
  }

  private StarlarkStatementNode translateIf(IfStatement stmt) {
    StarlarkExpressionNode condition = translateExpression(stmt.getCondition());
    StarlarkStatementNode thenBlock = translateBlock(stmt.getThenBlock());
    StarlarkStatementNode elseBlock =
        stmt.getElseBlock() != null ? translateBlock(stmt.getElseBlock()) : null;
    return new IfNode(condition, thenBlock, elseBlock);
  }

  private StarlarkStatementNode translateLoad(LoadStatement stmt) {
    String moduleName = stmt.getImport().getValue();
    List<LoadStatement.Binding> bindings = stmt.getBindings();
    String[] origNames = new String[bindings.size()];
    AssignTargetNode[] targets = new AssignTargetNode[bindings.size()];
    for (int i = 0; i < bindings.size(); i++) {
      LoadStatement.Binding binding = bindings.get(i);
      origNames[i] = binding.getOriginalName().getName();
      Identifier local = binding.getLocalName();
      targets[i] =
          new AssignIdentifierTargetNode(
              local.getBinding().getScope(), local.getBinding().getIndex());
    }
    return new LoadNode(moduleName, origNames, targets);
  }

  private StarlarkStatementNode translateReturn(ReturnStatement stmt) {
    StarlarkExpressionNode expr =
        stmt.getResult() != null ? translateExpression(stmt.getResult()) : null;
    return new ReturnNode(expr);
  }

  // ---- Expression Translation ----

  /** Translates a single expression to a Truffle expression node. */
  private StarlarkExpressionNode translateExpression(Expression expr) {
    StarlarkExpressionNode node =
        switch (expr.kind()) {
          case BINARY_OPERATOR -> translateBinaryOp((BinaryOperatorExpression) expr);
          case COMPREHENSION -> translateComprehension((Comprehension) expr);
          case CONDITIONAL -> translateConditional((ConditionalExpression) expr);
          case DICT_EXPR -> translateDict((DictExpression) expr);
          case DOT -> translateDot((DotExpression) expr);
          case CALL -> translateCall((CallExpression) expr);
          case CAST -> translateExpression(((CastExpression) expr).getValue());
          case IDENTIFIER -> translateIdentifier((Identifier) expr);
          case INDEX -> translateIndex((IndexExpression) expr);
          case INT_LITERAL -> translateIntLiteral((IntLiteral) expr);
          case FLOAT_LITERAL -> new LiteralFloatNode(((FloatLiteral) expr).getValue());
          case LAMBDA -> translateLambda((LambdaExpression) expr);
          case LIST_EXPR -> translateList((ListExpression) expr);
          case SLICE -> translateSlice((SliceExpression) expr);
          case STRING_LITERAL -> new LiteralStringNode(((StringLiteral) expr).getValue());
          case UNARY_OPERATOR -> translateUnaryOp((UnaryOperatorExpression) expr);
          default -> throw new IllegalArgumentException("unexpected expression: " + expr.kind());
        };
    node.setSourceLocation(expr.getStartLocation());
    return node;
  }

  private StarlarkExpressionNode translateBinaryOp(BinaryOperatorExpression expr) {
    StarlarkExpressionNode left = translateExpression(expr.getX());
    StarlarkExpressionNode right = translateExpression(expr.getY());

    // Short-circuit operators get special nodes
    return switch (expr.getOperator()) {
      case AND -> new ShortCircuitAndNode(left, right);
      case OR -> new ShortCircuitOrNode(left, right);
      default -> new BinaryOpNode(left, right, expr.getOperator(), expr.getOperatorLocation());
    };
  }

  private StarlarkExpressionNode translateComprehension(Comprehension comp) {
    int resultSlot = allocateExtraSlot();

    // Build the clause chain from inside out (body first, then wrapping with for/if clauses)
    StarlarkStatementNode body;
    if (comp.isDict()) {
      DictExpression.Entry bodyEntry = (DictExpression.Entry) comp.getBody();
      StarlarkExpressionNode keyExpr = translateExpression(bodyEntry.getKey());
      StarlarkExpressionNode valueExpr = translateExpression(bodyEntry.getValue());
      body = new ComprehensionBodyNode(keyExpr, valueExpr, resultSlot);
    } else {
      StarlarkExpressionNode valueExpr = translateExpression((Expression) comp.getBody());
      body = new ComprehensionBodyNode(valueExpr, resultSlot);
    }

    // Wrap with clauses from last to first
    StarlarkStatementNode chain = body;
    for (int i = comp.getClauses().size() - 1; i >= 0; i--) {
      Comprehension.Clause clause = comp.getClauses().get(i);
      if (clause instanceof Comprehension.For forClause) {
        StarlarkExpressionNode iterable = translateExpression(forClause.getIterable());
        AssignTargetNode variable = translateAssignTarget(forClause.getVars());
        chain = new ComprehensionForNode(iterable, variable, chain);
      } else {
        Comprehension.If ifClause = (Comprehension.If) clause;
        StarlarkExpressionNode condition = translateExpression(ifClause.getCondition());
        chain = new ComprehensionIfNode(condition, chain);
      }
    }

    return new ComprehensionNode(chain, comp.isDict(), resultSlot);
  }

  private StarlarkExpressionNode translateConditional(ConditionalExpression expr) {
    StarlarkExpressionNode condition = translateExpression(expr.getCondition());
    StarlarkExpressionNode thenExpr = translateExpression(expr.getThenCase());
    StarlarkExpressionNode elseExpr = translateExpression(expr.getElseCase());
    return new ConditionalNode(condition, thenExpr, elseExpr);
  }

  private StarlarkExpressionNode translateDict(DictExpression expr) {
    List<DictExpression.Entry> entries = expr.getEntries();
    StarlarkExpressionNode[] keys = new StarlarkExpressionNode[entries.size()];
    StarlarkExpressionNode[] values = new StarlarkExpressionNode[entries.size()];
    for (int i = 0; i < entries.size(); i++) {
      keys[i] = translateExpression(entries.get(i).getKey());
      values[i] = translateExpression(entries.get(i).getValue());
    }
    return new DictExprNode(keys, values);
  }

  private StarlarkExpressionNode translateDot(DotExpression expr) {
    StarlarkExpressionNode object = translateExpression(expr.getObject());
    return new DotNode(object, expr.getField().getName());
  }

  private StarlarkExpressionNode translateCall(CallExpression call) {
    List<StarlarkExpressionNode> positionalArgs = new ArrayList<>();
    List<String> namedArgNames = new ArrayList<>();
    List<StarlarkExpressionNode> namedArgValues = new ArrayList<>();
    StarlarkExpressionNode starArg = null;
    StarlarkExpressionNode starStarArg = null;

    for (Argument arg : call.getArguments()) {
      if (arg instanceof Argument.StarStar) {
        starStarArg = translateExpression(arg.getValue());
      } else if (arg instanceof Argument.Star) {
        starArg = translateExpression(arg.getValue());
      } else if (arg.getName() != null) {
        namedArgNames.add(arg.getName());
        namedArgValues.add(translateExpression(arg.getValue()));
      } else {
        positionalArgs.add(translateExpression(arg.getValue()));
      }
    }

    StarlarkExpressionNode[] positionalArray = positionalArgs.toArray(new StarlarkExpressionNode[0]);
    String[] namedNames = namedArgNames.toArray(new String[0]);
    StarlarkExpressionNode[] namedValues = namedArgValues.toArray(new StarlarkExpressionNode[0]);

    // If the function expression is a dot-access (receiver.method(args)), emit a fused
    // MethodCallNode to avoid the intermediate BuiltinFunction allocation.
    if (call.getFunction() instanceof DotExpression dot) {
      StarlarkExpressionNode receiverNode = translateExpression(dot.getObject());
      String methodName = dot.getField().getName();
      return new MethodCallNode(
          receiverNode,
          methodName,
          positionalArray,
          namedNames,
          namedValues,
          starArg,
          starStarArg,
          call.getLparenLocation());
    }

    StarlarkExpressionNode function = translateExpression(call.getFunction());
    return new CallNode(
        function,
        positionalArray,
        namedNames,
        namedValues,
        starArg,
        starStarArg,
        call.getLparenLocation());
  }

  private StarlarkExpressionNode translateIdentifier(Identifier id) {
    Resolver.Binding bind = id.getBinding();
    return switch (bind.getScope()) {
      case LOCAL -> new ReadLocalNode(bind.getIndex(), id.getName());
      case CELL -> new ReadCellNode(bind.getIndex());
      case FREE -> new ReadFreeNode(bind.getIndex());
      case GLOBAL -> new ReadGlobalNode(bind.getIndex(), id.getName());
      case PREDECLARED -> new ReadPredeclaredNode(id.getName());
      case UNIVERSAL -> new ReadUniversalNode(id.getName());
    };
  }

  private StarlarkExpressionNode translateIndex(IndexExpression expr) {
    StarlarkExpressionNode object = translateExpression(expr.getObject());
    StarlarkExpressionNode key = translateExpression(expr.getKey());
    return new IndexNode(object, key, expr.getLbracketLocation());
  }

  private StarlarkExpressionNode translateIntLiteral(IntLiteral expr) {
    Number n = expr.getValue();
    if (n instanceof Integer nInt) {
      return new LiteralIntNode(StarlarkInt.of(nInt));
    } else if (n instanceof Long nLong) {
      return new LiteralIntNode(StarlarkInt.of(nLong));
    } else {
      return new LiteralIntNode(StarlarkInt.of((BigInteger) n));
    }
  }

  private StarlarkExpressionNode translateLambda(LambdaExpression expr) {
    // Lambda is translated similarly to def, but as an expression.
    // Use a separate translator (same reason as translateDef - avoid slot corruption).
    Resolver.Function rfn = expr.getResolvedFunction();
    SyntaxToTruffleTranslator innerTranslator =
        new SyntaxToTruffleTranslator(module, globalIndex, thread);
    innerTranslator.language = this.language;
    FrameDescriptor innerFd = innerTranslator.buildFrameDescriptor(rfn);

    StarlarkStatementNode innerBody = innerTranslator.translateBlock(rfn.getBody());
    StarlarkRootNode rootNode =
        new StarlarkRootNode(language, innerFd, innerBody, rfn.getName());
    CallTarget callTarget = rootNode.getCallTarget();

    // For now, return a node that creates the function object at runtime
    return new LambdaNode(rfn, callTarget, module, globalIndex);
  }

  private StarlarkExpressionNode translateList(ListExpression expr) {
    StarlarkExpressionNode[] elements = new StarlarkExpressionNode[expr.getElements().size()];
    for (int i = 0; i < expr.getElements().size(); i++) {
      elements[i] = translateExpression(expr.getElements().get(i));
    }
    if (expr.isTuple()) {
      return new TupleExprNode(elements);
    } else {
      return new ListExprNode(elements);
    }
  }

  private StarlarkExpressionNode translateSlice(SliceExpression expr) {
    StarlarkExpressionNode object = translateExpression(expr.getObject());
    StarlarkExpressionNode start =
        expr.getStart() != null ? translateExpression(expr.getStart()) : null;
    StarlarkExpressionNode stop =
        expr.getStop() != null ? translateExpression(expr.getStop()) : null;
    StarlarkExpressionNode step =
        expr.getStep() != null ? translateExpression(expr.getStep()) : null;
    return new SliceNode(object, start, stop, step);
  }

  private StarlarkExpressionNode translateUnaryOp(UnaryOperatorExpression expr) {
    StarlarkExpressionNode operand = translateExpression(expr.getX());
    return new UnaryOpNode(operand, expr.getOperator());
  }

  // ---- Assignment Target Translation ----

  /** Translates an LHS expression into an assignment target node. */
  private AssignTargetNode translateAssignTarget(Expression lhs) {
    if (lhs instanceof Identifier id) {
      Resolver.Binding bind = id.getBinding();
      return new AssignIdentifierTargetNode(bind.getScope(), bind.getIndex());
    } else if (lhs instanceof IndexExpression index) {
      StarlarkExpressionNode object = translateExpression(index.getObject());
      StarlarkExpressionNode key = translateExpression(index.getKey());
      return new AssignIndexNode(object, key);
    } else if (lhs instanceof ListExpression list) {
      AssignTargetNode[] targets = new AssignTargetNode[list.getElements().size()];
      for (int i = 0; i < list.getElements().size(); i++) {
        targets[i] = translateAssignTarget(list.getElements().get(i));
      }
      return new AssignUnpackNode(targets);
    } else if (lhs instanceof DotExpression dot) {
      StarlarkExpressionNode object = translateExpression(dot.getObject());
      return new AssignDotNode(object, dot.getField().getName());
    } else {
      throw new IllegalArgumentException("cannot assign to: " + lhs.kind());
    }
  }

  // ---- Inner class for lambda expression nodes ----

  /**
   * A node that creates a StarlarkTruffleFunction at runtime (for lambda expressions). This is
   * similar to DefNode but used in expression context.
   */
  private static final class LambdaNode extends StarlarkExpressionNode {
    private final Resolver.Function rfn;
    private final CallTarget callTarget;
    private final Module module;
    private final int[] globalIndex;

    LambdaNode(Resolver.Function rfn, CallTarget callTarget, Module module, int[] globalIndex) {
      this.rfn = rfn;
      this.callTarget = callTarget;
      this.module = module;
      this.globalIndex = globalIndex;
    }

    @Override
    public Object executeGeneric(
        com.oracle.truffle.api.frame.VirtualFrame frame) {
      Object[] args = frame.getArguments();
      StarlarkThread thread = (StarlarkThread) args[1];

      // Capture free variables
      List<Resolver.Binding> freeVarBindings = rfn.getFreeVars();
      StarlarkFunction.Cell[] freevars = new StarlarkFunction.Cell[freeVarBindings.size()];
      for (int i = 0; i < freeVarBindings.size(); i++) {
        Resolver.Binding bind = freeVarBindings.get(i);
        if (bind.getScope() == Resolver.Scope.FREE) {
          net.starlark.java.eval.truffle.runtime.StarlarkTruffleFunction callee =
              (net.starlark.java.eval.truffle.runtime.StarlarkTruffleFunction) args[0];
          freevars[i] = callee.getFreeVar(bind.getIndex());
        } else { // CELL
          freevars[i] = (StarlarkFunction.Cell) frame.getObject(bind.getIndex());
        }
      }

      return new net.starlark.java.eval.truffle.runtime.StarlarkTruffleFunction(
          rfn,
          module,
          globalIndex,
          net.starlark.java.eval.Tuple.empty(),
          freevars,
          callTarget,
          thread.getNextIdentityToken());
    }
  }
}
