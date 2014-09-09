package com.txtr;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;

import org.apache.commons.lang3.StringUtils;

import com.google.common.base.Charsets;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.io.Files;
import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtBlock;
import spoon.reflect.code.CtLocalVariable;
import spoon.reflect.code.CtStatement;
import spoon.reflect.cu.CompilationUnit;
import spoon.reflect.cu.SourceCodeFragment;
import spoon.reflect.declaration.CtAnnotation;
import spoon.reflect.declaration.CtExecutable;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtParameter;

public class UpdateInterface extends AbstractProcessor<CtParameter<?>> {

    TreeMap<String, Map<String, Map<String, String>>> info = readInfo();

    @Override
    public boolean isToBeProcessed(CtParameter<?> element) {
        CtExecutable<?> parent = element.getParent();

        if (parent instanceof CtMethod<?>) {
            if ("token".equals(element.getSimpleName())) {
                if (info.containsKey(getInterfaceKey(element)) || info.containsKey(getImplKey(element))) {
                    return true;
                }
            }
        }

        return false;
    }

    private String getInterfaceKey(CtParameter<?> element) {
        return CreateInfo.getName(element).substring(1);
    }

    private String getImplKey(CtParameter<?> element) {
        return CreateInfo.getName(element);
    }

    public void process(CtParameter<?> element) {
        String interfaceKey = getInterfaceKey(element);
        if (info.containsKey(interfaceKey)) {
            Entry<String, Map<String, String>> type = Iterables.getOnlyElement(info.get(interfaceKey).entrySet());
            updateInterface(element, interfaceKey, type.getKey(), type.getValue());
        }

        String implKey = getImplKey(element);
        if (info.containsKey(implKey)) {
            Entry<String, Map<String, String>> type = Iterables.getOnlyElement(info.get(implKey).entrySet());
            updateImpl(element, implKey, type.getKey(), type.getValue());
        }
    }

    private void updateImpl(CtParameter element, String key, String type, Map<String, String> map) {
        CtBlock body = element.getParent().getBody();
        CtStatement statement = body.getStatement(0);

        if ("verifyAnonymousUser".equals(type)) {
            setType(element, "com.bookpac.server.common.AnonymousUserToken", true, statement);
        } else if ("verifyDummyContext".equals(type)) {
            setType(element, "com.bookpac.server.common.DummyToken", true, statement);
        } else if ("verifyKnownUser".equals(type)) {
            setType(element, "com.bookpac.server.common.KnownUserToken", map.isEmpty() || !hasExtraCheck(map.values()), statement);
        } else if (key.equals("WSAuth.authenticateUserWithSessionByName")) {
            //ok
        } else {
            throw new IllegalStateException("cannot process: " + key);
        }
    }

    private void removeAuthLine(CtStatement statement, String qualifiedName) {
        CompilationUnit cu = statement.getPosition().getCompilationUnit();
        SourceCodeFragment fragment1 = new SourceCodeFragment();
        SourceCodeFragment fragment = fragment1;
        int sourceStart = statement.getPosition().getSourceStart();
        int lineIndex = cu.beginOfLineIndex(sourceStart);
        fragment.position = lineIndex;
        fragment.replacementLength = cu.nextLineIndex(sourceStart) - lineIndex;
        fragment.code = getReplacement(statement, qualifiedName);

        cu.addSourceCodeFragment(fragment);
    }

    private String getReplacement(CtStatement statement, String qualifiedName) {
        if (statement instanceof CtLocalVariable<?>) {
            String type = qualifiedName.equals("com.bookpac.server.common.DummyToken") ? "INatureCallContext" : "AuthenticatedCallContext";
            return type + " " + ((CtLocalVariable) statement).getSimpleName() + " = com.bookpac.server.appserver.CallContextUtil.create(token);\n";
        }
        return "";
    }

    private void setType(CtParameter parameter, String qualifiedName, boolean removeLine, CtStatement statement) {
        CompilationUnit cu = parameter.getPosition().getCompilationUnit();
        SourceCodeFragment fragment1 = new SourceCodeFragment();
        SourceCodeFragment fragment = fragment1;
        int extra = "String ".length();
        fragment.position = parameter.getPosition().getSourceStart() - extra;
        fragment.replacementLength =
            parameter.getPosition().getSourceEnd() - parameter.getPosition().getSourceStart() + 1 + extra;
        fragment.code = qualifiedName + " token";

        cu.addSourceCodeFragment(fragment);

        if (removeLine) {
            removeAuthLine(statement, qualifiedName);
        }
    }

    private void updateInterface(CtParameter element, String key, String type, Map<String, String> map) {
        if ("verifyAnonymousUser".equals(type)) {
            setType(element, "com.bookpac.server.common.AnonymousUserToken", false, null);
        } else if ("verifyDummyContext".equals(type)) {
            setType(element, "com.bookpac.server.common.DummyToken", false, null);
        } else if ("verifyKnownUser".equals(type)) {
            setType(element, "com.bookpac.server.common.KnownUserToken", false, null);

            if (!map.isEmpty()) {
                addAnnotations(element.getParent(), map);
            }

        } else if (key.equals("WSAuth.authenticateUserWithSessionByName")) {
            //ok
        } else {
            throw new IllegalStateException("cannot process: " + key);
        }
    }

    private boolean hasExtraCheck(Collection<String> values) {
        for (String value : values) {
            if (!"#".equals(value) && !"null".equals(value)) {
                return true;
            }
        }
        return false;
    }

    private void addAnnotations(CtExecutable method, Map<String, String> map) {
        CtAnnotation<Annotation> annotation = getFactory().Annotation().annotate(method, getFactory().Type().<Annotation>createReference("com.bookpac.server.common.RequiredRights"));

        List<CtAnnotation<Annotation>> vals = new ArrayList<>();
        for (Entry<String, String> entry : map.entrySet()) {
            createAnnotation(entry, vals);
        }

        annotation.setElementValues(ImmutableMap.<String, Object>of("value", vals));

        method.addAnnotation(annotation);

        CompilationUnit cu = method.getPosition().getCompilationUnit();
        SourceCodeFragment fragment1 = new SourceCodeFragment();
        SourceCodeFragment fragment = fragment1;
        fragment.position = cu.beginOfLineIndex(method.getPosition().getSourceStart());
        fragment.replacementLength = 0;
        fragment.code = annotation.toString() + "\n";

        cu.addSourceCodeFragment(fragment);
    }

    private void createAnnotation(Entry<String, String> entry, List<CtAnnotation<Annotation>> vals) {
        String right = entry.getKey();
        String rest = entry.getValue();
        CtAnnotation<Annotation> annotate = getFactory().Core().createAnnotation();
        annotate.setAnnotationType(getFactory().Annotation().<Annotation>createReference("com.bookpac.server.common.RequiredRight"));

        LinkedHashMap<String, Object> values = new LinkedHashMap<>();
        values.put("value", getFactory().Field().createReference(
            getFactory().Type().createReference("com.bookpac.server.user.WSTRight"),
            getFactory().Type().createReference("com.bookpac.server.user.WSTRight"),
            StringUtils.substringAfterLast(right, ".")
        ));

        if (rest.equals("null")) {
            values.put("restriction", "");
        }

        annotate.setElementValues(values);

        vals.add(annotate);
    }

    public static TreeMap<String, Map<String, Map<String, String>>> readInfo() {
        try {
            return new Gson().fromJson(Files.toString(CreateInfo.INFO_FILE, Charsets.UTF_8), new TypeToken<TreeMap<String, Map<String, Map<String, String>>>>() {
            }.getType());
        } catch (IOException e) {
            throw Throwables.propagate(e);
        }
    }

    public static void main(String[] args) {
        readInfo();
    }

}