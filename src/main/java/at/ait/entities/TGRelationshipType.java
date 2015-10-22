package at.ait.entities;

import org.neo4j.graphdb.RelationshipType;

public enum TGRelationshipType implements RelationshipType {
	INPUT, OUTPUT, USES, BELONGS_TO
}
