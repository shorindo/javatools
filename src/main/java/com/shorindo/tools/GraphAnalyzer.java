package com.shorindo.tools;

import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Stack;

import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.neo4j.driver.Record;
import org.neo4j.driver.Result;
import org.neo4j.driver.Session;
import org.neo4j.driver.Transaction;
import org.neo4j.driver.Value;
import org.neo4j.driver.types.Entity;

public class GraphAnalyzer {
    private static Driver driver = null;
    private static Session session;
    private static Transaction tx;

    public static void main(String[] args) {

        try {
            String url = args[0];
            String user = args[1];
            String pass = args[2];
            driver = GraphDatabase.driver(url, AuthTokens.basic(user, pass));
            session = driver.session();
            tx = session.beginTransaction();
            //Result result = tx.run("MATCH (n) WHERE n.shortName='MarkdownController#action' RETURN n");
            Result result = tx.run("MATCH (n) WHERE n.shortName='ActionServlet#doGet' RETURN n");
            Record record = result.list().get(0);
            Map<String,Object> map = record.get("n").asMap();
            System.out.println(map);
            Stack<String> callStack = new Stack<>();
            //callStack.add(Objects.toString(map.get("key")));
            walkTree(callStack, Objects.toString(map.get("key")));
        } finally {
            driver.close();
        }
    }

    private static void walkTree(Stack<String> callStack, String callerKey) {
        Map<String,Object> params = new HashMap<>();
        params.put("key", callerKey);
        Result result = tx.run("MATCH (m)-[r]->(n) WHERE m.key=$key RETURN m,n ORDER BY r.order", params);
        for (Record record : result.list()) {
            Map<String,Object> map = record.get("n").asMap();
            String mod = Modifier.isAbstract(Integer.parseInt((String)map.get("modifier"))) ?
                    "<A>" : "";
            System.out.println(indent(callStack.size() + 1) 
                    + mod
                    + map.get("shortName"));
            String that = Objects.toString(map.get("key"));
            //String calleeName = Objects.toString(map.get("longName"));
            if (!callStack.contains(callerKey)
                    && !callerKey.equals(that) 
                    && !"ActionMessages#getCode".equals(map.get("shortName"))
                    && !"ActionMessages#getMessage".equals(map.get("shortName"))) {
                callStack.add(callerKey);
                walkTree(callStack, that);
                callStack.pop();
            }
        }
    }

    private static String indent(int level) {
        StringBuffer sb = new StringBuffer();
        for (int i = 0; i < level; i++)
            sb.append("  ");
        return sb.toString();
    }
}
