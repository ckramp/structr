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
package org.structr.core.parser.function;

import org.structr.common.error.FrameworkException;
import org.structr.core.GraphObject;
import org.structr.schema.action.ActionContext;
import org.structr.schema.action.Function;

/**
 *
 */
public class AndFunction extends Function<Object, Object> {

	public static final String ERROR_MESSAGE_AND = "Usage: ${and(bool1, bool2)}. Example: ${and(\"true\", \"true\")}";

	@Override
	public String getName() {
		return "and()";
	}

	@Override
	public Object apply(final ActionContext ctx, final GraphObject entity, final Object[] sources) throws FrameworkException {

		boolean result = true;

		if (sources != null) {

			if (sources.length < 2) {
				return usage(ctx.isJavaScriptContext());
			}

			for (Object i : sources) {

				if (i != null) {

					try {

						result &= "true".equals(i.toString()) || Boolean.TRUE.equals(i);

					} catch (Throwable t) {

						return t.getMessage();

					}

				} else {

					// null is false
					return false;
				}
			}

		}

		return result;
	}


	@Override
	public String usage(boolean inJavaScriptContext) {
		return ERROR_MESSAGE_AND;
	}

	@Override
	public String shortDescription() {
		return "Returns the conjunction of the given arguments";
	}

}
