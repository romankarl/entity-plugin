package at.ait.entities;

import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Transaction;
import org.neo4j.harness.junit.Neo4jRule;
import org.neo4j.test.server.HTTP;

import static org.junit.Assert.*;

import java.net.URI;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;

public class EntityTest {

	@Rule
	public Neo4jRule neo4j = new Neo4jRule()
			.withFixture(CYPHER_STATEMENT)
			.withExtension("/Entity", Entity.class);
	
	private static final String CYPHER_STATEMENT = new StringBuilder()
			.append("CREATE (a1:Address)-[:INPUT]->(t:Transaction {txid: \"abc\", processed: false}) ")
			.append("CREATE (a2:Address)-[:INPUT]->t ")
			.append("CREATE a1-[:BELONGS_TO]->(e:Entity)")
			.toString();
	
	@Path("")
	public static class UnmanagedEntityWrapper {
		@GET
		public Response createEntity(@Context final GraphDatabaseService graphDb) {
			Entity entityPlugin = new Entity();
			try (Transaction tx = graphDb.beginTx()) {
				entityPlugin.createEntity(graphDb.findNodes(TGLabel.Transaction).next());
				tx.success();
			}
			return Response.ok().build();
		}
	}
	
	@Ignore
	@Test
	public void testMyExtension() throws Exception {

		URI serverURI = neo4j.httpURI();

		HTTP.Response response = HTTP.GET(serverURI.resolve("Entity").toString());

		assertEquals(200, response.status());
	}
}
