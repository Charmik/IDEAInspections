import com.intellij.codeInspection.AbstractBaseUastLocalInspectionTool;
import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.psi.JavaRecursiveElementVisitor;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiCodeBlock;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiExpressionList;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiPrimitiveType;
import com.intellij.psi.PsiType;
import com.intellij.psi.impl.source.tree.java.PsiIdentifierImpl;
import com.intellij.psi.impl.source.tree.java.PsiReferenceExpressionImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.UParameter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Check that arguments have contracts.
 * @author Charm
 */
public class MethodContractInspection extends AbstractBaseUastLocalInspectionTool {

    @Nullable
    @Override
    public ProblemDescriptor[] checkMethod(@NotNull UMethod method,
                                           @NotNull InspectionManager manager,
                                           boolean isOnTheFly) {
        final PsiClass clazz = method.getContainingClass();
        if (clazz == null) {
            return ProblemDescriptor.EMPTY_ARRAY;
        }
        final List<UParameter> uastParameters = method.getUastParameters();
        final Map<String, PsiElement> paramNameCheckedByContract = new HashMap<>();
        for (UParameter parameter : uastParameters) {
            final PsiAnnotation[] annotations = parameter.getPsi().getAnnotations();
            boolean isNullableParameter = false;
            for (PsiAnnotation annotation : annotations) {
                if (annotation.getQualifiedName() != null && annotation.getQualifiedName().contains("Nullable")) {
                    isNullableParameter = true;
                }
            }
            final PsiType type = parameter.getType();
            if (!isNullableParameter && !(type instanceof PsiPrimitiveType)) {
                paramNameCheckedByContract.put(parameter.getName(), parameter.getJavaPsi());
            }
        }
        final PsiCodeBlock body = method.getJavaPsi().getBody();
        if (body != null) {
            body.accept(new JavaRecursiveElementVisitor() {
                @Override
                public void visitMethodCallExpression(PsiMethodCallExpression invoke) {
                    super.visitMethodCallExpression(invoke);
                    final PsiMethod invokedMethod = invoke.resolveMethod();
                    if (invokedMethod != null) {
                        final String methodName = invokedMethod.getName();
                        if (invokedMethod.getContainingClass() != null &&
                                "Contracts".equals(invokedMethod.getContainingClass().getName()) &&
                                ("ensureNonNull".equals(methodName) ||
                                "ensureNonNullArgument".equals(methodName) ||
                                "requireNonNullArgument".equals(methodName))) {
                            final PsiExpressionList arguments = invoke.getArgumentList();
                            final PsiExpression[] expressions = arguments.getExpressions();
                            for (PsiExpression expression : expressions) {
                                if (expression instanceof PsiReferenceExpressionImpl) {
                                    final PsiElement[] exprChildren = expression.getChildren();
                                    for (PsiElement exprChild : exprChildren) {
                                        if (exprChild instanceof PsiIdentifierImpl) {
                                            final PsiIdentifierImpl psiParam = (PsiIdentifierImpl) exprChild;
                                            paramNameCheckedByContract.remove(psiParam.getText());
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            });
        }
        List<ProblemDescriptor> result = new ArrayList<>();
        for (Map.Entry<String, PsiElement> entry : paramNameCheckedByContract.entrySet()) {
            final ProblemDescriptor problem = manager.createProblemDescriptor(
                    entry.getValue(), "Need to check it by contract", isOnTheFly,
                    new LocalQuickFix[0], ProblemHighlightType.WARNING);
            result.add(problem);
        }
        return result.toArray(new ProblemDescriptor[0]);
    }
}
