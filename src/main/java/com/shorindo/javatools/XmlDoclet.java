package com.shorindo.javatools;

import java.util.List;

import com.sun.javadoc.ClassDoc;
import com.sun.javadoc.PackageDoc;
import com.sun.javadoc.RootDoc;

/**
 * XML形式のjavadocを生成する
 */
public class XmlDoclet {
    public static boolean start(RootDoc rootDoc) {
        XmlDoclet ascDoclet = new XmlDoclet();
        return ascDoclet.parse(rootDoc);
    }

    public boolean parse(RootDoc rootDoc) {
        for (PackageDoc doc : rootDoc.specifiedPackages()) {
            addPackageNode(new PackageNode(doc));
        }
        return true;
    }

    protected void addPackageNode(PackageNode node) {
    }

    public static class RootNode {
        private List<PackageNode> packageNode;
    }

    class PackageNode {
        protected PackageNode(PackageDoc packageDoc) {
            packageDoc.allClasses();
            packageDoc.annotations();
            packageDoc.annotationTypes();
            packageDoc.commentText();
            packageDoc.errors();
            packageDoc.exceptions();
            packageDoc.inlineTags();
            packageDoc.interfaces();
            packageDoc.name();
            packageDoc.ordinaryClasses();
            packageDoc.seeTags();
            packageDoc.tags();
        }
        protected void addClassNode(ClassNode classNode) {
        }
    }
    
    class ClassNode {
        protected ClassNode(ClassDoc classDoc) {
            classDoc.commentText();
            classDoc.constructors();
            classDoc.enumConstants();
            classDoc.fields();
            classDoc.inlineTags();
            classDoc.innerClasses();
            classDoc.interfaces();
            classDoc.interfaceTypes();
            classDoc.methods();
            classDoc.seeTags();
            classDoc.serializableFields();
            classDoc.serializationMethods();
            classDoc.tags();
            classDoc.typeParameters();
            classDoc.typeParamTags();
        }
    }
}
