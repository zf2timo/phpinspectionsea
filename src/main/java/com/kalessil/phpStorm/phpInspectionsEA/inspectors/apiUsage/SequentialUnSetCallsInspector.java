package com.kalessil.phpStorm.phpInspectionsEA.inspectors.apiUsage;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import com.jetbrains.php.lang.documentation.phpdoc.psi.PhpDocComment;
import com.jetbrains.php.lang.psi.PhpPsiElementFactory;
import com.jetbrains.php.lang.psi.elements.PhpUnset;
import com.kalessil.phpStorm.phpInspectionsEA.openApi.BasePhpElementVisitor;
import com.kalessil.phpStorm.phpInspectionsEA.openApi.BasePhpInspection;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/*
 * This file is part of the Php Inspections (EA Extended) package.
 *
 * (c) Vladimir Reznichenko <kalessil@gmail.com>
 *
 * For the full copyright and license information, please view the LICENSE
 * file that was distributed with this source code.
 */

public class SequentialUnSetCallsInspector extends BasePhpInspection {
    private static final String message = "Can be safely replaced with 'unset(..., ...[, ...])' construction.";

    @NotNull
    public String getShortName() {
        return "SequentialUnSetCallsInspection";
    }

    @Override
    @NotNull
    public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder, boolean isOnTheFly) {
        return new BasePhpElementVisitor() {
            @Override
            public void visitPhpUnset(@NotNull PhpUnset unsetStatement) {
                PsiElement previous = unsetStatement.getPrevPsiSibling();
                while (previous instanceof PhpDocComment) {
                    previous = ((PhpDocComment) previous).getPrevPsiSibling();
                }
                if (previous instanceof PhpUnset) {
                    holder.registerProblem(unsetStatement, message, new TheLocalFix(unsetStatement));
                }
            }
        };
    }

    private static class TheLocalFix implements LocalQuickFix {
        final private SmartPsiElementPointer<PhpUnset> unset;

        @NotNull
        @Override
        public String getName() {
            return "Merge unset statements";
        }

        @NotNull
        @Override
        public String getFamilyName() {
            return getName();
        }

        TheLocalFix(@NotNull PhpUnset unset) {
            super();
            final SmartPointerManager manager =  SmartPointerManager.getInstance(unset.getProject());

            this.unset = manager.createSmartPsiElementPointer(unset);
        }

        @Override
        public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
            final PhpUnset unset = this.unset.getElement();
            if (unset != null) {
                synchronized (unset.getContainingFile()) {
                    /* find preceding unset-statement */
                    PsiElement previous = unset.getPrevPsiSibling();
                    while (previous instanceof PhpDocComment) {
                        previous = ((PhpDocComment) previous).getPrevPsiSibling();
                    }
                    if (!(previous instanceof PhpUnset)) {
                        return;
                    }

                    /* collect all parameters */
                    final List<PsiElement> arguments = new ArrayList<>();
                    arguments.addAll(Arrays.asList(((PhpUnset) previous).getArguments()));
                    arguments.addAll(Arrays.asList(unset.getArguments()));

                    /* generate target expression */
                    final String list    = arguments.stream().map(PsiElement::getText).collect(Collectors.joining(", "));
                    final String pattern = "unset(%p%);".replace("%p%", list);
                    arguments.clear();

                    /* apply refactoring */
                    final PhpUnset merged = PhpPsiElementFactory.createFromText(project, PhpUnset.class, pattern);
                    if (merged != null) {
                        previous.replace(merged);
                        unset.delete();
                    }
                }
            }
        }
    }
}