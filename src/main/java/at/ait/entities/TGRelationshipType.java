package at.ait.entities;

import org.neo4j.graphdb.RelationshipType;

public enum TGRelationshipType implements RelationshipType {
	CONTAINS, INPUT, OUTPUT, USES, BELONGS_TO
}
