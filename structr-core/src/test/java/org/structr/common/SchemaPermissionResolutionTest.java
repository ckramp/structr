/**
 * Copyright (C) 2010-2016 Structr GmbH
 *
 * This file is part of Structr <http://structr.org>.
 *
 * Structr is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * Structr is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Structr.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.structr.common;

import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import org.junit.Test;
import org.structr.core.Result;
import org.structr.core.app.StructrApp;
import org.structr.core.entity.SchemaNode;
import org.structr.core.entity.SchemaRelationshipNode;
import org.structr.core.entity.SchemaRelationshipNode.Propagation;
import org.structr.core.entity.TestUser;
import org.structr.core.graph.NodeAttribute;
import org.structr.core.graph.NodeInterface;
import org.structr.core.graph.Tx;
import org.structr.core.property.BooleanProperty;
import org.structr.schema.ConfigurationProvider;


public class SchemaPermissionResolutionTest extends StructrTest{

	private static final Logger logger = Logger.getLogger(AccessControlTest.class.getName());

	//~--- methods --------------------------------------------------------

	@Test
	public void simpleSchemaReadPermissionResolution() {

            try {

                    final ConfigurationProvider config = StructrApp.getConfiguration();
                    List<TestUser> users                                        = createTestNodes(TestUser.class, 1);
                    TestUser user                                               = (TestUser) users.get(0);
                    SecurityContext userContext                                 = SecurityContext.getInstance(user, AccessMode.Frontend);
                    Class fooBarRel                                             = null;

                    try(final Tx tx = app.tx()){

                            // create source and target node
                            final SchemaNode fooNode = app.create(SchemaNode.class, "Foo");
                            final SchemaNode barNode = app.create(SchemaNode.class, "Bar");

                            // create relationship
                            fooBarRel = app.create(SchemaRelationshipNode.class,
                                    new NodeAttribute<>(SchemaRelationshipNode.sourceNode, fooNode),
                                    new NodeAttribute<>(SchemaRelationshipNode.targetNode, barNode),
                                    new NodeAttribute<>(SchemaRelationshipNode.relationshipType, "grantsReadPermissionTo"),
                                    new NodeAttribute<>(SchemaRelationshipNode.readPropagation, Propagation.Add)
                            ).getClass();;

                            tx.success();

                    }

                    try(final Tx tx = app.tx()){

                            Class fooType = config.getNodeEntityClass("Foo");
                            Class barType = config.getNodeEntityClass("Bar");

                            NodeInterface foo1 = app.create(fooType, "Foo1");
                            NodeInterface bar1 = app.create(barType, "Bar1");

                            foo1.setProperty(new BooleanProperty("visibleToAuthenticatedUsers"), true);
                            bar1.setProperty(new BooleanProperty("visibleToAuthenticatedUsers"), false);

                            app.create(foo1, bar1, fooBarRel);

                            tx.success();

                    }

                    try(final Tx tx = app.tx()){

                            Class barType = config.getNodeEntityClass("Bar");
                            Result result = StructrApp.getInstance(userContext).nodeQuery(barType).getResult();

                            //Assert that the relationship between foo1 and bar1 granted read permissions on bar1
                            assertTrue(result.size() == 1);

                    }

            } catch (Throwable t) {

                    logger.log(Level.WARNING, "", t);
                    fail("Unexpected exception");

            }



	}

        @Test
	public void nestedSchemaReadPermissionResolution() {

            try {

                    final ConfigurationProvider config                          = StructrApp.getConfiguration();
                    List<TestUser> users                                        = createTestNodes(TestUser.class, 1);
                    TestUser user                                               = (TestUser) users.get(0);
                    SecurityContext userContext                                 = SecurityContext.getInstance(user, AccessMode.Frontend);
                    Class fooBarRel                                             = null;
                    Class barTest1Rel                                           = null;
                    Class test1Test2Rel                                         = null;
                    Class test1Test3Rel                                         = null;

                    try(final Tx tx = app.tx()){

                            // create source and target node
                            final SchemaNode fooNode = app.create(SchemaNode.class, "Foo");
                            final SchemaNode barNode = app.create(SchemaNode.class, "Bar");
                            final SchemaNode test1Node = app.create(SchemaNode.class, "Test1");
                            final SchemaNode test2Node = app.create(SchemaNode.class, "Test2");
                            final SchemaNode test3Node = app.create(SchemaNode.class, "Test3");

                            // create relationship
                            fooBarRel = app.create(SchemaRelationshipNode.class,
                                    new NodeAttribute<>(SchemaRelationshipNode.sourceNode, fooNode),
                                    new NodeAttribute<>(SchemaRelationshipNode.targetNode, barNode),
                                    new NodeAttribute<>(SchemaRelationshipNode.relationshipType, "grantsReadPermissionTo"),
                                    new NodeAttribute<>(SchemaRelationshipNode.readPropagation, Propagation.Add)
                            ).getClass();

                            barTest1Rel = app.create(SchemaRelationshipNode.class,
                                    new NodeAttribute<>(SchemaRelationshipNode.sourceNode, barNode),
                                    new NodeAttribute<>(SchemaRelationshipNode.targetNode, test1Node),
                                    new NodeAttribute<>(SchemaRelationshipNode.relationshipType, "keepsReadPermissionFromSource"),
                                    new NodeAttribute<>(SchemaRelationshipNode.readPropagation, Propagation.Keep)
                            ).getClass();

                            test1Test2Rel = app.create(SchemaRelationshipNode.class,
                                    new NodeAttribute<>(SchemaRelationshipNode.sourceNode, test1Node),
                                    new NodeAttribute<>(SchemaRelationshipNode.targetNode, test2Node),
                                    new NodeAttribute<>(SchemaRelationshipNode.relationshipType, "keepsReadPermissionFromSource"),
                                    new NodeAttribute<>(SchemaRelationshipNode.readPropagation, Propagation.Keep)
                            ).getClass();

                            test1Test3Rel = app.create(SchemaRelationshipNode.class,
                                    new NodeAttribute<>(SchemaRelationshipNode.sourceNode, test1Node),
                                    new NodeAttribute<>(SchemaRelationshipNode.targetNode, test3Node),
                                    new NodeAttribute<>(SchemaRelationshipNode.relationshipType, "removesReadPermissionFromSource"),
                                    new NodeAttribute<>(SchemaRelationshipNode.readPropagation, Propagation.Remove)
                            ).getClass();

                            tx.success();

                    }

                    Class fooType = config.getNodeEntityClass("Foo");
                    Class barType = config.getNodeEntityClass("Bar");
                    Class test1Type = config.getNodeEntityClass("Test1");
                    Class test2Type = config.getNodeEntityClass("Test2");
                    Class test3Type = config.getNodeEntityClass("Test3");

                    try(final Tx tx = app.tx()){

                            NodeInterface foo1 = app.create(fooType, "Foo1");
                            NodeInterface bar1 = app.create(barType, "Bar1");
                            NodeInterface test1 = app.create(barType, "Test1");
                            NodeInterface test2 = app.create(barType, "Test2");
                            NodeInterface test3 = app.create(barType, "Test3");

                            foo1.setProperty(new BooleanProperty("visibleToAuthenticatedUsers"), true);

                            //Create relationships
                            app.create(foo1, bar1, fooBarRel);
                            app.create(bar1, test1, barTest1Rel);
                            app.create(test1, test2, test1Test2Rel);
                            app.create(test1, test3, test1Test3Rel);

                            tx.success();

                    }


                    try(final Tx tx = app.tx()){

                            //Assert read permissions
                            Result result = StructrApp.getInstance(userContext).nodeQuery(barType).getResult();
                            assertTrue(result.size() == 1);

                            result = StructrApp.getInstance(userContext).nodeQuery(test1Type).getResult();
                            assertTrue(result.size() == 1);

                            result = StructrApp.getInstance(userContext).nodeQuery(test2Type).getResult();
                            assertTrue(result.size() == 1);

                            //Assert revoked read permission
                            result = StructrApp.getInstance(userContext).nodeQuery(test3Type).getResult();
                            assertTrue(result.isEmpty());

                    }

            } catch (Throwable t) {

                    logger.log(Level.WARNING, "", t);
                    fail("Unexpected exception");

            }



	}

}
