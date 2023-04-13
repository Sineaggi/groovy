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

import java.util.*;

/**
 * Data structures holding class info to enable lazy loading
 */
public class ClassStub extends MemberStub {
    final String className;
    final int accessModifiers;
    final String signature;
    final String superName;
    final String[] interfaceNames;
    List<MethodStub> methods;
    List<FieldStub> fields;
    final List<String> permittedSubclasses = new ArrayList<>(1);
    final List<RecordComponentStub> recordComponents  = new ArrayList<>(1);

    // Used to store the real access modifiers for inner classes
    int innerClassModifiers = -1;

    public ClassStub(String className, int accessModifiers, String signature, String superName, String[] interfaceNames) {
        this.className = className;
        this.accessModifiers = accessModifiers;
        this.signature = signature;
        this.superName = superName;
        this.interfaceNames = interfaceNames;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ClassStub classStub = (ClassStub) o;
        return accessModifiers == classStub.accessModifiers && innerClassModifiers == classStub.innerClassModifiers && Objects.equals(className, classStub.className) && Objects.equals(signature, classStub.signature) && Objects.equals(superName, classStub.superName) && Arrays.equals(interfaceNames, classStub.interfaceNames) && Objects.equals(methods, classStub.methods) && Objects.equals(fields, classStub.fields) && Objects.equals(permittedSubclasses, classStub.permittedSubclasses) && Objects.equals(recordComponents, classStub.recordComponents);
    }

    @Override
    public String toString() {
        return "ClassStub{" +
               "className='" + className + '\'' +
               ", accessModifiers=" + accessModifiers +
               ", signature='" + signature + '\'' +
               ", superName='" + superName + '\'' +
               ", interfaceNames=" + Arrays.toString(interfaceNames) +
               ", methods=" + methods +
               ", fields=" + fields +
               ", permittedSubclasses=" + permittedSubclasses +
               ", recordComponents=" + recordComponents +
               ", innerClassModifiers=" + innerClassModifiers +
               ", annotations=" + annotations +
               '}';
    }
}

interface AnnotatedStub {
    List<AnnotationStub> getAnnotations();
}

interface AnnotatedTypeStub {
    List<TypeAnnotationStub> getTypeAnnotations();
}

class MemberStub implements AnnotatedStub {
    List<AnnotationStub> annotations = null;

    AnnotationStub addAnnotation(String desc) {
        AnnotationStub stub = new AnnotationStub(desc);
        if (annotations == null) annotations = new ArrayList<AnnotationStub>(1);
        annotations.add(stub);
        return stub;
    }

    @Override
    public List<AnnotationStub> getAnnotations() {
        return annotations;
    }
}

class MethodStub extends MemberStub {
    final String methodName;
    final int accessModifiers;
    final String desc;
    final String signature;
    final String[] exceptions;
    Map<Integer, List<AnnotationStub>> parameterAnnotations;
    List<String> parameterNames;
    Object annotationDefault;

    public MethodStub(String methodName, int accessModifiers, String desc, String signature, String[] exceptions) {
        this.methodName = methodName;
        this.accessModifiers = accessModifiers;
        this.desc = desc;
        this.signature = signature;
        this.exceptions = exceptions;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MethodStub that = (MethodStub) o;
        return accessModifiers == that.accessModifiers && Objects.equals(methodName, that.methodName) && Objects.equals(desc, that.desc) && Objects.equals(signature, that.signature) && Arrays.equals(exceptions, that.exceptions) && Objects.equals(parameterAnnotations, that.parameterAnnotations) && Objects.equals(parameterNames, that.parameterNames) && Objects.equals(annotationDefault, that.annotationDefault);
    }

    @Override
    public String toString() {
        return "MethodStub{" +
               "methodName='" + methodName + '\'' +
               ", accessModifiers=" + accessModifiers +
               ", desc='" + desc + '\'' +
               ", signature='" + signature + '\'' +
               ", exceptions=" + Arrays.toString(exceptions) +
               ", parameterAnnotations=" + parameterAnnotations +
               ", parameterNames=" + parameterNames +
               ", annotationDefault=" + annotationDefault +
               ", annotations=" + annotations +
               '}';
    }
}

class FieldStub extends MemberStub {
    final String fieldName;
    final int accessModifiers;
    final String desc;
    final String signature;
    final Object value;

    public FieldStub(String fieldName, int accessModifiers, String desc, String signature) {
        this(fieldName, accessModifiers, desc, signature, null);
    }

    public FieldStub(String fieldName, int accessModifiers, String desc, String signature, Object value) {
        this.fieldName = fieldName;
        this.accessModifiers = accessModifiers;
        this.desc = desc;
        this.signature = signature;
        this.value = value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FieldStub fieldStub = (FieldStub) o;
        return accessModifiers == fieldStub.accessModifiers && Objects.equals(fieldName, fieldStub.fieldName) && Objects.equals(desc, fieldStub.desc) && Objects.equals(signature, fieldStub.signature) && Objects.equals(value, fieldStub.value);
    }

    @Override
    public String toString() {
        return "FieldStub{" +
               "fieldName='" + fieldName + '\'' +
               ", accessModifiers=" + accessModifiers +
               ", desc='" + desc + '\'' +
               ", signature='" + signature + '\'' +
               ", value=" + value +
               ", annotations=" + annotations +
               '}';
    }
}

class AnnotationStub {
    final String className;
    final Map<String, Object> members = new LinkedHashMap<>();

    public AnnotationStub(String className) {
        this.className = className;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AnnotationStub that = (AnnotationStub) o;
        return Objects.equals(className, that.className) && Objects.equals(members, that.members);
    }

    @Override
    public String toString() {
        return "AnnotationStub{" +
               "className='" + className + '\'' +
               ", members=" + members +
               '}';
    }
}

class TypeAnnotationStub extends AnnotationStub {
    public TypeAnnotationStub(String className) {
        super(className);
    }
}

class TypeWrapper {
    final String desc;

    public TypeWrapper(String desc) {
        this.desc = desc;
    }
}

class EnumConstantWrapper {
    final String enumDesc;
    final String constant;

    public EnumConstantWrapper(String enumDesc, String constant) {
        this.enumDesc = enumDesc;
        this.constant = constant;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        EnumConstantWrapper that = (EnumConstantWrapper) o;
        return Objects.equals(enumDesc, that.enumDesc) && Objects.equals(constant, that.constant);
    }

    @Override
    public String toString() {
        return "EnumConstantWrapper{" +
               "enumDesc='" + enumDesc + '\'' +
               ", constant='" + constant + '\'' +
               '}';
    }
}

class RecordComponentStub implements AnnotatedStub, AnnotatedTypeStub {
    final String name;
    final String descriptor;
    final String signature;
    List<AnnotationStub> annotations;
    List<TypeAnnotationStub> typeAnnotations;

    public RecordComponentStub(String name, String descriptor, String signature) {
        this.name = name;
        this.descriptor = descriptor;
        this.signature = signature;
    }

    AnnotationStub addAnnotation(String desc) {
        AnnotationStub stub = new AnnotationStub(desc);
        if (annotations == null) annotations = new ArrayList<AnnotationStub>(1);
        annotations.add(stub);
        return stub;
    }

    @Override
    public List<AnnotationStub> getAnnotations() {
        return annotations;
    }

    public TypeAnnotationStub addTypeAnnotation(String desc) {
        TypeAnnotationStub stub = new TypeAnnotationStub(desc);
        if (typeAnnotations == null) typeAnnotations = new ArrayList<TypeAnnotationStub>(1);
        typeAnnotations.add(stub);
        return stub;
    }

    @Override
    public List<TypeAnnotationStub> getTypeAnnotations() {
        return typeAnnotations;
    }
}
