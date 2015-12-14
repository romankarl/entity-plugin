package at.ait.entities;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;

public class PathSearch {
	
	private Map<Node, PathNode> sourceOutputs;
	private Map<Node, PathNode> targetOutputs;
	private Map<Node, PathNode> sourceFringe;
	private Map<Node, PathNode> targetFringe;
	
	public PathSearch(Node source, Node target) {
		sourceOutputs = getOutputsOfAddress(source);
		targetOutputs = getOutputsOfAddress(target);
		sourceFringe = new HashMap<>();
		sourceFringe.putAll(sourceOutputs);
		targetFringe = new HashMap<>();
		targetFringe.putAll(targetOutputs);
	}
	
	public List<Node> start() {
		List<Node> path = null;
		while (true) {
			boolean expandLeft = sourceFringe.size() <= targetFringe.size();
			System.out.println("expand from " + (expandLeft ? "source" : "target"));
			Map<Node, PathNode> nextFringe = new HashMap<>();
			for (PathNode output : getNextOutputs(activeFringe(expandLeft).values(), direction(expandLeft))) {
				System.out.println(String.format("process output %s (id: %s) from %s (id: %s)",
						output.node.getProperty("txid_n"), output.node.getId(),
						output.predecessor.node.getProperty("txid_n"), output.predecessor.node.getId()));
				if (passiveFringe(expandLeft).containsKey(output.node)) {
					PathNode passivePath = passiveFringe(expandLeft).get(output.node);
					path = expandLeft ?
							PathNode.constructPath(output, passivePath) :
						    PathNode.constructPath(passivePath, output);
					return path;
				} else if (activeOutputs(expandLeft).containsKey(output.node)) {
					System.out.println("Ignore known node");
				} else {
					nextFringe.put(output.node, output);
					activeOutputs(expandLeft).put(output.node, output);
				}
			}
			if (expandLeft)
				sourceFringe = nextFringe;
			else
				targetFringe = nextFringe;
			if (nextFringe.isEmpty()) {
				break;
			}
		}
		return path;
	}
	
	private Map<Node, PathNode> activeOutputs(boolean expandLeft) {
		return expandLeft ? sourceOutputs : targetOutputs;
	}

	private Map<Node, PathNode> activeFringe(boolean expandLeft) {
		return expandLeft ? sourceFringe : targetFringe;
	}
	
	private Map<Node, PathNode> passiveFringe(boolean expandLeft) {
		return expandLeft ? targetFringe : sourceFringe;
	}
	
	private Direction direction(boolean expandLeft) {
		return expandLeft ? Direction.OUTGOING : Direction.INCOMING;
	}

	private Map<Node, PathNode> getOutputsOfAddress(Node address) {
		HashMap<Node, PathNode> outputs = new HashMap<>();
		for (Relationship uses : address.getRelationships(TGRelationshipType.USES))
			outputs.put(uses.getStartNode(), new PathNode(uses.getStartNode()));
		return outputs;
	}
	
	private Iterable<PathNode> getNextOutputs(final Iterable<PathNode> outputs, final Direction direction) {
		return new Iterable<PathNode>() {

			@Override
			public Iterator<PathNode> iterator() {
				return new Iterator<PathNode>() {
					
					private Iterator<PathNode> outputIterator = outputs.iterator();
					private Iterator<Relationship> nextOutputIterator;
					private PathNode currentPath;
					private PathNode buffer;
					
					@Override
					public boolean hasNext() {
						buffer = buffer != null ? buffer : getNext();
						return buffer != null;
					}
					
					@Override
					public PathNode next() {
						PathNode next = buffer != null ? buffer : getNext();
						buffer = null;
						return next;
					}

					private PathNode getNext() {
						if (nextOutputIterator != null && nextOutputIterator.hasNext()) {
							Relationship io = nextOutputIterator.next();
							Node output = direction == Direction.OUTGOING ? io.getEndNode() : io.getStartNode();
							return new PathNode(output, currentPath);
						} else if (outputIterator.hasNext()) {
							currentPath = outputIterator.next();
							TGRelationshipType firstType;
							TGRelationshipType secondType;
							if (direction == Direction.OUTGOING) {
								firstType = TGRelationshipType.INPUT;
								secondType = TGRelationshipType.OUTPUT;
							} else {
								firstType = TGRelationshipType.OUTPUT;
								secondType = TGRelationshipType.INPUT;
							}
							Relationship io = currentPath.node.getSingleRelationship(firstType, direction);
							if (io != null) {
								Node transaction = io.getOtherNode(currentPath.node);
								nextOutputIterator = transaction.getRelationships(secondType, direction).iterator();
							}
							return getNext();
						} else {
							return null;
						}
					}

					@Override
					public void remove() {}					
				};
			}
			
		};
		
	}
}
