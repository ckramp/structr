package org.structr.flow;

import org.junit.Test;
import org.structr.common.SecurityContext;
import org.structr.common.error.FrameworkException;
import org.structr.core.app.App;
import org.structr.core.app.StructrApp;
import org.structr.core.graph.Tx;
import org.structr.flow.engine.FlowEngine;
import org.structr.flow.impl.FlowBaseNode;
import org.structr.flow.impl.FlowContainer;
import org.structr.flow.impl.FlowNode;
import org.structr.flow.impl.FlowReturn;
import org.structr.transform.StructrApiModuleTest;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class FlowTest extends StructrApiModuleTest {

	@Test
	public void testFlowContainer() {

		try (final Tx tx = app.tx()) {

			FlowContainer container = createTestNode(FlowContainer.class, "container");
			FlowReturn ret = createTestNode(FlowReturn.class, "return");

			List<FlowBaseNode> flowNodes = new ArrayList<>();

			flowNodes.add(ret);

			container.setProperty(FlowContainer.startNode, ret);
			container.setProperty(FlowContainer.flowNodes, flowNodes);

			Object result = container.evaluate(new HashMap<>());

			assertTrue(result instanceof Map);
			assertEquals(null, ((Map)result).get("result"));

			ret.setProperty(FlowReturn.result, "\"return\"");
			result = container.evaluate(new HashMap<>());

			assertEquals("return", ((Map)result).get("result"));

		} catch (FrameworkException ex) {

			fail("Exception during testFlowServlet: " + ex.getMessage());
		}

	}
}