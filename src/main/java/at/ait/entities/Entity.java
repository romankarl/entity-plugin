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
import org.neo4j.graphdb.ResourceIterator;
import org.neo4j.graphdb.Transaction;
import org.neo4j.server.plugins.Description;
import org.neo4j.server.plugins.PluginTarget;
import org.neo4j.server.plugins.ServerPlugin;
import org.neo4j.server.plugins.Source;

public class Entity extends ServerPlugin {
	
	private static Logger logger = Logger.getLogger(Entity.class.getName());
	private static final int BLOCKS_PER_TRANSACTION = 100;
	private static final String BLOCK_KEY = "block";
	
	@Description("Calculate all entity nodes.")
    @PluginTarget(GraphDatabaseService.class)
    public String createAllEntities(@Source GraphDatabaseService graphDb) {
		int blockNumber = 0;
		boolean nodesLeft = true;
		while (nodesLeft) {
			try (Transaction tx = graphDb.beginTx()) {
				for (int i=0; i < BLOCKS_PER_TRANSACTION && nodesLeft; i++) {
					ResourceIterator<Node> transactionIterator = graphDb.findNodes(TGLabel.Transaction, BLOCK_KEY, ++blockNumber);
					if (transactionIterator.hasNext()) {
						while (transactionIterator.hasNext()) {
							Node transaction = transactionIterator.next();
							createEntity(transaction);
						}
					} else {
						nodesLeft = false;
					}
				}
				tx.success();
			}
			if (blockNumber % (10 * BLOCKS_PER_TRANSACTION) == 0)
				logger.info("processed blocks: " + blockNumber);
		}
		return "processed blocks: " + blockNumber;
	}
	
	@Description("Create an entity for a new transaction node.")
    @PluginTarget(Node.class)
    public Node createEntity(@Source Node transaction) {
		logger.fine("create entity for node " + transaction.getProperty("txid"));
		GraphDatabaseService graphDb = transaction.getGraphDatabase();
		
		Node entity = null;
		try (Transaction tx = graphDb.beginTx()) {
			Set<Node> inputAddresses = new HashSet<>();
			for (Relationship input : transaction.getRelationships(TGRelationshipType.INPUT))
				inputAddresses.add(input.getStartNode());
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
