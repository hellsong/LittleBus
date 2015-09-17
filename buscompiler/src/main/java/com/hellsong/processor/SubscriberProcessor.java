package com.hellsong.processor;

import com.google.auto.service.AutoService;
import com.hellsong.littlebuslibrary.AbstractBus;
import com.hellsong.littlebuslibrary.OnEvent;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeSpec;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

import static java.util.Collections.singleton;
import static javax.lang.model.SourceVersion.latestSupported;
import static javax.tools.Diagnostic.Kind.ERROR;

@AutoService(Processor.class)
public class SubscriberProcessor extends AbstractProcessor {
    private Types mTypeUtils;
    private Elements mElementUtils;
    private Filer mFiler;
    private Messager mMessager;
    private Map<String, SubscriberClassInfo> subscriberMap;

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        mTypeUtils = processingEnv.getTypeUtils();
        mElementUtils = processingEnv.getElementUtils();
        mFiler = processingEnv.getFiler();
        mMessager = processingEnv.getMessager();
        subscriberMap = new HashMap<>();
    }

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        return singleton(OnEvent.class.getCanonicalName());
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return latestSupported();
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        for (Element element : roundEnv.getElementsAnnotatedWith(OnEvent.class)) {
            if (!(element instanceof ExecutableElement) || element.getKind() != ElementKind.METHOD) {
                error(element, "Annotation should use on method!");
                continue;
            }
            ExecutableElement executableElement = (ExecutableElement) element;
            List<? extends VariableElement> paramTypeList = executableElement.getParameters();
            if (!executableElement.getModifiers().contains(Modifier.PUBLIC)) {
                error(element, "Annotated method should declare as public!");
                continue;
            }
            if (paramTypeList == null || paramTypeList.size() != 1) {
                error(element, "Annotated method should have a parameter! paramTypeList.size = " + paramTypeList.size());
                continue;
            }
            OnEvent onEventAnnotation = element.getAnnotation(OnEvent.class);
            boolean isOnMain = onEventAnnotation.isRunOnBackGroundThread();
            TypeElement enclosingElement = (TypeElement) element.getEnclosingElement();
            String wrapClassFullName = enclosingElement.getQualifiedName().toString();
            if (subscriberMap.get(wrapClassFullName) == null) {
                SubscriberClassInfo subscriberClassInfo = new SubscriberClassInfo();
                subscriberMap.put(wrapClassFullName, subscriberClassInfo);
            }
            SubscriberClassInfo.EventHandlerMethod handlerMethod = new SubscriberClassInfo.EventHandlerMethod();
            handlerMethod.mMethod = element.getSimpleName().toString();
            handlerMethod.isRunOnMainThread = isOnMain;
            String eventFullName = Util.typeToString(paramTypeList.get(0).asType());
            subscriberMap.get(wrapClassFullName).addEvent(eventFullName, handlerMethod);
            subscriberMap.get(wrapClassFullName).setSubcriberClassName(wrapClassFullName);
        }
        generateCode();
        return true;
    }

    private void generateCode() {
        TypeSpec.Builder busTypeBuilder = TypeSpec.classBuilder(AbstractBus.BusClassName)
                .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                .addField(getBusClassName(), "sInstance", Modifier.PRIVATE, Modifier.VOLATILE, Modifier.STATIC);
        busTypeBuilder.superclass(ClassName.get(AbstractBus.class));

        busTypeBuilder.addMethod(createSingletonMethod());
        addRegisterMethod(busTypeBuilder);
        addPostMethod(busTypeBuilder);
        JavaFile javaFile = JavaFile.builder(AbstractBus.BusPackageName, busTypeBuilder.build())
                .build();

        try {
            javaFile.writeTo(mFiler);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void addPostMethod(TypeSpec.Builder busTypeBuilder) {
        Map<String, Set<SubscriberClassInfo>> eventHandlersMap = new HashMap<>();
        for (Map.Entry<String, SubscriberClassInfo> entry : subscriberMap.entrySet()) {
            SubscriberClassInfo subscriberClassInfo = entry.getValue();
            if (subscriberClassInfo.getEventMap() == null) {
                continue;
            }
            Set<String> eventSet = subscriberClassInfo.getEventMap().keySet();
            if (eventSet == null || eventSet.size() == 0) {
                continue;
            }
            for (String eventFullName : eventSet) {
                if (eventHandlersMap.get(eventFullName) == null) {
                    eventHandlersMap.put(eventFullName, new HashSet<SubscriberClassInfo>());
                }
                eventHandlersMap.get(eventFullName).add(subscriberClassInfo);
            }
        }

        for (Map.Entry<String, Set<SubscriberClassInfo>> entry : eventHandlersMap.entrySet()) {
            MethodSpec.Builder postInnerMethodBuilder = MethodSpec.methodBuilder("postInner")
                    .addModifiers(Modifier.PRIVATE)
                    .addParameter(ClassName.bestGuess(entry.getKey()), "event", Modifier.FINAL)
                    .addCode("if (event == null) {\n" +
                            "   return;\n" + "}\n");
            postInnerMethodBuilder.addCode("$T<Object> subscriberMap = mEventHandlerMap.get(event.getClass());\n", Set.class);
            postInnerMethodBuilder.beginControlFlow("for(final Object subscriber:subscriberMap)");
            for (SubscriberClassInfo subscriberClassInfo : entry.getValue()) {
                postInnerMethodBuilder.beginControlFlow("if(subscriber instanceof " + subscriberClassInfo.getSubscriberClass() + ")");
                TypeSpec taskRunnable = TypeSpec.anonymousClassBuilder("")
                        .addSuperinterface(ClassName.get(Runnable.class))
                        .addMethod(MethodSpec.methodBuilder("run")
                                .returns(void.class)
                                .addModifiers(Modifier.PUBLIC)
                                .addCode("((" + subscriberClassInfo.getSubscriberClass() + ")subscriber)." + subscriberClassInfo.getEventMap().get(entry.getKey()).mMethod + "(event);\n")
                                .build())
                        .build();
                boolean isOnMain = subscriberClassInfo.getEventMap().get(entry.getKey()).isRunOnMainThread;
                postInnerMethodBuilder.addStatement("mDispatcher.excuteTask( $L, " + isOnMain + ")", taskRunnable);
                postInnerMethodBuilder.endControlFlow();
            }
            postInnerMethodBuilder.endControlFlow();
            MethodSpec postInnerInnerMethod = postInnerMethodBuilder.build();
            busTypeBuilder.addMethod(postInnerInnerMethod);

            MethodSpec.Builder registerMethod = MethodSpec.methodBuilder("post")
                    .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                    .addParameter(ClassName.bestGuess(entry.getKey()), "event");
            registerMethod.addStatement("$T<Class<?>> eventHierarchySet = getHierarchyTypes(event);", Set.class);
            registerMethod.beginControlFlow("for(Class<?> eventClass: eventHierarchySet)");
            registerMethod.addCode(AbstractBus.BusClassName + ".getInstance().$N(eventClass.cast(event));\n", postInnerInnerMethod);
            registerMethod.endControlFlow();
            busTypeBuilder.addMethod(registerMethod.build());
        }
    }

    private void addRegisterMethod(TypeSpec.Builder busTypeBuilder) {
        for (Map.Entry<String, SubscriberClassInfo> entry : subscriberMap.entrySet()) {
            SubscriberClassInfo subscriberClassInfo = entry.getValue();
            if (subscriberClassInfo.getEventMap() == null || subscriberClassInfo.getEventMap().size() == 0 || subscriberClassInfo.getSubscriberClass() == null) {
                continue;
            }
            {
                MethodSpec.Builder registerInnerMethodBuilder = MethodSpec.methodBuilder("registerInner")
                        .addModifiers(Modifier.PRIVATE)
                        .addParameter(ClassName.bestGuess(subscriberClassInfo.getSubscriberClass()), "target")
                        .addCode("if (target == null) {\n" +
                                "   return;\n" + "}\n");
                for (String eventName : subscriberClassInfo.getEventMap().keySet()) {
                    registerInnerMethodBuilder.beginControlFlow("if(mEventHandlerMap.get(" + eventName + ".class)==null)")
                            .addStatement("mEventHandlerMap.put(" + eventName + ".class, new $T<>())", HashSet.class)
                            .endControlFlow();

                    registerInnerMethodBuilder.addStatement("mEventHandlerMap.get(" + eventName + ".class).add(target)");
                }
                MethodSpec registerInnerMethod = registerInnerMethodBuilder.build();
                busTypeBuilder.addMethod(registerInnerMethod);

                MethodSpec.Builder registerMethod = MethodSpec.methodBuilder("register")
                        .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                        .addParameter(ClassName.bestGuess(subscriberClassInfo.getSubscriberClass()), "target")
                        .addCode(AbstractBus.BusClassName + ".getInstance().$N(target);\n", registerInnerMethod);
                busTypeBuilder.addMethod(registerMethod.build());
            }
            //Unregister code.
            {
                MethodSpec.Builder unregisterInnerMethodBuilder = MethodSpec.methodBuilder("unregisterInner")
                        .addModifiers(Modifier.PRIVATE)
                        .addParameter(ClassName.bestGuess(subscriberClassInfo.getSubscriberClass()), "target")
                        .addCode("if (target == null) {\n" +
                                "   return;\n" + "}\n");
                for (String eventName : subscriberClassInfo.getEventMap().keySet()) {
                    unregisterInnerMethodBuilder.beginControlFlow("if(mEventHandlerMap.get(" + eventName + ".class)==null)")
                            .addStatement("mEventHandlerMap.put(" + eventName + ".class, new $T<>())", HashSet.class)
                            .endControlFlow();

                    unregisterInnerMethodBuilder.addStatement("mEventHandlerMap.get(" + eventName + ".class).remove(target)");
                }
                MethodSpec unregisterInnerMethod = unregisterInnerMethodBuilder.build();
                busTypeBuilder.addMethod(unregisterInnerMethod);

                MethodSpec.Builder unregisterMethod = MethodSpec.methodBuilder("unregister")
                        .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                        .addParameter(ClassName.bestGuess(subscriberClassInfo.getSubscriberClass()), "target")
                        .addCode(AbstractBus.BusClassName + ".getInstance().$N(target);\n", unregisterInnerMethod);
                busTypeBuilder.addMethod(unregisterMethod.build());
            }
        }
    }

    private MethodSpec createSingletonMethod() {
        MethodSpec main = MethodSpec.methodBuilder("getInstance")
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .returns(getBusClassName())
                .addCode("if (sInstance == null) {\n" +
                        "   synchronized (" + AbstractBus.BusClassName + ".class) {\n" +
                        "       if (sInstance == null) {\n" +
                        "           sInstance = new " + AbstractBus.BusClassName + "();\n" +
                        "       }\n" +
                        "   }\n" +
                        "}\n" +
                        "return sInstance;\n")
                .build();
        return main;
    }

    private ClassName getBusClassName() {
        return ClassName.get(AbstractBus.BusPackageName, AbstractBus.BusClassName);
    }

    private void error(Element element, String message, Object... args) {
        if (args.length > 0) {
            message = String.format(message, args);
        }
        mMessager.printMessage(ERROR, message, element);
    }
}
