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
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.structr.api.graph.Direction;
import org.structr.api.graph.Relationship;
import org.structr.api.util.Iterables;
import org.structr.common.error.FrameworkException;
import org.structr.core.GraphObject;
import org.structr.core.Result;
import org.structr.core.app.App;
import org.structr.core.app.StructrApp;
import org.structr.core.entity.AbstractNode;
import org.structr.core.entity.Group;
import org.structr.core.entity.ResourceAccess;
import org.structr.core.entity.TestOne;
import org.structr.core.entity.TestUser;
import org.structr.core.entity.TestUserExtendsUser;
import org.structr.core.entity.relationship.Ownership;
import org.structr.core.entity.relationship.PrincipalOwnsNode;
import org.structr.core.graph.NodeInterface;
import org.structr.core.graph.Tx;
import org.structr.core.property.PropertyKey;
import org.structr.core.property.PropertyMap;

//~--- classes ----------------------------------------------------------------

/**
 * Test access control with different permission levels.
 *
 *
 */
public class AccessControlTest extends StructrTest {

	private static final Logger logger = LoggerFactory.getLogger(AccessControlTest.class.getName());

	@Test
	public void test01PublicAccessToNonPublicNode() {

		// remove auto-generated resource access objects
		clearResourceAccess();

		try {

			List<TestUser> users = createTestNodes(TestUser.class, 1);
			TestUser user = (TestUser) users.get(0);

			// Create node with user context
			Class type = TestOne.class;
			createTestNode(TestOne.class, user);

			SecurityContext publicContext = SecurityContext.getInstance(null, AccessMode.Frontend);

			try (final Tx tx = app.tx()) {

				Result result = StructrApp.getInstance(publicContext).nodeQuery(type).getResult();

				// Node should not be visible in public context (no user logged in)
				assertTrue(result.isEmpty());
			}

		} catch (FrameworkException ex) {

			logger.warn("", ex);
			fail("Unexpected exception");

		}

	}


	@Test
	public void test02PublicAccessToPublicNode() {

		// remove auto-generated resource access objects
		clearResourceAccess();

		try {

			List<TestUser> users = createTestNodes(TestUser.class, 1);
			TestUser user = (TestUser) users.get(0);

			PropertyMap props = new PropertyMap();
			props.put(AbstractNode.visibleToPublicUsers, true);

			// Create two nodes with user context, one of them is visible to public users
			Class type = TestOne.class;
			TestOne t1 = createTestNode(TestOne.class, props, user);
			TestOne t2 = createTestNode(TestOne.class, user);

			SecurityContext publicContext = SecurityContext.getInstance(null, AccessMode.Frontend);

			try (final Tx tx = app.tx()) {

				Result result = StructrApp.getInstance(publicContext).nodeQuery(type).getResult();

				assertEquals(1, result.size());
				assertEquals(t1.getUuid(), result.get(0).getUuid());
			}

		} catch (FrameworkException ex) {

			logger.warn("", ex);
			fail("Unexpected exception");

		}

	}

	@Test
	public void test03PublicAccessToProtectedNode() {

		// remove auto-generated resource access objects
		clearResourceAccess();

		try {

			List<TestUser> users = createTestNodes(TestUser.class, 1);
			TestUser user = (TestUser) users.get(0);

			PropertyMap props = new PropertyMap();
			props.put(AbstractNode.visibleToPublicUsers, true);

			// Create two nodes with user context, one of them is visible to public users
			Class type = TestOne.class;
			TestOne t1 = createTestNode(TestOne.class, props, user);

			props = new PropertyMap();
			props.put(AbstractNode.visibleToAuthenticatedUsers, true);

			TestOne t2 = createTestNode(TestOne.class, props, user);

			SecurityContext publicContext = SecurityContext.getInstance(null, AccessMode.Frontend);

			try (final Tx tx = app.tx()) {

				Result result = StructrApp.getInstance(publicContext).nodeQuery(type).getResult();

				assertEquals(1, result.size());
				assertEquals(t1.getUuid(), result.get(0).getUuid());
			}

		} catch (FrameworkException ex) {

			logger.warn("", ex);
			fail("Unexpected exception");

		}

	}

	@Test
	public void test04BackendUserAccessToProtectedNode() {

		// remove auto-generated resource access objects
		clearResourceAccess();

		try {

			List<TestUser> users = createTestNodes(TestUser.class, 2);
			TestUser user1 = (TestUser) users.get(0);
			TestUser user2 = (TestUser) users.get(1);

			PropertyMap props = new PropertyMap();
			props.put(AbstractNode.visibleToPublicUsers, true);

			// Create two nodes with user context, one of them is visible to public users
			Class type = TestOne.class;
			TestOne t1 = createTestNode(TestOne.class, props, user1);

			props = new PropertyMap();
			props.put(AbstractNode.visibleToAuthenticatedUsers, true);

			TestOne t2 = createTestNode(TestOne.class, props, user1);

			// Let another user search
			SecurityContext user2Context = SecurityContext.getInstance(user2, AccessMode.Backend);

			try (final Tx tx = app.tx()) {
				Result result = StructrApp.getInstance(user2Context).nodeQuery(type).getResult();

				assertEquals(1, result.size());
			}

		} catch (FrameworkException ex) {

			logger.warn("", ex);
			fail("Unexpected exception");

		}

	}

	@Test
	public void test05FrontendUserAccessToProtectedNode() {

		// remove auto-generated resource access objects
		clearResourceAccess();

		try {

			List<TestUser> users = createTestNodes(TestUser.class, 2);
			TestUser user1 = (TestUser) users.get(0);
			TestUser user2 = (TestUser) users.get(1);

			PropertyMap props = new PropertyMap();
			props.put(AbstractNode.visibleToPublicUsers, true);

			// Create two nodes with user context, one of them is visible to public users
			Class type = TestOne.class;
			TestOne t1 = createTestNode(TestOne.class, props, user1);

			props = new PropertyMap();
			props.put(AbstractNode.visibleToAuthenticatedUsers, true);

			TestOne t2 = createTestNode(TestOne.class, props, user1);

			// Let another user search
			SecurityContext user2Context = SecurityContext.getInstance(user2, AccessMode.Frontend);

			try (final Tx tx = app.tx()) {

				Result result = StructrApp.getInstance(user2Context).nodeQuery(type).getResult();

				assertEquals(1, result.size());
			}

		} catch (FrameworkException ex) {

			logger.warn("", ex);
			fail("Unexpected exception");

		}

	}

	@Test
	public void test06GrantReadPermission() {

		// remove auto-generated resource access objects
		clearResourceAccess();

		try {

			List<TestUser> users = createTestNodes(TestUser.class, 2);
			TestUser user1 = (TestUser) users.get(0);
			TestUser user2 = (TestUser) users.get(1);
			Result result = null;

			// Let user 1 create a node
			Class type = TestOne.class;
			final TestOne t1 = createTestNode(TestOne.class, user1);

			try (final Tx tx = app.tx()) {

				// Grant read permission to user 2
				t1.grant(Permission.read, user2);
				tx.success();
			}

			// Let user 2 search
			SecurityContext user2Context = SecurityContext.getInstance(user2, AccessMode.Backend);

			try (final Tx tx = app.tx()) {

				result = StructrApp.getInstance(user2Context).nodeQuery(type).getResult();

				assertEquals(1, result.size());
				assertEquals(t1.getUuid(), result.get(0).getUuid());
			}

			try (final Tx tx = app.tx()) {

				// Revoke permission again
				t1.revoke(Permission.read, user2);
				tx.success();
			}

			try (final Tx tx = app.tx()) {

				result = StructrApp.getInstance(user2Context).nodeQuery(type).getResult();
				assertTrue(result.isEmpty());
			}

		} catch (FrameworkException ex) {

			logger.warn("", ex);
			fail("Unexpected exception");

		}

	}

	@Test
	public void test07ResultCount() {

		// remove auto-generated resource access objects
		clearResourceAccess();

		try {

			final Class type = TestOne.class;
			final List<NodeInterface> nodes = createTestNodes(type, 10);

			try (final Tx tx = app.tx()) {
				nodes.get(3).setProperty(AbstractNode.visibleToPublicUsers, true);
				nodes.get(5).setProperty(AbstractNode.visibleToPublicUsers, true);
				nodes.get(7).setProperty(AbstractNode.visibleToPublicUsers, true);
				tx.success();
			}

			SecurityContext publicContext = SecurityContext.getInstance(null, AccessMode.Frontend);

			try (final Tx tx = app.tx()) {

				Result result = StructrApp.getInstance(publicContext).nodeQuery(type).sort(AbstractNode.createdDate).getResult();

				assertEquals(3, result.size());
				assertEquals(3, (int) result.getRawResultCount());

				// do not test order of elements
//				assertEquals(nodes.get(3).getUuid(), result.get(0).getUuid());
//				assertEquals(nodes.get(5).getUuid(), result.get(1).getUuid());
//				assertEquals(nodes.get(7).getUuid(), result.get(2).getUuid());
			}

		} catch (FrameworkException ex) {

			logger.warn("", ex);
			fail("Unexpected exception");

		}

	}

	@Test
	public void test07ResultCountWithPaging() {

		// remove auto-generated resource access objects
		clearResourceAccess();

		try {

			final Class type = TestOne.class;
			final List<NodeInterface> nodes = createTestNodes(type, 10);
			int count = 0;

			try (final Tx tx = app.tx()) {

				// add names to make sorting work...
				for (final NodeInterface node : nodes) {
					node.setProperty(AbstractNode.name, "node0" + count++);
				}

				nodes.get(3).setProperty(AbstractNode.visibleToPublicUsers, true);
				nodes.get(5).setProperty(AbstractNode.visibleToPublicUsers, true);
				nodes.get(7).setProperty(AbstractNode.visibleToPublicUsers, true);
				nodes.get(9).setProperty(AbstractNode.visibleToPublicUsers, true);
				tx.success();
			}

			SecurityContext publicContext = SecurityContext.getInstance(null, AccessMode.Frontend);

			PropertyKey sortKey = AbstractNode.name;
			boolean sortDesc    = false;
			int pageSize        = 2;
			int page            = 1;

			try (final Tx tx = app.tx()) {

				Result result = StructrApp.getInstance(publicContext).nodeQuery(type).sort(sortKey).order(sortDesc).page(page).pageSize(pageSize).getResult();

				assertEquals(2, result.size());
				assertEquals(4, (int) result.getRawResultCount());

				assertEquals(nodes.get(3).getUuid(), result.get(0).getUuid());
				assertEquals(nodes.get(5).getUuid(), result.get(1).getUuid());
			}

		} catch (FrameworkException ex) {

			logger.warn("", ex);
			fail("Unexpected exception");

		}

	}

	@Test
	public void test08WriteAccess() {

		// remove auto-generated resource access objects
		clearResourceAccess();

		try {

			final TestUser owner = createTestNode(TestUser.class);
			final TestUser user  = createTestNode(TestUser.class);

			// create new node
			createTestNode(TestOne.class, owner);


			final SecurityContext userContext = SecurityContext.getInstance(owner, AccessMode.Frontend);
			final App userApp                 = StructrApp.getInstance(userContext);

			try (final Tx tx = userApp.tx()) {

				final TestOne t = StructrApp.getInstance(userContext).nodeQuery(TestOne.class).getFirst();

				assertNotNull(t);

				t.setProperty(TestOne.aString, "aString");

				assertEquals("aString", t.getProperty(TestOne.aString));

				tx.success();
			}

		} catch (FrameworkException ex) {

			logger.warn("", ex);
			fail("Unexpected exception");

		}

	}

	@Test
	public void test01WriteAccess() {

		// remove auto-generated resource access objects
		clearResourceAccess();

		try {

			final TestUser owner = createTestNode(TestUser.class);
			final TestUser user  = createTestNode(TestUser.class);

			// create new node
			final TestOne t1     = createTestNode(TestOne.class, owner);

			final SecurityContext ownerContext = SecurityContext.getInstance(owner, AccessMode.Frontend);
			final SecurityContext userContext  = SecurityContext.getInstance(user, AccessMode.Frontend);
			final App ownerAppContext          = StructrApp.getInstance(ownerContext);
			final App userAppContext           = StructrApp.getInstance(userContext);

			// test with owner, expect success
			try (final Tx tx = ownerAppContext.tx()) {

				final TestOne t = StructrApp.getInstance(ownerContext).nodeQuery(TestOne.class).getFirst();

				assertNotNull(t);

				t.setProperty(TestOne.aString, "aString");
				assertEquals("aString", t.getProperty(TestOne.aString));

				tx.success();
			}

			// test with foreign user, expect failure, node should not be found
			try (final Tx tx = userAppContext.tx()) {

				// node should not be found
				assertNull(StructrApp.getInstance(userContext).nodeQuery(TestOne.class).getFirst());

				tx.success();
			}

			// test with foreign user, expect failure, node should not be found
			try (final Tx tx = ownerAppContext.tx()) {

				// make node visible to user
				t1.grant(Permission.read, user);

				tx.success();
			}

			// try to grant read permissions in user context, should fail because user doesn't have access control permission
			try (final Tx tx = userAppContext.tx()) {

				try {
					final TestOne t = StructrApp.getInstance(userContext).nodeQuery(TestOne.class).getFirst();
					t.grant(Permission.read, user);

					fail("Non-owner should not be allowed to change permissions on object");

				} catch (FrameworkException fex) {

					// expect status 403 forbidden
					assertEquals(fex.getStatus(), 403);

				}

				tx.success();
			}

			// try to grant read permissions in owner context, should succeed (?)
			try (final Tx tx = ownerAppContext.tx()) {

				// important lesson here: the context under which the node is constructed defines the security context
				final TestOne t = StructrApp.getInstance(ownerContext).nodeQuery(TestOne.class).getFirst();
				t.grant(Permission.accessControl, user);
				tx.success();
			}

			// test with foreign user, expect failure
			try (final Tx tx = userAppContext.tx()) {

				final TestOne t = StructrApp.getInstance(userContext).nodeQuery(TestOne.class).getFirst();

				// node should be found because it's public
				assertNotNull(t);

				// setProperty should fail because of missing write permissions
				try {

					t.setProperty(TestOne.aString, "aString");
					fail("setProperty should not be allowed for non-owner on publicly visible nodes");

				} catch (FrameworkException fex) {

					// expect status 403 forbidden
					assertEquals(fex.getStatus(), 403);
				}

				tx.success();
			}

			// grant write
			try (final Tx tx = app.tx()) {

				// make t1 visible to public users explicitely
				t1.setProperty(GraphObject.visibleToPublicUsers, true);

				tx.success();
			}

		} catch (FrameworkException ex) {

			logger.warn("", ex);
			fail("Unexpected exception");

		}

	}

	@Test
	public void testGroupMembershipVisibility() {

		TestUser user1 = null;
		TestUser user2 = null;
		Group group    = null;

		// ################################################################################################################
		// create two users

		try (final Tx tx = app.tx()) {

			user1 = createTestNode(TestUser.class, "user1");
			user2 = createTestNode(TestUser.class, "user2");

			tx.success();

		} catch (Throwable t) {

			logger.warn("", t);
			fail("Unexpected exception.");
		}

		final SecurityContext user1Context = SecurityContext.getInstance(user1, AccessMode.Backend);
		final App user1App                 = StructrApp.getInstance(user1Context);

		// ################################################################################################################
		// create a group and a test object that becomes accessible for the second user by group membership

		try (final Tx tx = user1App.tx()) {

			group = user1App.create(Group.class, "group");
			user1App.create(TestOne.class, "testone");

			assertEquals("Invalid group owner", user1, group.getOwnerNode());

			tx.success();

		} catch (Throwable t) {

			logger.warn("", t);
			fail("Unexpected exception.");
		}

		// ################################################################################################################
		// user1 is owner of the test object
		// we now grant the group read access to the test object

		try (final Tx tx = user1App.tx()) {

			final TestOne test = user1App.nodeQuery(TestOne.class).getFirst();

			assertNotNull(test);

			test.grant(Permission.read, group);

			tx.success();

		} catch (Throwable t) {

			logger.warn("", t);
			fail("Unexpected exception.");
		}

		// ################################################################################################################
		// user2 is not yet member of the group, so
		// it should not be possible to access the object

		final SecurityContext user2Context = SecurityContext.getInstance(user2, AccessMode.Backend);
		final App user2App                 = StructrApp.getInstance(user2Context);

		try (final Tx tx = user2App.tx()) {

			final TestOne test = user2App.nodeQuery(TestOne.class).getFirst();
			assertNull(test);

			tx.success();

		} catch (FrameworkException fex) {

			logger.warn("", fex);
			fail("Unexpected exception.");
		}

		// ################################################################################################################
		// now we add user2 to the group

		try (final Tx tx = user1App.tx()) {

			group.addMember(user2);
			tx.success();

		} catch (Throwable t) {

			logger.warn("", t);
			fail("Unexpected exception.");
		}

		// ################################################################################################################
		// user2 is now member of the group, so
		// it should be possible to access the object

		try (final Tx tx = user2App.tx()) {

			final TestOne test = user2App.nodeQuery(TestOne.class).getFirst();
			assertNotNull(test);

			tx.success();

		} catch (FrameworkException fex) {

			logger.warn("", fex);
			fail("Unexpected exception.");
		}

		// ################################################################################################################
		// user2 should NOT be able to write the object

		try (final Tx tx = user2App.tx()) {

			final TestOne test = user2App.nodeQuery(TestOne.class).getFirst();
			assertNotNull(test);

			test.setProperty(TestOne.name, "newname");

			tx.success();

			fail("User should not be able to write an object that it doesn't own.");

		} catch (FrameworkException fex) {

			assertEquals("Invalid group permissions result", 403, fex.getStatus());
			assertEquals("Invalid group permissions result", "Modification not permitted.", fex.getMessage());
		}

		// ################################################################################################################
		// now we grant write access to the group

		try (final Tx tx = user1App.tx()) {

			final TestOne test = app.nodeQuery(TestOne.class).getFirst();
			assertNotNull(test);

			test.grant(Permission.write, group);

			tx.success();

		} catch (Throwable t) {

			logger.warn("", t);
			fail("Unexpected exception.");
		}

		// ################################################################################################################
		// user2 should now be able to write the object

		try (final Tx tx = user2App.tx()) {

			final TestOne test = user2App.nodeQuery(TestOne.class).getFirst();
			assertNotNull(test);

			test.setProperty(TestOne.name, "newname");

			tx.success();

		} catch (FrameworkException fex) {

			logger.warn("", fex);
			fail("Unexpected exception.");
		}

	}

	@Test
	public void testGroupVisibilityForMembers() {

		TestUser user1 = null;
		TestUser user2 = null;
		Group group    = null;

		// ################################################################################################################
		// create two users

		try (final Tx tx = app.tx()) {

			user1 = createTestNode(TestUser.class, "user1");
			user2 = createTestNode(TestUser.class, "user2");

			tx.success();

		} catch (Throwable t) {

			logger.warn("", t);
			fail("Unexpected exception.");
		}

		final SecurityContext user1Context = SecurityContext.getInstance(user1, AccessMode.Backend);
		final SecurityContext user2Context = SecurityContext.getInstance(user2, AccessMode.Backend);
		final App user1App                 = StructrApp.getInstance(user1Context);
		final App user2App                 = StructrApp.getInstance(user2Context);

		// ################################################################################################################
		// create a group and add the second user to that group

		try (final Tx tx = user1App.tx()) {

			group = user1App.create(Group.class, "group");

			assertEquals("Invalid group owner", user1, group.getOwnerNode());

			// add user2 to group
			group.addMember(user2);

			tx.success();

		} catch (Throwable t) {

			logger.warn("", t);
			fail("Unexpected exception.");
		}


		// ################################################################################################################
		// test read access to group

		try (final Tx tx = user2App.tx()) {


			final Group testGroup = user2App.nodeQuery(Group.class).andName("group").getFirst();

			assertNotNull(testGroup);
			assertEquals("Group name should be readable for members", "group", testGroup.getName());

			tx.success();

		} catch (Throwable t) {

			logger.warn("", t);
			fail("Unexpected exception.");
		}

		// ################################################################################################################
		// test write access to group, expected result: 403 Modification not permitted.

		try (final Tx tx = user2App.tx()) {

			final Group testGroup = user2App.nodeQuery(Group.class).andName("group").getFirst();

			assertNotNull(testGroup);
			assertEquals("Group name should be readable for members", "group", testGroup.getName());

			testGroup.setProperty(Group.name, "dontchangeme");

			fail("Griup name should not be writable for members");

			tx.success();

		} catch (FrameworkException t) {

			assertEquals(403, t.getStatus());
			assertEquals("Modification not permitted.", t.getMessage());
		}

	}

	@Test
	public void test00CreatePrincipal() {

		TestUser user1 = null;

		try (final Tx tx = app.tx()) {

			List<TestUser> users = createTestNodes(TestUser.class, 1);
			user1 = (TestUser) users.get(0);
			user1.setProperty(AbstractNode.name, "user1");

			tx.success();

		} catch (FrameworkException ex) {
			logger.error(ex.toString());
		}		

		// Switch user context to user1
		final App user1App = StructrApp.getInstance(SecurityContext.getInstance(user1, AccessMode.Frontend));
		try (final Tx tx = user1App.tx()) {

			final TestUserExtendsUser user2 = user1App.create(TestUserExtendsUser.class);

			assertNotNull(user2);

		} catch (FrameworkException ex) {
			logger.error(ex.toString());
			fail("Unexpected exception: " + ex.toString());
		}		
	}
	
	@Test
	public void test01SetOwner() {

		try {

			TestUser user1 = null;
			TestUser user2 = null;
			TestOne t1 = null;
			Class type = TestOne.class;

			try (final Tx tx = app.tx()) {

				List<TestUser> users = createTestNodes(TestUser.class, 2);
				user1 = (TestUser) users.get(0);
				user1.setProperty(AbstractNode.name, "user1");

				user2 = (TestUser) users.get(1);
				user2.setProperty(AbstractNode.name, "user2");

				t1 = createTestNode(TestOne.class);

				t1.setProperty(AbstractNode.owner, user1);

				tx.success();

			} catch (FrameworkException ex) {
				logger.error(ex.toString());
			}

			try (final Tx tx = app.tx()) {

				assertEquals(user1, t1.getProperty(AbstractNode.owner));

				// Switch user context to user1
				final App user1App = StructrApp.getInstance(SecurityContext.getInstance(user1, AccessMode.Backend));

				// Check if user1 can see t1
				assertEquals(t1, user1App.nodeQuery(type).getFirst());
			}

			try (final Tx tx = app.tx()) {

				// As superuser, make another user the owner
				t1.setProperty(AbstractNode.owner, user2);

				tx.success();

			} catch (FrameworkException ex) {
				logger.error(ex.toString());
			}

			try (final Tx tx = app.tx()) {

				// Switch user context to user2
				final App user2App = StructrApp.getInstance(SecurityContext.getInstance(user2, AccessMode.Backend));

				// Check if user2 can see t1
				assertEquals(t1, user2App.nodeQuery(type).getFirst());

				// Check if user2 is owner of t1
				assertEquals(user2, t1.getProperty(AbstractNode.owner));
			}

		} catch (FrameworkException ex) {

			logger.error(ex.toString());
			fail("Unexpected exception");

		}

	}

	@Test
	public void test02SetDifferentPrincipalTypesAsOwner() {

		try (final Tx tx = app.tx()) {

			List<TestUser> users = createTestNodes(TestUser.class, 2);
			TestUser user1 = (TestUser) users.get(0);

			List<Group> groups = createTestNodes(Group.class, 1);
			Group group1 = (Group) groups.get(0);

			TestOne t1 = createTestNode(TestOne.class);

			t1.setProperty(AbstractNode.owner, user1);
			t1.setProperty(AbstractNode.owner, group1);
			assertEquals(group1, t1.getProperty(AbstractNode.owner));

			Ownership ownerRel = t1.getIncomingRelationship(PrincipalOwnsNode.class);
			assertNotNull(ownerRel);

			// Do additional low-level check here to ensure cardinality!
			List<Relationship> incomingRels = Iterables.toList(t1.getNode().getRelationships(Direction.INCOMING, new PrincipalOwnsNode()));
			assertEquals(1, incomingRels.size());

			tx.success();

		} catch (FrameworkException ex) {

			logger.error(ex.toString());
			fail("Unexpected exception");
		}
	}

	@Test
	public void test09PrivilegeEscalation() {

		// remove auto-generated resource access objects
		clearResourceAccess();

		try {

			TestUser nonAdmin  = createTestNode(TestUser.class);

			final SecurityContext userContext           = SecurityContext.getInstance(nonAdmin, AccessMode.Frontend);
            nonAdmin.setSecurityContext(userContext);
			App userApp                                 = StructrApp.getInstance(userContext);

			try (final Tx tx = userApp.tx()) {

                assertFalse(nonAdmin.isAdmin());

				nonAdmin.setProperty(TestUser.isAdmin, true);

                fail("Privilege escalation using setProperty()-method! Non-admin may not set an admin flag!");

				tx.success();

			} catch (FrameworkException ex) {

                assertFalse("Privilege escalation using setProperty()-method! Non-admin may not set an admin flag!", nonAdmin.isAdmin());
                logger.warn("", ex);

            }

			try (final Tx tx = userApp.tx()) {

				assertFalse(nonAdmin.isAdmin());

                PropertyMap props = new PropertyMap();
                props.put(TestUser.isAdmin, true);
                nonAdmin.setProperties(userContext, props);

                fail("Privilege escalation using setProperties()-method! Non-admin may not set an admin flag!");

                tx.success();

			} catch (FrameworkException ex) {

                logger.warn("", ex);
                assertFalse("Privilege escalation using setProperties()-method! Non-admin may not set an admin flag!", nonAdmin.isAdmin());

            }

		} catch (FrameworkException ex) {

			logger.warn("", ex);
			fail("Unexpected Exception");

		}

	}
    
	// ----- private methods -----
	public static void clearResourceAccess() {

		final App app = StructrApp.getInstance();

		try (final Tx tx = app.tx()) {

			for (final ResourceAccess access : app.nodeQuery(ResourceAccess.class).getAsList()) {
				app.delete(access);
			}

			tx.success();

		} catch (Throwable t) {

			logger.warn("Unable to clear resource access grants", t);
		}
	}
}
