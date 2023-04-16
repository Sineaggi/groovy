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
import org.glavo.classfile.AccessFlags;
import org.glavo.classfile.AnnotationValue;
import org.glavo.classfile.ClassElement;
import org.glavo.classfile.ClassModel;
import org.glavo.classfile.Classfile;
import org.glavo.classfile.FieldElement;
import org.glavo.classfile.FieldModel;
import org.glavo.classfile.Interfaces;
import org.glavo.classfile.MethodElement;
import org.glavo.classfile.MethodModel;
import org.glavo.classfile.Superclass;
import org.glavo.classfile.attribute.AnnotationDefaultAttribute;
import org.glavo.classfile.attribute.ConstantValueAttribute;
import org.glavo.classfile.attribute.ExceptionsAttribute;
import org.glavo.classfile.attribute.RuntimeVisibleAnnotationsAttribute;
import org.glavo.classfile.attribute.SignatureAttribute;
import org.glavo.classfile.constantpool.ConstantValueEntry;
import org.glavo.classfile.constantpool.DoubleEntry;
import org.glavo.classfile.constantpool.FloatEntry;
import org.glavo.classfile.constantpool.IntegerEntry;
import org.glavo.classfile.constantpool.LongEntry;
import org.glavo.classfile.constantpool.StringEntry;
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
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
                    Files.write(Paths.get("C:\\Users\\Clayton\\Source\\groovy\\ours.txt"), nuParse(cm).toString().getBytes());
                    Files.write(Paths.get("C:\\Users\\Clayton\\Source\\groovy\\theirs.txt"), visitor.result.toString().getBytes());
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
        private SignatureAttribute signatureAttribute;
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
            if (classElement instanceof SignatureAttribute) {
                signatureAttribute = (SignatureAttribute) classElement;
            }
        }
        public ClassStub result() {
            int accessModifiers = accessFlags.flagsMask();
            String signature = null;
            if (signatureAttribute != null) {
                signature = signatureAttribute.signature().stringValue();
            }
            String superName = superclass.superclassEntry().name().stringValue();
            String[] interfaceNames = interfaces.interfaces().stream().map(classEntry -> classEntry.name().stringValue()).toArray(String[]::new);
            ClassStub result = new ClassStub(className, accessModifiers, signature, superName, interfaceNames);
            methodModels.forEach(methodModel -> {
                if (result.methods == null) result.methods = new ArrayList<>();
                String methodName = methodModel.methodName().stringValue();
                if ("<clinit>".equals(methodName)) {
                    return;
                }
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
                //members.put(name, ofArray.values().stream().map(i -> of(i)).collect(Collectors.toList()));
                ofArray.values().forEach(this);
                System.out.println("OFARRAY" + ofArray);
            } else if (annotationValue instanceof AnnotationValue.OfString) {
                AnnotationValue.OfString ofString = (AnnotationValue.OfString) annotationValue;
                members.put(name, ofString.stringValue());
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
        private SignatureAttribute signatureAttribute;
        private RuntimeVisibleAnnotationsAttribute runtimeVisibleAnnotationsAttribute;
        private ExceptionsAttribute exceptionsAttribute;
        private AnnotationDefaultAttribute annotationDefaultAttribute;
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
                exceptionsAttribute = (ExceptionsAttribute) methodElement;
                //throw new RuntimeException("Unsupported method that throws exceptions " + methodElement + " with exceptions " + exceptionsAttribute.exceptions());
            }
            if (methodElement instanceof SignatureAttribute) {
                signatureAttribute = (SignatureAttribute) methodElement;
            }
            if (methodElement instanceof RuntimeVisibleAnnotationsAttribute) {
                if (runtimeVisibleAnnotationsAttribute != null) {
                    throw new IllegalStateException("More than one RuntimeVisibleAnnotationsAttribute");
                }
                runtimeVisibleAnnotationsAttribute = (RuntimeVisibleAnnotationsAttribute) methodElement;
            }
            if (methodElement instanceof AnnotationDefaultAttribute) {
                if (annotationDefaultAttribute != null) {
                    throw new IllegalStateException("More than one AnnotationDefaultAttribute");
                }
                annotationDefaultAttribute = (AnnotationDefaultAttribute) methodElement;
            }
        }

        public MethodStub result() {
            int accessModifiers = accessFlags.flagsMask();
            String signature = null;
            if (signatureAttribute != null) {
                signature = signatureAttribute.signature().stringValue();
            }
            String[] exceptions = new String[]{};
            if (exceptionsAttribute != null) {
                exceptions = exceptionsAttribute.exceptions().stream().map(i -> i.name().stringValue()).toArray(String[]::new);
            }
            MethodStub result = new MethodStub(methodName, accessModifiers, desc, signature, exceptions);
            result.annotationDefault = new ArrayList(1);
            if (annotationDefaultAttribute != null) {
                AnnotationValue defaultValue = annotationDefaultAttribute.defaultValue();
                result.annotationDefault = annotations(defaultValue);
            }
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
            return result;
        }

        private Object annotations(AnnotationValue defaultValue) {
            if (defaultValue instanceof AnnotationValue.OfEnum) {
                //if (true) return null;
                AnnotationValue.OfEnum ofEnum = ((AnnotationValue.OfEnum) defaultValue);
                return new EnumConstantWrapper(ofEnum.className().stringValue(), ofEnum.constantName().stringValue());
                //return annotationDefault;
                //result.annotationDefault = annotationDefault;
            } else if (defaultValue instanceof AnnotationValue.OfBoolean) {
                AnnotationValue.OfBoolean ofBoolean = ((AnnotationValue.OfBoolean) defaultValue);
                return ofBoolean.booleanValue();
            } else if (defaultValue instanceof AnnotationValue.OfArray) {
                //ArrayList annotationDefault = new ArrayList(1);
                return ((AnnotationValue.OfArray) defaultValue).values().stream().map(i -> annotations(i)).collect(Collectors.toList());
                //annotationDefault.set(0, ((AnnotationValue.OfArray) defaultValue).values().stringValue());
                //result.annotationDefault = annotationDefault;
            } else {
                throw new RuntimeException("tood " + defaultValue.getClass());
            }
        }
    }

    private static class FieldElementConsumer implements Consumer<FieldElement> {

        private final String fieldName;
        private final String desc;
        private AccessFlags accessFlags;
        private SignatureAttribute signatureAttribute;
        private Object value;
        private RuntimeVisibleAnnotationsAttribute runtimeVisibleAnnotationsAttribute;

        private FieldElementConsumer(String fieldName, String desc) {
            this.fieldName = fieldName;
            this.desc = desc;
        }

        @Override
        public void accept(FieldElement fieldElement) {
            if (fieldElement instanceof AccessFlags) {
                accessFlags = (AccessFlags) fieldElement;
            }
            if (fieldElement instanceof SignatureAttribute) {
                signatureAttribute = (SignatureAttribute) fieldElement;
            }
            if (fieldElement instanceof ConstantValueAttribute) {
                ConstantValueAttribute constantValueAttribute = (ConstantValueAttribute) fieldElement;
                ConstantValueEntry constantValueEntry = constantValueAttribute.constant();
                if (constantValueEntry instanceof DoubleEntry) {
                    value = ((DoubleEntry) constantValueEntry).doubleValue();
                } else if (constantValueEntry instanceof FloatEntry) {
                    value = ((FloatEntry) constantValueEntry).floatValue();
                } else if (constantValueEntry instanceof IntegerEntry) {
                    value = ((IntegerEntry) constantValueEntry).intValue();
                } else if (constantValueEntry instanceof LongEntry) {
                    value = ((LongEntry) constantValueEntry).longValue();
                } else if (constantValueEntry instanceof StringEntry) {
                    value = ((StringEntry) constantValueEntry).stringValue();
                } else {
                    throw new RuntimeException("Encountered unsupported constant value of type " + constantValueEntry.getClass());
                }
            }
            if (fieldElement instanceof RuntimeVisibleAnnotationsAttribute) {
                runtimeVisibleAnnotationsAttribute = (RuntimeVisibleAnnotationsAttribute) fieldElement;
            }
            System.out.println("field elem " + fieldElement);
        }

        public FieldStub result() {
            int accessModifiers = accessFlags.flagsMask();
            if ("DYNAMIC_TYPE".equals(fieldName)) {
                accessFlags.flagsMask();
                AccessFlags.ofField();
                //throw new RuntimeException("onoz " + accessModifiers + "  ugh " + Integer.toBinaryString(accessFlags.flagsMask()));
                //throw new RuntimeException("onoz " + accessModifiers + "  ugh " + accessFlags.flags());
            }
            String signature = null;
            if (signatureAttribute != null) {
                signature = signatureAttribute.signature().stringValue();
            }
            FieldStub result = new FieldStub(fieldName, accessModifiers, desc, signature, value);
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
            return result;
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

            MethodStub stub = new MethodStub(name, access & 0b011111111111111111, desc, signature, exceptions != null ? exceptions : EMPTY_STRING_ARRAY);
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
            if ("DYNAMIC_TYPE".equals(name)) {

                //throw new RuntimeException("onoz bro" + name + " has flags " + access + " with bit pattern " + Integer.toBinaryString(access));
            }
            FieldStub stub = new FieldStub(name, access & 0b011111111111111111, desc, signature, value);
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
            //if (value instanceof Boolean) throw new RuntimeException("oh we got visited " + "wit name " + name + " with val " + value);
            //if (value != null) throw new RuntimeException("oh we got visited " + "wit name " + name + " with val " + value);
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
