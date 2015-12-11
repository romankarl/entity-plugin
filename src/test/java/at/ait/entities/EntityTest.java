package at.ait.entities;

import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Transaction;
import org.neo4j.harness.junit.Neo4jRule;
import org.neo4j.test.server.HTTP;

import static org.junit.Assert.*;

import java.net.URI;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

public class EntityTest {

	@Rule
	public Neo4jRule neo4j = new Neo4jRule()
			.withFixture(CYPHER_STATEMENT)
			.withExtension("/Entity", Entity.class);
	
	private static final String CYPHER_STATEMENT = new StringBuilder()
			.append("CREATE (a1:Address {address: \"111\"}) ")
			.append("CREATE (a2:Address {address: \"112\"}) ")
			.append("CREATE (a3:Address {address: \"113\"}) ")
			.append("CREATE (o1:Output)-[:USES]->(a1) ")
			.append("CREATE (o2:Output)-[:USES]->(a2) ")
			.append("CREATE (o3:Output)-[:USES]->(a3) ")
			.append("CREATE (o1)-[:INPUT]->(:Transaction)-[:OUTPUT]->(o2) ")
			.append("CREATE (o2)-[:INPUT]->(:Transaction)-[:OUTPUT]->(o3) ")
			.append("CREATE a1-[:BELONGS_TO]->(e:Entity)")
			.toString();
	
	@Path("")
	public static class UnmanagedEntityWrapper {
		@GET
		public Response createEntity(@Context final GraphDatabaseService graphDb) {
			Entity entityPlugin = new Entity();
			String result;
			try (Transaction tx = graphDb.beginTx()) {
				//entityPlugin.createEntity(graphDb.findNodes(TGLabel.Transaction).next());
				Node source = graphDb.findNode(TGLabel.Address, "address", "111");
				Node target = graphDb.findNode(TGLabel.Address, "address", "113");
				result = entityPlugin.findPathWithBidirectionalStrategy(source, target).toString();
				tx.success();
			}
			return Response.ok(result).build();
		}
	}
	
	//@Ignore
	@Test
	public void testMyExtension() throws Exception {

		URI serverURI = neo4j.httpURI();

		HTTP.Response response = HTTP.GET(serverURI.resolve("Entity").toString());
		
		System.out.println(response.rawContent());
		
		assertEquals(200, response.status());
	}
}
