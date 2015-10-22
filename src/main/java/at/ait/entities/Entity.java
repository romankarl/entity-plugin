package at.ait.entities;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.Transaction;
import org.neo4j.server.plugins.Description;
import org.neo4j.server.plugins.PluginTarget;
import org.neo4j.server.plugins.ServerPlugin;
import org.neo4j.server.plugins.Source;

public class Entity extends ServerPlugin {
	
	private static Logger logger = Logger.getLogger(Entity.class.getName());
	
	@Description("Create entities for all transactions of a new block.")
    @PluginTarget(Node.class)
    public String createEntities(@Source Node block) {
		GraphDatabaseService graphDb = block.getGraphDatabase();
		String height;
		try (Transaction tx = graphDb.beginTx()) {
			height = block.getProperty("height").toString();
			logger.fine("create entities for block with height " + height);
			for (Relationship contains : block.getRelationships(TGRelationshipType.CONTAINS))
				createEntity(contains.getEndNode());
		}
		
		return "block with height " + height + " has been processed";
	}
	
	@Description("Create an entity for a new transaction.")
    @PluginTarget(Node.class)
    public Node createEntity(@Source Node transaction) {
		GraphDatabaseService graphDb = transaction.getGraphDatabase();
		
		Node entity = null;
		try (Transaction tx = graphDb.beginTx()) {
			logger.fine("create entity for transaction " + transaction.getProperty("txid"));
			Set<Node> inputAddresses = new HashSet<>();
			for (Relationship input : transaction.getRelationships(TGRelationshipType.INPUT)) {
				Node output = input.getStartNode();
				for (Relationship uses : output.getRelationships(TGRelationshipType.USES))
					inputAddresses.add(uses.getEndNode());
			}
			if (inputAddresses.size() >= 2) {
				Set<Node> entities = collectEntities(inputAddresses);
				Iterable<Node> addresses;
				if (entities.isEmpty()) {
					addresses = inputAddresses;
					entity = graphDb.createNode(TGLabel.Entity);
				} else if (entities.size() == 1) {
					addresses = inputAddresses;
					entity = entities.iterator().next();
				} else {
					addresses = collectAddressesAndDeleteEntities(entities);
					entity = graphDb.createNode(TGLabel.Entity);
				}
				associateWith(addresses, entity);
			}
			tx.success();
		}
		return entity;
	}

	private Set<Node> collectEntities(Iterable<Node> addresses) {
		logger.fine("collect entities");
		Set<Node> entities = new HashSet<>();
		for (Node address : addresses) {
			Relationship belongsTo = address.getSingleRelationship(TGRelationshipType.BELONGS_TO, Direction.OUTGOING);
			if (belongsTo != null) {
				entities.add(belongsTo.getEndNode());
			}
		}
		return entities;
	}
	
	private List<Node> collectAddressesAndDeleteEntities(Iterable<Node> entities) {
		logger.fine("collect addresses and delete obsolete entities");
		List<Node> addresses = new ArrayList<>();
		for (Node entity : entities) {
			for (Relationship belongsTo : entity.getRelationships()) {
				addresses.add(belongsTo.getStartNode());
				belongsTo.delete();
			}
			entity.delete();
		}
		return addresses;
	}
	
	private void associateWith(Iterable<Node> addresses, Node entity) {
		logger.fine("associate addresses with new entity");
		for (Node address : addresses)
			if (!address.hasRelationship(TGRelationshipType.BELONGS_TO))
				address.createRelationshipTo(entity, TGRelationshipType.BELONGS_TO);
	}
}
