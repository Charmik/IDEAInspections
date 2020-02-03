import com.intellij.patterns.StandardPatterns;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiLiteral;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceContributor;
import com.intellij.psi.PsiReferenceProvider;
import com.intellij.psi.PsiReferenceRegistrar;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;

/**
 * Created with IntelliJ IDEA.
 * User: Max
 * Date: 05.05.13
 * Time: 0:19
 */
public class FileReferenceContributor extends PsiReferenceContributor {

    @Override
    public void registerReferenceProviders(PsiReferenceRegistrar registrar) {

        try {
            Class.forName("com.intellij.psi.PsiLiteral");
            registrar.registerReferenceProvider(StandardPatterns.instanceOf(PsiLiteral.class), new PsiReferenceProvider() {
                @NotNull
                public PsiReference[] getReferencesByElement(@NotNull PsiElement element, @NotNull ProcessingContext context) {
                    return new PsiReference[]{new SourceNavigation<>((PsiLiteral) element)};
                }
            });
        } catch (ClassNotFoundException e) {
            //Ok, then. Some JetBrains platform IDE that has no Java support.
        }
    }
}
