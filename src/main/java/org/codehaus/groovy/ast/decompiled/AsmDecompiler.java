/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */
package org.codehaus.groovy.ast.decompiled;

import groovy.lang.GroovyRuntimeException;
import org.codehaus.groovy.util.URLStreams;
import org.glavo.classfile.*;
import org.glavo.classfile.attribute.ExceptionsAttribute;
import org.glavo.classfile.attribute.RuntimeVisibleAnnotationsAttribute;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.RecordComponentVisitor;
import org.objectweb.asm.Type;
import org.objectweb.asm.TypePath;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.SoftReference;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static org.codehaus.groovy.control.CompilerConfiguration.ASM_API_VERSION;
import static org.codehaus.groovy.control.ResolveVisitor.EMPTY_STRING_ARRAY;

/**
 * A utility class responsible for decompiling JVM class files and producing {@link ClassStub} objects reflecting their structure.
 */
public abstract class AsmDecompiler {

    /**
     * Caches stubs per URI. This cache is useful when performing multiple compilations in the same JVM/class loader and in tests.
     *
     * It's synchronized "just in case". Occasional misses are expected if several threads attempt to load the same class,
     * but this shouldn't result in serious memory issues.
     */
    private static final Map<URI, SoftReference<ClassStub>> stubCache = new ConcurrentHashMap<>(); // According to http://michaelscharf.blogspot.jp/2006/11/javaneturlequals-and-hashcode-make.html, use java.net.URI instead.

    /**
     * Loads the URL contents and parses them with ASM, producing a {@link ClassStub} object representing the structure of
     * the corresponding class file. Stubs are cached and reused if queried several times with equal URLs.
     *
     * @param url a URL from a class loader, most likely a file system file or a JAR entry.
     * @return the class stub
     * @throws IOException if reading from this URL is impossible
     */
    public static ClassStub parseClass(final URL url) throws IOException {
        URI uri;
        try {
            uri = url.toURI();
        } catch (URISyntaxException e) {
            throw new GroovyRuntimeException(e);
        }

        SoftReference<ClassStub> ref = stubCache.get(uri);
        ClassStub stub = (ref != null ? ref.get() : null);
        if (stub == null) {
            DecompilingVisitor visitor = new DecompilingVisitor();

            boolean useOldMethod = true;
            if (useOldMethod) {
                //if (true) throw new RuntimeException("onoz");
                try (InputStream stream = new BufferedInputStream(URLStreams.openUncachedStream(url))) {
                    new ClassReader(stream).accept(visitor, ClassReader.SKIP_FRAMES);
                }
                byte[] bytes;
                try (InputStream stream = new BufferedInputStream(URLStreams.openUncachedStream(url))) {
                    bytes = stream.readAllBytes();
                }
                ClassModel cm = Classfile.parse(bytes);

                stub = nuParse(cm);
                if (!Objects.equals(nuParse(cm), visitor.result)) {
                    throw new RuntimeException("they not equal\n" + nuParse(cm) + "\n" + visitor.result);
                }
                //throw new RuntimeException("oh noz" + cm);

            } else {
                byte[] bytes;
                try (InputStream stream = new BufferedInputStream(URLStreams.openUncachedStream(url))) {
                    bytes = stream.readAllBytes();
                }
                ClassModel cm = Classfile.parse(bytes);
                stub = nuParse(cm);
            }


            stub = visitor.result;
            stubCache.put(uri, new SoftReference<>(stub));
        }
        return stub;
    }

    private static ClassStub nuParse(ClassModel cm) {
        String className = dropFirstAndLastChar(fromInternalName(cm.thisClass().asSymbol().descriptorString()));
        ClassElementConsumer classElementConsumer = new ClassElementConsumer(className);
        cm.forEach(classElementConsumer);
        cm.forEach(f -> {
            System.out.println(f.toString());
        });
        return classElementConsumer.result();
    }

    private static AnnotationReader readAnnotationMembers(final AnnotationStub stub) {
        return new AnnotationReader() {
            @Override
            void visitAttribute(final String name, final Object value) {
                stub.members.put(name, value);
            }
        };
    }

    static String fromInternalName(final String name) {
        return name.replace('/', '.');
    }

    static String dropFirstAndLastChar(String s) {
        return s.substring(1, s.length() - 1);
    }

    //--------------------------------------------------------------------------

    private static class ClassElementConsumer implements Consumer<ClassElement> {
        //private ClassStub result;
        private final String className;
        public ClassElementConsumer(String className) {
            this.className = className;
        }
        private AccessFlags accessFlags;
        private Superclass superclass;
        private Interfaces interfaces;
        private RuntimeVisibleAnnotationsAttribute runtimeVisibleAnnotationsAttribute;
        private List<MethodModel> methodModels = new ArrayList<>();
        private List<FieldModel> fieldModels = new ArrayList<>();
        @Override
        public void accept(ClassElement classElement) {
            if (classElement instanceof AccessFlags) {
                if (accessFlags != null) {
                    throw new IllegalStateException("More than one AccessFlags");
                }
                accessFlags = (AccessFlags) classElement;
            }
            if (classElement instanceof Superclass) {
                if (superclass != null) {
                    throw new IllegalStateException("More than one Superclass");
                }
                superclass = (Superclass) classElement;
            }
            if (classElement instanceof Interfaces) {
                if (interfaces != null) {
                    throw new IllegalStateException("More than one Interfaces");
                }
                interfaces = (Interfaces) classElement;
            }
            if (classElement instanceof MethodModel) {
                methodModels.add((MethodModel) classElement);
            }
            if (classElement instanceof FieldModel) {
                fieldModels.add((FieldModel) classElement);
            }
            if (classElement instanceof RuntimeVisibleAnnotationsAttribute) {
                if (runtimeVisibleAnnotationsAttribute != null) {
                    throw new IllegalStateException("More than one RuntimeVisibleAnnotationsAttribute");
                }
                runtimeVisibleAnnotationsAttribute = (RuntimeVisibleAnnotationsAttribute) classElement;
            }
        }
        public ClassStub result() {
            int accessModifiers = accessFlags.flagsMask();
            String signature = null;
            String superName = superclass.superclassEntry().name().stringValue();
            String[] interfaceNames = interfaces.interfaces().stream().map(classEntry -> classEntry.name().stringValue()).toArray(String[]::new);
            ClassStub result = new ClassStub(className, accessModifiers, signature, superName, interfaceNames);
            methodModels.forEach(methodModel -> {
                if (result.methods == null) result.methods = new ArrayList<>();
                String methodName = methodModel.methodName().stringValue();
                String desc = methodModel.methodType().stringValue();
                MethodElementConsumer methodElementConsumer = new MethodElementConsumer(methodName, desc);
                methodModel.forEach(methodElement -> {
                    System.out.println("mefod " + methodElement);
                    methodElementConsumer.accept(methodElement);
                });
                result.methods.add(methodElementConsumer.result());

            });
            fieldModels.forEach(fieldModel -> {
                if (result.fields == null) result.fields = new ArrayList<>();
                String fieldName = fieldModel.fieldName().stringValue();
                String desc = fieldModel.fieldType().stringValue();
                FieldElementConsumer fieldElementConsumer = new FieldElementConsumer(fieldName, desc);
                fieldModel.forEach(fieldElement -> {
                    System.out.println("feld " + fieldElement);
                    fieldElementConsumer.accept(fieldElement);
                });
                result.fields.add(fieldElementConsumer.result());
            });
            if (runtimeVisibleAnnotationsAttribute != null) {
                runtimeVisibleAnnotationsAttribute.annotations().forEach(annotation -> {
                    System.out.println(annotation);
                    AnnotationStub annotationStub = result.addAnnotation(annotation.className().stringValue());
                    annotation.elements().forEach(annotationElement -> {
                        System.out.println("annot elem " + annotationElement);
                        AnnotationValue annotationValue = annotationElement.value();
                        AnnotationValueConsumer annotationValueConsumer = new AnnotationValueConsumer(annotationStub.members, annotationElement.name().stringValue());
                        annotationValueConsumer.accept(annotationValue);
                    });
                    // todo: more stuff to annotationStub ?
                });
            }
            //result.methods = methodModels.stream().map(i -> new MethodStub())
            return result;
        }
    }

    private static class AnnotationValueConsumer implements Consumer<AnnotationValue> {

        private final Map<String, Object> members;
        private final String name;

        private AnnotationValueConsumer(Map<String, Object> members, String name) {
            this.members = members;
            this.name = name;
        }

        @Override
        public void accept(AnnotationValue annotationValue) {
            if (annotationValue instanceof AnnotationValue.OfEnum) {
                AnnotationValue.OfEnum ofEnum = (AnnotationValue.OfEnum) annotationValue;
                members.put(name, of(ofEnum));
            } else if (annotationValue instanceof AnnotationValue.OfArray) {
                AnnotationValue.OfArray ofArray = (AnnotationValue.OfArray) annotationValue;
                members.put(name, ofArray.values().stream().map(i -> of((AnnotationValue.OfEnum)i)).collect(Collectors.toList()));
                System.out.println("OFARRAY" + ofArray);
            } else {
                // todo: should probably not have code like this for forwards compat concerns
                throw new RuntimeException("unsupported type " + annotationValue);
            }
        }

        private EnumConstantWrapper of(AnnotationValue.OfEnum ofEnum) {
            return new EnumConstantWrapper(ofEnum.className().stringValue(), ofEnum.constantName().stringValue());
        }
    }

    private static class MethodElementConsumer implements Consumer<MethodElement> {

        private final String methodName;
        private final String desc;
        private AccessFlags accessFlags;
        public MethodElementConsumer(String methodName, String desc) {
            this.methodName = methodName;
            this.desc = desc;
        }

        @Override
        public void accept(MethodElement methodElement) {
            if (methodElement instanceof AccessFlags) {
                accessFlags = (AccessFlags) methodElement;
            }
            if (methodElement instanceof ExceptionsAttribute) {
                throw new RuntimeException("Unsupported method that throws exceptions " + methodElement);
            }
            //if (methodElement instanceof Signature) {
            //
            //}
        }

        public MethodStub result() {
            int accessModifiers = accessFlags.flagsMask();
            String signature = null;
            String[] exceptions = new String[]{};
            MethodStub methodStub = new MethodStub(methodName, accessModifiers, desc, signature, exceptions);
            methodStub.annotationDefault = new ArrayList(1);
            return methodStub;
        }
    }

    private static class FieldElementConsumer implements Consumer<FieldElement> {

        private final String fieldName;
        private final String desc;
        private AccessFlags accessFlags;

        private FieldElementConsumer(String fieldName, String desc) {
            this.fieldName = fieldName;
            this.desc = desc;
        }

        @Override
        public void accept(FieldElement fieldElement) {
            if (fieldElement instanceof AccessFlags) {
                accessFlags = (AccessFlags) fieldElement;
            }
            System.out.println("field elem " + fieldElement);
        }

        public FieldStub result() {
            final int accessModifiers = accessFlags.flagsMask();
            final String signature = null;
            final Object value = null;
            return new FieldStub(fieldName, accessModifiers, desc, signature, value);
        }

    }

    private static class DecompilingVisitor extends ClassVisitor {

        private ClassStub result;

        public DecompilingVisitor() {
            super(ASM_API_VERSION);
            //throw new RuntimeException("breh");
        }

        @Override
        public void visit(final int version, final int access, final String name, final String signature, final String superName, final String[] interfaceNames) {
            System.out.println("ClassStub(className=" + fromInternalName(name) + ", accessModifiers=" + access + ", signature=" + signature + ", superName=" + superName + ", interfaceNames=" + Arrays.toString(interfaceNames));
            result = new ClassStub(fromInternalName(name), access, signature, superName, interfaceNames);
        }

        @Override
        public void visitInnerClass(final String name, final String outerName, final String innerName, final int access) {
            /*
             * Class files generated for inner classes have an INNERCLASS
             * reference to self. The top level class access modifiers for
             * an inner class will not accurately reflect their access. For
             * example, top-level access modifiers for private inner classes
             * are package-private, protected inner classes are public, and
             * the static modifier is not included. So the INNERCLASS self
             * reference is used to capture the correct modifiers.
             *
             * Must compare against the fully qualified name because there may
             * be other INNERCLASS references to same named nested classes from
             * other classes.
             *
             * Example:
             *
             *   public final class org/foo/Groovy8632$Builder extends org/foo/Groovy8632Abstract$Builder  {
             *     public final static INNERCLASS org/foo/Groovy8632$Builder org/foo/Groovy8632 Builder
             *     public static abstract INNERCLASS org/foo/Groovy8632Abstract$Builder org/foo/Groovy8632Abstract Builder
             */
            if (fromInternalName(name).equals(result.className)) {
                result.innerClassModifiers = access;
            }
        }

        @Override
        public MethodVisitor visitMethod(final int access, final String name, final String desc, final String signature, final String[] exceptions) {
            if ("<clinit>".equals(name)) return null;

            MethodStub stub = new MethodStub(name, access, desc, signature, exceptions != null ? exceptions : EMPTY_STRING_ARRAY);
            if (result.methods == null) result.methods = new ArrayList<>(1);
            result.methods.add(stub);
            return new MethodVisitor(api) {
                @Override
                public AnnotationVisitor visitAnnotation(final String desc, final boolean visible) {
                    return readAnnotationMembers(stub.addAnnotation(desc));
                }

                @Override
                public AnnotationVisitor visitParameterAnnotation(final int parameter, final String desc, final boolean visible) {
                    if (stub.parameterAnnotations == null) stub.parameterAnnotations = new HashMap<>(1);
                    List<AnnotationStub> list = stub.parameterAnnotations.computeIfAbsent(parameter, k -> new ArrayList<>());
                    AnnotationStub annotationStub = new AnnotationStub(desc);
                    list.add(annotationStub);
                    return readAnnotationMembers(annotationStub);
                }

                @Override
                public AnnotationVisitor visitAnnotationDefault() {
                    return new AnnotationReader() {
                        @Override
                        void visitAttribute(final String name, final Object value) {
                            stub.annotationDefault = value;
                        }
                    };
                }

                @Override
                public void visitParameter(final String name, final int access) {
                    if (stub.parameterNames == null) stub.parameterNames = new ArrayList<>();
                    stub.parameterNames.add(name);
                }
            };
        }

        @Override
        public AnnotationVisitor visitAnnotation(final String desc, final boolean visible) {
            return readAnnotationMembers(result.addAnnotation(desc));
        }

        @Override
        public void visitPermittedSubclass(final String permittedSubclass) {
            result.permittedSubclasses.add(permittedSubclass);
        }

        @Override
        public RecordComponentVisitor visitRecordComponent(
                final String name, final String descriptor, final String signature) {

            RecordComponentStub recordComponentStub = new RecordComponentStub(name, descriptor, signature);
            result.recordComponents.add(recordComponentStub);

            return new RecordComponentVisitor(api) {
                @Override
                public AnnotationVisitor visitAnnotation(final String descriptor, final boolean visible) {
                    return readAnnotationMembers(recordComponentStub.addAnnotation(descriptor));
                }

                @Override
                public AnnotationVisitor visitTypeAnnotation(final int typeRef, final TypePath typePath, final String descriptor, final boolean visible) {
                    return readAnnotationMembers(recordComponentStub.addTypeAnnotation(descriptor));
                }
            };
        }

        @Override
        public FieldVisitor visitField(final int access, final String name, final String desc, final String signature, final Object value) {
            FieldStub stub = new FieldStub(name, access, desc, signature, value);
            if (result.fields == null) result.fields = new ArrayList<>(1);
            result.fields.add(stub);
            return new FieldVisitor(api) {
                @Override
                public AnnotationVisitor visitAnnotation(final String desc, final boolean visible) {
                    return readAnnotationMembers(stub.addAnnotation(desc));
                }
            };
        }
    }

    //--------------------------------------------------------------------------

    private abstract static class AnnotationReader extends AnnotationVisitor {

        public AnnotationReader() {
            super(ASM_API_VERSION);
        }

        abstract void visitAttribute(String name, Object value);

        @Override
        public void visit(final String name, final Object value) {
            visitAttribute(name, value instanceof Type ? new TypeWrapper(((Type) value).getDescriptor()) : value);
        }

        @Override
        public void visitEnum(final String name, final String desc, final String value) {
            visitAttribute(name, new EnumConstantWrapper(desc, value));
        }

        @Override
        public AnnotationVisitor visitAnnotation(final String name, final String desc) {
            AnnotationStub stub = new AnnotationStub(desc);
            visitAttribute(name, stub);
            return readAnnotationMembers(stub);
        }

        @Override
        public AnnotationVisitor visitArray(final String name) {
            List<Object> list = new ArrayList<>();
            visitAttribute(name, list);
            return new AnnotationReader() {
                @Override
                void visitAttribute(String name, Object value) {
                    list.add(value);
                }
            };
        }
    }
}
