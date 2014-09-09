package com.txtr;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import com.google.common.base.Charsets;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.Files;
import com.google.gson.Gson;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtBlock;
import spoon.reflect.code.CtCodeSnippetStatement;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtLocalVariable;
import spoon.reflect.code.CtStatement;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.declaration.CtExecutable;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtNamedElement;
import spoon.reflect.declaration.CtParameter;
import spoon.reflect.reference.CtTypeReference;

public class CreateInfo extends AbstractProcessor<CtParameter<?>> {

    public static final File INFO_FILE = new File("/home/gregor/tmp/info.json");
    TreeMap<String, Map<String, Map<String, String>>> info = new TreeMap<>();

    @Override
    public boolean isToBeProcessed(CtParameter<?> element) {
        CtExecutable<?> parent = element.getParent();

        if (parent instanceof CtMethod<?>) {
            if ("token".equals(element.getSimpleName())) {
                CtElement c = element.getParent().getParent();
                if (c instanceof CtClass<?>) {
                    CtClass<?> ctClass = (CtClass<?>) c;
                    if (ctClass.getSimpleName().startsWith("WS") && !ctClass.getSimpleName().endsWith("Initializer") && parent.getAnnotation(Override.class) != null) {
                        Set<CtTypeReference<?>> superInterfaces = ctClass.getSuperInterfaces();
                        if (superInterfaces.size() == 1 && superInterfaces.iterator().next().getSimpleName().startsWith("IWS")) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    public void process(CtParameter<?> element) {
        // we declare a new snippet of code to be inserted
        CtCodeSnippetStatement snippet = getFactory().Core().createCodeSnippetStatement();

        CtMethod method = element.getParent(CtMethod.class);
        CtBlock body = method.getBody();
        String value;
        if (body.getStatements().size() > 0) {
            CtStatement statement = body.getStatement(0);
            value = statement.toString();
            if (statement instanceof CtLocalVariable<?>) {
                CtExpression<?> defaultExpression = ((CtLocalVariable<?>) statement).getDefaultExpression();
                if (defaultExpression instanceof CtInvocation<?>) {
                    if (processInvocation(element, (CtInvocation<?>) defaultExpression)) {
                        return;
                    }
                }
            }
            if (statement instanceof CtInvocation<?>) {
                if (processInvocation(element, (CtInvocation<?>) statement)) {
                    return;
                }
            }
        } else {
            value = "empty";
        }

        Map<String, String> v1 = new LinkedHashMap<>();
        v1.put(value, "null");
        info.put(getName(element), ImmutableMap.of("could not process", v1));
        System.out.println("could not process: " + method.toString());

//        // this snippet contains an if check
//        snippet.setValue("if(" + element.getSimpleName() + " == null "
//            + ") throw new IllegalArgumentException(\"[Spoon inserted check] null passed as parameter\");");
//
//        // we insert the snippet at the beginning of the method boby
//        if (element.getParent(CtExecutable.class).getBody() != null) {
//            element.getParent(CtExecutable.class).getBody().insertBegin(snippet);
//        }
    }

    private boolean processInvocation(CtParameter<?> element, CtInvocation<?> invocation) {
        CtExpression<?> target = invocation.getTarget();
        if (target == null) {
            return false;
        }

        if (target.getType().getSimpleName().equals("IAuth")) {

            String tokenType = invocation.getExecutable().getSimpleName();

            if (Arrays.asList("verifyAnonymousUser", "verifyKnownUser", "verifyDummyContext").contains(tokenType)) {

                String name = getName(element);
                Map<String, Map<String, String>> put = info.put(name, ImmutableMap.of(tokenType, getRequiredRights(invocation)));
                if (put != null) {
                    System.out.println("already seen: " + name);
                }

                return true;
            }
        }
        return false;
    }

    public static String getName(CtParameter<?> element) {
        String mname = element.getParent().getSimpleName();
        String cname = ((CtNamedElement) element.getParent().getParent()).getSimpleName();
        return cname + "." + mname;
    }

    private Map<String, String> getRequiredRights(CtInvocation<?> invocation) {
        Map<String, String> result = new LinkedHashMap<>();

        if (invocation.getArguments().size() > 1) {
            for (CtExpression<?> expression : invocation.getArguments().subList(1, invocation.getArguments().size())) {
                List<CtExpression<?>> arguments = (List<CtExpression<?>>) ((CtInvocation) expression).getArguments();
                if (arguments.size() == 1) {
                    result.put(arguments.get(0).toString(), "#");
                } else if (arguments.size() == 2) {
                    result.put(arguments.get(0).toString(), arguments.get(1).toString());
                } else {
                    throw new IllegalStateException();
                }
            }
        }

        return result;
    }

    @Override
    public void processingDone() {
        try {
            Files.write(new Gson().toJson(info), INFO_FILE, Charsets.UTF_8);
        } catch (IOException e) {
            throw Throwables.propagate(e);
        }
    }

}