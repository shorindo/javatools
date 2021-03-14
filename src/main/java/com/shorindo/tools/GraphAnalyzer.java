package com.shorindo.tools;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.Stack;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.neo4j.driver.Record;
import org.neo4j.driver.Result;
import org.neo4j.driver.Session;
import org.neo4j.driver.types.Entity;
import org.neo4j.driver.types.Relationship;

public class GraphAnalyzer {
    private Driver driver = null;
    private Session session;
    private Set<Relationship> pathSet;
    private Printer printer;

    public static void main(String[] args) {
        GraphAnalyzer analyzer = new GraphAnalyzer(args);
        try {
            TreeNode start = analyzer.makeTree("ActionServlet#doGet");
            //TreeNode start = analyzer.makeTree("ActionServlet#doPost");
            analyzer.printTree(start);
        } finally {
            analyzer.close();
        }
    }

    public GraphAnalyzer(String[] args) throws IllegalArgumentException {
        Iterator<String> it = Arrays.asList(args).iterator();
        try {
            while (it.hasNext()) {
                String arg = it.next();
                switch (arg) {
                case "-server":
                    String server = it.next();
                    Pattern p = Pattern.compile("^(bolt://)([^:]+):([^@]+)@(.*)$");
                    Matcher m = p.matcher(server);
                    if (m.matches()) {
                        String url = m.group(1) + m.group(4);
                        String user = m.group(2);
                        String pass = m.group(3);
                        driver = GraphDatabase.driver(url, AuthTokens.basic(user, pass));
                        session = driver.session();
                        pathSet = new HashSet<>();
                    } else {
                        throw new IllegalArgumentException("invalid:" + server);
                    }
                    break;
                case "-output":
                    String output = it.next();
                    printer = output.endsWith(".html") ?
                            new HtmlPrinter(output) : new TextPrinter(output);
                    break;
                default:
                    //ERROR
                }
            }
        } catch (Exception e) {
            //throw new IllegalArgumentException(e);
            usage();
        }
    }

    private static void usage() {
        System.out.println("java " + GraphAnalyzer.class.getSimpleName()
                + "-server bold://<user>:<pass>@<host>:<port> "
                + "-output <file(.html)>");
    }

    public void close() {
        if (printer != null) printer.close();
        if (driver != null) driver.close();
    }

    private TreeNode makeTree(String startName) {
        TreeNode start = null;
        Map<String,Object> params = new HashMap<>();
        params.put("startName", startName);
        Result result = session.run("MATCH p = (m)-[*1..10]->(n) WHERE m.shortName=$startName RETURN m,n,relationships(p) AS r", params);
        List<Record> resultList = result.list();

        // 開始点を取得する
        Map<Long,TreeNode> methodMap = new HashMap<>();
        if (resultList.size() > 0) {
            Entity m = resultList.get(0).get("m").asEntity();
            start = new TreeNode(m);
            methodMap.put(m.id(), start);
        } else {
            System.err.println("NOT FOUND:" + startName);
            return start;
        }

        // 全ノードを取得する
        for (Record record : resultList) {
            Entity n = record.get("n").asEntity();
            TreeNode method = new TreeNode(n);
            methodMap.put(n.id(), method);
        }

        // リレーションを取得する
        for (Record record : resultList) {
            record.get("r")
                .asList(e -> e.asRelationship())
                .stream()
                .sorted((r1, r2) -> {
                    Long o1 = (long)r1.asMap().get("order");
                    Long o2 = (long)r2.asMap().get("order");
                    return o1.compareTo(o2);
                })
                .forEach(r -> {
                    if (!pathSet.contains(r)) {
                        TreeNode caller = methodMap.get(r.startNodeId());
                        TreeNode callee = methodMap.get(r.endNodeId());
                        caller.getCalleeList().add(new Call(r.type(), callee));
                        pathSet.add(r);
                    }
                });
        }

        return start;
    }

    public void printTree(TreeNode start) {
        String name = Objects.toString(start.getMethod().asMap().get("shortName"));
        Stack<TreeNode> stack = new Stack<>();
        stack.add(start);
        printer.start(0, name);
        printTree(stack, start);
        printer.end();
    }

    private Set<TreeNode> callSet = new HashSet<>();
    public void printTree(Stack<TreeNode> stack, TreeNode start) {
        start.getMethod().asMap().get("shortName");
        for (Call call : start.getCalleeList()) {
            String prefix = "";
            if ("IMPLEMENT".equals(call.getType())) {
                prefix = "<I>";
            } else if ("INHERIT".equals(call.getType())) {
                prefix = "<E>";
            }

            if (callSet.contains(call.getNode())) {
                prefix = "<*>";
                String name = prefix + call.getNode().getMethod().asMap().get("shortName");
                printer.start(stack.size(), name);
                printer.end();
            } else {
                String name = prefix + call.getNode().getMethod().asMap().get("shortName");
                printer.start(stack.size(), name);
                callSet.add(call.getNode());
                if (call.getNode().getCalleeList().size() > 0) {
                    stack.push(call.getNode());
                    printTree(stack, call.getNode());
                    stack.pop();
                }
                printer.end();
            }
        }
    }

    private String indent(int level) {
        StringBuffer sb = new StringBuffer();
        for (int i = 0; i < level; i++)
            sb.append("  ");
        return sb.toString();
    }

    public static class TreeNode {
        private Entity method;
        private List<Call> calleeList;

        public TreeNode(Entity method) {
            this.method = method;
            this.calleeList = new ArrayList<>();
        }
        public Entity getMethod() {
            return method;
        }
        public List<Call> getCalleeList() {
            return calleeList;
        }
    }

    public static class Call {
        private String type;
        private TreeNode node;

        public Call(String type, TreeNode callee) {
            this.type = type;
            this.node = callee;
        }
        public String getType() {
            return type;
        }
        public TreeNode getNode() {
            return node;
        }
    }

    public static class Method {
        private String key;
        private int modifier;
        private String shortName;
        private String longName;
        private String className;
        private String methodName;
        private List<Method> calleeList;

        public Method(Map<String,Object> map) {
            key = Objects.toString(map.get("key"));
            modifier = Integer.parseInt(Objects.toString(map.get("modifier")));
            shortName = Objects.toString(map.get("shortName"));
            longName = Objects.toString(map.get("longName"));
            className = Objects.toString(map.get("className"));
            methodName = Objects.toString(map.get("methodName"));
            calleeList = new ArrayList<>();
        }
        public String getKey() {
            return key;
        }
        public int getModfier() {
            return modifier;
        }
        public String getShortName() {
            return shortName;
        }
        public String getLongName() {
            return longName;
        }
        public String getClassName() {
            return className;
        }
        public String getMethodName() {
            return methodName;
        }
        public List<Method> getCalleeList() {
            return calleeList;
        }
        public String toString() {
            return "[" + key + "]=" + shortName;
        }
    }

    public static class Relation {
        private int start;
        private int end;
        private String type;
        private int order;

        public Relation(Relationship r) {
            r.get("properties").asMap().get("order");
        }
        public int getStart() {
            return start;
        }
        public int getEnd() {
            return end;
        }
        public String getType() {
            return type;
        }
        public int getOrder() {
            return order;
        }
    }

    public enum PrintMode {
        TEXT, HTML;
    }

    public interface Printer {
        public void close();
        public void start(int level, String text);
        public void end();
    }

    public class TextPrinter implements Printer {
        PrintWriter pw;

        public TextPrinter(String fileName) throws IOException {
            pw = new PrintWriter(new OutputStreamWriter(new FileOutputStream(fileName), "UTF-8"));
        }

        @Override
        public void close() {
            pw.close();
        }

        @Override
        public void start(int level, String text) {
            pw.println(indent(level) + text);
        }

        @Override
        public void end() {
        }
    }

    public class HtmlPrinter implements Printer {
        private PrintWriter pw;

        public HtmlPrinter(String fileName) throws IOException {
            pw = new PrintWriter(new OutputStreamWriter(new FileOutputStream(fileName), "UTF-8"));
            pw.println(new StringBuilder()
                    .append("<!doctype html>")
                    .append("<html>")
                    .append("<head>")
                    .append("<meta http-equiv=\"Content-Type\" content=\"text/html;charset=UTF-8\">")
                    .append("<title>CallTree</title>")
                    .append("<style type=\"text/css\">")
                    .append("body { font-family:monospace; }")
                    .append(".child { margin-left:20px; }")
                    .append("div.open > span.icon::before { content:\"- \" }")
                    .append("div.open ~ div.child { display:block; }")
                    .append("div.close > span.icon::before { content:\"+ \" }")
                    .append("div.close ~ div.child { display:none; }")
                    .append("</style>")
                    .append("<script type=\"text/javascript\">\n")
                    .append("<!--\n")
                    .append("function toggle(target) {\n")
                    .append("console.log(target);\n")
                    .append("var parent = target.parentNode;\n")
                    .append("if (parent.className == 'open')\n")
                    .append("parent.className = 'close';\n")
                    .append("else\n")
                    .append("parent.className = 'open';\n")
                    .append("}\n")
                    .append("-->")
                    .append("</script>")
                    .append("</head>")
                    .append("<body>")
                    .toString());
        }

        public void close() {
            pw.print(new StringBuilder()
                    .append("</body>")
                    .append("</html>")
                    .toString());
            pw.close();
        }

        @Override
        public void start(int level, String text) {
            pw.println(new StringBuilder()
                    .append("<div>")
                    .append("<div class=\"open\">")
                    .append("<span class=\"icon\" onclick=\"javascript:toggle(this);\"></span>")
                    .append(escape(text))
                    .append("</div>")
                    .append("<div class=\"child\">")
                    .toString());
        }

        @Override
        public void end() {
            pw.println("</div>");
            pw.println("</div>");
        }

        private String escape(String text) {
            return text
                    .replaceAll("&", "&amp;")
                    .replaceAll("<", "&lt;")
                    .replaceAll(">", "&gt;")
                    .replaceAll("\"", "&quot;");
        }
    }
}
