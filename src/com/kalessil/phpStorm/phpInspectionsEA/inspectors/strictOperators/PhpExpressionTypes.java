package com.kalessil.phpStorm.phpInspectionsEA.inspectors.strictOperators;

import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.PsiElement;
import com.jetbrains.php.PhpIndex;
import com.jetbrains.php.lang.psi.elements.Function;
import com.jetbrains.php.lang.psi.elements.PhpClass;
import com.kalessil.phpStorm.phpInspectionsEA.utils.ExpressionSemanticUtil;
import com.kalessil.phpStorm.phpInspectionsEA.utils.TypeFromPsiResolvingUtil;
import com.kalessil.phpStorm.phpInspectionsEA.utils.Types;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.HashSet;

public class PhpExpressionTypes {
    private final HashSet<String> types = new HashSet<String>();
    private boolean isMixed;
    private final PhpIndex objIndex;
    final static private String strTypeObject = "object";
    final static private String strTypeStatic = "static";
    final static private String strTypeArrayAccess = "\\ArrayAccess";

    public PhpExpressionTypes(final PsiElement expr, @NotNull final ProblemsHolder holder) {
        objIndex = PhpIndex.getInstance(holder.getProject());

        if (expr != null) {
            final Function objScope = ExpressionSemanticUtil.getScope(expr);
            TypeFromPsiResolvingUtil.resolveExpressionType(expr, objScope, objIndex, types);
        }

        types.remove(Types.strResolvingAbortedOnPsiLevel);
        types.remove(Types.strClassNotResolved);

        checkTypes();
    }

    public PhpExpressionTypes(@NotNull final String strTypes, @NotNull final ProblemsHolder holder) {
        objIndex = PhpIndex.getInstance(holder.getProject());

        if ((strTypes.indexOf('?') >= 0) || (strTypes.indexOf('#') >= 0)) {
            types.add(Types.strMixed);
        } else {
            for (final String str : strTypes.split("\\|")) {
                if (!str.isEmpty()) {
                    types.add(Types.getType(str));
                }
            }
        }

        checkTypes();
    }

    private void checkTypes() {
        if (types.contains(Types.strCallable)) {
            types.add(Types.strString);
        }
        if (types.contains(strTypeStatic)) {
            types.add(strTypeObject);
        }
        if (types.isEmpty()) {
            types.add(Types.strMixed);
        }

        isMixed = types.contains(Types.strMixed);
    }

    public boolean equals(@NotNull final PhpExpressionTypes another) {
        // skip if one of expressions has "mixed" type
        // (otherwise many false-positives are generated)
        // @todo remove if type deduction will be improved in next PhpStorm version
        if (isMixed || another.isMixed) {
            return true;
        }

        final HashSet<String> copy = new HashSet<String>(types);
        copy.retainAll(another.types);
        return !copy.isEmpty();

    }

    public String toString() {
        switch (types.size()) {
            case 0:
                return "unknown";
            case 1:
                return types.iterator().next();
            default:
                final StringBuilder sb = new StringBuilder();
                for (final String s : types) {
                    sb.append(s);
                    sb.append('|');
                }
                return sb.delete(sb.length() - 1, sb.length()).toString();
        }
    }

    public boolean contains(final String type) {
        return types.contains(type);
    }

    public boolean instanceOf(final PhpExpressionTypes base) {
        final boolean instanceOfObject = base.types.contains(strTypeObject);
        for (final String type1 : types) {
            if (type1.charAt(0) == '\\') {
                if (instanceOfObject) {
                    return true;
                }

                final HashSet<String> extendslist = new HashSet<String>();
                getParentsList(type1, extendslist);

                for (final String type2 : base.types) {
                    if (type2.charAt(0) == '\\') {
                        if (extendslist.contains(type2)) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    public boolean isInt() {
        return isMixed || types.contains(Types.strInteger);
    }

    public boolean isFloat() {
        return isMixed || types.contains(Types.strFloat);
    }

    public boolean isNumeric() {
        return isMixed || types.contains(Types.strInteger) || types.contains(Types.strFloat);
    }

    public boolean isString() {
        return isMixed || types.contains(Types.strString);
    }

    public boolean isBoolean() {
        return isMixed || types.contains(Types.strBoolean);
    }

    public boolean isArray() {
        return isMixed || types.contains(Types.strArray);
    }

    public boolean isNull() {
        return isMixed || types.contains(Types.strNull);
    }

    public boolean isMixed() {
        return isMixed;
    }

    public boolean isObject() {
        for (final String type : types) {
            if (type.charAt(0) == '\\') {
                return true;
            }
        }
        return false;
    }

    public boolean isArrayAccess() {
        for (final String type1 : types) {
            if (type1.charAt(0) == '\\') {
                final HashSet<String> extendslist = new HashSet<String>();
                getParentsList(type1, extendslist);
                if (extendslist.contains(strTypeArrayAccess)) {
                    return true;
                }
            }
        }
        return false;
    }

    private void getParentsList(final String className, final HashSet<String> extendslist) {
        for (PhpClass typeclass : objIndex.getAnyByFQN(className)) {
            while (typeclass != null) {
                extendslist.add(typeclass.getFQN());

                String[] interfaceNames = typeclass.getInterfaceNames();
                for (final String interfaceName : interfaceNames) {
                    if (!extendslist.contains(interfaceName)) {
                        extendslist.add(interfaceName);
                        getParentsList(interfaceName, extendslist);
                    }
                }

                extendslist.addAll(Arrays.asList(typeclass.getTraitNames()));
                extendslist.addAll(Arrays.asList(typeclass.getMixinNames()));

                typeclass = typeclass.getSuperClass();
            }
        }
    }
}
