import com.google.common.collect.ComparisonChain;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ContentIterator;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileFilter;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementResolveResult;
import com.intellij.psi.PsiExpressionList;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiPolyVariantReferenceBase;
import com.intellij.psi.ResolveResult;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Comparator;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/**
 * Navigation from junit-test to test's data.
 * @author Charm
 */
public class SourceNavigation<T extends PsiElement> extends PsiPolyVariantReferenceBase<T> {

    public SourceNavigation(@NotNull T psiElement) {
        super(psiElement, true);
    }

    @NotNull
    @Override
    public ResolveResult[] multiResolve(boolean incompleteCode) {
        final String value = computeStringValue();

        Project project = getElement().getProject();
        ProjectFileIndex fileIndex = ProjectRootManager.getInstance(project).getFileIndex();

        final Set<Pair<Integer, VirtualFile>> sortedResults = new TreeSet<>((o1, o2) -> ComparisonChain.start().
                compare(o1.getFirst(), o2.getFirst()).
                compare(o1.getSecond(), o2.getSecond(), new Comparator<VirtualFile>() {
                    @Override
                    public int compare(VirtualFile o1, VirtualFile o2) {
                        if (o1 == null && o2 == null) {
                            return 0;
                        } else if (o1 == null) {
                            return 1;
                        } else if (o2 == null) {
                            return -1;
                        }
                        String o1CanonicalPath = o1.getCanonicalPath();
                        String o2CanonicalPath = o2.getCanonicalPath();

                        if (o1CanonicalPath != null && o2CanonicalPath != null) {
                            return o1CanonicalPath.compareTo(o2CanonicalPath);
                        } else {
                            return 0;
                        }
                    }
                    // TODO: fix NPE
                }).compare(o1.getSecond().getName(), o2.getSecond().getName()).result());
        // TODO: fix for perfomance
        // fileIndex.iterateContent(processor,filter)
        fileIndex.iterateContent(new ContentIterator() {
            @Override
            public boolean processFile(VirtualFile fileOrDir) {
                final String absPath = fileOrDir.getPath();
                if (fileOrDir.isDirectory()) {
                    try {
                        Files.walk(Paths.get(absPath), 3)
                                .filter(file -> !file.toFile().isDirectory())
                                .min(Comparator.comparing(Path::toString))
                                .ifPresent(file -> {
                                    final Path relativize = Paths.get(absPath).relativize(file);
                                    sortedResults.add(
                                            new Pair<>(10, fileOrDir.findFileByRelativePath(relativize.toString())));
                                });
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
//                        }
                }
                return true;
            }
        }, new VirtualFileFilter() {
            @Override
            public boolean accept(VirtualFile file) {
                return file.getPath().contains(value);
            }
        });
        PsiManager psiManager = PsiManager.getInstance(project);
        ResolveResult[] result = new ResolveResult[1];
        final Optional<Pair<Integer, VirtualFile>> resourcesPair = sortedResults.stream()
                .filter(pair -> pair.getSecond().getPath().contains("resources"))
                .max(Comparator.comparingInt(a -> a.second.getParent().getPath().length()));
        if (resourcesPair.isPresent()) {
            PsiFile psiFile = psiManager.findFile(resourcesPair.get().getSecond());
            if (psiFile != null) {
                result[0] = new PsiElementResolveResult(psiFile);
                return result;
            }
        }
        sortedResults.stream()
                .max(Comparator.comparingInt(a -> a.second.getParent().getPath().length()))
                .ifPresent(pair -> {
                    PsiFile psiFile = psiManager.findFile(pair.getSecond());
                    if (psiFile != null) {
                        result[0] = new PsiElementResolveResult(psiFile);
                    }
                });
        return result;
    }

    private String computeStringValue() {
        final T element = getElement();

        PsiElement current = element;
        while ((current instanceof PsiLiteralExpression
                || current instanceof PsiExpressionList
                || current instanceof PsiMethodCallExpression)
                && !current.equals(current.getParent())) {
            if (current.getParent() == null) {
                break;
            }
            current = current.getParent();
        }
        final Collection<PsiLiteralExpression> children =
                PsiTreeUtil.findChildrenOfType(current, PsiLiteralExpression.class);
        StringBuilder result = new StringBuilder();
        int i = 0;
        for (PsiLiteralExpression child : children) {
            if (child.getValue() instanceof String) {
                result.append((String) child.getValue());
            }
            if (i < children.size() - 1) {
                result.append(File.separator);
            }
            i++;
        }
        return result.toString();
    }
}
