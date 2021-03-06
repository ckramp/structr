/*
 * Copyright (C) 2010-2021 Structr GmbH
 *
 * This file is part of Structr <http://structr.org>.
 *
 * Structr is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * Structr is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Structr.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.structr.test.web.advanced;

import com.jayway.restassured.RestAssured;
import com.jayway.restassured.filter.log.ResponseLoggingFilter;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Function;
import org.hamcrest.Matchers;
import org.structr.common.AccessMode;
import org.structr.common.Permission;
import org.structr.common.SecurityContext;
import org.structr.common.error.FrameworkException;
import org.structr.core.app.StructrApp;
import org.structr.core.entity.AbstractNode;
import org.structr.core.entity.Group;
import org.structr.core.entity.MailTemplate;
import org.structr.core.entity.Principal;
import org.structr.core.entity.SchemaGrant;
import org.structr.core.entity.SchemaNode;
import org.structr.core.graph.NodeAttribute;
import org.structr.core.graph.Tx;
import org.structr.web.entity.User;
import org.structr.web.entity.dom.DOMElement;
import org.structr.web.entity.dom.Page;
import org.structr.web.entity.html.Body;
import org.structr.web.entity.html.Div;
import org.structr.web.entity.html.Head;
import org.structr.web.entity.html.Html;
import org.structr.web.entity.html.Option;
import org.structr.web.entity.html.Select;
import static org.testng.AssertJUnit.assertTrue;
import static org.testng.AssertJUnit.fail;
import org.testng.annotations.Test;

public class Deployment5Test extends DeploymentTestBase {

	@Test
	public void test51SchemaGrantsRoundtrip() {

		/*
		 * This method verifies that schema-based permissions survive an export/import deployment
		 * roundtrip even if the UUID of the group changes. The test simulates the deployment of
		 * an application from one server to another with differen groups.
		 */

		// setup
		try (final Tx tx = app.tx()) {

			// Create a group with name "SchemaAccess" and allow access to all nodes of type "MailTemplate"
			final SchemaNode schemaNode = app.nodeQuery(SchemaNode.class).andName("MailTemplate").getFirst();
			final Group group           = app.create(Group.class, "SchemaAccess");
			final User user             = app.create(User.class, "tester");

			group.addMember(securityContext, user);

			// create schema grant object
			app.create(SchemaGrant.class,
				new NodeAttribute<>(SchemaGrant.schemaNode,  schemaNode),
				new NodeAttribute<>(SchemaGrant.principal,   group),
				new NodeAttribute<>(SchemaGrant.allowRead,   true),
				new NodeAttribute<>(SchemaGrant.allowWrite,  true),
				new NodeAttribute<>(SchemaGrant.allowDelete, true)
			);

			// create MailTemplate instances
			app.create(MailTemplate.class, "TEMPLATE1");
			app.create(MailTemplate.class, "TEMPLATE2");

			tx.success();

		} catch (FrameworkException fex) {
			fex.printStackTrace();
			fail("Unexpected exception.");
		}

		// test1: verify that user is allowed to access MailTemplates
		try (final Tx tx = app.tx()) {

			final User user                   = app.nodeQuery(User.class).andName("tester").getFirst();
			final SecurityContext userContext = SecurityContext.getInstance(user, AccessMode.Backend);

			for (final MailTemplate template : app.nodeQuery(MailTemplate.class).getAsList()) {

				assertTrue("User should have read access to all mail templates", template.isGranted(Permission.read, userContext));
				assertTrue("User should have write access to all mail templates", template.isGranted(Permission.write, userContext));
				assertTrue("User should have delete access to all mail templates", template.isGranted(Permission.delete, userContext));
			}

			tx.success();

		} catch (FrameworkException fex) {
			fex.printStackTrace();
			fail("Unexpected exception.");
		}

		// deployment export, clean database, create new group with same name but different ID, deployment import
		doImportExportRoundtrip(true, true, new Function() {

			@Override
			public Object apply(final Object o) {

				try (final Tx tx = app.tx()) {

					final Group group = app.create(Group.class, "SchemaAccess");
					final User user   = app.create(User.class, "tester");

					group.addMember(securityContext, user);

					tx.success();

				} catch (FrameworkException fex) {
					fex.printStackTrace();
					fail("Unexpected exception.");
				}

				return null;
			}
		});

		// test2: verify that new user is allowed to access MailTemplates
		try (final Tx tx = app.tx()) {

			final User user                   = app.nodeQuery(User.class).andName("tester").getFirst();
			final SecurityContext userContext = SecurityContext.getInstance(user, AccessMode.Backend);

			for (final MailTemplate template : app.nodeQuery(MailTemplate.class).getAsList()) {

				assertTrue("User should have read access to all mail templates", template.isGranted(Permission.read, userContext));
				assertTrue("User should have write access to all mail templates", template.isGranted(Permission.write, userContext));
				assertTrue("User should have delete access to all mail templates", template.isGranted(Permission.delete, userContext));
			}

			tx.success();

		} catch (FrameworkException fex) {
			fex.printStackTrace();
			fail("Unexpected exception.");
		}
	}

	@Test
	public void test52SpecialDOMNodeAttributes() {

		String uuid = null;

		// setup
		try (final Tx tx = app.tx()) {

			app.create(User.class,
				new NodeAttribute<>(AbstractNode.name, "admin"),
				new NodeAttribute<>(StructrApp.key(Principal.class, "password"), "admin"),
				new NodeAttribute<>(StructrApp.key(Principal.class, "isAdmin"), true)
			);

			final Group parent       = app.create(Group.class, "parent");
			final List<Group> groups = new LinkedList<>();

			for (int i=0; i<8; i++) {
				groups.add(app.create(Group.class, "group0" + i));
			}

			uuid = parent.getUuid();

			// add some members
			parent.addMember(securityContext, groups.get(1));
			parent.addMember(securityContext, groups.get(3));
			parent.addMember(securityContext, groups.get(4));
			parent.addMember(securityContext, groups.get(6));

			// create first page
			final Page page1 = Page.createNewPage(securityContext,   "test52_1");
			final Html html1 = createElement(page1, page1, "html");
			final Head head1 = createElement(page1, html1, "head");
			createElement(page1, head1, "title", "test52_1");

			final Body body1 =  createElement(page1, html1, "body");
			final Div div1   =  createElement(page1, body1, "div");
			final Select sel1 = createElement(page1, div1,  "select");
			final Option opt1 = createElement(page1, sel1,  "option", "${group.name}");

			sel1.setProperty(StructrApp.key(Select.class, "_html_multiple"), "multiple");

			// repeater config
			opt1.setProperty(StructrApp.key(DOMElement.class, "functionQuery"), "find('Group', sort('name'))");
			opt1.setProperty(StructrApp.key(DOMElement.class, "dataKey"),       "group");

			// special keys for Option element
			opt1.setProperty(StructrApp.key(Option.class, "selectedValues"), "current.members");
			opt1.setProperty(StructrApp.key(Option.class, "_html_value"),    "${group.id}");

			tx.success();

		} catch (FrameworkException fex) {
			fail("Unexpected exception.");
		}

		RestAssured.basePath = "/";

		// check HTML result before roundtrip
		RestAssured
			.given()
			.filter(ResponseLoggingFilter.logResponseIfStatusCodeIs(401))
			.filter(ResponseLoggingFilter.logResponseIfStatusCodeIs(403))
			.filter(ResponseLoggingFilter.logResponseIfStatusCodeIs(404))
			.filter(ResponseLoggingFilter.logResponseIfStatusCodeIs(500))
			.header("x-user", "admin")
			.header("x-password", "admin")
			.expect()
			.body("html.body.div.select.option[0]",            Matchers.equalTo("group00"))
			.body("html.body.div.select.option[1]",            Matchers.equalTo("group01"))
			.body("html.body.div.select.option[1].@selected",   Matchers.equalTo("selected"))
			.body("html.body.div.select.option[2]",            Matchers.equalTo("group02"))
			.body("html.body.div.select.option[3]",            Matchers.equalTo("group03"))
			.body("html.body.div.select.option[3].@selected",   Matchers.equalTo("selected"))
			.body("html.body.div.select.option[4]",            Matchers.equalTo("group04"))
			.body("html.body.div.select.option[4].@selected",   Matchers.equalTo("selected"))
			.body("html.body.div.select.option[5]",            Matchers.equalTo("group05"))
			.body("html.body.div.select.option[6]",            Matchers.equalTo("group06"))
			.body("html.body.div.select.option[6].@selected",   Matchers.equalTo("selected"))
			.body("html.body.div.select.option[7]",            Matchers.equalTo("group07"))
			.body("html.body.div.select.option[8]",            Matchers.equalTo("parent"))
			.statusCode(200)
			.when()
			.get("/test52_1/" + uuid);

		// test roundtrip
		compare(calculateHash(), true);

		// user must be created again...
		try (final Tx tx = app.tx()) {

			app.create(User.class,
				new NodeAttribute<>(AbstractNode.name, "admin"),
				new NodeAttribute<>(StructrApp.key(Principal.class, "password"), "admin"),
				new NodeAttribute<>(StructrApp.key(Principal.class, "isAdmin"), true)
			);

			final Group parent       = app.create(Group.class, "parent");
			final List<Group> groups = new LinkedList<>();

			for (int i=0; i<8; i++) {
				groups.add(app.create(Group.class, "group0" + i));
			}

			uuid = parent.getUuid();

			// add some members
			parent.addMember(securityContext, groups.get(1));
			parent.addMember(securityContext, groups.get(3));
			parent.addMember(securityContext, groups.get(4));
			parent.addMember(securityContext, groups.get(6));

			tx.success();

		} catch (FrameworkException fex) {
			fail("Unexpected exception.");
		}

		// wait for transaction to settle
		try { Thread.sleep(1000); } catch (Throwable t) {}

		RestAssured.basePath = "/";

		// check HTML result after roundtrip
		RestAssured
			.given()
			.filter(ResponseLoggingFilter.logResponseIfStatusCodeIs(401))
			.filter(ResponseLoggingFilter.logResponseIfStatusCodeIs(403))
			.filter(ResponseLoggingFilter.logResponseIfStatusCodeIs(404))
			.filter(ResponseLoggingFilter.logResponseIfStatusCodeIs(500))
			.header("x-user", "admin")
			.header("x-password", "admin")
			.expect()
			.body("html.body.div.select.option[0]",            Matchers.equalTo("group00"))
			.body("html.body.div.select.option[1]",            Matchers.equalTo("group01"))
			.body("html.body.div.select.option[1].@selected",   Matchers.equalTo("selected"))
			.body("html.body.div.select.option[2]",            Matchers.equalTo("group02"))
			.body("html.body.div.select.option[3]",            Matchers.equalTo("group03"))
			.body("html.body.div.select.option[3].@selected",   Matchers.equalTo("selected"))
			.body("html.body.div.select.option[4]",            Matchers.equalTo("group04"))
			.body("html.body.div.select.option[4].@selected",   Matchers.equalTo("selected"))
			.body("html.body.div.select.option[5]",            Matchers.equalTo("group05"))
			.body("html.body.div.select.option[6]",            Matchers.equalTo("group06"))
			.body("html.body.div.select.option[6].@selected",   Matchers.equalTo("selected"))
			.body("html.body.div.select.option[7]",            Matchers.equalTo("group07"))
			.body("html.body.div.select.option[8]",            Matchers.equalTo("parent"))
			.statusCode(200)
			.when()
			.get("/test52_1/" + uuid);

	}

}
