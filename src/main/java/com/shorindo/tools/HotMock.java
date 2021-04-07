package com.shorindo.tools;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.lang.reflect.Modifier;

import org.mozilla.javascript.Context;
import org.mozilla.javascript.ContextFactory;
import org.mozilla.javascript.NativeJavaObject;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;
import org.mozilla.javascript.tools.shell.Global;

import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtMethod;
import javassist.CtNewMethod;

public class HotMock {
	public static void mock(String className, MockMethod...mockMethods) {
		try {
			ClassPool cp = ClassPool.getDefault();
			CtClass cc = cp.get(className);

			for (MockMethod mockMethod : mockMethods) {
				for (CtMethod origMethod : cc.getDeclaredMethods(mockMethod.getMethodName())) {
					// 元のメソッドを別名でコピーする
					String origName = origMethod.getName();
					String newName = "_mock$" + origName;
					CtMethod newMethod = CtNewMethod.copy(origMethod, newName, cc, null);
					newMethod.setModifiers(Modifier.PRIVATE);
					cc.addMethod(newMethod);

					// メソッドをモック化する
					// スクリプトが存在しない場合はコピーした元メソッドを呼ぶ
					String path = mockMethod.getScript().getAbsolutePath().replaceAll("\\\\", "/");
					String evalName = HotMock.class.getName() + ".eval";
					String returnType = newMethod.getReturnType().getName();
					String returnStatement = ("void".equals(returnType) ? "" : "return");
					String body = new StringBuffer("{")
							.append("  java.io.File file = new java.io.File(\"" + path + "\");")
							.append("  if (file.exists()) {")
							.append("      java.lang.System.out.println(\"using '" + mockMethod.getScript().getName() + "'.\");")
							.append("    " + returnStatement + " (" + returnType + ")" + evalName + "(\"" + path + "\", $args);")
							.append("  } else {")
							.append("      java.lang.System.out.println(\"using '" + origName + "' because '" + mockMethod.getScript().getName() + "' not found.\");")
							.append("    " + returnStatement + " " + newName + "($$);")
							.append("  }")
							.append("}")
							.toString();
					origMethod.setBody(body);
				}
			}
			cc.toClass();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public static Object eval(String path, Object...args) {
		Object result = null;
		System.out.println("path=" + path);
		File file = new File(path);
		StringBuffer source = new StringBuffer();
		int len = 0;
		char[] buff = new char[2048];
		try (Reader reader = new InputStreamReader(new FileInputStream(file), "UTF-8")) {
			while ((len = reader.read(buff)) > 0) {
				source.append(buff, 0, len);
			}
			Context ctx = ContextFactory.getGlobal().enterContext();
			Global global = new Global();
			global.init(ctx);
			global.defineProperty("$ARGS", args, ScriptableObject.READONLY);
			Scriptable scope = ctx.initSafeStandardObjects(global);
			result = ctx.evaluateString(scope, source.toString(), file.getName(), 1, null);
			if (result instanceof NativeJavaObject) {
				result = ((NativeJavaObject)result).unwrap();
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return result;
	}

	public static class MockMethod {
		private String methodName;
		private File script;

		public MockMethod(String methodName, File script) {
			this.methodName = methodName;
			this.script = script;
		}
		public String getMethodName() {
			return methodName;
		}
		public File getScript() {
			return script;
		}
	}
}
